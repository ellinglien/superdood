// ===================================================================================================================
// The page's planes (the app's ToneDocument: a page of two layers, Wash under Ink as InkWashLayers.blank, each a base and
// four kind planes, Dry media, Wash, Pen, Opaque; ToneDocument.stacked shows them both bases first, then kind by kind,
// the wash layer's under the ink layer's). A stroke reads and writes only its own plane (strokeBaseTones): the surface's
// page, base, kept, print and dots are that plane's while it draws (planeBind), so the engine is as it was on one page.
// Each plane keeps a coverage beside its tones over white (cov, 0 to 1; the app's alpha): a transparent mark covers by
// its darkness, opaque paint, light over dark, White and Invert by their own (planeCover, engine.js). What shows is the planes stacked (planesShow: s.shown); a page that
// has only ever had one plane shows it as it is, and draws exactly as before. Built last (engine-files.cjs).
// User layers (the layers panel) are left out: a brush reads none but the pair's.
// ===================================================================================================================
const KIND_PEN = 1, KIND_PAINT = 2, KIND_PENCIL = 4, KIND_ALL = 7, KIND_OPAQUE = 8;
const PLANE_KINDS = [KIND_PENCIL, KIND_PAINT, KIND_PEN, KIND_PAINT], LAYER_WASH = 0, LAYER_INK = 1, STACK_N = 10;
// What a plane holds of the surface: the engine's arrays while it is bound.
const PLANE_FIELDS = ["page", "base", "cov", "covBase", "kept", "keptBase", "print", "dots"];
// Place k of the stack, bottom to top (ToneDocument.stacked): layer 0 (wash) or 1 (ink), plane p (-1 its base).
const stackAt = (layer, p) => 2 * (p + 1) + layer;
// The plane a gesture of kinds lays on (ToneDocument.planeOf); -1, the base, for none or more than one.
function kindPlane(kinds) {
  if (kinds & KIND_OPAQUE) return (kinds & KIND_ALL) === KIND_PAINT ? 3 : -1;
  return kinds === KIND_PENCIL ? 0 : kinds === KIND_PAINT ? 1 : kinds === KIND_PEN ? 2 : -1;
}
// The kind its Medium is (ResolvedBrush.medium): opaque paint Paint on its own plane; a texture (Screen, Stipple, Hatch,
// Hand Hatch) Paint whatever its medium; charcoal, graphite and crayon Dry media; the rest Pen.
function brushMedium(B, R) {
  const mk = B.medium.k, tk = B.tip.k, texture = !!R.screen || tk === "stipple" || tk === "hatch" || tk === "handhatch";
  return mk === "cover" ? KIND_PAINT | KIND_OPAQUE : mk === "wash" || texture ? KIND_PAINT : mk === "charcoal" || mk === "graphite" || mk === "crayon" ? KIND_PENCIL : KIND_PEN;
}
// The layer it draws on (ModularStroke.layerOf): ink, charcoal, graphite, resist, screentone and crayon the ink layer,
// but a wet wash (wet.js wetWashes); the rest the wash layer.
const brushLayer = B => ["ink", "charcoal", "graphite", "resist", "tone", "crayon", "vector"].includes(B.medium.k) && !wetWashes(B) ? LAYER_INK : LAYER_WASH;
// The plane a stroke of B draws on (ModularStroke.kinds): its paint's; a smudge with none of its own its Medium's plane
// where its layer has one, else the base.
function strokePlane(s, B, R) {
  const layer = brushLayer(B), medium = brushMedium(B, R);
  let kinds = R.load > 0 ? medium : 0;
  if (!kinds && R.carry && R.carry.moves) { const p = kindPlane(medium); kinds = p >= 0 && s.planes && s.planes[stackAt(layer, p)] ? medium : 0; }
  return stackAt(layer, kindPlane(kinds));
}
// The plane the parity harness lays its marks on (WebParity.scene): the brush's own.
const markPlane = strokePlane;

