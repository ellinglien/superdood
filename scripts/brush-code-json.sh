#!/usr/bin/env bash
# Writes docs/brush-code.json (the brush code's tables and test codes, docs/BRUSH-CODE.md) from the app's enums: CodeSpecChecks write.
# usage: bash scripts/brush-code-json.sh   (needs the JDK from Android Studio)
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
main="$REPO/app/src/main/java/io/github/mpdairy/monopaint"
sources=$(sed -n 's/^sources=(\(.*\))$/\1/p' "$REPO/scripts/test_inkwash.sh")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -nowarn -d "$tmp" -sourcepath "$REPO/app/src/test/java" $(for s in $sources; do echo "$main/$s.java"; done) "$REPO/app/src/test/java/io/github/mpdairy/monopaint/CodeSpecChecks.java" 2>&1 | grep -v "^Note:" || true
cd "$REPO" && java -cp "$tmp" io.github.mpdairy.monopaint.CodeSpecChecks write
