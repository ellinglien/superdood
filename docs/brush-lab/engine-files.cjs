// The Modular Brush Lab's engine as Brush Studio and Brush Scan build it, read from prototypes/modular-lab-src as it
// stands (never copied): its core files in the lab's build order, then every other engine file there, sorted (today
// newmods.js, voice.js and wet.js), so an addition to the lab reaches both pages when they are rebuilt. ui.js is the lab's page.
"use strict";
const fs = require("fs"), path = require("path");
const LAB = path.join(__dirname, "prototypes", "modular-lab-src");
const CORE = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "float.js", "parts.js", "checks.js"];
const NOT_ENGINE = new Set(["ui.js", "icons.js", "textures.js"]);
// planes.js wraps every other engine file's strokes, so it comes last.
const LAST = ["planes.js"];
function engineFiles() {
  const extra = fs.readdirSync(LAB).filter(f => f.endsWith(".js") && !CORE.includes(f) && !NOT_ENGINE.has(f) && !LAST.includes(f)).sort();
  return [...CORE.filter(f => fs.existsSync(path.join(LAB, f))), ...extra, ...LAST.filter(f => fs.existsSync(path.join(LAB, f)))];
}
module.exports = { LAB, engineFiles };