function planeSave(s) { if (s.bound !== undefined) { const p = s.planes[s.bound]; for (const f of PLANE_FIELDS) p[f] = s[f]; } }
// Binds plane k: the surface's arrays become its (made blank the first time). The first plane bound on a page takes what
// the page holds (marks laid before any stroke, as the parity harness lays them on the brush's own).
function planeBind(s, k) {
  if (k === undefined || s.bound === k) return;
  const n = s.w * s.h, P = PAPER_TONE;
  if (s.bound === undefined) {
    s.planes = new Array(STACK_N).fill(null); s.planes[k] = {}; s.bound = k;
    s.cov = new Float32Array(n); for (let i = 0; i < n; i++) s.cov[i] = Math.max(0, 1 - s.page[i] / P);
    s.covBase = Float32Array.from(s.cov);
    return planeSave(s);
  }
  planeSave(s);
  let p = s.planes[k];
  if (!p) {
    // A second plane: from now on the page shows them stacked (one alone shows as it is).
    if (!s.shown) s.shown = Float32Array.from(s.page);
    const page = new Float32Array(n).fill(P);
    p = s.planes[k] = { page, base: Float32Array.from(page), cov: new Float32Array(n), covBase: new Float32Array(n), kept: null, keptBase: null, print: null, dots: null };
  }
  for (const f of PLANE_FIELDS) s[f] = p[f];
  s.bound = k;
}
// fn with plane k bound (undefined: as it is), the plane bound before put back.
function planeWith(s, k, fn) {
  const back = s.bound;
  if (k === undefined || back === undefined || k === back) return fn();
  planeBind(s, k);
  try { return fn(); } finally { planeBind(s, back); }
}
// The planes stacked over rect r into s.shown (ToneDocument.compositeTones): each over what is under it by its coverage,
// a transparent mark's at least its darkness (kept, as the app's write rule keeps a pixel's coverage).
function planesShow(s, r) {
  if (!s.shown || !r) return;
  planeSave(s);
  const P = PAPER_TONE, w = s.w, x0 = Math.max(0, Math.floor(r[0])), y0 = Math.max(0, Math.floor(r[1])), x1 = Math.min(w - 1, Math.ceil(r[2])), y1 = Math.min(s.h - 1, Math.ceil(r[3]));
  const list = s.planes.filter(p => p);
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    let v = P;
    for (const p of list) { const t = p.page[i], c = Math.max(p.cov[i], 1 - t / P); if (c > p.cov[i]) p.cov[i] = c; v = t + (v - P) * (1 - c); }
    s.shown[i] = v;
  }
}
// The page as it shows, every plane stacked: the page itself while it has one plane.
function shownPage(s) { if (s.shown) planesShow(s, [0, 0, s.w - 1, s.h - 1]); return s.shown || s.page; }
// fn reading s.page as the page shows (planesShow first, for the rect it reads).
function planesView(s, fn) { if (!s.shown) return fn(); const page = s.page; s.page = s.shown; try { return fn(); } finally { s.page = page; } }
// The page under a stroke as it finds it (ModularCompose.Under): the planes of layers 0 to top stacked, its own as found
// (it has drawn nothing yet). Null while the page has one plane: then it is the stroke's base.
function planesUnder(s, top) {
  if (!s.shown) return null;
  planeSave(s);
  const P = PAPER_TONE, out = new Float32Array(s.w * s.h).fill(P), list = s.planes.filter((p, k) => p && (k & 1) <= top);
  for (let i = 0; i < out.length; i++) { let v = P; for (const p of list) { const t = p.page[i], c = Math.max(p.cov[i], 1 - t / P); v = t + (v - P) * (1 - c); } out[i] = v; }
  return out;
}

