# TeaVM pilot (roadmap step 6), 2026-10-31

Step 10 built on it: `web/engine/` (the WebAssembly build, its API, the shims, which moved there from `src/org`),
drawing Brush Studio and the live probe; the pilot stays as it was run.

The app's Java engine compiled to JS and to WebAssembly (GC) by TeaVM 0.16.0. Nothing here is wired into Studio or
probe.html. No app code changed: the app's sources (test_inkwash.sh's list) compile as they are, plus shims in `src/`.

    bash docs/brush-lab/teavm-pilot/run.sh        # both pilots; results in build/pilot/ (a.txt, b-js.txt, b-wasm.txt)
    bash docs/brush-lab/teavm-pilot/run.sh a|b    # one

Its own Gradle build (`../../../gradlew -p docs/brush-lab/teavm-pilot generateJavaScript generateWasmGC -Ppilot=A|B`), separate
from the app's.

## A: code reading and "This brush" words (works)

- `spec()` (CodeSpecChecks.spec, compiled) writes `docs/brush-code.json` byte for byte: tables and all 38 test codes.
- 5,000 rolled codes: compiled = app on the JVM on every line (reading and words).
- Against the JS copy: all 5,000 read the same; 3,518 have the same words. The other 1,482 are the copy's drift, not the
  compiler's (openers, flick/fling, Paint Load, Erasable sentences).
- 763 KB (217 KB gzipped): ResolvedBrush pulls in a lot of the engine. About 0.6 ms a code, vs 0.1 ms in the copy (regex).

## B: the stroke engine, parity harness strokes, 137 brushes (works; WasmGC exact)

| | same as JVM, pixel for pixel | ms a stroke | ms settling a stroke | size (gzipped) |
|---|---|---|---|---|
| TeaVM JS | 48 of 137 (the rest within 1 tone, wet paint up to 134) | 13.8 | 85.5 | 1257 KB (424) |
| TeaVM WasmGC | 136 of 137 (Stump: 77 px, 1 tone) | 7.5 | 51.8 | 1641 KB (572) |
| JS copy | (0.08% of pixels more than 1 grey off, mean) | 18 | 78 | engine.js+wet.js 138 KB (40); all engine files 717 KB (222) |
| JVM, one core, warm | reference | 5.1 | 11.4 | |

Node 22, this Mac. The JS target treats `float` as `double` (no `Math.fround`), which is why it drifts; WasmGC has real
f32. The Stump pixels are most likely JS `Math` (WasmGC imports it for sin, pow and so on) against Java's.

## What blocked compiling, and the seams

- `java.util.concurrent` gaps in TeaVM's class library: Future, FutureTask, ExecutorService, Executors, ThreadPoolExecutor,
  ThreadFactory, LinkedBlockingQueue, TimeoutException, locks.ReentrantLock, atomic.AtomicReferenceArray. Shimmed in
  `src/org/teavm/classlib/...` (TeaVM maps `T`-classes by name): work runs inline on the one thread; `FutureTask.get()` runs
  the work if no thread has.
- `Runtime.maxMemory()` missing: `src/.../TRuntime.java` replaces TeaVM's (the pilot's classes go first on its classpath);
  it also says one processor, so Cores and WetWatercolor work in place.
- QuickStroke's render thread waits on the pen's (wait/notify): can't run on one JS thread. The pilot draws every stroke
  live (`QuickStroke.benchAlways = false`; QuickStrokeChecks says the page ends the same). The JVM reference does the same.
  Step 10 needs a seam here: a quick stroke that renders at lift on the caller's thread.
- WasmGC: an export can't be called `name` (TeaVM puts statics on a function).
- No reflection, no Android classes, memory fine.
