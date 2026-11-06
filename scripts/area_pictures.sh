#!/usr/bin/env bash
# The size previews of the brushes that only rework the marks there, before (5d286725, the explore branch before
# explore-area) and after: the hover ring over what a usual stroke changes, the bar's row and the readout dot, drawn by
# the app's engine (AreaPictures) into the file given (default: /tmp/effect-previews.png); the branch explore-area keeps
# it in docs/archive/pictures/explore/area/effect-previews.png.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-/tmp/effect-previews.png}
before_ref=5d286725
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
mkdir -p "$work/now" "$work/old" "$work/old_classes"
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$work/now" $(for s in $sources; do echo "$main/$s.java"; done) "$test/AreaPictures.java" 2>&1 | grep -v '^Note:' || true
java -cp "$work/now" io.github.mpdairy.monopaint.AreaPictures strip "$work/after.png"
git archive "$before_ref" "$main" | tar -x -C "$work/old"
javac -nowarn -d "$work/old_classes" -sourcepath "$work/old/app/src/main/java" "$test/AreaPictures.java" 2>&1 | grep -v '^Note:' || true
java -cp "$work/old_classes" io.github.mpdairy.monopaint.AreaPictures strip "$work/before.png"
java -cp "$work/now" io.github.mpdairy.monopaint.AreaPictures join "$work/before.png" "$work/after.png" "$out"
echo "wrote $out"
