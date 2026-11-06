#!/usr/bin/env bash
# Later's media (Salt, Plastic Wrap, Feathering, Foxing) drawn by the app's engine (LaterMediaPictures) into the folder
# given (default: docs/archive/pictures/later-media). See docs/superpowers/specs/2026-10-31-brush-system-roadmap.md.
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-docs/archive/pictures/later-media}
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=$(sed -n 's/^sources=(\(.*\))/\1/p' scripts/test_inkwash.sh)
checks=$(sed -n 's/^checks=(\(.*\))/\1/p' scripts/test_inkwash.sh)
helpers=$(sed -n 's/^helpers=(\(.*\))/\1/p' scripts/test_inkwash.sh)
javac -nowarn -d "$classes" $(for s in $sources; do echo "$main/$s.java"; done) \
  $(for c in $checks $helpers WetSmudgePictures LaterMediaPictures; do echo "$test/$c.java"; done) 2>&1 | grep -v '^Note:' || true
java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaPictures "$out" "${2:-}"
