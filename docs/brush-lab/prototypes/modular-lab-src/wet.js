// ===================================================================================================================
// Wet paint (the wet paint spec; Drying's Wet, life "wetpaint"): the app's wet canvas, ported from its Java as the spec
// (WetWatercolor, MonoPaint's wet canvas in whole-canvas mode, upstream 8743286, MIT, © mpdairy; WetCanvas,
// WetPaintStroke, FootprintSweep, BrushInk, PaintLoad). Wet strokes lay their footprint into the wet paint, final under
// the pen; between strokes it moves a frame at a time (blooms, dark edges, bleed, granulation; opaque paint evening
// out) and stays wet across strokes until dried. A Stroke whose host has getWet() lays into it; without one (samples,
// loops) Wet lays as Instant, as the app does wherever it has no wet canvas.
//   host.getWet()          the wet canvas (a WetCanvas on the stroke's surface), made the first time
//   canvas.step(ms)        moves it for about ms of work (a page's loop, requestAnimationFrame); returns ms until the
//                          next step, or -1 at rest. canvas.settle() moves it until it rests (a check, a replay)
//   canvas.takeDirty()     the rect it changed since last asked, or null
// The page is the surface's: tones 0 to 255 as the app's, paper (PAPER_TONE) read as 255.
// shortcut: no Erasable kept tones, no memory cap (roomFor), no Pastels/Dry brush joining, no gravity (a level page);
// add them if the web gets a tilted page or wet strokes that erase part.
// ===================================================================================================================
const WP = {
  FRAME_MS: 150, FLOW_FRAME_MS: 50, PACED_TILES: 64, SIDE: 16, PIXELS: 256, FRAMES: 20, TILES_PER_FRAME: 96, WATER_FRAMES: 40,
  DRYING: .985, STILL: .4, DAMP: .3, HUGE_TILES: 2048, HUGE_STEP: 2, LOOSE: .7, DEPTH: 2, DISSOLVE: .8, SHOWS: .4, MIX: .25,
  SPREAD: .25, WASH_SPREAD: 0, WASH_FALL: .4, SPREAD_STEPS: 8, POOL: .7, LIGHT_MIXES: .35, MERGES: .45, MIXES: .8, MIXES_FROM: .45,
  COMBINE: .7, COMBINES: .05, FLOODS: .15, BLEEDS: .62, EDGE: 4, TIDE: .62, REACH: 8
};
WP.SPAN = WP.SIDE + 2 * WP.REACH;
const f32 = Math.fround;
const wetMobility = fresh => f32(fresh * fresh * fresh);
const wetSmooth = t => { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); };
const wetMargin = downhill => 8 + Math.round(24 * Math.max(0, downhill));
const bitCount = n => { n = n - ((n >>> 1) & 0x55555555); n = (n & 0x33333333) + ((n >>> 2) & 0x33333333); return (((n + (n >>> 4)) & 0xF0F0F0F) * 0x1010101) >>> 24; };
const anySet = a => { for (let i = 0; i < a.length; i++) if (a[i]) return true; return false; };

class WetTile {
  constructor(x, y) {
    const P = WP.PIXELS;
    this.x = x; this.y = y;
    this.base = new Uint8Array(P); this.pigment = new Float32Array(P); this.target = new Float32Array(P); this.next = new Float32Array(P);
    this.shade = new Int16Array(P);
    this.wet = new Uint8Array(P); this.touched = new Uint8Array(P); this.water = new Uint8Array(P); this.flowing = new Uint8Array(P);
    this.wetRows = new Int32Array(WP.SIDE); this.wetColumns = new Int32Array(WP.SIDE);
    this.pull = null; this.mobility = null; this.nextPull = null; this.washed = null;
    this.gran = 0; this.rim = 0; this.mottle = 0; this.tide = 0; this.grain = null; this.blob = null;
    this.edge = null; this.edgeStale = true; this.wave = null; this.nextWave = null; this.combined = null; this.bled = false; this.inFrame = -1;
    this.paint = null; this.plain = null; this.reserve = null; this.stuck = null; this.soaked = null;
    this.frames = 0; this.wetPixels = 0; this.flowingPixels = 0; this.loose = false; this.dampFrames = 0; this.meanInk = 0; this.meanFresh = 0;
    this.flowLeft = 0; this.flowRight = 0; this.flowTop = 0; this.flowBottom = 0; this.spread = 0; this.spreadInk = 0; this.spreadSlot = -1; this.spreadFrame = -1;
  }
}
// The page as the app's ToneDocument: tones 0 to 255 (paper 255), its opacity (whether a pixel holds paint) and what a
// stroke found there. Writes go to page, the base the next stroke reads and the dirty rect.
class WetPage {
  constructor(surf) { this.s = surf; this.width = surf.w; this.height = surf.h; this.dirty = null; this.opaque = new Uint8Array(surf.w * surf.h); }
  tone(x, y) { const v = this.s.page[y * this.width + x]; return v >= PAPER_TONE - .5 ? 255 : Math.round(v); }
  strokeBaseTone(x, y) { const v = this.s.base[y * this.width + x]; return v >= PAPER_TONE - .5 ? 255 : Math.round(v); }
  // shortcut: opacity is "darker than paper, or laid opaque", not the layer's coverage, so white paint laid by another stroke reads as paper.
  opacity(x, y) { const i = y * this.width + x; return this.opaque[i] || this.s.page[i] < PAPER_TONE - .5 ? 255 : 0; }
  setTone(x, y, t) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    const i = y * this.width + x, v = Math.min(PAPER_TONE, t);
    if (this.s.page[i] === v) return;
    this.s.page[i] = v;
    if (this.dirty) grow(this.dirty, [x, y, x, y]); else this.dirty = [x, y, x, y];
  }
}

