// Builds docs/brush-lab/studio.html: Brush Studio, one self-contained page.
//   node docs/brush-lab/studio-src/build.cjs
// The engine is the Modular Brush Lab's, read from ../prototypes/modular-lab-src as it stands (never copied): its core
// files in the lab's build order, then any other engine file there (newmods.js and the like), so additions to the lab
// show up here when rebuilt. ui.js is the lab's page and is left out. The paper scans come from the lab's page, the
// typeface from the app's res/font (SIL OFL 1.1). The app's own engine, compiled to WebAssembly (web/engine), draws where the
// browser can run it, the lab's engine elsewhere. Also writes studio-src/out/node-engine.cjs for the Node checks.
"use strict";
const fs = require("fs"), path = require("path");
const { LAB, engineFiles } = require("../engine-files.cjs");
const HERE = __dirname, REPO = path.join(HERE, "..", "..", "..");
const read = f => fs.readFileSync(f, "utf8");

// ---------- the engine: the lab's core, in its build order, then whatever else the lab adds (engine-files.cjs) ----------
const files = engineFiles();
let engine = files.map(f => "// ---- modular-lab-src/" + f + "\n" + read(path.join(LAB, f))).join("\n;\n");
// An older oldmodel.js took the app's name lists at build time.
const NAMES = path.join(REPO, "app", "src", "main", "assets", "names");
for (const [k, f] of [["ADJECTIVES", "adjectives"], ["PRODUCE", "produce"]])
  if (engine.includes("\"@@" + k + "@@\"")) engine = engine.replace("\"@@" + k + "@@\"", JSON.stringify(read(path.join(NAMES, f + ".txt"))));

// ---------- the paper scans, as the lab's page carries them ----------
const labPage = read(path.join(HERE, "..", "prototypes", "modular-lab.html"));
const tm = /const TEXTURE_SRC = \{[^\n]*\};/.exec(labPage);
if (!tm) throw new Error("TEXTURE_SRC not found in modular-lab.html");
const textures = tm[0];

// ---------- the typeface: iA Writer Duo 400 and 700, embedded ----------
const FONT = path.join(REPO, "app", "src", "main", "res", "font");
const face = (file, weight) => "@font-face { font-family: \"iA Writer Duo\"; font-style: normal; font-weight: " + weight + "; font-display: block; src: url(data:font/ttf;base64," +
  fs.readFileSync(path.join(FONT, file)).toString("base64") + ") format(\"truetype\"); }";
const fonts = face("ia_writer_duo_regular.ttf", 400) + "\n" + face("ia_writer_duo_bold.ttf", 700);
const ofl = read(path.join(REPO, "app", "src", "main", "assets", "fonts", "OFL.txt"));
const esc = t => t.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

// ---------- the app's engine, compiled to WebAssembly (web/engine/build.sh; Gradle skips it when no Java changed) ----------
// Built here, not committed: the page always draws with the Java as it stands (a committed .wasm would go stale on the
// next engine change), and the page stays one file, as it is published (the artifact, superdood.org/studio).
const ENGINE_OUT = path.join(REPO, "web", "engine", "out");
require("child_process").execFileSync("bash", [path.join(REPO, "web", "engine", "build.sh")], { stdio: "inherit" });
const gz64 = f => require("zlib").gzipSync(fs.readFileSync(path.join(ENGINE_OUT, f)), { level: 9 }).toString("base64");

// ---------- the page ----------
const studio = ["qr.js", "render.js", "studio.js"].map(f => read(path.join(HERE, f)));
// The Community library (community.js) goes on the end of studio.js: it uses the page's names and adds to its ACT.
studio[2] += "\n;\n" + read(path.join(HERE, "community.js"));
// The Guide tab (guide.js, on the end of studio.js too) shows Brush Lab Dials' tiles (dial-demo-src: render.js, tiles.js,
// view.js and view.css, never copied), limited to what the app offers (app-features.json, from the app's enums).
const DIALS_SRC = path.join(HERE, "..", "dial-demo-src");
studio[2] += "\n;\n" + read(path.join(HERE, "guide.js"));
const guide = "const APP_FEATURES = " + JSON.stringify(JSON.parse(read(path.join(HERE, "..", "app-features.json")))) + ";\n" +
  ["render.js", "tiles.js", "view.js"].map(f => read(path.join(DIALS_SRC, f))).join("\n;\n");