// ---------- strokes on their planes ----------
const StrokePl = Stroke;
Stroke = class extends StrokePl {
  constructor(surf, B, size, shade, host) {
    const R = resolve(B, size, shade);
    // Masking fluid lays on the page's fluid (resist planes), no plane; a stroke on floating paint or wet paint lays on
    // that paint's plane (ModularStroke.begin: water.kinds; WetPaintStroke: canvas.kinds).
    const bath = R.float && host && host.bath && host.bath.wet ? host.bath : null, wet = B.life.k === "wetpaint" && host && host.wet ? host.wet : null;
    if (!(R.resist && !(R.erase < 1))) planeBind(surf, bath && bath.plane !== undefined ? bath.plane : wet && wet.plane !== undefined ? wet.plane : strokePlane(surf, B, R));
    // White, Invert and light over dark read the page under them, the stroke's own layer and below; Behind the pair's.
    surf.under = R.blend === "behind" ? planesUnder(surf, LAYER_INK) : R.blend === "lighten" || R.blend === "invert" || lightOver(R) > 0 ? planesUnder(surf, brushLayer(B)) : null;
    super(surf, B, size, shade, host);
    if (surf.cov) surf.covBase.set(surf.cov);
    this.plane = surf.bound; this.under = surf.under;
    // The layer drawn on is the page's active one after (ModularStroke.selectLayer): what a live page's change lays on.
    surf.active = brushLayer(B);
  }
  onPlane(fn) { const s = this.surf; return planeWith(s, this.plane, () => { const u = s.under; s.under = this.under; try { return fn(); } finally { s.under = u; } }); }
  add(samples) { return this.onPlane(() => this.smeared(super.add(samples))); }
  end() { return this.onPlane(() => this.smeared(super.end())); }
  holdTick(t) { return this.onPlane(() => super.holdTick(t)); }
  // A smudge's write (ToneDocument.smearTones): its coverage what the smear leaves there, not what it found.
  // shortcut: as its darkness, so a smeared opaque mark covers as a transparent one; keep the carry's coverage if that shows.
  smeared(r) {
    const s = this.surf, C = this.R.carry;
    if (r && s.cov && C && C.moves) for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * s.w + Math.max(0, r[0]), e = y * s.w + Math.min(s.w - 1, r[2]); i <= e; i++)
      if (s.page[i] !== s.base[i]) s.cov[i] = Math.max(0, 1 - s.page[i] / PAPER_TONE);
    return r;
  }
};
// What keeps working after its stroke (Slow's water, Float's bath, wet paint, Halo's veins, Fading's marks) works on the
// plane it was laid on.
const addWetPl = Water.prototype.addWet;
Water.prototype.addWet = function (...a) { this.plane = this.s.bound; return addWetPl.apply(this, a); };
// shortcut: a pen line into a wet wash (Takes ink) trades with the water on the pen's plane; keep the water's plane apart if that shows.
for (const m of ["step", "dryAll"]) { const f = Water.prototype[m]; Water.prototype[m] = function (...a) { return planeWith(this.s, this.plane, () => f.apply(this, a)); }; }
const BathPl = Bath;
Bath = class extends BathPl { constructor(surf, ...rest) { super(surf, ...rest); this.plane = surf.bound; } };
for (const m of ["render", "print", "undoLast"]) { const f = BathPl.prototype[m]; if (f) BathPl.prototype[m] = function (...a) { return planeWith(this.s, this.plane, () => f.apply(this, a)); }; }
// shortcut: wet paint keeps the plane of the stroke that first wet it, as the app's canvas keeps its kinds; a stroke of
// another layer joins it there.
const WetPaintStrokePl = WetPaintStroke;
WetPaintStroke = class extends WetPaintStrokePl { constructor(st, canvas, ...rest) { if (canvas.plane === undefined) canvas.plane = st.surf.bound; super(st, canvas, ...rest); } };
for (const m of ["catchUp", "frame", "step"]) { const f = WetCanvas.prototype[m]; WetCanvas.prototype[m] = function (...a) { return planeWith(this.s, this.plane, () => f.apply(this, a)); }; }
const HaloPl = Halo;
Halo = class extends HaloPl {
  constructor(s, ...rest) { super(s, ...rest); this.plane = s.bound; }
  step(...a) { return planeWith(this.s, this.plane, () => super.step(...a)); }
};
const fadeStepPl = fadeStep;
fadeStep = function (s, M, t) { if (M.plane === undefined) M.plane = s.bound; return planeWith(s, M.plane, () => fadeStepPl(s, M, t)); };