// The wet model (WetWatercolor), whole-canvas mode, as WetCanvas uses it.
class WetWatercolor {
  constructor(page, amount) {
    this.document = page; this.columns = Math.ceil(page.width / WP.SIDE);
    this.tiles = new Array(this.columns * Math.ceil(page.height / WP.SIDE)).fill(null);
    this.active = []; this.batch = []; this.spreading = [];
    this.spreadInk = new Float32Array(16); this.spreadChange = new Float32Array(16); this.links = new Float32Array(16); this.downhill = new Float32Array(16);
    this.spreadPixels = new Int32Array(16); this.linked = new Int32Array(32);
    this.frame = 0; this.spreadCandidates = null; this.spreadPhase = 0; this.spreadCursor = 0; this.spreadPairs = 0; this.spreadStep = 0;
    this.gravityX = 0; this.gravityY = 0; this.strokePixels = 0; this.frameRemaining = 0; this.advancedTiles = 0;
    this.hasFlowing = false; this.flowingFrame = false; this.looseWater = false; this.looseSoFar = false; this.runTiles = 0; this.step = 1;
    this.drying = WP.DRYING; this.washDrying = WP.DRYING; this.wakeFrames = WP.FRAMES; this.paintOnly = false; this.dryStays = false;
    this.wash = null; this.waves = 0; this.strokeWave = 0; this.joined = new Int16Array(16);
    this.setWetness(amount);
    // Working space (the Java's Scratch).
    this.sc = { spread: new Float32Array(WP.PIXELS), nearInk: new Float32Array(9), nearFresh: new Float32Array(9), nearInky: new Float32Array(9),
      near: new Float32Array(9), ink: new Float32Array(WP.SPAN * WP.SPAN), free: new Float32Array(WP.SPAN * WP.SPAN) };
  }
  setWetness(amount) { this.wetness = f32(Math.max(0, Math.min(100, amount)) / 100); }
  setFrames(frames) { this.wakeFrames = Math.max(1, frames); }
  washOf(wave) { while (wave > 0 && wave < this.joined.length && this.joined[wave] !== 0) wave = this.joined[wave]; return wave; }
  join(a, b) {
    a = this.washOf(a); b = this.washOf(b);
    if (a === b || a <= 0 || b <= 0) return;
    const most = Math.max(a, b);
    if (most >= this.joined.length) { const j = new Int16Array(Math.max(most + 1, this.joined.length * 2)); j.set(this.joined); this.joined = j; }
    this.joined[most] = Math.min(a, b);
  }
  beginStroke() { this.strokePixels = 0; this.strokeWave = 0; for (const t of this.tiles) if (t) t.touched.fill(0); }
  isAnimating() { return this.active.length > 0; }
  framePending() { return this.frameRemaining > 0; }
  flowing() { return this.flowingFrame && this.active.length > 0; }
  flowFrameMillis() { return !this.flowing() || this.looseWater ? 0 : Math.round(WP.FLOW_FRAME_MS * Math.max(0, 1 - this.runTiles / WP.PACED_TILES)); }
  rest() { for (const t of this.active) t.frames = 0; this.active.length = 0; this.frameRemaining = 0; this.spreadCandidates = null; this.spreading.length = 0; this.runTiles = 0; this.looseWater = this.looseSoFar = false; }
  tileAt(x, y) {
    const key = ((y / WP.SIDE) | 0) * this.columns + ((x / WP.SIDE) | 0);
    return this.tiles[key] ??= new WetTile(((x / WP.SIDE) | 0) * WP.SIDE, ((y / WP.SIDE) | 0) * WP.SIDE);
  }
  tileOf(x, y) { return this.tiles[((y / WP.SIDE) | 0) * this.columns + ((x / WP.SIDE) | 0)]; }
  wake(tile, frames = this.wakeFrames) { if (tile.frames === 0) this.active.push(tile); tile.frames = Math.max(tile.frames, frames); }
  wakeAround(left, top, right, bottom) {
    const D = this.document;
    left = (Math.max(0, left) / WP.SIDE) | 0; top = (Math.max(0, top) / WP.SIDE) | 0;
    right = (Math.min(D.width - 1, right) / WP.SIDE) | 0; bottom = (Math.min(D.height - 1, bottom) / WP.SIDE) | 0;
    for (let ty = top; ty <= bottom; ty++) for (let tx = left; tx <= right; tx++) { const t = this.tiles[ty * this.columns + tx]; if (t) this.wake(t); }
  }
  wakeBeside(tile) {
    const D = this.document;
    for (let dir = 0; dir < 4; dir++) {
      const x = tile.x + (dir === 0 ? -WP.SIDE : dir === 1 ? WP.SIDE : 0), y = tile.y + (dir === 2 ? -WP.SIDE : dir === 3 ? WP.SIDE : 0);
      if (x < 0 || y < 0 || x >= D.width || y >= D.height) continue;
      const o = this.tileOf(x, y);
      if (o && o.nextPull && o.wetPixels > 0) this.wake(o, this.step + 1);
    }
  }
  static markWet(tile, i) {
    tile.wet[i] = 1; tile.wetPixels++;
    const row = (i / WP.SIDE) | 0, col = i % WP.SIDE;
    tile.wetRows[row] |= 1 << col; tile.wetColumns[col] |= 1 << row;
  }
  static clearWater(tile, i) {
    tile.water[i] = 0; tile.flowing[i] = 0;
    if (tile.plain) tile.plain[i] = 0;
    if (tile.mobility) tile.mobility[i] = 0;
    if (tile.washed) { tile.washed[i] = 0; tile.edgeStale = true; }
  }
  lift(tile, i, x, y) { WetWatercolor.markWet(tile, i); tile.base[i] = 255; tile.pigment[i] = tile.target[i] = 255 - this.document.tone(x, y); }
  hydrated(tile, i, x, y) {
    const D = this.document;
    if (!this.paintOnly || D.opacity(x, y) === 0) { this.lift(tile, i, x, y); return; }
    if (!this.dryStays) { this.lift(tile, i, x, y); (tile.paint ??= new Uint8Array(WP.PIXELS))[i] = 1; return; }
    const tone = D.tone(x, y);
    WetWatercolor.markWet(tile, i);
    tile.base[i] = 255; tile.pigment[i] = tile.target[i] = 0;
    if (!tile.stuck) { tile.stuck = new Float32Array(WP.PIXELS); tile.soaked = new Float32Array(WP.PIXELS); }
    tile.stuck[i] = 255 - tone; tile.soaked[i] = 0;
  }
  hydrate(left, top, right, bottom) {
    const D = this.document, S = WP.SIDE;
    left = Math.max(0, left); top = Math.max(0, top); right = Math.min(D.width, right); bottom = Math.min(D.height, bottom);
    if (left >= right || top >= bottom) return;
    for (let ty = (top / S) | 0; ty <= ((bottom - 1) / S | 0); ty++) for (let tx = (left / S) | 0; tx <= ((right - 1) / S | 0); tx++) {
      let tile = this.tiles[ty * this.columns + tx];
      if (!tile) tile = this.tiles[ty * this.columns + tx] = new WetTile(tx * S, ty * S);
      else if (tile.wetPixels === WP.PIXELS) continue;
      const x0 = Math.max(left, tile.x), x1 = Math.min(right, tile.x + S), y0 = Math.max(top, tile.y), y1 = Math.min(bottom, tile.y + S);
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) { const i = (y - tile.y) * S + x - tile.x; if (!tile.wet[i]) this.hydrated(tile, i, x, y); }
    }
  }
  // The tone a pixel's paint shows.
  static shade(tile, i) {
    const ink = f32(tile.pigment[i] * WetWatercolor.look(tile, i) + (tile.stuck ? tile.stuck[i] : 0));
    return Math.max(0, Math.min(255, Math.round(tile.base[i] * f32(1 - Math.min(255, ink) / 255))));
  }
  static look(tile, i) {
    if (!tile.washed || !tile.washed[i]) return 1;
    const dried = !tile.flowing[i] ? 1 : Math.max(0, Math.min(1, (WP.LOOSE - tile.pull[i]) / (WP.LOOSE - WP.STILL)));
    if (dried <= 0) return 1;
    let look = 1;
    const x = tile.x + i % WP.SIDE, y = tile.y + ((i / WP.SIDE) | 0);
    if (tile.grain && tile.gran > 0) look *= 1 + 1.2 * tile.gran * dried * (2 * tile.grain(x, y) - 1);
    if (tile.rim > 0 && tile.edge) { const e = tile.edge[i] / 255; look *= 1 + tile.rim * dried * (3.6 * e * e - .35); }
    if (tile.blob && (tile.mottle > 0 || tile.tide > 0)) {
      const b = tile.blob(x, y), off = (b - WP.TIDE) / .018;
      look *= 1 + dried * (tile.mottle * .9 * (b - .5) + tile.tide * Math.max(0, 1 - off * off));
    }
    return Math.max(0, look);
  }
  composite(tile, i, x, y) { this.document.setTone(x, y, WetWatercolor.shade(tile, i)); }
  // Opaque paint ({@code strength} of grey {@code gray}), mixing into paint under it by the wetness.
  paintMask(mask, stride, x, y, w, h, gray, transparent, strength) {
    const D = this.document, S = WP.SIDE;
    const mL = wetMargin(-this.gravityX), mT = wetMargin(-this.gravityY), mR = wetMargin(this.gravityX), mB = wetMargin(this.gravityY);
    this.hydrate(x - mL, y - mT, x + w + mR, y + h + mB);
    for (let row = 0; row < h; row++) for (let col = 0; col < w; col++) {
      const px = x + col, py = y + row;
      if (!mask[row * stride + col] || px < 0 || py < 0 || px >= D.width || py >= D.height) continue;
      const tile = this.tileAt(px, py), i = (py % S) * S + px % S;
      if (tile.touched[i]) continue;
      tile.touched[i] = 1; this.strokePixels++;
      let pigment = f32((255 - gray) * strength);
      const old = tile.pigment[i];
      if (transparent) pigment = f32(old + (255 - old) * (255 - gray) * strength / 510);
      else pigment = f32(old + (255 - gray - old) * strength);
      let mixes = f32(.6 * this.wetness);
      if (!transparent && pigment < old) mixes = f32(mixes * WP.LIGHT_MIXES);
      if (this.paintOnly) {
        if (!tile.paint || !tile.paint[i]) mixes = 0;
        (tile.paint ??= new Uint8Array(WP.PIXELS))[i] = 1;
        if (!transparent && tile.stuck) tile.stuck[i] *= 1 - strength;
      }
      tile.target[i] = old * mixes + pigment * (1 - mixes);
      tile.pigment[i] = pigment;
      WetWatercolor.clearWater(tile, i);
      if (!transparent) D.opaque[py * D.width + px] = 1;
      this.composite(tile, i, px, py);
      this.wake(tile);
    }
    this.wakeAround(x - mL, y - mT, x + w + mR, y + h + mB);
  }
  // Flowing water from a brush (clear, or holding {@code ink}), each new pixel trading {@code trade} dabs' worth of ink.
  waterMask(mask, stride, x, y, w, h, pull, ink, trade) {
    const D = this.document, S = WP.SIDE;
    const mL = wetMargin(-this.gravityX), mT = wetMargin(-this.gravityY), mR = wetMargin(this.gravityX), mB = wetMargin(this.gravityY);
    this.hydrate(x - mL, y - mT, x + w + mR, y + h + mB);
    for (let row = 0; row < h; row++) for (let col = 0; col < w; col++) {
      const px = x + col, py = y + row;
      if (!mask[row * stride + col] || px < 0 || py < 0 || px >= D.width || py >= D.height) continue;
      const tile = this.tileAt(px, py), i = (py % S) * S + px % S, coverage = 1;
      // A pixel the stroke already wet trades ink with the brush again (rub); only new ones take water.
      if (tile.touched[i]) { if (tile.flowing[i]) this.soak(tile, i, px, py, ink, f32(pull * coverage)); continue; }
      if (!tile.wet[i]) this.lift(tile, i, px, py); else WetWatercolor.loosen(tile, i, 1);
      tile.touched[i] = 1; this.strokePixels++;
      const newWater = !tile.water[i];
      if (newWater) tile.target[i] = tile.pigment[i];
      if (!tile.pull) { tile.pull = new Float32Array(WP.PIXELS); tile.mobility = new Float32Array(WP.PIXELS); }
      tile.pull[i] = newWater ? pull : Math.max(tile.pull[i], pull);
      tile.mobility[i] = wetMobility(tile.pull[i]);
      if (newWater && tile.pigment[i] > 0) (tile.reserve ??= new Float32Array(WP.PIXELS))[i] = tile.pigment[i] * (WP.DEPTH - 1);
      tile.water[i] = 1; tile.flowing[i] = 1; this.hasFlowing = this.looseWater = true;
      if (tile.plain) tile.plain[i] = 0;   // a brush's water, never plain (it holds ink, or soaks it up)
      const tint = 255 * ink.tint() * (1 - (tile.stuck ? tile.stuck[i] : 0) / 255);
      if (tile.pigment[i] < tint) tile.pigment[i] += (tint - tile.pigment[i]) * coverage;
      this.soak(tile, i, px, py, ink, f32(pull * coverage * trade));
      this.wake(tile, WP.WATER_FRAMES);
    }
    this.wakeAround(x - mL, y - mT, x + w + mR, y + h + mB);
  }
  soak(tile, i, px, py, ink, share) {
    const pigment = tile.pigment[i], reachable = WetWatercolor.reachable(tile, i);
    const moved = ink.trade(pigment + reachable, share);
    if (moved === 0) { this.composite(tile, i, px, py); return; }
    if (moved > 0) {
      const hidden = !tile.reserve ? 0 : Math.min(moved * (1 - WP.SHOWS), tile.reserve[i]);
      if (hidden > 0) tile.reserve[i] -= hidden;
      let rest = moved - hidden;
      const loose = Math.min(rest, pigment);
      rest -= loose;
      if (loose > 0) { const kept = 1 - loose / pigment; if (tile.reserve) tile.reserve[i] *= kept; tile.target[i] *= kept; }
      tile.pigment[i] = pigment - loose;
      if (rest > 0 && tile.stuck) tile.stuck[i] = Math.max(0, tile.stuck[i] - rest);
    } else tile.pigment[i] = Math.min(255, pigment - moved);
    this.composite(tile, i, px, py);
  }
  static reachable(tile, i) { if (!tile.stuck || tile.stuck[i] <= 0) return 0; const s = tile.soaked[i]; return tile.stuck[i] * s * s; }
  static loosen(tile, i, share) {
    if (!tile.stuck || tile.stuck[i] <= 0 || share <= 0) return;
    const freed = f32(tile.stuck[i] * Math.min(1, share));
    tile.stuck[i] -= freed; tile.pigment[i] += freed;
  }
  // A watercolour wash: each new pixel fresh flowing water taking {@code strength} of grey {@code gray} as a glaze;
  // with {@code line}, as much dry ink stuck beneath (a pen line as wide as the wash).
  washMask(mask, stride, x, y, w, h, gray, strength, pull, rows, line) {
    const D = this.document, S = WP.SIDE, wc = this.wash;
    const mL = wetMargin(-this.gravityX), mT = wetMargin(-this.gravityY), mR = wetMargin(this.gravityX), mB = wetMargin(this.gravityY);
    this.hydrate(x - mL, y - mT, x + w + mR, y + h + mB);
    this.washTiles(x - mL, y - mT, x + w + mR, y + h + mB);
    const dark = f32((255 - gray) / 255);
    for (let row = 0; row < h; row++) for (let col = rows ? rows[2 * row] : 0, last = rows ? rows[2 * row + 1] : w - 1; col <= last; col++) {
      const px = x + col, py = y + row;
      if (!mask[row * stride + col] || px < 0 || py < 0 || px >= D.width || py >= D.height) continue;
      const tile = this.tileAt(px, py), i = (py % S) * S + px % S;
      if (tile.touched[i]) continue;
      tile.touched[i] = 1; this.strokePixels++;
      if (!tile.wet[i]) this.lift(tile, i, px, py);
      if (line > 0) {
        if (!tile.stuck) { tile.stuck = new Float32Array(WP.PIXELS); tile.soaked = new Float32Array(WP.PIXELS); }
        tile.stuck[i] = Math.max(tile.stuck[i], line);
      }
      const stuck = tile.stuck ? tile.stuck[i] : 0, loose = tile.pigment[i];
      const ink = Math.min(255, loose + stuck), laid = f32(dark * strength);
      const water = tile.water[i] && tile.flowing[i];
      const mixes = water && tile.washed[i] ? f32(WP.MIXES * wetSmooth((tile.pull[i] - WP.MIXES_FROM) / (WP.LOOSE - WP.MIXES_FROM))) : 0;
      tile.pigment[i] += (1 - mixes) * (255 - ink) * laid + mixes * Math.max(0, (255 - stuck) * laid - loose);
      tile.target[i] = tile.pigment[i];
      if (tile.reserve) tile.reserve[i] = 0;
      const fresh = f32(pull * (1 - wc.uneven * .8 * wc.blob(px, py)));
      const under = water && tile.washed[i] && tile.pull[i] > WP.MERGES ? tile.wave[i] : 0;
      tile.pull[i] = water ? tile.pull[i] + (1 - mixes) * Math.max(0, fresh - tile.pull[i]) : fresh;
      tile.mobility[i] = wetMobility(tile.pull[i]);
      if (this.strokeWave === 0) this.strokeWave = ++this.waves;
      if (under > 0) this.join(this.strokeWave, under);
      tile.wave[i] = this.strokeWave;
      tile.combined[i] = 0;
      tile.water[i] = 1; tile.flowing[i] = 1; this.hasFlowing = this.looseWater = true;
      (tile.plain ??= new Uint8Array(WP.PIXELS))[i] = 1;
      tile.washed[i] = 1;
      tile.gran = Math.max(tile.gran, wc.gran); tile.rim = Math.max(tile.rim, Math.max(wc.edge, .5 * wc.bloom)); tile.grain = wc.grain;
      tile.mottle = Math.max(tile.mottle, wc.bloom); tile.tide = Math.max(tile.tide, wc.uneven); tile.blob = wc.blob;
      this.composite(tile, i, px, py);
      this.wake(tile, WP.WATER_FRAMES);
    }
    this.wakeAround(x - mL, y - mT, x + w + mR, y + h + mB);
    this.edgesChanged(x, y, x + w, y + h);
  }
  // A wash's pen line: dry ink stuck beneath the water, at least {@code strength} of grey {@code gray}.
  lineMask(mask, stride, x, y, w, h, gray, strength, rows) {
    const D = this.document, S = WP.SIDE, dark = f32((255 - gray) * Math.max(0, Math.min(1, strength)));
    for (let row = 0; row < h; row++) for (let col = rows ? rows[2 * row] : 0, last = rows ? rows[2 * row + 1] : w - 1; col <= last; col++) {
      const px = x + col, py = y + row;
      if (!mask[row * stride + col] || px < 0 || py < 0 || px >= D.width || py >= D.height) continue;
      const tile = this.tileAt(px, py), i = (py % S) * S + px % S;
      if (!tile.wet[i]) this.hydrated(tile, i, px, py);
      if (!tile.stuck) { tile.stuck = new Float32Array(WP.PIXELS); tile.soaked = new Float32Array(WP.PIXELS); }
      if (tile.stuck[i] >= dark) continue;
      tile.stuck[i] = dark;
      if (tile.touched[i]) { this.composite(tile, i, px, py); this.wake(tile); }
    }
  }
  washTiles(left, top, right, bottom) {
    const D = this.document, S = WP.SIDE, P = WP.PIXELS;
    left = Math.max(0, left); top = Math.max(0, top); right = Math.min(D.width, right); bottom = Math.min(D.height, bottom);
    if (left >= right || top >= bottom) return;
    for (let ty = (top / S) | 0; ty <= ((bottom - 1) / S | 0); ty++) for (let tx = (left / S) | 0; tx <= ((right - 1) / S | 0); tx++) {
      const tile = this.tiles[ty * this.columns + tx] ??= new WetTile(tx * S, ty * S);
      if (!tile.pull) { tile.pull = new Float32Array(P); tile.mobility = new Float32Array(P); }
      if (!tile.nextPull) { tile.nextPull = new Float32Array(P); tile.washed = new Uint8Array(P); tile.wave = new Int16Array(P); tile.nextWave = new Int16Array(P); tile.combined = new Uint8Array(P); }
    }
  }
  edgeness(tile) {
    if (!tile.nextPull || !tile.edgeStale && tile.edge) return;
    tile.edge ??= new Uint8Array(WP.PIXELS);
    tile.edgeStale = false;
    const E = WP.EDGE, n = (2 * E + 1) * (2 * E + 1);
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.flowing[i]) continue;
      const x = tile.x + i % WP.SIDE, y = tile.y + ((i / WP.SIDE) | 0), own = this.washOf(tile.wave[i]);
      let dry = 0;
      for (let dy = -E; dy <= E; dy++) for (let dx = -E; dx <= E; dx++) if (this.waveAt(x + dx, y + dy) !== own) dry++;
      tile.edge[i] = Math.round(255 * Math.min(1, f32(dry * 2.3 / n)));
    }
  }
  edgesChanged(left, top, right, bottom) {
    const D = this.document, E = WP.EDGE, S = WP.SIDE;
    left = Math.max(0, left - E); top = Math.max(0, top - E); right = Math.min(D.width - 1, right + E); bottom = Math.min(D.height - 1, bottom + E);
    for (let ty = (top / S) | 0; ty <= ((bottom / S) | 0); ty++) for (let tx = (left / S) | 0; tx <= ((right / S) | 0); tx++) { const t = this.tiles[ty * this.columns + tx]; if (t) t.edgeStale = true; }
  }
  waveAt(x, y) {
    const D = this.document;
    if (x < 0 || y < 0 || x >= D.width || y >= D.height) return -1;
    const t = this.tileOf(x, y), i = (y % WP.SIDE) * WP.SIDE + x % WP.SIDE;
    return !t || !t.pull || !t.flowing[i] ? -1 : !t.wave ? 0 : this.washOf(t.wave[i]);
  }
  flowsAt(x, y) {
    const D = this.document;
    if (x < 0 || y < 0 || x >= D.width || y >= D.height) return false;
    const t = this.tileOf(x, y);
    return !!t && !!t.pull && !!t.flowing[(y % WP.SIDE) * WP.SIDE + x % WP.SIDE];
  }
  capillary(tile, i, value) {
    const wc = this.wash, D = this.document, S = WP.SIDE;
    const pi = tile.pull[i];
    let gain = 0, flood = 0, combine = 0, wave = tile.wave[i], withW = 0;
    const own = wave, combined = tile.combined[i];
    const x = tile.x + i % S, y = tile.y + ((i / S) | 0);
    const ei = tile.edge ? tile.edge[i] / 255 : 0, ni = wc.frill(x, y);
    for (let dir = 0; dir < 4; dir++) {
      const nx = x + (dir === 0 ? -1 : dir === 1 ? 1 : 0), ny = y + (dir === 2 ? -1 : dir === 3 ? 1 : 0);
      if (nx < 0 || ny < 0 || nx >= D.width || ny >= D.height) continue;
      const t = this.tileOf(nx, ny), j = (ny % S) * S + nx % S;
      if (!t || t.inFrame !== this.frame || !t.pull || !t.flowing[j]) continue;
      const pj = t.pull[j], d = f32(pi - pj), n = f32(.5 * (ni + wc.frill(nx, ny))), gj = t.pigment[j];
      const wj = t.wave ? t.wave[j] : 0;
      const one = wj === own || this.washOf(wj) === this.washOf(own), merged = wj !== own && one;
      const k = merged || one && (combined || t.combined && t.combined[j]) ? 0 : wc.carry(n);
      gain -= d > 0 ? k * d * value : k * d * gj;
      if (-d > WP.FLOODS && n > 1 - wc.bloom) { if (pj > flood) { flood = pj; wave = wj; } }
      if (merged && -d > WP.COMBINES && pj > combine) { combine = pj; withW = wj; }
    }
    let next = Math.max(pi, flood);
    if (pi + WP.COMBINE * (combine - pi) > next) { next = f32(pi + WP.COMBINE * (combine - pi)); wave = withW; }
    tile.nextPull[i] = next * (1 - wc.edgeDrying() * ei * ei);
    tile.nextWave[i] = wave;
    return f32(gain);
  }
  bleedInto(tile, i) {
    const wc = this.wash, S = WP.SIDE;
    if (wc.bleed <= 0 || tile.pigment[i] >= 1) return 0;
    const x = tile.x + i % S, y = tile.y + ((i / S) | 0);
    if (wc.frill(x, y) < 1 - wc.bleed) return 0;
    let best = 0;
    for (let dir = 0; dir < 4; dir++) {
      const nx = x + (dir === 0 ? -1 : dir === 1 ? 1 : 0), ny = y + (dir === 2 ? -1 : dir === 3 ? 1 : 0);
      const t = this.flowsAt(nx, ny) ? this.tileOf(nx, ny) : null;
      if (!t || t.inFrame !== this.frame) continue;
      const j = (ny % S) * S + nx % S;
      if (t.pull[j] > best) { best = t.pull[j]; tile.nextWave[i] = t.wave ? t.wave[j] : 0; }
    }
    return best > WP.BLEEDS ? f32(best * wc.feather) : 0;
  }
  // One slice: at most maxTiles of the frame running now (a new frame begun when the last is done); false when idle.
  advance(maxTiles = WP.TILES_PER_FRAME) {
    this.advancedTiles = 0;
    if (!this.active.length || maxTiles <= 0) return false;
    if (this.frameRemaining === 0) {
      this.frameRemaining = this.active.length; this.frame++;
      if (this.wash) for (const t of this.active) t.inFrame = this.frame;
      this.runTiles = Math.max(this.runTiles, this.active.length);
      this.flowingFrame = this.active.some(t => anySet(t.flowing));
      this.step = this.flowingFrame && this.runTiles >= WP.HUGE_TILES ? WP.HUGE_STEP : 1;
      this.drying = f32(Math.pow(WP.DRYING, this.step));
      this.washDrying = this.wash ? f32(Math.pow(this.wash.drying, this.step)) : this.drying;
      this.spreadCandidates = this.hasFlowing ? this.active.slice() : null;
      this.spreading.length = 0;
      this.spreadPhase = this.spreadCursor = this.spreadPairs = this.spreadStep = 0;
    }
    this.planSpread();
    const batch = this.batch; batch.length = 0;
    const count = Math.min(maxTiles, this.frameRemaining);
    for (let t = 0; t < count; t++) batch.push(this.active.shift());
    const fromLeft = 1 + this.gravityX, fromRight = 1 - this.gravityX, fromAbove = 1 + this.gravityY, fromBelow = 1 - this.gravityY;
    if (this.wash) for (const t of batch) this.edgeness(t);
    for (const t of batch) this.computeTile(t, fromLeft, fromRight, fromAbove, fromBelow);
    for (const t of batch) this.settleTile(t);
    for (const t of batch) {
      for (let i = 0; i < WP.PIXELS; i++) if (t.shade[i] >= 0) this.document.setTone(t.x + i % WP.SIDE, t.y + ((i / WP.SIDE) | 0), t.shade[i]);
      this.looseSoFar ||= t.loose;
      if ((t.frames -= this.step) > 0) this.active.push(t);
    }
    if (this.wash) for (const t of batch) {
      if (t.loose && t.nextPull) this.wakeBeside(t);
      if (t.bled) { t.bled = false; this.edgesChanged(t.x, t.y, t.x + WP.SIDE - 1, t.y + WP.SIDE - 1); }
    }
    this.advancedTiles = batch.length;
    this.frameRemaining -= batch.length;
    if (this.frameRemaining === 0) { this.looseWater = this.looseSoFar; this.looseSoFar = false; }
    if (!this.active.length) this.runTiles = 0;
    return true;
  }
  settleTile(tile) {
    const D = this.document, S = WP.SIDE;
    tile.shade.fill(-1);
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.wet[i]) continue;
      tile.pigment[i] = tile.next[i];
      const x = tile.x + i % S, y = tile.y + ((i / S) | 0);
      if (x >= D.width || y >= D.height) continue;
      const tone = WetWatercolor.shade(tile, i);
      if (tone !== D.tone(x, y)) tile.shade[i] = tone;
    }
    let wettest = 0, loose = false;
    const washes = !!this.wash && !!tile.nextPull;
    if (washes) for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.wet[i] || tile.flowing[i] || tile.nextPull[i] <= 0) continue;
      tile.water[i] = 1; tile.flowing[i] = 1; tile.washed[i] = 1;
      (tile.plain ??= new Uint8Array(WP.PIXELS))[i] = 1;
      tile.target[i] = tile.pigment[i]; tile.pull[i] = tile.nextPull[i]; tile.wave[i] = tile.nextWave[i];
      this.hasFlowing = true; tile.bled = true;
    }
    const rate = washes ? this.washDrying : this.drying;
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.flowing[i]) continue;
      if (washes) {
        tile.pull[i] = tile.nextPull[i];
        if (tile.wave[i] !== tile.nextWave[i]) {
          if (this.washOf(tile.wave[i]) === this.washOf(tile.nextWave[i])) tile.combined[i] = 1;
          tile.wave[i] = tile.nextWave[i]; tile.bled = true;
        }
      }
      if (tile.reserve && tile.reserve[i] > 0) {
        const gap = Math.max(0, tile.target[i] - tile.pigment[i]);
        const dissolved = Math.min(tile.reserve[i], this.step === 1 ? gap * tile.pull[i] * WP.DISSOLVE : gap * (1 - Math.pow(1 - tile.pull[i] * WP.DISSOLVE, this.step)));
        tile.reserve[i] -= dissolved; tile.pigment[i] += dissolved;
      }
      tile.pull[i] *= rate;
      wettest = Math.max(wettest, tile.pull[i]);
      loose ||= tile.pull[i] > WP.LOOSE;
      tile.mobility[i] = wetMobility(tile.pull[i]);
    }
    tile.loose = loose;
    const damp = wettest > WP.STILL;
    tile.dampFrames = damp ? Math.ceil(Math.log(WP.STILL / wettest) / Math.log(washes ? this.wash.drying : WP.DRYING)) : 0;
    if (damp) tile.frames = Math.max(tile.frames, this.step + 1);
  }
  computeTile(tile, fL, fR, fA, fB) {
    const shares = this.sc.spread, spreads = this.shapeSpread(tile), washes = !!this.wash && !!tile.nextPull;
    if (this.surrounded(tile)) { this.computeSurrounded(tile, spreads, washes, fL, fR, fA, fB); return; }
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.wet[i]) continue;
      const value = tile.pigment[i];
      if (tile.flowing[i]) {
        const washed = washes ? this.capillary(tile, i, value) : 0;
        if (tile.pull[i] <= WP.STILL) { tile.next[i] = value + washed; continue; }
        const fresh = tile.mobility[i];
        tile.next[i] = washed + WetWatercolor.flowed(value, fL * this.exchange(tile, i, -1, 0, value, fresh) + fR * this.exchange(tile, i, 1, 0, value, fresh)
          + fA * this.exchange(tile, i, 0, -1, value, fresh) + fB * this.exchange(tile, i, 0, 1, value, fresh), spreads ? shares[i] : 0);
        continue;
      }
      tile.next[i] = this.evened(tile, i, value, fL * this.neighbor(tile, i, -1, 0, value) + fR * this.neighbor(tile, i, 1, 0, value)
        + fA * this.neighbor(tile, i, 0, -1, value) + fB * this.neighbor(tile, i, 0, 1, value));
      if (washes) {
        if (value < 1 && tile.target[i] < 1) tile.next[i] = value;
        tile.nextPull[i] = this.bleedInto(tile, i);
      }
    }
  }
  static flowed(value, gained, share) { return f32(value + f32(gained) / 12 + share); }
  evened(tile, i, value, neighbors) {
    if (tile.water[i]) return f32(value + (neighbors / 12 - value) * tile.pull[i]);
    const flow = this.wetness;
    return f32(value * (1 - .55 * flow) + neighbors * (flow / 30) + tile.target[i] * (.15 * flow));
  }
  surrounded(tile) {
    if (tile.wetPixels !== WP.PIXELS) return false;
    for (let side = 0; side < 4; side++) {
      const o = this.beside(tile, 1, side === 0 ? -1 : side === 1 ? 1 : 0, side === 2 ? -1 : side === 3 ? 1 : 0);
      if (!o || o.wetPixels !== WP.PIXELS) return false;
    }
    return true;
  }
  computeSurrounded(tile, spreads, washes, fL, fR, fA, fB) {
    const shares = this.sc.spread, ink = this.sc.ink, free = this.sc.free, S = WP.SIDE, R = WP.REACH, SP = WP.SPAN;
    WetWatercolor.gather(tile, 0, 0, 0, 0, S, S, ink, free);
    WetWatercolor.gather(this.beside(tile, 1, -1, 0), S - R, 0, -R, 0, R, S, ink, free);
    WetWatercolor.gather(this.beside(tile, 1, 1, 0), 0, 0, S, 0, R, S, ink, free);
    WetWatercolor.gather(this.beside(tile, 1, 0, -1), 0, S - R, 0, -R, S, R, ink, free);
    WetWatercolor.gather(this.beside(tile, 1, 0, 1), 0, 0, 0, S, S, R, ink, free);
    for (let i = 0; i < WP.PIXELS; i++) {
      const p = (((i / S) | 0) + R) * SP + i % S + R, value = ink[p];
      if (tile.flowing[i]) {
        const washed = washes ? this.capillary(tile, i, value) : 0;
        if (tile.pull[i] <= WP.STILL) { tile.next[i] = value + washed; continue; }
        const fresh = tile.mobility[i], ray = WetWatercolor.ray;
        tile.next[i] = washed + WetWatercolor.flowed(value, fL * ray(ink, free, p, -1, value, fresh) + fR * ray(ink, free, p, 1, value, fresh)
          + fA * ray(ink, free, p, -SP, value, fresh) + fB * ray(ink, free, p, SP, value, fresh), spreads ? shares[i] : 0);
        continue;
      }
      tile.next[i] = this.evened(tile, i, value, fL * (ink[p - 1] + ink[p - 4] + ink[p - 8]) + fR * (ink[p + 1] + ink[p + 4] + ink[p + 8])
        + fA * (ink[p - SP] + ink[p - 4 * SP] + ink[p - 8 * SP]) + fB * (ink[p + SP] + ink[p + 4 * SP] + ink[p + 8 * SP]));
      if (washes) {
        if (value < 1 && tile.target[i] < 1) tile.next[i] = value;
        tile.nextPull[i] = this.bleedInto(tile, i);
      }
    }
  }
  static gather(from, x, y, toX, toY, w, h, ink, free) {
    const S = WP.SIDE, R = WP.REACH, SP = WP.SPAN;
    for (let row = 0; row < h; row++) {
      const source = (y + row) * S + x, target = (toY + row + R) * SP + toX + R;
      ink.set(from.pigment.subarray(source, source + w), target);
      if (!from.mobility) free.fill(0, target, target + w); else free.set(from.mobility.subarray(source, source + w), target);
    }
  }
  static ray(ink, free, p, step, value, fresh) {
    return Math.min(fresh, free[p + step]) * (ink[p + step] - value) + Math.min(fresh, free[p + 4 * step]) * (ink[p + 4 * step] - value)
      + Math.min(fresh, free[p + 8 * step]) * (ink[p + 8 * step] - value);
  }
  // Tiles joined by flowing water trade their mean ink over several quick steps, once a frame; into each tile's spread.
  planSpread() {
    if (!this.spreadCandidates) return;
    const D = this.document, S = WP.SIDE, cand = this.spreadCandidates, sp = this.spreading;
    for (const t of cand) if (anySet(t.flowing)) { WetWatercolor.measure(t); if (t.meanFresh > WP.DAMP) { t.spreadSlot = sp.length; sp.push(t); } }
    const count = sp.length;
    if (this.spreadInk.length < count) { this.spreadInk = new Float32Array(count * 2); this.spreadChange = new Float32Array(count * 2); this.spreadPixels = new Int32Array(count * 2); }
    let pairs = 0;
    for (let a = 0; a < count; a++) {
      const tile = sp[a];
      this.spreadInk[a] = tile.meanInk; this.spreadPixels[a] = tile.flowingPixels;
      for (let side = 0; side < 2; side++) {
        const x = tile.x + (side === 0 ? S : 0), y = tile.y + (side === 1 ? S : 0);
        if (x >= D.width || y >= D.height) continue;
        const other = this.tileOf(x, y);
        if (!other || other.spreadSlot < 0) continue;
        const joined = bitCount(side === 0 ? tile.flowRight & other.flowLeft : tile.flowBottom & other.flowTop);
        if (joined === 0) continue;
        if (this.links.length <= pairs) {
          const grow2 = (A, n) => { const B = new A.constructor(n); B.set(A); return B; };
          this.links = grow2(this.links, pairs * 2); this.downhill = grow2(this.downhill, pairs * 2); this.linked = grow2(this.linked, pairs * 4);
        }
        this.links[pairs] = WP.SPREAD * joined / S * wetMobility(Math.min(tile.meanFresh, other.meanFresh)) * Math.min(tile.flowingPixels, other.flowingPixels);
        this.downhill[pairs] = side === 0 ? this.gravityX : this.gravityY;
        this.linked[pairs * 2] = a; this.linked[pairs * 2 + 1] = other.spreadSlot; pairs++;
      }
    }
    if (pairs > 0) {
      const evens = this.wash ? WP.WASH_SPREAD : 1, falls = this.wash ? WP.WASH_FALL : 1;
      for (let s = 0; s < WP.SPREAD_STEPS * this.step; s++) {
        this.spreadChange.fill(0, 0, count);
        for (let c = 0; c < pairs; c++) {
          const a = this.linked[c * 2], b = this.linked[c * 2 + 1], link = this.links[c], slope = this.downhill[c], fall = WP.POOL * Math.abs(slope);
          const moved = f32(link * (1 - fall) * evens * (this.spreadInk[b] - this.spreadInk[a]) + link * fall * falls * (slope > 0 ? -this.spreadInk[a] : this.spreadInk[b]));
          this.spreadChange[a] += moved; this.spreadChange[b] -= moved;
        }
        for (let c = 0; c < count; c++) this.spreadInk[c] += this.spreadChange[c] / this.spreadPixels[c];
      }
    }
    for (let c = 0; c < count; c++) {
      const tile = sp[c], change = f32(this.spreadInk[c] - tile.meanInk);
      tile.spread = f32(change / Math.max(change > 0 ? WP.DAMP : 1, change > 0 ? tile.meanFresh : tile.meanInk));
      tile.spreadInk = change; tile.spreadFrame = this.frame; tile.spreadSlot = -1;
    }
    this.spreadCandidates = null;
  }
  static planned(tile, what) { return what === 0 ? tile.spread : what === 1 ? tile.spreadInk : what === 2 ? tile.meanFresh : tile.meanInk; }
  gatherSpread(tile, near, what = 0) {
    const D = this.document, S = WP.SIDE;
    const own = tile.spreadFrame !== this.frame ? 0 : WetWatercolor.planned(tile, what);
    let any = own !== 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const x = tile.x + dx * S, y = tile.y + dy * S;
      const other = x >= 0 && y >= 0 && x < D.width && y < D.height ? this.tileOf(x, y) : null;
      const value = other && other.spreadFrame === this.frame ? WetWatercolor.planned(other, what) : what === 1 && other && anySet(other.flowing) ? 0 : own;
      near[(dy + 1) * 3 + dx + 1] = value;
      any ||= value !== 0;
    }
    return any;
  }
  shapeSpread(tile) {
    const near = this.sc.near, spread = this.sc.spread, S = WP.SIDE;
    if (!this.gatherSpread(tile, near)) return false;
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.flowing[i]) continue;
      const blended = WetWatercolor.spreadAt(i, near, 0, 0);
      spread[i] = blended * (blended > 0 ? tile.pull[i] : tile.pigment[i]);
    }
    if (!tile.plain || !anySet(tile.plain)) return true;
    if (tile.spreadFrame !== this.frame) { for (let i = 0; i < WP.PIXELS; i++) if (tile.plain[i]) spread[i] = 0; return true; }
    const ink = this.sc.nearInk, fresh = this.sc.nearFresh, inky = this.sc.nearInky;
    this.gatherSpread(tile, ink, 1); this.gatherSpread(tile, fresh, 2); this.gatherSpread(tile, inky, 3);
    const shiftX = f32((f32(this.frame * .618034) % 1) - .5), shiftY = f32((f32(this.frame * .754878) % 1) - .5);
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.plain[i]) continue;
      const change = WetWatercolor.spreadAt(i, ink, shiftX, shiftY);
      spread[i] = tile.pull[i] <= WP.STILL ? 0 : change > 0 ? change * tile.pull[i] / Math.max(WP.DAMP, WetWatercolor.spreadAt(i, fresh, shiftX, shiftY))
        : Math.max(-tile.pigment[i], change * tile.pigment[i] / Math.max(1, WetWatercolor.spreadAt(i, inky, shiftX, shiftY)));
    }
    return true;
  }
  static spreadAt(i, n, shiftX, shiftY) {
    const S = WP.SIDE, u = f32((i % S + .5) / S - .5 + shiftX), v = f32((((i / S) | 0) + .5) / S - .5 + shiftY);
    const sx = u < 0 ? 0 : 2, sy = v < 0 ? 0 : 6, a = Math.abs(u), b = Math.abs(v);
    return f32((1 - a) * (1 - b) * n[4] + a * (1 - b) * n[3 + sx] + (1 - a) * b * n[sy + 1] + a * b * n[sy + sx]);
  }
  static measure(tile) {
    let ink = 0, fresh = 0, count = 0, left = 0, right = 0, top = 0, bottom = 0;
    const S = WP.SIDE;
    for (let i = 0; i < WP.PIXELS; i++) {
      if (!tile.flowing[i]) continue;
      ink = f32(ink + tile.pigment[i]); fresh = f32(fresh + tile.pull[i]); count++;
      const row = (i / S) | 0, col = i % S;
      if (col === 0) left |= 1 << row; else if (col === S - 1) right |= 1 << row;
      if (row === 0) top |= 1 << col; else if (row === S - 1) bottom |= 1 << col;
    }
    tile.meanInk = f32(ink / count); tile.meanFresh = f32(fresh / count); tile.flowingPixels = count;
    tile.flowLeft = left; tile.flowRight = right; tile.flowTop = top; tile.flowBottom = bottom;
  }
  static edge(i, dx, dy) { const S = WP.SIDE; return dx < 0 ? i % S + 1 : dx > 0 ? S - i % S : dy < 0 ? ((i / S) | 0) + 1 : S - ((i / S) | 0); }
  beside(tile, edge, dx, dy) {
    if (edge > 8) return null;
    const D = this.document, x = tile.x + dx * WP.SIDE, y = tile.y + dy * WP.SIDE;
    return x >= 0 && y >= 0 && x < D.width && y < D.height ? this.tileOf(x, y) : null;
  }
  neighbor(tile, i, dx, dy, fallback) {
    const S = WP.SIDE, edge = WetWatercolor.edge(i, dx, dy), step = dx + dy * S, wrap = dx * S + dy * WP.PIXELS;
    const next = this.beside(tile, edge, dx, dy), reach = this.reach(tile, next, i, dx, dy, edge);
    const close = reach < 1 ? fallback : edge > 1 ? tile.pigment[i + step] : next.pigment[i + step - wrap];
    const near = reach < 4 ? fallback : edge > 4 ? tile.pigment[i + 4 * step] : next.pigment[i + 4 * step - wrap];
    const far = reach < 8 ? fallback : edge > 8 ? tile.pigment[i + 8 * step] : next.pigment[i + 8 * step - wrap];
    return f32(f32(close + near) + far);
  }
  exchange(tile, i, dx, dy, value, fresh) {
    const S = WP.SIDE, edge = WetWatercolor.edge(i, dx, dy), step = dx + dy * S, wrap = dx * S + dy * WP.PIXELS;
    const next = this.beside(tile, edge, dx, dy), reach = this.reach(tile, next, i, dx, dy, edge), trade = WetWatercolor.trade;
    let gained = 0;
    if (reach >= 1) gained += edge > 1 ? trade(tile, i + step, value, fresh) : trade(next, i + step - wrap, value, fresh);
    if (reach >= 4) gained += edge > 4 ? trade(tile, i + 4 * step, value, fresh) : trade(next, i + 4 * step - wrap, value, fresh);
    if (reach >= 8) gained += edge > 8 ? trade(tile, i + 8 * step, value, fresh) : trade(next, i + 8 * step - wrap, value, fresh);
    return f32(gained);
  }
  static trade(source, i, value, fresh) { return !source.mobility ? 0 : f32(Math.min(fresh, source.mobility[i]) * (source.pigment[i] - value)); }
  reach(tile, next, i, dx, dy, edge) {
    const S = WP.SIDE;
    if (tile.wetPixels === WP.PIXELS && (edge > 8 || next && next.wetPixels === WP.PIXELS)) return 8;
    let position = dx !== 0 ? i % S : (i / S) | 0;
    const line = dx !== 0 ? (i / S) | 0 : i % S;
    let bits = dx !== 0 ? tile.wetRows[line] : tile.wetColumns[line];
    let nextBits = !next ? 0 : dx !== 0 ? next.wetRows[line] : next.wetColumns[line];
    if (dx + dy < 0) { bits = rev16(bits); nextBits = rev16(nextBits); position = S - 1 - position; }
    let connected = bits >>> (position + 1);
    if (edge <= 8) connected |= nextBits << (edge - 1);
    const free = ~connected;
    return free === 0 ? 32 : 31 - Math.clz32(free & -free);
  }
  // Takes up what else changed the page as its paint (an Undo, another stroke): those pixels' water goes.
  rewet(wake) {
    const D = this.document, S = WP.SIDE;
    let changed = 0;
    for (const tile of this.tiles) {
      if (!tile || tile.wetPixels === 0) continue;
      let any = false;
      for (let i = 0; i < WP.PIXELS; i++) {
        if (!tile.wet[i]) continue;
        const x = tile.x + i % S, y = tile.y + ((i / S) | 0);
        if (x >= D.width || y >= D.height) continue;
        const tone = D.tone(x, y);
        if (Math.abs(tone - WetWatercolor.shade(tile, i)) <= 1) continue;
        tile.base[i] = 255; tile.pigment[i] = tile.target[i] = 255 - tone;
        if (this.paintOnly && D.opacity(x, y) > 0) (tile.paint ??= new Uint8Array(WP.PIXELS))[i] = 1;
        if (tile.reserve) tile.reserve[i] = 0;
        if (tile.stuck) tile.stuck[i] = 0;
        WetWatercolor.clearWater(tile, i);
        any = true; changed++;
      }
      if (any && wake) this.wake(tile);
    }
    return changed;
  }
}
// The low 16 bits reversed (Integer.reverse(bits) >>> 16).
function rev16(v) { let r = 0; for (let k = 0; k < 16; k++) r |= ((v >>> k) & 1) << (15 - k); return r; }

