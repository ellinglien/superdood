#!/usr/bin/env bash
# The web parity harness: every preset and the ten first-launch brushes, the same strokes drawn by the app's engine
# (WebParity.java, on the desktop) and Brush Studio's (scripts/web_parity.cjs, in Node); per brush the % of pixels more
# than one of the Manta's 16 greys apart, and app | web | diff side by side. Then the same for the app's engine compiled to
# WebAssembly (web/engine), which should match the JVM pixel for pixel.
#   bash scripts/web_parity.sh [name regex]   → build/web-parity/report.txt and grid.png; report-wasm.txt and grid-wasm.png
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" PATH="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin:$PATH"
out=build/web-parity classes=build/web-parity/classes
rm -rf "$out"; mkdir -p "$classes"
# The app's engine as the desktop checks compile it (test_inkwash.sh's sources), and the harness.
eval "$(grep -E '^sources=\(' scripts/test_inkwash.sh)"
main=app/src/main/java/io/github/mpdairy/monopaint test=app/src/test/java/io/github/mpdairy/monopaint
javac -nowarn -d "$classes" $(for s in "${sources[@]}"; do echo "$main/$s.java"; done) \
  $test/WebParity.java 2>&1 | grep -v '^Note:' || true
node scripts/web_parity.cjs scene "$out/scene.txt"
java -cp "$classes" io.github.mpdairy.monopaint.WebParity "$out/scene.txt" "$out" ${1:+"$1"}
node scripts/web_parity.cjs compare "$out" ${1:+"$1"}
# The app's engine as WebAssembly (web/engine), the same scene through its API: report-wasm.txt, grid-wasm.png.
bash web/engine/build.sh
node scripts/web_parity.cjs compare-wasm "$out" ${1:+"$1"}
