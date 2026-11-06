#!/usr/bin/env bash
# The new print and age tools (Riso, Letterpress, Cracks) drawn by the app's engine (NewPresetPictures) into the folder given (default:
# docs/archive/pictures/explore/presets): handwriting, a small drawing and Make Ink text, each finished.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-docs/archive/pictures/explore/presets}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers LaterMediaPictures TextPictures NewPresetPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.NewPresetPictures "$out"
