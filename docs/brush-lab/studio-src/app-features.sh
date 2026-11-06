#!/usr/bin/env bash
# Writes docs/brush-lab/app-features.json from the app's enums (StudioGuideChecks write): what Studio's Guide tab shows.
# usage: bash docs/brush-lab/studio-src/app-features.sh   (needs the JDK from Android Studio); then rebuild Studio.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; REPO="$(cd "$HERE/../../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
main="$REPO/app/src/main/java/io/github/mpdairy/monopaint"
sources=$(sed -n 's/^sources=(\(.*\))$/\1/p' "$REPO/scripts/test_inkwash.sh")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -nowarn -d "$tmp" $(for s in $sources; do echo "$main/$s.java"; done) "$REPO/app/src/test/java/io/github/mpdairy/monopaint/StudioGuideChecks.java" 2>&1 | grep -v "^Note:" || true
cd "$REPO" && java -cp "$tmp" io.github.mpdairy.monopaint.StudioGuideChecks write