// A watercolour wash's dials and noises (ResolvedBrush.Wet.wash).
class WetWash {
  constructor(edge, bloom, uneven, bleed, gran, drying, feather, frill, blob, grain) {
    Object.assign(this, { edge: f32(edge), bloom: f32(bloom), uneven: f32(uneven), bleed: f32(bleed), gran: f32(gran), drying: f32(drying), feather: f32(feather), frill, blob, grain });
  }
  carry(frill) { return Math.min(.22, f32(.3 * this.bloom * (.3 + 2 * frill * frill))); }
  edgeDrying() { return f32(.02 + .04 * this.edge); }
}
// The ink a wet brush holds in its water (BrushInk); untinted here.
class BrushInk {
  constructor(capacity = BrushInk.CAPACITY) { this.capacity = capacity; this.ink = 0; this.floor = 0; }
  fullness() { return f32(this.ink / this.capacity); }
  tint() { return this.floor; }
  restore(fullness) { this.ink = Math.max(0, Number.isFinite(fullness) ? Math.min(1, fullness) : 0) * this.capacity; }
  load(gray) { this.ink = Math.max(this.floor, (255 - Math.max(0, Math.min(255, gray))) / 255) * this.capacity; }
  trade(pigment, share) {
    let moved = f32((pigment - 255 * this.fullness()) * .2 * share);
    moved = Math.max(-(this.ink - this.floor * this.capacity) * 255, Math.min((this.capacity - this.ink) * 255, moved));
    this.ink = f32(this.ink + moved / 255);
    return moved;
  }
}
BrushInk.CAPACITY = 3000;

