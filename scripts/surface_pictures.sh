#!/usr/bin/env bash
# Surface: every surface across a page's width with a pencil pass and a wash, the same wash on Smooth, Cold press and
# Cotton, and the old scans against the new surfaces, drawn by the app's engine (SurfacePictures) into the folder given
# (default: /tmp/surface-pictures); the branch explore-surface keeps them in docs/archive/pictures/explore/surface.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/surface-pictures}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -Xmx3g -cp "$classes" io.github.mpdairy.monopaint.SurfacePictures "$out"
