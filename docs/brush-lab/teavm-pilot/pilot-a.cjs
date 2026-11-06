// Pilot A: the app's code reading and "This brush" words compiled by TeaVM (build/…/pilot-a.cjs), against
//   1. docs/brush-code.json, which the app's CodeSpecChecks writes: the compiled spec() must write it byte for byte;
//   2. the app itself on the JVM (PilotA's main), on 5,000 rolled codes: every line the same;
//   3. the hand-kept JS copy (prototypes/modular-lab-src: readAnyCode + encodeM, brushWords) on the same codes.
//   node pilot-a.cjs codes <out.txt>                 writes the rolled codes
//   node pilot-a.cjs compare <codes.txt> <jvm.txt>    the three comparisons
"use strict";
const fs = require("fs"), path = require("path");
const { LAB, engineFiles } = require("../engine-files.cjs");
const REPO = path.join(__dirname, "..", "..", "..");
const lab = () => new Function(engineFiles().map(f => fs.readFileSync(path.join(LAB, f), "utf8")).join("\n;\n") +
  "\nreturn { readAnyCode, encodeM, rollStyle, normBrush, brushWords, PARTS, CHOICES, DIALS, STYLE_NAMES, WILDCARD };")();

function codes(n) {
  const E = lab(), keep = Math.random, out = [];
  let s = 20261031; Math.random = () => ((s = Math.imul(s ^ (s >>> 15), 0x2c1b3c6d) + 0x6d2b79f5 >>> 0) / 4294967296);
  // Half rolled by style as Randomize rolls them, half any choice with any dial across its whole range (codes.cjs's way).
  for (let i = 0; i < n / 2; i++) out.push(E.encodeM(E.rollStyle(E.STYLE_NAMES[i % E.STYLE_NAMES.length])));
  for (let i = 0; i < n / 2; i++) {
    const B = E.rollStyle(E.WILDCARD);
    for (const part of E.PARTS) {
      const list = E.CHOICES[part].filter(c => c.k !== "custom"), c = list[(Math.random() * list.length) | 0], P = { k: c.k, d: {} };
      for (const key of c.dials) { const sp = E.DIALS[part][key]; if (sp.hide && part === "hand") continue; P.d[key] = sp.choices ? (Math.random() * sp.choices.length) | 0 : sp.min + (sp.max - sp.min) * Math.random(); }
      B[part] = P;
    }
    out.push(E.encodeM(E.normBrush(B)));
  }
  Math.random = keep;
  return out;
}

function compare(codesFile, jvmFile) {
  const T = require("./build/generated/teavm/js/pilot-a.cjs"), E = lab();
  let ok = true;
  const t0 = performance.now(), spec = T.spec(), specMs = performance.now() - t0;
  const same = spec === fs.readFileSync(path.join(REPO, "docs", "brush-code.json"), "utf8");
  console.log(`1. brush-code.json: compiled spec() ${same ? "writes it byte for byte" : "DIFFERS"} (${specMs.toFixed(0)} ms, its 38 test codes read)`);
  ok &&= same;
  const cs = fs.readFileSync(codesFile, "utf8").trim().split("\n"), jvm = fs.readFileSync(jvmFile, "utf8").replace(/\n$/, "").split("\n");
  const t1 = performance.now(), mine = cs.map(c => T.read(c) + "\t" + T.words(c)), ms = performance.now() - t1;
  const t2 = performance.now(); cs.forEach(c => { const r = E.readAnyCode(c); if (r) { E.encodeM(r.b); E.brushWords(r.b); } }); const jsMs = performance.now() - t2;
  const vsJvm = mine.filter((l, i) => l !== jvm[i]);
  console.log(`2. app on the JVM: ${cs.length - vsJvm.length} of ${cs.length} codes read and worded the same` + (vsJvm.length ? "\n  " + vsJvm.slice(0, 3).join("\n  ") : ""));
  ok &&= !vsJvm.length;
  let reads = 0, words = 0; const off = [];
  cs.forEach((c, i) => {
    const [read, w] = mine[i].split("\t"), r = E.readAnyCode(c);
    const jsRead = r ? E.encodeM(r.b) : "null", jsWords = r ? E.brushWords(r.b) : "";
    if (read.replace(/ newer$/, "") === jsRead) reads++; else off.push("read  " + c + "\n    app " + read + "\n    js  " + jsRead);
    if (w === jsWords) words++; else off.push("words " + c + "\n    app " + w + "\n    js  " + jsWords);
  });
  console.log(`3. JS copy: ${reads} of ${cs.length} read the same, ${words} worded the same` + (off.length ? "; first differences:\n  " + off.slice(0, 4).join("\n  ") : ""));
  console.log(`   time for ${cs.length} codes: compiled ${ms.toFixed(0)} ms, JS copy ${jsMs.toFixed(0)} ms`);
  process.exit(ok ? 0 : 1);
}

if (require.main === module) {
  const [cmd, a, b] = process.argv.slice(2);
  if (cmd === "codes") fs.writeFileSync(a, codes(5000).join("\n") + "\n");
  else if (cmd === "compare") compare(a, b);
  else { console.error("node pilot-a.cjs codes <file> | compare <codes> <jvm>"); process.exit(2); }
}