// Wet for a brush (ResolvedBrush.Wet), or null: its wetness, frames, whether dried paint rewets, ink in water, a wash.
const WET_INK_WASH = { RIM: .4, FEATHER: .95 };
function wetWashes(B) {
  if (B.life.k !== "wetpaint" || B.carry.k === "water") return false;
  if (B.style === "Watercolor") return B.medium.k === "wash";
  return B.style === "Ink wash" && (B.medium.k === "wash" || B.medium.k === "ink" && (B.medium.d.wet ?? 0) > .08);
}
function wetOf(B, R, surf) {
  if (B.life.k !== "wetpaint") return null;
  const water = B.carry.k === "water", washes = wetWashes(B), mk = B.medium.k, inks = !water && !washes && (mk === "ink" || mk === "wash");
  const stays = B.life.d.stays ?? .5, runs = B.life.d.runs ?? 0;
  if (!washes && !(mk === "cover" || water || inks)) return null;
  const W = { stays, runs, rewets: !washes && B.style !== "Oils", inks, wash: null, washPull: 0, line: false,
    wetness: Math.round(15 + 70 * stays), frames: Math.round(8 + 32 * stays) };
  if (washes) {
    useTexture(surf, { texture: R.paper });
    const M = B.medium.d, ink = B.style === "Ink wash", rim = ink ? WET_INK_WASH.RIM : 1;
    const fine = surf.fine, mid = surf.mid, low = surf.low, blob = surf.blob, w = surf.w, at = (x, y) => y * w + x;
    W.wash = new WetWash(rim * (M.pool ?? 0), rim * Math.min(1, (M.bloom ?? 0) + .5 * (M.backrun ?? 0)), M.backrun ?? 0, M.bleed ?? 0, B.paper.d.tooth ?? 0,
      1 - .015 * (1.5 - stays), ink ? WET_INK_WASH.FEATHER : .85,
      (x, y) => f32(.55 * fine[at(x, y)] + .45 * blob[at(x, y)]), (x, y) => low[at(x, y)], (x, y) => f32(1 - (.6 * fine[at(x, y)] + .4 * mid[at(x, y)])));
    W.washPull = f32(ink ? .6 + .25 * (M.wet ?? 0) : .45 + .4 * (M.wet ?? 0));
    W.line = (M.line ?? 0) > 0;
  }
  return W;
}

