#!/usr/bin/env bash
# Hold to Snap drawn by the app's engine (HoldSnapPictures): rough shapes beside the same strokes snapped, for several
# brushes, into the folder given (default: docs/archive/pictures/explore/snap).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-docs/archive/pictures/explore/snap}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers WetSmudgePictures HoldSnapPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.HoldSnapPictures "$out"
