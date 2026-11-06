#!/usr/bin/env bash
# The TeaVM pilot (roadmap step 6): the app's Java engine compiled to JS (and WebAssembly) by TeaVM, then
#   A: code reading and "This brush" words against docs/brush-code.json, the app on the JVM and the JS copy (5,000 codes);
#   B: the parity harness's scene drawn by every WebParity brush, against the app on the JVM, timed against the JS copy.
#   bash docs/brush-lab/teavm-pilot/run.sh [a|b]     results in docs/brush-lab/teavm-pilot/build/pilot/
set -euo pipefail
cd "$(dirname "$0")/../../.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
here=docs/brush-lab/teavm-pilot out=docs/brush-lab/teavm-pilot/build/pilot classes=docs/brush-lab/teavm-pilot/build/classes/java/main
mkdir -p "$out"
if [ "${1:-a}" = a ]; then
  ./gradlew -q -p "$here" generateJavaScript -Ppilot=A
  node "$here/pilot-a.cjs" codes "$out/codes.txt"
  java -cp "$classes" io.github.mpdairy.monopaint.PilotA < "$out/codes.txt" > "$out/jvm-a.txt"
  node "$here/pilot-a.cjs" compare "$out/codes.txt" "$out/jvm-a.txt" | tee "$out/a.txt"
fi
if [ "${1:-b}" = b ]; then
  ./gradlew -q -p "$here" generateJavaScript generateWasmGC copyWasmGCRuntime -Ppilot=B
  node scripts/web_parity.cjs scene "$out/scene.txt"
  # The reference: the app's engine on the JVM, the same loop, a warm-up pass first, on one core as the web has.
  mkdir -p "$out/jvm"
  java -DoneCore=true -cp "$classes" io.github.mpdairy.monopaint.PilotBJvm "$out/scene.txt" "$out/jvm" warm > "$out/jvm/times.txt"
  node "$here/pilot-b.cjs" "$out/scene.txt" "$out/jvm" ${2:+"$2"} > "$out/b-js.txt"
  node "$here/pilot-b.cjs" --wasm "$out/scene.txt" "$out/jvm" ${2:+"$2"} > "$out/b-wasm.txt"
  sed -n '/brushes pixel/,$p' "$out/b-js.txt"; sed -n '/brushes pixel/,$p' "$out/b-wasm.txt"
fi