// Wet paint on a surface (WetCanvas): its model, how it runs, and what each holder's brush keeps between strokes.
class WetCanvas {
  constructor(surf) {
    this.s = surf; this.page = new WetPage(surf); this.wet = new WetWatercolor(this.page, 50);
    this.rev = surf.rev | 0; this.kinds = -1; this.held = new Map(); this.frameStarted = 0;
  }
  moving() { return this.wet.isAnimating(); }
  brush(W) {
    const m = this.wet;
    m.setWetness(W.wetness); m.setFrames(W.frames);
    m.paintOnly = true; m.dryStays = !W.rewets;
    if (W.wash) m.wash = W.wash;
  }
  // What else changed the page since this canvas left it (surf.rev): taken up as paint, which then rests (wake: moves).
  catchUp(wake) {
    if ((this.s.rev | 0) === this.rev) return;
    this.wet.rewet(wake);
    if (!wake) this.wet.rest();
    this.rev = this.s.rev | 0;
  }
  // Unpaced: the frame running now to its end; false once the paint rests (WetCanvas.frame).
  frame() {
    this.catchUp(false);
    if (!this.wet.isAnimating()) return false;
    do this.wet.advance(); while (this.wet.framePending());
    return this.wet.isAnimating();
  }
  // Moves it until it rests, as a tap on the wet indicator runs it on.
  settle(most = 100000) { let n = 0; while (this.frame() && ++n < most) { /* frames */ } }
  // A page's loop: about ms of slices; returns ms until the next (as WetCanvas.step and MonoPaint's scheduleWet), -1 at rest.
  step(ms = 8) {
    this.catchUp(false);
    const m = this.wet;
    if (!m.isAnimating()) return -1;
    const t0 = now();
    let frame = m.frame;
    do { m.advance(); if (m.frame !== frame) { frame = m.frame; this.frameStarted = t0; } } while (m.framePending() && now() - t0 < ms);
    if (!m.isAnimating()) return -1;
    if (m.framePending()) return 0;
    if (m.flowing()) return Math.max(0, m.flowFrameMillis() - (now() - this.frameStarted));
    return WP.FRAME_MS;
  }
  takeDirty() { const d = this.page.dirty; this.page.dirty = null; return d; }
}

