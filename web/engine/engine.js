// The app's engine in the browser: WebEngine.java compiled to WebAssembly (GC) by TeaVM (bash web/engine/build.sh), behind
// a small API for Brush Studio, the live probe and, later, the app in the browser. A page, window or worker; Node too.
//   TeaVM's runtime (out/mbd-engine.wasm-runtime.js) first: a <script>, importScripts or require; then
//   if (MBDEngine.supported()) { const E = await MBDEngine.load(wasm, assets); ... }
//     wasm: mbd-engine.wasm's URL or bytes; assets: mbd-assets.bin's URL or bytes (the app's textures, build.sh packs them).
//   const page = E.page(w, h)                   a blank page as the drawing screen's (the Wash and Ink layers, wet paint)
//   const s = page.stroke(code, size, dark)     pen down with a brush code (null if the app doesn't read it); page.eraser(size, kinds)
//   s.add([[x, y, travel, ms, tiltX, tiltY], ...])   an input event's samples (the JS copy's Stroke.add): travel is the pen's
//                                               pressure after the Manta's curve, tilt in degrees, NaN upright; drawn at once
//   s.end()                                     lift: one Undo step
//   page.pen(tool, size, dark, kinds)           pen down with the bar's tool as the drawing screen starts it (WebEngine.pen:
//                                               the host's slot, or the eraser; mirrored as the page's mirrors), a stroke as above
//   page.scratch(inLab)                         pen down on a scratch pad with the Tools page's tool or Brush Lab's (WebEngine.scratch)
//   const c = E.crypt(key)                      a live page sealed with its link's key (LiveCrypt, AES-256-GCM, the Manta's
//                                               code: no WebCrypto on plain http): c.access, the hash a relay is given;
//                                               c.seal(bytes) under a fresh random nonce; c.open(sealed) throws unless sealed with the key;
//                                               c.n its number, for E.T.liveSaid (the host's id told by the key)
//   E.T                                         WebEngine's exports themselves: the drawing screen's layout, tools, gestures (live.html)
//   page.tick()   one frame of wet paint (or anything else settling); page.settling: whether more is left; page.dry()
//   page.changed()                              the rect changed since the last call (any E.T call on the page: a shape, the Lasso)
//   page.tones(x, y, w, h)                      the page as it shows, a byte a pixel (0 to 255)
//   page.ground(code, size, dark, x, y, w, h, greys)   marks under a brush's strokes, on its own layer and kind (pictures)
//   page.lay(x, y, w, h, greys, set, kinds, layer)     a live change received, as the Manta lays one, on its layer (1 to 3;
//                                               2 when left out, an older app's); page.picture(x, y, w, h, greys, layer)
//                                               the rect made a plain picture of greys (255 paper): this page's own Undo, on
//                                               the layer its change was drawn on (the layer in use when left out)
//   page.undo(), page.redo(), page.free(); page.canUndo, page.canRedo: whether either has anything to do
//   page.sharp(k, x, y, w, h)                   sharp dry brushes: page px x, y, w × h drawn k points a pixel (2, 4 or 8;
//                                               E.T.sharpStep(scale) picks it), (w k) × (h k) greys row by row, −1 where the
//                                               page shows as it is; page.sharpTile(k, tx, ty) one 64 px tile's (empty: none)
//   E.staysSharp(code)                          whether a brush's strokes are drawn sharp when zoomed
// Every call that changes the page returns the rect it changed, [x0, y0, x1, y1] inclusive (showPage's), or null.
// Greys are a byte a pixel, row by row, 255 clear.
"use strict";
(function (G) {
  // WasmGC here: a module holding one struct type validates.
  const supported = () => typeof WebAssembly === "object" && WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 127, 0]));
  const bytesOf = async src => typeof src === "string" ? new Uint8Array(await (await fetch(src)).arrayBuffer()) : src instanceof Uint8Array ? src : new Uint8Array(src);
  // mbd-assets.bin: a JSON line of [path, length] pairs, then their bytes in turn.
  function unpack(b) {
    const nl = b.indexOf(10), list = JSON.parse(new TextDecoder().decode(b.subarray(0, nl)));
    let at = nl + 1;
    return list.map(([path, n]) => [path, b.subarray(at, at += n)]);
  }
  const u8 = a => new Uint8Array(a.buffer, a.byteOffset, a.length);
  const i8 = a => new Int8Array(a.buffer, a.byteOffset, a.length);
  async function load(wasm, assets) {
    if (!G.TeaVM || !G.TeaVM.wasmGC) throw new Error("load TeaVM's runtime (mbd-engine.wasm-runtime.js) first");
    const [w, a] = await Promise.all([bytesOf(wasm), bytesOf(assets)]);
    const T = (await G.TeaVM.wasmGC.load(w)).exports;
    for (const [path, b] of unpack(a)) T.asset(path, i8(b));
    function Page(n) {
      // The changed rect since the last call, inclusive; null when nothing changed.
      const took = () => { const r = T.dirty(n); return r[2] > r[0] && r[3] > r[1] ? [r[0], r[1], r[2] - 1, r[3] - 1] : null; };
      const stroke = () => ({
        add(samples) { for (const q of samples) T.sample(n, q[0], q[1], q[2], q[3], q[4], q[5]); T.present(n); return took(); },
        end() { T.lift(n); return took(); }
      });
      return {
        id: n,
        stroke(code, size, dark) { return T.down(n, code, size, dark) ? stroke() : null; },
        eraser(size, kinds = 0) { T.eraser(n, size, kinds); return stroke(); },
        pen(tool, size, dark, kinds = 0, finger = false) { T.pen(n, tool, size, dark, kinds, !!finger); return stroke(); },
        scratch(inLab) { T.scratch(n, !!inLab); return stroke(); },
        tick() { T.tick(n); return took(); },
        changed: took,
        get settling() { return T.settling(n); },
        dry() { T.dry(n); return took(); },
        tones: (x, y, w, h) => u8(T.tones(n, x, y, w, h)).slice(),
        ground(code, size, dark, x, y, w, h, greys) { return T.ground(n, code, size, dark, x, y, w, h, i8(greys)); },
        lay(x, y, w, h, greys, set, kinds, layer = 2) { T.lay(n, x, y, w, h, i8(greys), !!set, kinds | 0, layer | 0); return took(); },
        picture(x, y, w, h, greys, layer = 0) { T.picture(n, x, y, w, h, i8(greys), layer | 0); return took(); },
        sharp: (k, x, y, w, h) => new Int32Array(T.sharpArea(n, k, x, y, w, h)),
        sharpTile: (k, tx, ty) => new Int32Array(T.sharpTile(n, k, tx, ty)),
        undo() { T.undo(n); return took(); },
        redo() { T.redo(n); return took(); },
        get canUndo() { return T.canUndo(n); },
        get canRedo() { return T.canRedo(n); },
        free() { T.free(n); }
      };
    }
    function crypt(key) {
      const c = T.crypt(key);
      return {
        n: c,  // its number, for T.liveSaid (LivePresence.guest tells the host's id by this key)
        access: T.access(c),
        // crypto.getRandomValues works on plain http too (crypto.subtle doesn't): 96 random bits a message, never reused.
        seal: raw => u8(T.seal(c, i8(crypto.getRandomValues(new Uint8Array(12))), i8(raw))).slice(),
        open(sealed) { const r = T.open(c, i8(sealed)); if (r == null) throw new Error("not sealed with this page's key"); return u8(r).slice(); }
      };
    }
    return { page: (w, h) => Page(T.page(w, h)), reads: code => T.reads(code), staysSharp: code => T.staysSharp(code), brushName: (code, names) => T.brushName(code, names.join("\n")), crypt, T };
  }
  const api = { supported, load };
  G.MBDEngine = api;
  if (typeof module === "object" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : self);
