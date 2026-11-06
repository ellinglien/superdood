// The Stamp, Cut Stamp and Import Image on the browser's drawing screen (live.html, /live), as on the Manta:
// a slot holding a Stamp offers Stamp ▾ in its picker, its menu beside the slot (its card, Cut Stamp, Turns, Spacing,
// Ink Fade: InkWashBar.stampMenu); Cut Stamp (that menu's, the Tools page's and Brush Lab's) makes the pen's next stroke
// a lasso, in marching ants, whose ink becomes the Stamp's nib (PaintActivity.stampCut, BrushLabActivity.cutStamp);
// the menu's Import places a picture (PicturePlacer): fitted inside what shows of the page, moved, resized from its
// handle or pinched, made Lighter or Darker and More or Less Contrast, then Place or Cancel along the page's foot. Every decision is the Manta's Java through WebEngine
// (DrawLayout.stampRows, ToolSlots.cutStamp, StampNib, PictureImport's Frame, Placing, greys and lay); this draws them.
// A guest's stamps are strokes and a picture placed is a mark, so both reach the host as live pixels (live.html's send).
// On the web the picture comes from a file input (the browser's picker has its own Cancel), or a drop on the page; words
// dropped there (a .txt file or plain text) are a text box where they land (m4.js dropText).
export function install(G) {
  const { T, el, font, popup, placePopup, closePopup, menuRow, say, pull, send, view, sized } = G;
  const SW = JSON.parse(T.stampWords());

  // ---- Helpers ----
  const pagePx = dp => dp * G.dpr / G.V.s;
  // A rect [x0, y0, x1, y1) on the page as pull takes it (inclusive), or null when none of it is on the page.
  const clip = a => { const x0 = Math.max(0, a[0]), y0 = Math.max(0, a[1]), x1 = Math.min(G.W, a[2]) - 1, y1 = Math.min(G.H, a[3]) - 1; return x1 < x0 || y1 < y0 ? null : [x0, y0, x1, y1]; };
  const union = (a, b) => !b ? a : !a ? b : [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])];
  // What shows of the page, page px (as m4.js's Lasso reads it): left, top, right, bottom.
  function shown() { const V = G.V, vw = view.offsetWidth * G.dpr, vh = view.offsetHeight * G.dpr; return [-V.x / V.s, -V.y / V.s, (vw - V.x) / V.s, (vh - V.y) / V.s]; }
  // Marching ants along pts (x, y pairs) from point i on, on ctx in its own px, k of them to a CSS px.
  function ants(ctx, pts, i, k) {
    if (pts.length < 4) return;
    ctx.save(); ctx.strokeStyle = "#000"; ctx.lineWidth = 1.5 * k; ctx.setLineDash([4 * k, 4 * k]); ctx.beginPath();
    ctx.moveTo(pts[Math.max(0, i - 2)], pts[Math.max(1, i - 1)]);
    for (let j = Math.max(2, i); j < pts.length; j += 2) ctx.lineTo(pts[j], pts[j + 1]);
    ctx.stroke(); ctx.restore();
  }
  const box = (pts, pad) => { let r = null; for (let i = 0; i < pts.length; i += 2) r = union(r, [pts[i], pts[i + 1], pts[i], pts[i + 1]]); return r && [Math.floor(r[0] - pad), Math.floor(r[1] - pad), Math.ceil(r[2] + pad) + 1, Math.ceil(r[3] + pad) + 1]; };

  // ---- The stamp menu (InkWashBar.stampMenu), from the picker's Stamp ▾ ----
  function stampMenu(slot, anchor) {
    const pop = popup(anchor, 0);
    pop.dataset.stamp = slot;
    fill(pop, slot);
    placePopup();
  }
  function fill(pop, slot) {
    const m = JSON.parse(T.stampMenu(slot, innerWidth, innerHeight));
    pop.style.width = Math.min(m.width, innerWidth) + "px";
    pop.replaceChildren();
    // Its card: the brush's loop, its name and line.
    const card = el("div", "cell"), art = el("canvas"), name = el("div", "name", m.name), line = el("div", "name small", m.line);
    card.style.height = m.card + "px"; card.style.padding = "4px"; card.setAttribute("aria-label", m.said); card.setAttribute("role", "img");
    name.style.font = font("CAPTION", 700); line.style.font = font("CAPTION");
    card.append(art, name, line);
    const aw = m.width - 8, ah = Math.max(10, m.card - 8 - 2 * G.HOLD.type.CAPTION[0] * 1.4), ctx = sized(art, aw, ah);
    ctx.drawImage(G.picture("stamp " + m.key, Math.round(aw * G.dpr), Math.round(ah * G.dpr), (w, h) => T.stampLoop(slot, w, h)), 0, 0, aw, ah);
    pop.append(card, menuRow(m.cut, false, () => { closePopup(); cut(slot); }, m.chip));
    // Turns, Spacing and Ink Fade: a label, then a chip for each preset, the nearest marked; a tap sets it, the menu drawn again in place.
    m.rows.forEach((r, i) => {
      const label = el("div", "otitle", r.label); label.style.font = font("MENU", 700); label.style.padding = `6px ${m.inset}px`;
      const chips = el("div", "split");
      r.words.forEach((w, j) => chips.appendChild(menuRow(w, j === r.marked, () => { if (T.stampSet(slot, i, j)) G.ownTools(); fill(pop, slot); }, m.chip)));
      pop.append(label, chips);
    });
  }

  // ---- Cut Stamp: the pen's next stroke on the page is a lasso; the ink in it the slot's Stamp's nib ----
  let cutting = -1, loop = null;
  function cut(slot) {
    if (placing) return;
    if (G.M4) G.M4.chose();
    cutting = slot;
    say(SW.lasso, SW.lassoMs);
  }
  function cutDown(e) {
    const [x, y] = G.pagePoint(e);
    loop = { id: e.pointerId, pts: [x, y] };
    try { view.setPointerCapture(e.pointerId); } catch (err) { /* synthetic */ }
    G.hideRing();
  }
  function cutMove(e) {
    const [x, y] = G.pagePoint(e), p = loop.pts, n = p.length;
    if (Math.hypot(x - p[n - 2], y - p[n - 1]) < pagePx(3)) return;
    p.push(x, y);
    ants(G.pageCtx, p, n, G.dpr / G.V.s);
  }
  function cutUp() {
    const slot = cutting, pts = loop.pts, r = clip(box(pts, pagePx(4)) || [0, 0, 0, 0]);
    loop = null; cutting = -1;
    if (r) pull(r);  // the ants off the page
    const said = T.stampCut(G.P.id, slot, new Float32Array(pts));
    if (said === SW.done) G.ownTools();
    say(said, said === SW.nothing ? SW.nothingMs : undefined);
  }

  // ---- Brush Lab's Cut Stamp: the scratch pad's next pen stroke is the lasso; tapped again while it waits, it stops ----
  let labWaits = null, labSaid = null;
  function labButton(words, render) {
    const waiting = labWaits && labWaits === G.pad;
    const b = el("button", "lb" + (waiting ? " ink" : ""), labSaid || words);
    b.style.font = font("LABEL"); b.setAttribute("aria-pressed", String(!!waiting));
    b.onclick = () => { labWaits = waiting ? null : G.pad; labSaid = null; render(); };
    return b;
  }
  let padLoop = null;
  const padAt = (e, pad) => { const r = pad.c.getBoundingClientRect(); return [(e.clientX - r.left) * G.dpr, (e.clientY - r.top) * G.dpr]; };
  function padDown(e, pad) {
    if (!labWaits || labWaits !== pad || !pad.inLab || e.button > 0) return false;
    padLoop = { id: e.pointerId, pts: padAt(e, pad) };
    try { pad.c.setPointerCapture(e.pointerId); } catch (err) { /* synthetic */ }
    return true;
  }
  function padMove(e, pad) {
    if (!padLoop || e.pointerId !== padLoop.id) return false;
    const [x, y] = padAt(e, pad), p = padLoop.pts, n = p.length;
    if (Math.hypot(x - p[n - 2], y - p[n - 1]) >= 3 * G.dpr) { p.push(x, y); ants(pad.ctx, p, n, G.dpr); }
    return true;
  }
  function padUp(e, pad, render) {
    if (!padLoop || e.pointerId !== padLoop.id) return false;
    const pts = padLoop.pts, r = box(pts, 4 * G.dpr);
    padLoop = null; labWaits = null;
    if (r) pad.put([Math.max(0, r[0]), Math.max(0, r[1]), Math.min(pad.pw, r[2]) - 1, Math.min(pad.ph, r[3]) - 1]);
    const said = T.labCut(pad.P.id, new Float32Array(pts));
    if (said === SW.nothing) { labSaid = said; setTimeout(() => { labSaid = null; render(); }, SW.nothingMs); }
    else say(said);
    render();
    return true;
  }

  // ---- Import Image (PicturePlacer): the picture over the page, Cancel and Place along its foot ----
  let placing = null;
  function importImage(file) {
    if (file) return read(file);
    const input = el("input"); input.type = "file"; input.accept = "image/*";
    input.onchange = () => { if (input.files[0]) read(input.files[0]); };
    input.click();
  }
  // Read no larger than fits the page (PicturePlacer.read; the browser turns it upright as its camera took it).
  async function read(file) {
    let img;
    try { img = await createImageBitmap(file); } catch (err) { return say(SW.unreadable); }
    const [w, h] = T.pictureSize(img.width, img.height, G.W, G.H);
    let pic = img;
    if (w !== img.width) {
      pic = new OffscreenCanvas(w, h);
      const c = pic.getContext("2d"); c.imageSmoothingQuality = "high"; c.drawImage(img, 0, 0, w, h); img.close();
    }
    begin(pic);
  }
  function begin(pic) {
    if (G.live || G.shown) return;
    cancel(); closePopup(); cutting = -1;
    if (G.M4) G.M4.chose();
    const s = shown(), note = T.placeBegin(G.P.id, pic.width, pic.height, s[0], s[1], s[2], s[3]);
    if (note) return say(note);
    placing = { pic, page: G.P, pointers: new Map(), first: -1, second: -1, area: null, bar: bar(), frame: 0 };
    show();
  }
  function bar() {
    const b = el("div"); b.dataset.placing = "1";
    Object.assign(b.style, { position: "absolute", left: 0, right: 0, bottom: 0, height: SW.bar + "px", display: "flex", alignItems: "center", justifyContent: "flex-end",
      gap: SW.gap + "px", padding: `0 ${SW.gap}px`, background: "#fff", borderTop: "1px solid #000", boxSizing: "border-box", zIndex: 6 });
    b.addEventListener("pointerdown", e => e.stopPropagation());
    const steps = SW.steps.map((words, i) => [words, () => step(i), "lb", i]);
    for (const [words, act, cls, i] of [...steps, [SW.cancel, cancel, "lb"], [SW.place, place, "lb ink"]]) {
      const k = el("button", cls, words); k.style.font = font("LABEL"); k.style.height = SW.button + "px"; k.onclick = act; b.appendChild(k);
      if (i !== undefined) k.dataset.step = i;
    }
    view.appendChild(b);
    return b;
  }
  // Lighter, Darker, More or Less Contrast (PictureImport.STEPS), a step (Placing.step): shown again with it, each off once its last step is taken.
  function step(i) {
    if (!placing || frame()[12] || !T.placeStep(i)) return;
    placing.bar.querySelectorAll("[data-step]").forEach(k => { k.disabled = !T.placeCanStep(+k.dataset.step); });
    show();
  }
  // The frame: x, y, w, h, its rect, its handle, whether a pointer moves it (WebEngine.placeFrame).
  const frame = () => T.placeFrame();
  // The picture as it covers the page's x, y, w × h, ARGB (PicturePlacer.region: drawn smoothed into the frame).
  function region(f, x, y, w, h) {
    const c = new OffscreenCanvas(w, h), ctx = c.getContext("2d");
    ctx.imageSmoothingQuality = "high"; ctx.drawImage(placing.pic, f[0] - x, f[1] - y, f[2], f[3]);
    const d = new Uint32Array(ctx.getImageData(0, 0, w, h).data.buffer), argb = new Int32Array(w * h);
    for (let i = 0; i < d.length; i++) { const v = d[i]; argb[i] = (v & 0xff00ff00) | (v & 0xff) << 16 | v >>> 16 & 0xff; }
    return argb;
  }
  // Drawn over the page's own pixels (as the Manta draws over its raster), what it covered put back first: while a
  // pointer moves it its outline and handle (ink outside, a line of paper inside), at rest the picture as Place will lay it.
  function show() {
    if (!placing) return;
    if (placing.area) pull(placing.area);
    const f = frame(), r = [f[4], f[5], f[6], f[7]], hd = [f[8], f[9], f[10], f[11]], ctx = G.pageCtx, t = SW.outline;
    placing.area = clip(union([r[0] - t, r[1] - t, r[2] + t, r[3] + t], hd));
    const ring = (l, tp, rt, b, w, c) => { ctx.fillStyle = c; ctx.fillRect(l, tp, rt - l, w); ctx.fillRect(l, b - w, rt - l, w); ctx.fillRect(l, tp + w, w, b - tp - 2 * w); ctx.fillRect(rt - w, tp + w, w, b - tp - 2 * w); };
    if (f[12]) { ring(r[0] - t, r[1] - t, r[2] + t, r[3] + t, t - 1, "#000"); ring(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 1, "#fff"); }
    else {
      const a = clip(r);
      if (a) {
        const w = a[2] - a[0] + 1, h = a[3] - a[1] + 1, g = new Int32Array(T.pictureGreys(region(f, a[0], a[1], w, h))), img = ctx.createImageData(w, h), S = G.S, W = G.W;
        for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
          const i = y * w + x, under = S[(a[1] + y) * W + a[0] + x], v = G.screenGrey(g[i] < 0 ? under : Math.min(under, g[i]));
          img.data[4 * i] = img.data[4 * i + 1] = img.data[4 * i + 2] = v; img.data[4 * i + 3] = 255;
        }
        ctx.putImageData(img, a[0], a[1]);
      }
      ring(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 1, "#000");
    }
    ctx.fillStyle = "#fff"; ctx.fillRect(hd[0], hd[1], hd[2] - hd[0], hd[3] - hd[1]);
    ctx.fillStyle = "#000"; ctx.fillRect(hd[0] + 3, hd[1] + 3, hd[2] - hd[0] - 6, hd[3] - hd[1] - 6);
  }
  // At most once a frame while it moves.
  const showSoon = () => { if (placing && !placing.frame) placing.frame = requestAnimationFrame(() => { if (placing) { placing.frame = 0; show(); } }); };
  // Place: the picture laid where it lies, on the layer drawn on, as any mark (one Undo step), and sent as one.
  function place() {
    if (!placing) return;
    if (G.P !== placing.page) return cancel();
    const f = frame(), a = clip([f[4], f[5], f[6], f[7]]);
    if (placing.area) pull(placing.area);
    placing.area = null;
    if (a) {
      const w = a[2] - a[0] + 1, h = a[3] - a[1] + 1, argb = region(f, a[0], a[1], w, h);
      pull(G.P.changed());
      const found = G.S.slice();
      if (T.placeLay(G.P.id, a[0], a[1], w, h, argb)) { const r = G.P.changed(); if (r) { pull(r); send(r, [], found); } }
    }
    end();
  }
  // Cancel, or the page left: the page as it was, nothing laid.
  function cancel() {
    if (!placing) return;
    if (placing.area && G.P === placing.page) pull(placing.area);
    end();
  }
  function end() {
    cancelAnimationFrame(placing.frame);
    placing.bar.remove();
    if (placing.pic.close) placing.pic.close();
    placing = null; T.placeCancel();
  }
  // Every pointer on the page is the picture's while it is placed, the pen's and a finger's alike (PicturePlacer.touch).
  function placeDown(e) {
    const p = G.pagePoint(e), P = placing;
    P.pointers.set(e.pointerId, p);
    try { view.setPointerCapture(e.pointerId); } catch (err) { /* synthetic */ }
    G.hideRing();
    if (P.first < 0) { P.first = e.pointerId; T.placeDown(p[0], p[1]); show(); }
    else if (P.second < 0) { const a = P.pointers.get(P.first); if (T.placePinch(a[0], a[1], p[0], p[1])) P.second = e.pointerId; }
  }
  function placeMoved() {
    const P = placing, a = P.pointers.get(P.first), b = T.placePinching() ? P.pointers.get(P.second) : a;
    if (a && b && T.placeMove(a[0], a[1], b[0], b[1])) showSoon();
  }
  function placeUp(e) {
    const P = placing, id = e.pointerId;
    P.pointers.set(id, G.pagePoint(e));
    // One finger of a pinch lifts: it is done, the other waits to lift.
    if (T.placePinching() && (id === P.first || id === P.second)) { placeMoved(); T.placeLifting(); show(); }
    P.pointers.delete(id);
    if (P.pointers.size) return;
    const moving = frame()[12];
    if (moving) placeMoved();
    T.placeUp(); P.first = P.second = -1;
    if (moving) show();
  }

  // ---- The page's pointers, before the column's modes (live.html's view listeners) ----
  function down(e) {
    if (placing) { if (G.P !== placing.page) { cancel(); return false; } placeDown(e); return true; }
    if (cutting >= 0 && !loop && e.button <= 0) { cutDown(e); return true; }
    return false;
  }
  function move(e) {
    if (placing) { if (!placing.pointers.has(e.pointerId)) return false; placing.pointers.set(e.pointerId, G.pagePoint(e)); placeMoved(); return true; }
    if (loop && e.pointerId === loop.id) { cutMove(e); return true; }
    return false;
  }
  function up(e) {
    if (placing) { if (!placing.pointers.has(e.pointerId)) return false; placeUp(e); return true; }
    if (loop && e.pointerId === loop.id) { cutUp(); return true; }
    return false;
  }
  // Another page shown (a notebook's page turned, a host's new page): the picture is cancelled.
  function viewChanged() { if (placing && G.P !== placing.page) { placing.area = null; end(); } }

  // A picture dropped on the page (a desktop's), as Import's; words (a .txt file or plain text) a text box where they land (m4.js).
  const isPicture = f => /^image\//.test(f.type), isText = f => f.type === "text/plain";
  Live.dropFiles(view, () => !G.shown, async (files, text, e) => {
    const pic = files.find(isPicture), words = files.find(isText), [x, y] = G.pagePoint(e);
    if (pic) return read(pic);
    // shortcut: a file's first 64 KB only, enough while a box holds 4000 characters (TextBoxes.MOST_CHARS); raise it with that.
    if (words) text = await words.slice(0, 1 << 16).text();
    if (text && G.M4) G.M4.dropText(text, x, y);
  }, SW.dropHint, i => i.kind === "file" && isPicture(i) || i.type === "text/plain");

  return { stampMenu, cut, labButton, padDown, padMove, padUp, importImage, down, move, up, viewChanged,
    get cutting() { return cutting; }, get placing() { return !!placing; }, get labWaits() { return !!labWaits; } };
}
