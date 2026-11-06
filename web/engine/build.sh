#!/usr/bin/env bash
# Builds the app's engine to WebAssembly: web/engine/out/mbd-engine.wasm, its TeaVM runtime (mbd-engine.wasm-runtime.js) and
# the assets it reads (mbd-assets.bin); web/engine/engine.js is its API.
# Brush Studio's build runs this; so can the live probe's relay. Gradle skips it when no Java changed.
#   bash web/engine/build.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -q -p web/engine generateWasmGC copyWasmGCRuntime
mkdir -p web/engine/out
cp web/engine/build/generated/teavm/wasm-gc/mbd-engine.wasm web/engine/build/generated/teavm/wasm-gc/mbd-engine.wasm-runtime.js web/engine/out/
# The app's assets the engine reads (its paper scans; the kits, the brushes' personalities and the names' lists, which
# the Tools page and Brush Lab read), packed: a JSON line of [path, length], then the bytes (engine.js unpack).
node -e '
const fs = require("fs"), root = "app/src/main/assets/";
const files = [["textures", ".bin"], ["brushes", ".txt"], ["names", ""]].flatMap(([dir, end]) => fs.readdirSync(root + dir).filter(f => f.endsWith(end)).sort().map(f => dir + "/" + f));
const bytes = files.map(f => fs.readFileSync(root + f));
fs.writeFileSync("web/engine/out/mbd-assets.bin", Buffer.concat([Buffer.from(JSON.stringify(files.map((f, i) => [f, bytes[i].length])) + "\n"), ...bytes]));'
