#!/bin/bash
# Builds docs/site/install.js, the install page's one script (deployed with docs/site's other files).
set -euo pipefail
cd "$(dirname "$0")"
[ -d node_modules ] || npm ci --ignore-scripts --no-audit --no-fund
# The Tango library is MIT: its notice rides at the top of the bundle.
npx esbuild install.js --banner:js="/*! Superdood installer, superdood.org. Uses ya-webadb (Tango): $(sed 's/\*\//* \//g' node_modules/@yume-chan/adb/LICENSE) */" --bundle --minify --format=iife --target=chrome100 --legal-comments=eof --outfile=../../site/install.js
