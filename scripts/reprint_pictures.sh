#!/usr/bin/env bash
# Reprint over a journal page (handwriting, a pencil sketch, a grey wash): the Photocopier, Newsprint, a Riso, a Soft dither and a light touch, drawn by the app's engine (ReprintPictures) into the folder given
# (default: /tmp/reprint-pictures); the branch explore-reprint keeps them in docs/archive/pictures/explore/reprint.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/reprint-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.ReprintPictures "$out"
