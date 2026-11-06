// The web reader against docs/brush-code.json (docs/BRUSH-CODE.md), the file the app's CodeSpecChecks checks: the same
// styles, parts, choices and dials in code order, and every test code read as the app reads it. After build.cjs.
// usage: node docs/brush-lab/studio-src/code-spec-check.cjs
"use strict";
const fs = require("fs"), path = require("path"), E = require("./out/node-engine.cjs");
const spec = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "..", "brush-code.json"), "utf8"));
const bad = [], near = (a, b) => Math.abs(a - b) < 1e-6, round = v => Math.round(v * 1e6) / 1e6;
const laterless = part => part === "later" && !(E.CHOICES && E.CHOICES.later);
const holdsLater = t => t.parts && t.parts.later && t.parts.later.choice !== "off" && laterless("later");
// shortcut: options the app appended that the web lab hasn't yet (Print Shading's Risograph, Photocopy and Soft Dither,
// 2026-10-31; Maze, Brick, Basketweave, Waves, Honeycomb and Rings, 2026-11-06; Pixels and Stitches, the same day) wait as Later does: a dial with fewer web options than the spec's, and every test code using one past
// them. Drop this once the web lab has them (newmods.js's screens and SCREEN choices).
const newerOptions = new Set();
const holdsNewerOption = t => t.parts && Object.keys(t.parts).some(part => !laterless(part) && Object.entries(t.parts[part].dials).some(([key, v]) => {
  const sp = E.DIALS[part] && E.DIALS[part][key];
  return sp && sp.choices && v >= sp.choices.length;
}));
const tables = () => {
  if (spec.styles.join() !== E.STYLE_NAMES.join()) bad.push("styles: " + E.STYLE_NAMES.join());
  for (const { part, choices } of spec.parts) if (laterless(part)) continue; else choices.forEach((c, i) => {
    // Hand Hatch, retired, reads as Hatch: the web lab keeps it apart from its choices (newmods.js HAND_HATCH_AT).
    if (c.key === "handhatch") return;
    // shortcut: the nibs (explore-nibs: Ruling Pen, Twig, Fan, Brayer, Tip choices 13 to 16) are the app's only until
    // the web lab draws them; drop this once it does.
    if (["ruling", "twig", "fan", "roller"].includes(c.key)) return;
    const js = E.CHOICES[part][i];
    if (!js || js.k !== c.key) { bad.push(part + " choice " + i + ": " + (js && js.k) + ", not " + c.key); return; }
    const fresh = E.freshPart(part, c.key);
    if (js.dials.join() !== c.dials.map(d => d.key).join()) bad.push(part + "." + c.key + " dials: " + js.dials.join());
    else for (const d of c.dials) {
      const sp = E.DIALS[part][d.key];
      if (d.options && sp.choices && sp.choices.length < d.options) newerOptions.add(part + "." + d.key + " (" + sp.choices.length + " of " + d.options + ")");
      else if (d.options ? !sp.choices || sp.choices.length !== d.options : sp.min !== d.min || sp.max !== d.max) bad.push(part + "." + c.key + "." + d.key + "'s range");
      if (!near(round(fresh.d[d.key]), d.fresh)) bad.push(part + "." + c.key + "." + d.key + " fresh " + fresh.d[d.key] + ", not " + d.fresh);
    }
  });
};
// shortcut: until the web lab has Later (roadmap step 7: the parts table, its eighth segment, Drying's retired mischief
// split into it), every test code holding a Later waits; drop this once it does.
const waiting = [], waitingOptions = [];
const tests = () => {
  for (const t of spec.tests) {
    if (holdsLater(t)) { waiting.push(t.code); continue; }
    if (holdsNewerOption(t)) { waitingOptions.push(t.code); continue; }
    const r = E.readAnyCode(t.code);
    // A newer version's: an M code read as far as the web knows (r.newer), or a shape it doesn't know (newerShape).
    if (!t.reads) { if (r) bad.push("reads, but shouldn't: " + t.code); else if (E.newerShape(t.code) !== !!t.newer) bad.push("newer " + !t.newer + ": " + t.code); continue; }
    if (!r) { bad.push("doesn't read: " + t.code); continue; }
    const B = r.b, wrong = [];
    if (!!r.newer !== !!t.newer) wrong.push("newer " + !!r.newer);
    if (E.encodeM(B) !== t.writes) wrong.push("writes " + E.encodeM(B));
    if (B.name !== t.name || B.style !== t.style || B.seed !== t.seed || (B.nib || 0) !== (t.nib || 0)) wrong.push("name, style, seed or nib");
    for (const part of E.PARTS) {
      const want = t.parts[part], P = B[part];
      if (P.k !== want.choice) { wrong.push(part + " " + P.k); continue; }
      const fresh = E.normPart(part, E.freshPart(part, P.k));
      for (const key of Object.keys(P.d)) {
        const v = key in want.dials ? want.dials[key] : round(fresh.d[key]);
        if (!near(round(P.d[key]), v)) wrong.push(part + "." + key + " " + P.d[key] + ", not " + v);
      }
    }
    if (wrong.length) bad.push(t.code + ": " + wrong.join("; "));
  }
};
tables(); tests();
if (waiting.length) console.log("WAITING for the web lab's Later: " + waiting.length + " test codes\n  " + waiting.join("\n  "));
if (newerOptions.size) console.log("WAITING for the web lab's newer options of " + [...newerOptions].join(", ") + ": " + waitingOptions.length + " test codes\n  " + waitingOptions.join("\n  "));
if (bad.length) { console.log("FAIL: the web reader differs from docs/brush-code.json:\n  " + bad.join("\n  ")); process.exit(1); }
console.log("PASS: the web reader has docs/brush-code.json's styles, parts and dials, and reads its test codes as the app does");
