#!/usr/bin/env bash
# The "Start Here" notebook (StartHereTemplate): a cover and ten pages for the owner to draw a guide in, each page's light
# grey typed guide on its own bottom layer; written to the folder given (default: /tmp/start-here), its pages' previews
# in previews/ there.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/start-here}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) "$test/StartHereTemplate.java" 2>&1 | grep -v '^Note' || true
java -Djava.awt.headless=true -cp "$classes" io.github.mpdairy.monopaint.StartHereTemplate "$out" app/src/main/res/font "$out/previews"