// Brush export (export.js) with its Procreate template, the archive of a real brush (procreate-template.json, MIT).
const tpl = JSON.parse(read(path.join(HERE, "procreate-template.json")));
const exporter = "const PROCREATE_TEMPLATE = " + JSON.stringify({ source: tpl.source, license: tpl.license, archive: tpl.archive }) + ";\n" + read(path.join(HERE, "export.js"));
const parts = { FONTS: fonts, CSS: read(path.join(HERE, "studio.css")) + "\n" + read(path.join(DIALS_SRC, "view.css")) + "\n" + read(path.join(HERE, "guide.css")),
  TEXTURES: textures, ENGINE: "\"use strict\";\n" + engine, GUIDE: "\"use strict\";\n" + guide,
  RENDER: "\"use strict\";\n" + studio[1], QR: "\"use strict\";\n" + studio[0], EXPORT: "\"use strict\";\n" + exporter, STUDIO: "\"use strict\";\n" + studio[2], OFL: esc(ofl),
  ENGINE_FILES: files.join(", "),
  TEAVM: read(path.join(ENGINE_OUT, "mbd-engine.wasm-runtime.js")), MBDENGINE: read(path.join(REPO, "web", "engine", "engine.js")),
  WASM: gz64("mbd-engine.wasm"), WASMASSETS: gz64("mbd-assets.bin"),
  // Last: its mark is inside studio.js. The owner's favourite brushes, one code a line.
  FAVOURITES: JSON.stringify(fs.existsSync(path.join(HERE, "..", "favourites.txt")) ? read(path.join(HERE, "..", "favourites.txt")).split("\n").map(l => l.trim()).filter(l => l && !l.startsWith("#")) : []) };
for (const [k, v] of Object.entries(parts)) if (/<\/script/i.test(v) || ((k === "CSS" || k === "FONTS") && /<\/style/i.test(v))) throw new Error("closing tag inside " + k);
let page = read(path.join(HERE, "page.html"));
for (const [k, v] of Object.entries(parts)) {
  const mark = "/*@@" + k + "@@*/";
  if (!page.includes(mark)) throw new Error("no mark " + mark);
  page = page.split(mark).join(k === "FAVOURITES" ? v + "||" : v);
}
if (/@@[A-Z_]+@@/.test(page)) throw new Error("unfilled mark " + /@@[A-Z_]+@@/.exec(page)[0]);
const out = path.join(HERE, "..", "studio.html");
fs.writeFileSync(out, page);
// The Community library's admin page (the admin page's source, not in this repo): the same engine, to draw each brush waiting.
if (fs.existsSync(path.join(HERE, "..", "community-server", "admin.html"))) {  // not in the public repo
let admin = read(path.join(HERE, "..", "community-server", "admin.html"));
for (const k of ["FONTS", "CSS", "TEXTURES", "ENGINE", "RENDER"]) admin = admin.split("/*@@" + k + "@@*/").join(parts[k]);
fs.writeFileSync(path.join(HERE, "..", "community-admin.html"), admin);
}
// The engine and the tile renderer for Node (no DOM): the checks load this.
fs.mkdirSync(path.join(HERE, "out"), { recursive: true });
fs.writeFileSync(path.join(HERE, "out", "node-engine.cjs"), "\"use strict\";\n" + engine + "\n;\n" + studio[1] + "\n;\n" + studio[0] + "\n;\n" + exporter +
  "\nmodule.exports = { appRead, appCode, appParts, appEdit, useWasm: E => { STUDIO_WASM = E; }, TEXTURES, PROCREATE_TEMPLATE, writeBplist, procreateSet, procreateValues, exportGrain, SHAPE_PX, GRAIN_PX, switchChoice, studioJob, encodeM, readAnyCode, newerShape, freshPart, normPart, rollStyle, normBrush, cloneBrush, PARTS, CHOICES, DIALS, selfTest, qrMatrix, builtins, WILDCARD, STYLE_NAMES, plainWords, brushWords, sayWords, VOICE_MISSED };\n");
console.log("built " + path.relative(REPO, out) + " " + (page.length / 1024).toFixed(0) + " KB; engine: " + files.join(", "));
