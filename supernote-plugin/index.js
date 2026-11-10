/**
 * Lasso to Superdood: lasso pen strokes in a note, tap Superdood, and they come back drawn with the brush selected in
 * Superdood (the app ing.ell.mybaddrawings draws them: its LassoProvider), as a picture over the strokes.
 * A first experiment (2026-11-08): the strokes stay; the picture goes on top, lassoed, to move or delete. Tried on
 * the Manta through a try-out button (in git history: strokes put on the page by the plugin, as injected pen events
 * don't reach a note's ink).
 */
import {AppRegistry, Image, NativeModules} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import {PluginManager, PluginCommAPI, PluginNoteAPI, NativeUIUtils} from 'sn-plugin-lib';

AppRegistry.registerComponent(appName, () => App);
PluginManager.init();

const LASSO = 200, FILL = 201;
// Lasso paths arrive as strokes too (penType 4, measured on a Manta by the Snap plugin).
const LASSO_PEN = 4;
const SPREAD = 400;

PluginManager.registerButton(2, ['NOTE'], {
  id: LASSO,
  name: 'Superdood',
  icon: Image.resolveAssetSource(require('./assets/icon.png')).uri,
  editDataTypes: [0],
  showType: 0,
});
// Highlighter fill (the owner, 2026-11-09): the lasso's inside scrubbed with the brush, the lassoed writing left clear.
PluginManager.registerButton(2, ['NOTE'], {
  id: FILL,
  name: 'Superdood fill',
  icon: Image.resolveAssetSource(require('./assets/icon.png')).uri,
  editDataTypes: [0],
  showType: 0,
});

PluginManager.registerButtonListener({
  onButtonPress(event) {
    const work = event.id === LASSO ? run('draw') : event.id === FILL ? run('fill') : null;
    work?.catch(e => say('Superdood: ' + (e?.message ?? e)));
  },
});

function ok(res, what) {
  if (!res?.success) throw new Error(what + ': ' + (res?.error?.message ?? 'failed'));
  return res.result;
}
function say(text) {
  console.log(text);
  NativeUIUtils.showRattaDialog(text, '', 'OK', false);
}

/** The permission, asked for when not yet given ("declare, check, request", the SDK's guide). */
async function allow(permission) {
  if ((await PluginManager.hasPermission(permission)) >= 1) return; // 1 this time, 2 always
  const given = await PluginManager.requestPermission(permission);
  if (!(given >= 1)) throw new Error('Superdood needs ' + permission + ' to read and change the note.');
}

/** {@code method}: 'draw' (the strokes with Superdood's brush) or 'fill' (the highlighter fill). */
/** Px left clear each side of a stroke of the note's {@code thickness} under a fill: its half width (400 draws about 8 px, measured on the Manta 2026-11-09) and 2 more. */
const CLEAR = thickness => Math.max(2, thickness / 100 + 2);

/**
 * The lasso's outline, x then y (page px): the lasso's own path (a stroke of pen type 4); without one (a lasso made by
 * a plugin), the strokes' box, 30 px round.
 */
async function outline(elements, xy) {
  const box = p => {
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (let i = 0; i < p.length; i += 2) { x0 = Math.min(x0, p[i]); x1 = Math.max(x1, p[i]); y0 = Math.min(y0, p[i + 1]); y1 = Math.max(y1, p[i + 1]); }
    return {x0, y0, x1, y1};
  };
  const s = box(xy);
  const path = elements.find(e => e.type === 0 && e.stroke && e.stroke.penType === LASSO_PEN);
  if (path) {
    const points = (await path.stroke.points.getRange(0, await path.stroke.points.size())).flatMap(p => [p.x, p.y]);
    const l = box(points), cx = (s.x0 + s.x1) / 2, cy = (s.y0 + s.y1) / 2;
    // Only a path round the strokes (a hand-drawn lasso's path is untried, 2026-11-09: a plugin's lasso has none).
    if (points.length >= 6 && l.x0 <= cx && cx <= l.x1 && l.y0 <= cy && cy <= l.y1) return points;
    console.log('Superdood: lasso path not round the strokes: ' + JSON.stringify(l) + ' vs ' + JSON.stringify(s));
  }
  return [s.x0 - 30, s.y0 - 30, s.x1 + 30, s.y0 - 30, s.x1 + 30, s.y1 + 30, s.x0 - 30, s.y1 + 30];
}