// ---------- the footprint a Wet stroke lays (FootprintSweep, WetCanvas.Footprint) ----------
const FP_SIDES = 24, FP_COS = [], FP_SIN = [];
for (let i = 0; i < FP_SIDES; i++) { FP_COS.push(Math.cos(2 * Math.PI * i / FP_SIDES)); FP_SIN.push(Math.sin(2 * Math.PI * i / FP_SIDES)); }
class WetFootprint {
  constructor(R, pts, surf, isLine) {
    this.R = R; this.pts = pts; this.s = surf; this.width = surf.w; this.height = surf.h; this.isLine = isLine;
    this.smudge = isLine && !ownLine(R);
    const h = surf.h;
    this.lo = new Int32Array(h).fill(2147483647); this.hi = new Int32Array(h).fill(-1); this.lo2 = new Int32Array(h).fill(2147483647); this.hi2 = new Int32Array(h).fill(-1);
    this.left = new Float64Array(h); this.right = new Float64Array(h); this.hullLeft = new Float64Array(h); this.hullRight = new Float64Array(h);
    this.top = 2147483647; this.bottom = -1; this.first = true; this.lifted = false; this.hakeStart = NaN; this.hakeSettle = 0;
    this.px = []; this.py = []; this.qx = []; this.qy = []; this.pn = 0; this.qn = 0;
    this.spans = []; this.box = null; this.mask = new Uint8Array(0); this.rows = new Int32Array(0);
  }
  head() {
    const R = this.R;
    if (this.smudge) return null;
    const head = this.isLine ? (R.lineTip === "hake" ? R.lineHake : null) : R.body && R.body.head === "flat" ? R.body : null;
    return head && head.headTurn !== "fixed" ? head : null;
  }
  holds() { return Number.isNaN(this.hakeStart) && !this.lifted && !!this.head() && this.total() < hakeSettle(this.isLine ? this.R.inkSize : this.R.bodySize); }
  total() { const p = this.pts; return p.length ? p[p.length - 1].d : 0; }
  to(k) {
    if (Number.isNaN(this.hakeStart) && this.head()) {
      const settle = hakeSettle(this.isLine ? this.R.inkSize : this.R.bodySize);
      this.hakeSettle = settle;
      this.hakeStart = hakeStartTravel(this.pts, Math.min(settle, this.total()));
      if (Number.isNaN(this.hakeStart)) { const T = tiltOf(this.pts[0].tx, this.pts[0].ty); this.hakeStart = T && T.dir !== null ? T.dir : 0; }
      if (k > 0 && this.first) this.sweepTo(0);
    }
    this.sweepTo(k);
  }
  sweepTo(k) {
    [this.px, this.qx] = [this.qx, this.px]; [this.py, this.qy] = [this.qy, this.py];
    this.pn = this.qn;
    const [a, b, c, s] = this.ellipseAt(k), x = this.pts[k].x, y = this.pts[k].y;
    for (let i = 0; i < FP_SIDES; i++) { const u = a * FP_COS[i], q = b * FP_SIN[i]; this.qx[i] = x + u * c - q * s; this.qy[i] = y + u * s + q * c; }
    this.qn = FP_SIDES;
    if (this.first) { this.first = false; this.fill(this.qx, this.qy, this.qn, null, null, 0); return; }
    const [hx, hy] = this.hull();
    this.fill(hx, hy, hx.length, this.px, this.py, this.pn);
  }
  // Sample k's shape: the stampers' own ellipse there (half-axes a and b, turned to cos, sin).
  ellipseAt(k) {
    const R = this.R, pts = this.pts, P = pts[k], j = Math.max(0, k - 1), Q = pts[j], d = P.d, seg = P.d - Q.d;
    const dirx = seg > 0 ? (P.x - Q.x) / seg : 1, diry = seg > 0 ? (P.y - Q.y) / seg : 0;
    // A smudge's dab (Carry.stumpR): its reach, or out to a comb's outer tines.
    if (this.smudge) { const C = R.carry, r = Math.max(1, C.tines ? Math.max(...C.tines.map(Math.abs)) + C.tineReach : C.r); return [r, r, 1, 0]; }
    const head = this.isLine ? (R.lineTip === "hake" ? R.lineHake : null) : R.body.head === "flat" ? R.body : null;
    if (head) {
      const hs = this.isLine ? R.inkSize : R.bodySize, pr = P.p, T = tiltOf(P.tx, P.ty);
      const D = paintDiameter(head, hs, pr, T);
      const travel = hakeTravelAt(head.headTurn === "fixed" ? NaN : this.hakeStart, this.hakeSettle, d, Math.atan2(P.uy ?? 0, P.ux ?? 1));
      let th = head.headTurn !== "fixed" ? travel + Math.PI / 2 : head.angle * DEG;
      if (head.tilt > 0 && T && T.dir !== null) th = turnToward(th, T.dir + Math.PI / 2, head.tilt);
      const thick = Math.max(1.5, hs * (0.03 + 0.05 * Math.pow(clamp(pr), 1.2)) * (1 + 0.8 * (head.pWidth || 0) * (pr - 0.4)));
      return [Math.max(1, D / 2), Math.max(1, thick / 2), Math.cos(th), Math.sin(th)];
    }
    if (this.isLine) {
      // LineStamper.nib's ellipse, as stampLine works it out at a sample.
      const b = R.pen, size = R.inkSize, big = inkBig(b, size), pr = P.p, v = P.v, T = tiltOf(P.tx, P.ty);
      let wd = size * lerp(1 - 0.75 * b.pressure, 1 + 0.6 * b.pressure, pr) * clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
      const inLen = taperLens(b, size)[0];
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08;
      if (b.fast && b.taperOut > 0) {
        const fall = Q.pk > 0 ? clamp(pr / (Q.pk * 0.7)) : 1, flick = clamp((v - 1.2) / 2);
        wd *= lerp(1, Math.pow(fall, 0.8) * 0.92 + 0.08, b.taperOut) * (1 - 0.8 * b.taperOut * flick);
      }
      const xi = clamp((P.x / big) | 0, 0, this.width - 1), yi = clamp((P.y / big) | 0, 0, this.height - 1);
      if (b.wobble > 0) wd *= 1 + b.wobble * 0.7 * (this.s.mid[yi * this.width + xi] - 0.5);
      if (b.pool > 0) { const grw = b.pool * 0.9 * (1 - clamp(v / 0.25)) ** 2; wd = big > 1 ? wd + Math.min(wd * grw, 16 * grw * Math.sqrt(big)) : wd * (1 + grw); }
      let ang = b.angle * DEG, flat = 1;
      if (((b.tiltSize || 0) + (b.tiltAngle || 0) + (b.tDark || 0) + (b.side || 0) > 0 || b.filbert) && T) {
        wd *= tiltWidth(b, T.lean);
        const shade = sideShade(b, T.lean);
        if (shade > 0) wd *= 1 + SIDE.WIDTH * shade;
        if (b.tiltAngle > 0 && T.dir !== null) ang = turnToward(ang, T.dir + Math.PI / 2, b.tiltAngle);
        if (b.filbert && T.dir !== null) ang = T.dir + Math.PI / 2;
        flat = tiltFlat(b, T.lean);
      }
      if (b.sAngle > 0) ang = turnToward(ang, Math.atan2(diry, dirx) + Math.PI / 2, b.sAngle * clamp(v / 1.5));
      const a = Math.max(0.6, wd / 2);
      let bb = Math.max(0.6, Math.min(a, Math.max(1, a * b.aspect * flat)));
      if (b.pWidth > 0) bb = b.aspect >= 1 ? Math.max(0.6, bb * (1 - 0.45 * b.pWidth * pr)) : Math.max(0.6, Math.min(a, bb * (1 + 1.2 * b.pWidth * (pr - 0.4))));
      return [a, bb, Math.cos(ang), Math.sin(ang)];
    }
    // The body's dab (BodyStamper.shape), its taper in, no taper out (not yet lifted).
    // The pen's lean read only for Tilt → size or darkness (FootprintSweep.ellipseAt): a Filbert's oval lies at its Nib Angle here.
    const bd = R.body, T = bd.tilt > 0 || bd.tDark ? tiltOf(P.tx, P.ty) : null;
    const D = paintDiameter(bd, R.bodySize, P.p, bd.tilt > 0 ? T : null);
    const inLen = (bd.taperIn || 0) * 70 * Math.max(1, R.bodySize / 24);
    let r = Math.max(0.8, D / 2);
    if (inLen > 0) r = Math.max(0.8, r * (Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08));
    if (bd.speed) r *= clamp(1 + bd.speed * 0.55 * (clamp(P.v / 2.5) - 0.3), 0.35, 1.6);
    let A = r, B = r, th = 0;
    const reads = T && T.dir !== null;
    if (bd.filbert) { if (reads && bd.tilt > 0 && T.lean > 0) A = r * (1 + 0.6 * bd.tilt * T.lean); B = Math.max(0.8, r * bd.aspect); th = reads ? T.dir + Math.PI / 2 : bd.angle * DEG; }
    else if (bd.tilt > 0 && reads && T.lean > 0) { A = r * (1 + 0.6 * bd.tilt * T.lean); B = r * (1 - 0.35 * bd.tilt * T.lean); th = T.dir + Math.PI / 2; }
    else if (bd.aspect !== undefined && bd.aspect < 1) { B = Math.max(0.8, r * bd.aspect); th = bd.angle * DEG; }
    return [A, B, Math.cos(th), Math.sin(th)];
  }
  // The convex hull of the last two shapes (Andrew's monotone chain), as FootprintSweep.hull: its corners.
  hull() {
    const sx = [...this.px.slice(0, this.pn), ...this.qx.slice(0, this.qn)], sy = [...this.py.slice(0, this.pn), ...this.qy.slice(0, this.qn)], m = sx.length;
    const order = [];
    for (let i = 0; i < m; i++) {
      let at = i;
      order.push(i);
      while (at > 0 && (sx[order[at - 1]] > sx[i] || sx[order[at - 1]] === sx[i] && sy[order[at - 1]] > sy[i])) { order[at] = order[at - 1]; at--; }
      order[at] = i;
    }
    const hx = [], hy = [], cross = (ax, ay, bx, by, cx, cy) => (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    for (let i = 0; i < m; i++) { const p = order[i]; while (hx.length >= 2 && cross(hx[hx.length - 2], hy[hy.length - 2], hx[hx.length - 1], hy[hy.length - 1], sx[p], sy[p]) <= 0) { hx.pop(); hy.pop(); } hx.push(sx[p]); hy.push(sy[p]); }
    const low = hx.length + 1;
    for (let i = m - 2; i >= 0; i--) { const p = order[i]; while (hx.length >= low && cross(hx[hx.length - 2], hy[hy.length - 2], hx[hx.length - 1], hy[hy.length - 1], sx[p], sy[p]) <= 0) { hx.pop(); hy.pop(); } hx.push(sx[p]); hy.push(sy[p]); }
    hx.pop(); hy.pop();
    return [hx, hy];
  }
  fill(xs, ys, n, ox, oy, on) {
    const rows = this.edges(xs, ys, n, this.hullLeft, this.hullRight);
    if (!rows) return;
    const old = ox ? this.edges(ox, oy, on, this.left, this.right) : null, w = this.width;
    for (let y = rows[0]; y <= rows[1]; y++) {
      const x0 = Math.max(0, Math.ceil(this.hullLeft[y] - .5)), x1 = Math.min(w - 1, Math.floor(this.hullRight[y] - .5));
      if (x1 < x0) continue;
      if (old && y >= old[0] && y <= old[1]) {
        const e0 = Math.ceil(this.left[y] - .5), e1 = Math.floor(this.right[y] - .5);
        if (e0 <= e1 && e1 >= x0 && e0 <= x1) { if (x0 < e0) this.add(y, x0, e0 - 1); if (x1 > e1) this.add(y, e1 + 1, x1); continue; }
      }
      this.add(y, x0, x1);
    }
  }
  add(y, a, b) {
    const lo = this.lo, hi = this.hi, lo2 = this.lo2, hi2 = this.hi2, MAX = 2147483647;
    if (y < this.top) this.top = y;
    if (y > this.bottom) this.bottom = y;
    if (lo[y] > hi[y]) { lo[y] = a; hi[y] = b; return; }
    if (b >= lo[y] - 1 && a <= hi[y] + 1) {
      lo[y] = Math.min(lo[y], a); hi[y] = Math.max(hi[y], b);
      if (lo2[y] <= hi2[y] && hi2[y] >= lo[y] - 1 && lo2[y] <= hi[y] + 1) { lo[y] = Math.min(lo[y], lo2[y]); hi[y] = Math.max(hi[y], hi2[y]); lo2[y] = MAX; hi2[y] = -1; }
      return;
    }
    if (lo2[y] > hi2[y]) { lo2[y] = a; hi2[y] = b; return; }
    if (b >= lo2[y] - 1 && a <= hi2[y] + 1) {
      lo2[y] = Math.min(lo2[y], a); hi2[y] = Math.max(hi2[y], b);
      if (hi2[y] >= lo[y] - 1 && lo2[y] <= hi[y] + 1) { lo[y] = Math.min(lo[y], lo2[y]); hi[y] = Math.max(hi[y], hi2[y]); lo2[y] = MAX; hi2[y] = -1; }
      return;
    }
    this.write(y);
    lo[y] = a; hi[y] = b;
  }
  write(y) {
    const span = (a, b) => { this.spans.push(y, a, b); const B = this.box ??= [a, y, b, y]; if (a < B[0]) B[0] = a; if (b > B[2]) B[2] = b; if (y < B[1]) B[1] = y; if (y > B[3]) B[3] = y; };
    span(this.lo[y], this.hi[y]);
    if (this.lo2[y] <= this.hi2[y]) span(this.lo2[y], this.hi2[y]);
    this.lo[y] = this.lo2[y] = 2147483647; this.hi[y] = this.hi2[y] = -1;
  }
  flush() { for (let y = this.top; y <= this.bottom; y++) if (this.lo[y] <= this.hi[y]) this.write(y); this.top = 2147483647; this.bottom = -1; }
  edges(xs, ys, n, left, right) {
    let t = 0, b = 0;
    for (let i = 1; i < n; i++) { if (ys[i] < ys[t]) t = i; if (ys[i] > ys[b]) b = i; }
    const y0 = Math.max(0, Math.ceil(ys[t] - .5)), y1 = Math.min(this.height - 1, Math.floor(ys[b] - .5));
    if (y1 < y0) return null;
    const chain = (step, at) => {
      for (let i = t; i !== b;) {
        const k = (i + step) % n, ya = ys[i], yb = ys[k];
        if (yb > ya) {
          const r0 = Math.max(y0, Math.ceil(ya - .5)), r1 = Math.min(y1, Math.ceil(yb - .5) - 1), slope = (xs[k] - xs[i]) / (yb - ya);
          for (let y = r0; y <= r1; y++) at[y] = xs[i] + (y + .5 - ya) * slope;
        }
        i = k;
      }
      const last = Math.ceil(ys[b] - .5);
      if (last >= y0 && last <= y1 && last + .5 === ys[b]) at[last] = xs[b];
    };
    chain(1, left); chain(n - 1, right);
    for (let y = y0; y <= y1; y++) if (left[y] > right[y]) { const s = left[y]; left[y] = right[y]; right[y] = s; }
    return [y0, y1];
  }
  // The pixels swept since the last take as one mask over their box (1 where new), and each row's first and last new column.
  take(lay) {
    this.flush();
    if (!this.box) return false;
    const [x0, y0, x1, y1] = this.box, w = x1 - x0 + 1, h = y1 - y0 + 1, sp = this.spans;
    if (this.mask.length < w * h) this.mask = new Uint8Array(w * h); else this.mask.fill(0, 0, w * h);
    if (this.rows.length < 2 * h) this.rows = new Int32Array(4 * h);
    const rows = this.rows;
    for (let r = 0; r < h; r++) { rows[2 * r] = 2147483647; rows[2 * r + 1] = -2; }
    for (let i = 0; i < sp.length; i += 3) {
      const r = sp[i] - y0, row = r * w - x0;
      this.mask.fill(1, row + sp[i + 1], row + sp[i + 2] + 1);
      rows[2 * r] = Math.min(rows[2 * r], sp[i + 1] - x0); rows[2 * r + 1] = Math.max(rows[2 * r + 1], sp[i + 2] - x0);
    }
    for (let r = 0; r < h; r++) if (rows[2 * r + 1] < 0) rows[2 * r] = -1;
    this.spans = []; this.box = null;
    lay(this.mask, x0, y0, w, h, rows);
    return true;
  }
}

// ---------- what a Wet stroke lays (WetPaintStroke) ----------
const WET_STROKE = { WASH_BODY: 1, PAPER: 250, PICKUP_PX: 40, INK_PULL: .6, INK_LIGHT: .2, INK_FULL: .92, INK_PX: 48, INK_LOAD: 4 };
// The app's line and body exist only with paint of its own (ResolvedBrush: Clear Water has neither; its footprint is its smudge's dab).
const ownLine = R => !!R.line && R.load > 0, ownBody = R => !!R.body && R.load > 0;
const wetReach = R => Math.max(ownLine(R) ? R.inkSize * (1 + .6 * R.pen.pressure) * (1 + 1.06 * (R.pen.fuzz || 0)) : 0, ownLine(R) && R.linePlaced ? R.placeSize : 0, ownBody(R) ? R.bodySize : 0);
const inkTrade = travel => { const t = Math.max(0, Math.min(1, travel)); return f32((WET_STROKE.INK_LIGHT + (WET_STROKE.INK_FULL - WET_STROKE.INK_LIGHT) * t * t) / (.2 * WET_STROKE.INK_PULL)); };
const inkCapacity = reach => { const k = Math.max(1, reach / WET_STROKE.INK_PX); return f32(WET_STROKE.INK_LOAD * BrushInk.CAPACITY * k * k); };
function washStrengthAt(R, travel) {
  const t = Math.max(0, Math.min(1, travel)), body = (R.lineInk ? GLAZE : 1) * R.parts.bodyAmt;
  return f32(WET_STROKE.WASH_BODY * body * Math.min(1, .55 + .65 * t) * (1 - .7 * clamp(R.pen.pDark || 0) * (1 - t)));
}
// The paint a dragging, loaded brush carries (PaintLoad): its own grey running out, mixed toward what it picked up.
class WetPaintLoad {
  constructor(page, gray) { this.page = page; this.gray = gray; this.picked = 0; this.travelled = 0; this.pickedAt = 0; this.amount = 1; this.started = false; }
  tone() { return Math.round(this.picked + (this.gray - this.picked) * this.amount); }
  travel(dist) { this.travelled += dist; }
  left(share) { this.amount = Math.max(0, Math.min(1, share)); }
  deposit() { return f32(.45 + .55 * this.amount); }
  pickUp(mask, x, y, w, h) {
    const D = this.page;
    let under = 0, count = 0;
    for (let row = 0; row < h; row++) for (let col = 0; col < w; col++) {
      const px = x + col, py = y + row;
      if (mask[row * w + col] && px >= 0 && py >= 0 && px < D.width && py < D.height) { under += D.strokeBaseTone(px, py); count++; }
    }
    const distance = this.travelled - this.pickedAt; this.pickedAt = this.travelled;
    if (count === 0) return;
    under /= count;
    if (!this.started) { this.picked = under; this.started = true; }
    else this.picked += (under - this.picked) * (1 - Math.exp(-distance / 40));
  }
}
class WetPaintStroke {
  constructor(st, canvas, W, holder) {
    const R = st.R, s = st.surf;
    this.st = st; this.R = R; this.W = W; this.canvas = canvas; this.holder = holder; this.swept = -1; this.finished = false;
    canvas.catchUp(false);
    canvas.brush(W);
    const m = canvas.wet;
    this.water = !!R.carry && R.carry.kind === "water";
    this.rinsed = this.water ? new BrushInk() : null;
    this.gray = Math.round(255 * (1 - R.shade));
    this.held = W.inks ? new BrushInk(inkCapacity(wetReach(R))) : null;
    if (this.held) { const f = canvas.held.get("ink:" + holder); if (f === undefined) this.held.load(this.gray); else this.held.restore(f); }
    this.step = Math.max(2, wetReach(R) / 24);
    this.sweep = new WetFootprint(R, st.pts, s, !ownBody(R));
    const lines = !!W.wash && W.line && !!R.line, whole = lines && R.bodySize <= R.inkSize;
    this.lineSweep = lines && !whole ? new WetFootprint(R, st.pts, s, true) : null;
    this.lineInWash = whole ? f32((255 - this.gray) * clamp(R.parts.inkShare)) : 0;
    if (R.loaded) st.oil = { share: clamp(Math.max(R.loaded.start, canvas.held.get("load:" + holder) || 0)), spent: 0 };
    const drags = !this.water && !this.held && !!R.carry && R.carry.moves;
    this.load = drags && R.loaded ? new WetPaintLoad(canvas.page, this.gray) : null;
    this.dirties = drags && !R.loaded;
    this.own = this.dirties ? f32(1 - .3 * R.carry.pick) : 1;
    this.dirt = -1;
    m.beginStroke();
  }
  present() {
    const pts = this.st.pts, newest = pts.length - 1;
    if (this.finished || newest <= this.swept) return;
    for (let k = this.swept + 1; k <= newest; k++) {
      if (this.sweep.holds()) break;
      if (k < newest && this.swept >= 0 && pts[k].d - pts[this.swept].d < this.step) continue;
      const travelled = this.swept < 0 ? 0 : f32(pts[k].d - pts[this.swept].d);
      this.sweep.to(k);
      if (this.lineSweep) {
        this.lineSweep.to(k);
        this.lineSweep.take((mask, x, y, w, h, rows) => this.canvas.wet.lineMask(mask, w, x, y, w, h, this.gray, this.R.parts.inkShare, rows));
      }
      this.sweep.take((mask, x, y, w, h, rows) => this.lay(mask, x, y, w, h, rows, k, travelled));
      this.swept = k;
    }
  }
  lay(mask, x, y, w, h, rows, k, travelled) {
    const m = this.canvas.wet, R = this.R, p = this.st.pts[k];
    if (this.water) { m.waterMask(mask, w, x, y, w, h, f32(.5 + .5 * clamp(R.carry.pick)), this.rinsed, 1); return; }
    if (this.W.wash) { m.washMask(mask, w, x, y, w, h, this.gray, washStrengthAt(R, p.p), this.W.washPull, rows, this.lineInWash); return; }
    if (this.held) { m.waterMask(mask, w, x, y, w, h, WET_STROKE.INK_PULL, this.held, inkTrade(p.p)); return; }
    if (this.dirties) { m.paintMask(mask, w, x, y, w, h, this.dirty(mask, x, y, w, h, travelled), false, f32(R.cover)); return; }
    if (!this.load) { m.paintMask(mask, w, x, y, w, h, this.gray, false, f32(R.cover)); return; }
    this.load.travel(travelled);
    this.load.pickUp(mask, x, y, w, h);
    this.load.left(p.own ?? 1);
    m.paintMask(mask, w, x, y, w, h, this.load.tone(), false, f32(R.cover * this.load.deposit()));
  }
  // Drag or Smear without Paint Load: the paint it crosses (as the stroke found it) mixed into what it picked up.
  dirty(mask, x, y, w, h, travelled) {
    const D = this.canvas.page;
    let under = 0, count = 0;
    for (let row = 0; row < h; row += 2) for (let col = 0; col < w; col += 2) {
      const px = x + col, py = y + row;
      if (!mask[row * w + col] || px < 0 || py < 0 || px >= D.width || py >= D.height) continue;
      const tone = D.strokeBaseTone(px, py);
      if (tone < WET_STROKE.PAPER) { under += tone; count++; }
    }
    if (count > 0) { under = f32(under / count); this.dirt = this.dirt < 0 ? under : f32(this.dirt + (under - this.dirt) * (1 - Math.exp(-travelled / WET_STROKE.PICKUP_PX))); }
    return this.dirt < 0 ? this.gray : Math.round(this.dirt + (this.gray - this.dirt) * this.own);
  }
  finish() {
    if (this.finished) return;
    this.sweep.lifted = true;
    if (this.lineSweep) this.lineSweep.lifted = true;
    this.present();
    this.finished = true;
    const st = this.st, held = this.canvas.held;
    if (this.R.loaded && st.oil) { const left = Math.max(0, st.oil.share - st.oil.spent / this.R.loaded.full); held.set("load:" + this.holder, left > this.R.loaded.start ? left : 0); }
    if (this.held) held.set("ink:" + this.holder, this.held.fullness());
    this.canvas.rev = this.canvas.s.rev | 0;
  }
}

// A wet wash with a pen line holds its body near the line (ResolvedBrush.wetBody): at most 40 px past it each side, 180 across.
const resolveWetBody = resolve;
resolve = function (B, size, shade) {
  const R = resolveWetBody(B, size, shade);
  const lineMedium = B.medium.k === "ink" || B.medium.k === "charcoal" || B.medium.k === "graphite";
  if (wetWashes(B) && lineMedium) R.bodySize = Math.min(R.bodySize, Math.max(R.inkSize, Math.min(180, R.inkSize + 80)));
  return R;
};
// A Wet stroke with a host that holds wet paint lays into it; any other stroke marks the page changed for it (surf.rev).
const StrokeWet = Stroke;
Stroke = class extends StrokeWet {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    const canvas = host && host.getWet && B.life.k === "wetpaint" ? host.getWet() : null, W = canvas ? wetOf(B, this.R, surf) : null;
    this.wetStroke = W ? new WetPaintStroke(this, canvas, W, host.holder || "any") : null;
  }
  add(samples) {
    if (!this.wetStroke) return super.add(samples);
    for (const s of samples) if (addPt(this.pts, ...s) && this.oil) this.oilSpend();
    const D = this.wetStroke.canvas.page, was = D.dirty; D.dirty = null;
    this.wetStroke.present();
    const r = D.dirty; D.dirty = r ? union(r, was || r) : was;
    if (r) grow(this.rect, r);
    return r;
  }
  end() {
    if (!this.wetStroke) { const r = super.end(); this.surf.rev = (this.surf.rev | 0) + 1; return r; }
    if (this.pts.length === 1) { const p = this.pts[0]; this.add([[p.x + .8, p.y, p.p, p.t + 16, p.tx, p.ty]]); }
    const D = this.wetStroke.canvas.page, was = D.dirty; D.dirty = null;
    this.wetStroke.finish();
    const r = D.dirty; D.dirty = r ? union(r, was || r) : was;
    if (r) grow(this.rect, r);
    this.lift = 0;
    return emptyRect(this.rect) ? null : this.rect.slice();
  }
};
// The eraser changes the page too: wet paint takes up what it left (surf.rev) when next used.
const EraserWet = Eraser;
Eraser = class extends EraserWet {
  add(samples) { const r = super.add(samples); if (r) this.s.rev = (this.s.rev | 0) + 1; return r; }
};
// Checks: a pen's pressure goes through the Manta's curve; a Wet stroke with a host stays wet and moves after lift, and
// one without lays as Instant; another stroke over moving paint lets it rest.
function wetPaintChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  t("a pen's pressure through the Manta's curve: .05 none, .30 a usual press (.625), .78 all of it", penTravel(.05) === 0 && Math.abs(penTravel(.3) - .625) < 1e-9 && penTravel(.78) === 1 && penTravel(1) === 1);
  const B = readAnyCode("watercolour-washy.M60i04o4b0000ji_1zz8t00ag9600_1zzex_000_7js_8zz0000000000000000zz0.1s13pn4").b;
  const pts = []; for (let i = 0; i <= 60; i++) pts.push({ x: 40 + 4 * i, y: 100 + 20 * Math.sin(i / 9), p: .7, t: 8 * i, tx: NaN, ty: NaN });
  const s = modSurface(320, 200, 7), host = { wet: null, getWet() { return this.wet ??= new WetCanvas(s); } };
  layStroke(s, B, pts, 60, .3, host);
  const lifted = s.page.slice(), moving = host.wet && host.wet.moving();
  host.wet.settle();
  let moved = 0; for (let i = 0; i < lifted.length; i++) if (Math.abs(lifted[i] - s.page[i]) >= 1) moved++;
  t("a Wet stroke with a host stays wet and moves after lift (" + moved + " px moved)", moving && moved > 200 && !host.wet.moving());
  const s2 = modSurface(320, 200, 7); layStroke(s2, B, pts, 60, .3, null);
  let laid = 0; for (let i = 0; i < s2.page.length; i++) if (s2.page[i] < PAPER_TONE - 1) laid++;
  t("without one it lays as Instant (" + laid + " px)", laid > 2000);
  layStroke(s, B, pts.map(q => ({ ...q, y: q.y + 15 })), 60, .3, host);
  const pen = readAnyCode("steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud").b;
  layStroke(s, pen, pts, 4, 1, host);
  t("another stroke over moving wet paint lets it rest, still wet", host.wet.step(1) === -1);
  return out;
}
const selfTestWetPaint = selfTest;
selfTest = function () { const r = selfTestWetPaint(); r.wetPaint = wetPaintChecks(); r.ok = r.ok && r.wetPaint.every(c => c.ok); return r; };
