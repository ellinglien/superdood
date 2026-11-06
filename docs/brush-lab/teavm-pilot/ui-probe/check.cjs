// PilotC's layouts compiled by TeaVM (JS, WasmGC) against the JVM's, line for line.  node check.cjs <jvm-c.txt>
"use strict";
const fs = require("fs"), path = require("path"), G = path.join(__dirname, "build", "generated", "teavm");
(async () => {
  const jvm = fs.readFileSync(process.argv[2], "utf8");
  const js = require(path.join(G, "js", "pilot-c.cjs")).all();
  require(path.join(G, "wasm-gc", "pilot-c.wasm-runtime.js"));
  const wasm = (await globalThis.TeaVM.wasmGC.load(path.join(G, "wasm-gc", "pilot-c.wasm"))).exports.all();
  for (const [name, out] of [["JS", js], ["WasmGC", wasm]]) {
    const a = out.split("\n"), b = jvm.split("\n"), bad = a.filter((l, i) => l !== b[i]);
    console.log(`${name}: ${a.length - 1 - bad.length} of ${b.length - 1} screens the same as the JVM`);
    bad.forEach(l => console.log("  differs: " + l));
  }
  const kb = f => (fs.statSync(path.join(G, f)).size / 1024).toFixed(0) + " KB";
  console.log(`sizes: JS ${kb("js/pilot-c.cjs")}, WasmGC ${kb("wasm-gc/pilot-c.wasm")}`);
  process.stdout.write(jvm);
  // shortcut: Node 22 here never exits after a WasmGC load (even process.exit); Ctrl-C once the lines are out.
  process.exit(0);
})();
