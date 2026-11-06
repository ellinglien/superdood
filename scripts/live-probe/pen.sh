#!/usr/bin/env bash
# Builds PenStroke.java (a stylus stroke with pressure and tilt, injected as the shell user) and pushes it to the
# Manta as /data/local/tmp/pen.dex. Run one: adb shell 'CLASSPATH=/data/local/tmp/pen.dex app_process / PenStroke <file>'.
set -euo pipefail
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
out=$(mktemp -d); trap 'rm -rf "$out"' EXIT
javac -source 8 -target 8 -nowarn -cp "$sdk/platforms/android-34/android.jar" -d "$out" "$(dirname "$0")/PenStroke.java"
"$sdk/build-tools/35.0.0/d8" --min-api 26 --output "$out" "$out/PenStroke.class"
adb push -q "$out/classes.dex" /data/local/tmp/pen.dex