async function run(method) {
  const t0 = Date.now();
  let lassoed = await PluginCommAPI.getLassoElements();
  // Read permission asked for only when the lasso can't be read without it (its dialog didn't show over a lasso, 2026-11-08).
  console.log('Superdood: lasso read ' + JSON.stringify(lassoed?.success) + ' ' + JSON.stringify(lassoed?.error));
  if (lassoed?.error?.code === 1503) {
    await allow('plugin.permission.FILE:READ');
    lassoed = await PluginCommAPI.getLassoElements();
  }
  const elements = ok(lassoed, 'Lasso');
  const strokes = elements.filter(e => e.type === 0 && e.stroke && e.stroke.penType !== LASSO_PEN);
  if (!strokes.length) return say('Lasso some pen strokes first.');
  const counts = [], xy = [], travel = [], clear = [];
  for (const e of strokes) {
    clear.push(CLEAR(e.thickness));
    const n = await e.stroke.points.size();
    const points = await e.stroke.points.getRange(0, n);
    const pressures = await e.stroke.pressures.getRange(0, await e.stroke.pressures.size());
    counts.push(points.length);
    points.forEach((p, i) => {
      xy.push(p.x, p.y);
      travel.push(Math.min(1, (pressures[i] ?? 2048) / 4096));
    });
  }
  // The page drawn on reaches past the strokes by SPREAD, room for a brush's spread; getPageSize would need read
  // permission, whose dialog never showed (2026-11-08). shortcut: a wash spreading past SPREAD is cut; widen it if one is.
  const lasso = method === 'fill' ? await outline(elements, xy) : [];
  let right = 0, bottom = 0;
  for (const p of [xy, lasso]) for (let i = 0; i < p.length; i += 2) { right = Math.max(right, p[i]); bottom = Math.max(bottom, p[i + 1]); }
  const size = {width: Math.ceil(right + SPREAD), height: Math.ceil(bottom + SPREAD)};
  const t1 = Date.now();
  const dir = await PluginManager.getPluginDirPath();
  const drawn = await NativeModules.Superdood.draw(method, dir, size.width, size.height, counts, xy, travel, lasso, clear);
  const t2 = Date.now();
  console.log('Superdood: ' + method + ', ' + strokes.length + ' strokes (thickness ' + strokes.map(e => e.thickness) + '), lasso ' + lasso.length / 2 + ' points, ' + xy.length / 2 + ' points, page ' + JSON.stringify(size) + ', read ' + (t1 - t0) + ' ms, drawn ' + (t2 - t1) + ' ms: ' + JSON.stringify(drawn));
  ok(await PluginCommAPI.setLassoBoxState(2), 'Lasso off');
  const inserted = await PluginNoteAPI.insertImage(drawn.path);
  // A permission's dialog doesn't show over a lasso (2026-11-09: the request waited for good), so it is given in Settings.
  if (inserted?.error?.code === 1501) return say('Superdood can\'t add its picture yet. In Settings, open Apps, then Plugins, then Superdood, then Permissions, and allow it to change notes.');
  ok(inserted, 'Insert');
  // The note puts the picture at the page's middle, lassoed, and the lasso's box still reads as the strokes' for
  // seconds after (measured 2026-11-08), so the picture's box is set outright onto the strokes.
  // shortcut: the box set is the picture's own size; if the note pads a picture's lasso, it is scaled that little.
  const box = {left: drawn.x, top: drawn.y, right: drawn.x + drawn.w, bottom: drawn.y + drawn.h};
  const moved = await PluginCommAPI.resizeLassoRect(box);
  console.log('Superdood: moved to ' + JSON.stringify(box) + ': ' + JSON.stringify(moved) + ', all in ' + (Date.now() - t0) + ' ms');
}
