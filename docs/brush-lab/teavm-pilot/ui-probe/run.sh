#!/usr/bin/env bash
# The browser app's layout probe: PilotC (the app's pure layouts) through TeaVM's JS and WasmGC, against the JVM.
#   bash docs/brush-lab/teavm-pilot/ui-probe/run.sh
set -euo pipefail
cd "$(dirname "$0")/../../../.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"; export PATH="$JAVA_HOME/bin:$PATH"
p=docs/brush-lab/teavm-pilot/ui-probe
./gradlew -q -p $p generateJavaScript generateWasmGC copyWasmGCRuntime
java -cp $p/build/classes/java/main io.github.mpdairy.monopaint.PilotC > $p/build/jvm-c.txt
node $p/check.cjs $p/build/jvm-c.txt   # prints, then Node may hang: Ctrl-C