// ---------- the eraser: every plane of the kinds it takes ----------
// The app's EraseBothStroke takes the pair's planes of its kinds and both bases (paint of no known kind is every kind);
// er.kinds (EraseKind.KINDS: 0 Everything, KIND_PEN, KIND_PAINT, KIND_PENCIL) takes them whole, permanent paint too
// (the owner, 2026-10-22), a chosen kind Tone's dots whole; left undefined, the lab's Erasable rule (ToneDocument.eraseKinds(0)).
// shortcut: a chosen kind still lifts masking fluid where it passes; the app's leaves the fluid (no kind of mark).
const EraserPl = Eraser;
Eraser = class extends EraserPl {
  disc(cx, cy, rad, ...rest) {
    const s = this.s, whole = this.kinds !== undefined, takes = this.kinds ? this.kinds : KIND_ALL;
    if (!s.planes) return this.discOn(cx, cy, rad, rest, whole && this.kinds !== 0 ? "dots" : whole);
    const n = s.w * s.h, back = s.bound, targets = [];
    for (let k = 0; k < STACK_N; k++) if (s.planes[k] && (k < 2 || PLANE_KINDS[(k >> 1) - 1] & takes)) targets.push(k);
    this.seens ??= [];
    // Fluid this pass lifts leaves every plane under it (the first plane's pass takes it).
    const lift = [], rp = s.resist, x0 = Math.max(0, Math.floor(cx - rad)), x1 = Math.min(s.w - 1, Math.ceil(cx + rad)), y0 = Math.max(0, Math.floor(cy - rad)), y1 = Math.min(s.h - 1, Math.ceil(cy + rad));
    if (rp) for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const i = y * s.w + x; if (rp[i] > s.resistKept[i] + .004 && (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= rad * rad) lift.push(i); }
    let r = null;
    try {
      targets.forEach((k, j) => {
        planeBind(s, k);
        this.seen = this.seens[k] ??= new Uint8Array(n);
        if (j > 0) for (const i of lift) this.seen[i] = 1;
        r = this.discOn(cx, cy, rad, rest, whole && this.kinds !== 0 ? "dots" : whole);
      });
    } finally { planeBind(s, back); }
    return r || [x0, y0, x1, y1];
  }
  // One plane's disc: whole takes what the eraser can't (kept) too; "dots" Tone's dots whole as well.
  discOn(cx, cy, rad, rest, whole) {
    const s = this.s, kept = s.kept, dots = s.dots, seen = this.seen, x0 = Math.max(0, Math.floor(cx - rad)), x1 = Math.min(s.w - 1, Math.ceil(cx + rad)), y0 = Math.max(0, Math.floor(cy - rad)), y1 = Math.min(s.h - 1, Math.ceil(cy + rad));
    const was = s.cov ? [] : null;
    if (was) for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const i = y * s.w + x; if (!seen[i]) was.push(i); }
    if (whole) s.kept = null;
    if (whole === "dots") s.dots = null;
    let r;
    try { r = super.disc(cx, cy, rad, ...rest); } finally { s.kept = kept; s.dots = dots; }
    if (whole && kept) for (const i of was || []) if (seen[i]) kept[i] = PAPER_TONE;
    if (whole === "dots" && dots) for (const i of was || []) if (seen[i]) dots[i] = 0;
    // What it erased covers as what it left: paper, or the tone kept.
    if (was) for (const i of was) if (seen[i]) s.cov[i] = Math.max(0, 1 - s.page[i] / PAPER_TONE);
    return r;
  }
};

