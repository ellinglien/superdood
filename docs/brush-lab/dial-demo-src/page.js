// ===================================================================================================================
// Brush Lab Dials: the page. Every part, choice and dial of the lab (tiles.js), shown by view.js (which Brush Studio's
// Guide tab shares); this file adds the page's Pause and Steps.
// ===================================================================================================================
const $ = id => document.getElementById(id);
const VIEW = dialGuide({ main: $("main"), nav: $("parts"), tiles: demoTiles(),
  parts: DEMO_PARTS.map(p => ({ key: p, label: ROLE[p].label, blurb: ROLE[p].blurb, intro: PART_INTRO[p] })),
  src: $("engine").textContent + "\n;\n" + $("render").textContent, all: /[?&]all\b/.test(location.search) });
const { TILES, R, STATE } = VIEW;

function setStrip(on) {
  VIEW.setStrip(on);
  $("steps").setAttribute("aria-pressed", String(on));
  $("steps").textContent = on ? "Animate" : "Steps";
  $("play").hidden = on;
}
function setPlaying(on) {
  VIEW.setPlaying(on);
  $("play").textContent = on ? "Pause" : "Play";
  $("play").setAttribute("aria-pressed", String(!on));
}

async function start() {
  $("play").onclick = () => setPlaying(!STATE.playing);
  $("steps").onclick = () => setStrip(!STATE.strip);
  await loadTextures();
  VIEW.start(/[?&]inpage\b/.test(location.search));
  if (/[?#&]steps\b/.test(location.search + location.hash) || STATE.strip) setStrip(true);
  document.documentElement.dataset.ready = "1";
}
window.DEMO = { TILES, R, STATE };
start();
