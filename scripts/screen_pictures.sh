#!/usr/bin/env bash
# Print Shading's riso, photocopy and soft dither beside Grain, Halftone and Dither, drawn by the app's engine
# (ScreenPictures) into the folder given (default: docs/archive/pictures/explore/screen), with each kind's time a pixel.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-docs/archive/pictures/explore/screen}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers WetSmudgePictures LaterMediaPictures ScreenPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.ScreenPictures "$out"