// ---------- the page shown, cleared, laid on ----------
const showPagePl = showPage;
showPage = function (ctx, surf, rect) { if (!surf.shown) return showPagePl(ctx, surf, rect); planesShow(surf, rect); return planesView(surf, () => showPagePl(ctx, surf, rect)); };
const pageGraysPl = pageGrays;
pageGrays = function (s, hatch) { shownPage(s); return planesView(s, () => pageGraysPl(s, hatch)); };
// A clear page: one plane again, unbound, taken by the next stroke.
const blankPagePl = blankPage;
blankPage = function (s) { if (s.bound !== undefined) planeSave(s); s.planes = undefined; s.bound = undefined; s.shown = null; s.under = null; s.cov = s.covBase = null; blankPagePl(s); };
// Greys received from another page (a live page's LiveChange: what that page showed, over all its layers), laid as the
// Manta lays them (LiveProbe.onLayer, then PictureImport.lay): on the active layer's base (the layer last drawn on), as ink, each its layer's look
// scaled by how much darker the page must get there, only where that darkens the layer; set (an Undo's) as they are,
// lighter too. greys: w a row, 255 or more clear (paper is PAPER_TONE here); returns whether anything changed.
// shortcut: the Manta's own (its comment): exact for black ink over another layer's marks, near for grey ink on this layer.
function planesLay(s, x, y, w, h, greys, set, layer = s.active ?? LAYER_INK) {
  planesShow(s, [x, y, x + w - 1, y + h - 1]);
  const P = PAPER_TONE, before = new Float32Array(w * h).fill(P), shown = s.shown || s.page;
  for (let j = 0; j < w * h; j++) { const px = x + j % w, py = y + (j / w | 0); if (px < s.w && py < s.h) before[j] = shown[py * s.w + px]; }
  let changed = false;
  if (s.bound === undefined) planeBind(s, stackAt(layer, -1));
  planeWith(s, stackAt(layer, -1), () => {
    planeSave(s);
    const look = s.planes.filter((p, k) => p && (k & 1) === layer);
    for (let j = 0; j < w * h; j++) {
      const px = x + j % w, py = y + (j / w | 0), i = py * s.w + px;
      if (greys[j] >= 255 || px >= s.w || py >= s.h) continue;
      // The layer's look here (its base and planes as one, ToneDocument.layerTone).
      let lt = P;
      for (const p of look) { const t = p.page[i], c = Math.max(p.cov[i], 1 - t / P); lt = t + (lt - P) * (1 - c); }
      const g = Math.min(P, greys[j]), now = before[j];
      let v = g;
      if (!set) { if (g >= now) continue; v = now <= 0 ? g : Math.min(P, lt * g / now); if (v >= lt) continue; }
      if (s.page[i] === v) continue;
      s.page[i] = s.base[i] = v; s.cov[i] = Math.max(s.cov[i], 1 - v / P); changed = true;
    }
  });
  planesShow(s, [x, y, x + w - 1, y + h - 1]);
  s.rev = (s.rev | 0) + 1;
  return changed;
}
// The page made to show greys (w a row, 255 or more left as it is) as one plain picture: every plane clear there and the
// active layer's base those greys. This page's own Undo (a live page's takeBack), whose pixels must show as they were.
function planesSet(s, x, y, w, h, greys) {
  const k = stackAt(s.active ?? LAYER_INK, -1);
  if (s.bound === undefined) planeBind(s, k);
  planeWith(s, k, () => {
    planeSave(s);
    for (let j = 0; j < w * h; j++) {
      const px = x + j % w, py = y + (j / w | 0), i = py * s.w + px;
      if (greys[j] >= 255 || px >= s.w || py >= s.h) continue;
      for (const p of s.planes) if (p) { p.page[i] = p.base[i] = PAPER_TONE; p.cov[i] = 0; if (p.kept) p.kept[i] = PAPER_TONE; if (p.print) p.print[i] = PAPER_TONE; if (p.dots) p.dots[i] = 0; }
      const v = Math.min(PAPER_TONE, greys[j]);
      s.page[i] = s.base[i] = v; s.cov[i] = Math.max(0, 1 - v / PAPER_TONE);
    }
  });
  planesShow(s, [x, y, x + w - 1, y + h - 1]);
  s.rev = (s.rev | 0) + 1;
}
