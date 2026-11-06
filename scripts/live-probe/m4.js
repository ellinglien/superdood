// The rest of a live page guest's drawing screen (live.html; docs/superpowers/specs/2026-10-31-browser-app-design.md, M4):
// the column's Menu (Finger Action, Left-Handed), the smudge tool's row and its chooser, Clean Brush, the Dropper, the
// Lasso (Cut, Duplicate, Make Stamp, Delete, Paste), Shapes (and Filled), Play (Text with Make Ink, Mirrors and their guides), Zen
// Mode (its button) and Hold to Snap. Every decision is the Manta's Java through
// WebEngine (DrawLayout's rows, Mirrors, TextBoxes, LassoPieces, ShapeStroke, ToolSlots, SwatchPicker, HoldSnap); this
// draws them, and sends what they change on the page as live pixels (live.html's send). Text boxes are the page's own,
// not its pixels, so the host sees a box once it is made ink.
export function install(G) {
  const { T, $, el, font, icon, sized, place, popup, placePopup, closePopup, menuRow, holdable, say, pull, send, view } = G;
  const WD = JSON.parse(T.m4Words()), LW = JSON.parse(T.lassoWords()), DROP = JSON.parse(T.dropper());
  const over = $("over"), right = () => document.body.classList.contains("right");
  // The column's modes: the Lasso, the Dropper or Shapes in use (the slot's side bar gives way, InkWashBar.lassoChanged).
  let mode = null, textOn = false, placing = false, zen = false;
  let gesture = null;          // the pen's on the page while a mode has it
  const B = {};                // the column's buttons by name
  let piece = null, lassoFound = null, shownArea = null, bar = null, field = null;

  // ---- Helpers ----
  const page = () => G.P.id;
  // A page point on screen, CSS px within the view; a length in page px for dp on screen.
  const toView = (x, y) => [(x * G.V.s + G.V.x) / G.dpr, (y * G.V.s + G.V.y) / G.dpr];
  const pagePx = dp => dp * G.dpr / G.V.s;
  const clip = a => { const x0 = Math.max(0, a[0]), y0 = Math.max(0, a[1]), x1 = Math.min(G.W, a[2]) - 1, y1 = Math.min(G.H, a[3]) - 1; return x1 < x0 || y1 < y0 ? null : [x0, y0, x1, y1]; };
  const union = (a, b) => !b ? a : !a ? b : [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])];
  // What the engine changed goes on screen (take), and once done out to the others, against the page as it was
  // ({@code found}): all it changed since, and {@code also} (where a preview showed).
  let pending = null;
  const take = r => { if (r) { pull(r); pending = union(pending, r); } return r; };
  function commit(found, also) {
    const r = union(union(take(G.P.changed()), pending), also || null);
    pending = null;
    if (r) { pull(r); send(r, [], found); }
  }
  const rule = () => { const d = el("div", "rule"); d.style.background = "#bbb"; d.style.margin = "4px 0"; return d; };
  // A menu's rows (DrawLayout.Row) in a popup beside anchor; act(key) on a tap, the popup closed first.
  function rowsMenu(anchor, rows, act, width = 240, caption = null) {
    const box = popup(anchor, width);
    for (const r of rows) {
      if (r.key == null) { box.appendChild(rule()); continue; }
      const row = menuRow(r.words, r.on, () => { closePopup(); act(r.key); });
      row.style.justifyContent = "flex-start"; row.style.gap = "12px";
      if (r.icon) row.prepend(icon(r.icon, WD.iconSide));
      box.appendChild(row);
    }
    if (caption) { const c = el("div", "otitle small", caption); c.style.font = font("CAPTION"); box.appendChild(c); }
    placePopup();
    return box;
  }
  // A column's icon button (SlotTile.Face's icon alone) at x, y, w × h in the column; its side bar at its left or right while on.
  function iconButton(name, words, x, y, w, h, side, act) {
    const b = el("button", "at"); place(b, x, y, w, h); if (name === "menu") b.id = "menu";
    b.setAttribute("aria-label", words); b.dataset.m4 = name;
    const i = icon(name === "zen" ? (zen ? "ic_ph_circle" : "ic_ph_circle_dashed") : B[name + "Icon"], Math.min(name === "menu" || name === "zen" ? WD.zenIcon : WD.iconSide, w, h));
    Object.assign(i.style, { position: "absolute", left: (w - parseFloat(i.style.width)) / 2 + "px", top: (h - parseFloat(i.style.height)) / 2 + "px" });
    const mark = el("i"); Object.assign(mark.style, { position: "absolute", top: G.C.BAR_INSET + "px", bottom: G.C.BAR_INSET + "px", width: G.C.SIDE_BAR + "px", background: "#000", display: "none", [side]: "0" });
    b.append(i, mark); b.onclick = act;
    return b;
  }
  const markButton = (name, on) => { const b = B[name]; if (!b) return; b.lastChild.style.display = on ? "" : "none"; b.setAttribute("aria-pressed", String(!!on)); };
  function marks() {
    markButton("dropper", mode === "dropper"); markButton("lasso", mode === "lasso");
    markButton("play", textOn || mode === "shapes" || placing || JSON.parse(T.guides(page())).acts);
    G.showTools();
  }
  function setMode(m) {
    if (mode === "lasso" && m !== "lasso") putDown();
    mode = m;
    if (m) { closeText(); textOn = false; }
    marks(); drawOver();
  }

  // ---- The column: Menu, the Dropper and the Lasso, Play (Shapes its row); Zen Mode's button in the corner ----
  B.menuIcon = "ic_ph_list"; B.dropperIcon = "ic_ph_eyedropper"; B.lassoIcon = "ic_ph_lasso"; B.playIcon = "ic_ph_confetti";
  let zenButton = null;
  function arranged(top, across) {
    const L = G.L, C = G.C, column = $("column");
    const add = (name, words, x, y, w, h, side, act) => { const b = iconButton(name, words, x, y, w, h, side, act); column.appendChild(b); B[name] = b; return b; };
    add("menu", WD.menu, C.PAD, top("MENU"), L.menuRow, L.menuRow, "left", () => fileMenu(B.menu));
    const block = top("BLOCK"), x0 = L.edge, x1 = L.edge + L.half;
    add("dropper", WD.dropper, x0, block, L.half, L.icon, "left", () => setMode(mode === "dropper" ? null : "dropper"));
    add("lasso", WD.lasso, x1, block, L.half, L.icon, "right", tapLasso);
    add("play", WD.play, x1, block + L.icon + L.wet, L.half, L.wet, "right", () => playMenu(B.play));
    // Zen Mode's button: the foot corner under the column, in both states (DrawLayout.zenCell).
    zenButton?.remove();
    const z = T.zenCell(innerWidth, innerHeight, right());
    zenButton = iconButton("zen", zen ? WD.zenLeave : WD.zen, z[0], z[1], z[2], z[3], "left", () => setZen(!zen));
    zenButton.style.zIndex = 5; document.body.appendChild(zenButton);
    for (const id of ["column", "zoom", "undos", "line"]) $(id).style.display = zen ? "none" : "";
    if (zen) place($("scroll"), 0, 0, innerWidth, innerHeight);
    marks();
  }
  // Zen Mode (DrawLayout.ZEN): the column, the brush line and the page bar gone, the page the whole screen.
  function setZen(on) { zen = on; closePopup(); G.arrange(); }

  // ---- Menu: Finger Action and the drawing hand (DrawLayout.fileRows) ----
  // A live page's guest: Notebook Explorer, the Manta's shelf, and Export Page while a Manta page is shown (books.js menuRows),
  // then Import (stamp.js).
  // Export Page only where it has a page (not on a cover), as the Manta's.
  function fileMenu(anchor) {
    const nb = G.books ? G.books.menuRows() : [], acts = { settings: nb[0] && nb[0][1], open: nb[1] && nb[1][1], exportPage: nb[2] && nb[2][1] };
    const rows = JSON.parse(T.fileMenu(G.sel, !!nb.length)).filter(r => !(r.key in acts) || acts[r.key]);
    // The browser's own: Full Screen hides its bars (web only; an iPhone's Safari can't, so no row there).
    const fs = document.documentElement, full = () => document.fullscreenElement || document.webkitFullscreenElement;
    if (fs.requestFullscreen || fs.webkitRequestFullscreen) rows.push({ key: "fullScreen", words: "Full Screen", on: !!full() });
    rowsMenu(anchor, rows, key => {
      if (key === "fullScreen") {
        if (full()) return (document.exitFullscreen || document.webkitExitFullscreen).call(document);
        return Promise.resolve((fs.requestFullscreen || fs.webkitRequestFullscreen).call(fs)).catch(() => {});
      }
      if (acts[key]) return acts[key]();
      if (key === "finger") return fingerMenu(anchor);
      if (key === "import") return G.ST.importImage();
      T.fileAct(key); G.saveStore(); G.arrange();
    });
  }
  function fingerMenu(anchor) {
    const rows = JSON.parse(T.fingerMenu(G.sel)).map(c => ({ key: c.key, words: c.words, on: c.on, icon: c.tool === G.C.ERASER ? "tool_eraser" : c.tool === G.C.SMUDGE ? "tool_smudge" : c.tool < 0 ? "ic_finger" : null }));
    rowsMenu(anchor, rows, key => { T.setFinger(key); G.saveStore(); }, 280);
  }

  // ---- The smudge tool's chooser (InkWashBar.tapSmudge) and Clean Brush (InkWashBar.cleanMenu) ----
  function held(slot, row) {
    if (slot === G.C.SMUDGE) { if (G.sel !== slot) G.choose(slot); smudgeMenu(row); return true; }
    if (!T.held(slot)) return false;
    const box = popup(row);
    const [clean, tools] = JSON.parse(T.cleanMenu());
    box.append(menuRow(clean, false, () => { closePopup(); if (T.clean(slot)) G.showTools(); }), menuRow(tools, false, () => { closePopup(); G.picker(slot, row); }));
    placePopup();
    const shown = G.popped; setTimeout(() => { if (G.popped === shown) closePopup(); }, WD.offerMs);
    return true;
  }
  function smudgeMenu(row) {
    const m = JSON.parse(T.smudgeMenu(innerWidth, innerHeight)), box = popup(row, m.columns * (m.width + 1) + 3), grid = el("div", "cells");
    box.dataset.smudge = "1";
    grid.style.gridTemplateColumns = `repeat(${m.columns}, ${m.width}px)`; grid.style.gap = "1px";
    m.cards.forEach((c, i) => {
      const card = el("button", "cell" + (c.on ? " marked" : "")), art = el("canvas"), name = el("div", "name", c.name), line = el("div", "name small", c.line);
      card.style.height = m.card + "px"; card.style.padding = "4px";
      card.setAttribute("aria-label", c.said); card.setAttribute("aria-pressed", String(c.on));
      name.style.font = font("CAPTION", c.on ? 700 : 400); line.style.font = font("CAPTION");
      card.append(art, name, line);
      const aw = m.width - 8, ah = Math.max(10, m.card - 8 - 2 * G.HOLD.type.CAPTION[0] * 1.4), ctx = sized(art, aw, ah);
      ctx.drawImage(G.picture("smudge " + c.name, Math.round(aw * G.dpr), Math.round(ah * G.dpr), (w, h) => T.smudgeLoop(i, w, h)), 0, 0, aw, ah);
      card.onclick = () => { closePopup(); const n = T.smudgePick(i); if (n) { G.tools.smudge = JSON.parse(T.toolsJson()).smudge; G.saveStore(); G.showTools(); say(n); } };
      grid.appendChild(card);
    });
    box.appendChild(grid);
    if (m.clean) box.appendChild(menuRow(m.cleanWords, false, () => { closePopup(); if (T.clean(G.C.SMUDGE)) G.showTools(); }));
    placePopup();
  }

  // ---- Shapes (DrawLayout.shapeRows): the slot in use draws the shape picked, its grey and its size the outline's ----
  function shapesMenu(anchor) {
    rowsMenu(anchor, JSON.parse(T.shapeMenu(mode === "shapes")), key => {
      if (G.sel >= G.C.COUNT) G.choose(0);
      say(T.shapePick(key)); setMode("shapes");
    }, 200);
  }

  // ---- Play (DrawLayout.playRows): Text, Shapes, Mirrors ----
  function playMenu(anchor) {
    rowsMenu(anchor, JSON.parse(T.playMenu(page(), textOn, mode === "shapes", placing)), key => {
      if (key === "shapes") shapesMenu(anchor);
      else if (key === "text") { const on = !textOn; setMode(null); textOn = on; if (!on) closeText(); marks(); drawOver(); }
      else if (key === "mirrors") mirrorsMenu(anchor);
    });
  }
  function mirrorsMenu(anchor) {
    const m = JSON.parse(T.mirrorMenu(page(), placing));
    rowsMenu(anchor, m.rows, key => {
      if (key === "PLACE") placing = true;
      else {
        const found = G.S.slice();
        if (T.mirrorAct(page(), key)) placing = false;
        commit(found);
      }
      marks(); drawOver();
    }, 280, m.caption);
  }

  // ---- The Lasso (DrawLayout.lassoTap, LassoPieces): a loop lifts a piece; drag it, turn it by its knob, scale it by a corner ----
  function tapLasso() {
    const t = T.lassoTap(mode === "lasso");
    if (t === 0) return setMode("lasso");
    if (t === 1) rowsMenu(B.lasso, [{ key: "paste", words: LW.paste, icon: "ic_ph_clipboard", on: false }], () => lassoAct(4), 160);
  }
  // The piece as Java has it: its corners, knob and area, the screen's up and what of the page shows.
  function pieceNow() {
    const vw = view.offsetWidth * G.dpr, vh = view.offsetHeight * G.dpr, V = G.V;
    return JSON.parse(T.lasso(0, -1, pagePx(40), -V.x / V.s, -V.y / V.s, (vw - V.x) / V.s, (vh - V.y) / V.s)).piece;
  }
  // The page shown again where the piece was, then the piece where it lies (LassoPlanes.shown), its frame over it.
  function showPiece() {
    if (shownArea) pull(shownArea);
    piece = pieceNow(); shownArea = null;
    if (piece) {
      const r = clip(piece.area);
      if (r) {
        const w = r[2] - r[0] + 1, h = r[3] - r[1] + 1, t = new Int32Array(T.lassoShown(page(), r[0], r[1], w, h)), img = G.pageCtx.getImageData(r[0], r[1], w, h);
        for (let i = 0; i < t.length; i++) if (t[i] >= 0) { const g = G.screenGrey(t[i]); img.data[4 * i] = img.data[4 * i + 1] = img.data[4 * i + 2] = g; }
        G.pageCtx.putImageData(img, r[0], r[1]);
        shownArea = r;
      }
    }
    drawOver(); showBar();
  }
  // The Lasso's bar over the floating piece (InkWashBar.lassoBar): Cut, Duplicate, Make Stamp, Delete; gone while it moves.
  function showBar() {
    bar?.remove(); bar = null;
    if (!piece || piece.dragging) return;
    bar = el("div", "pop across"); bar.style.zIndex = 15; bar.dataset.lassoBar = "1";
    LW.bar.forEach((w, i) => bar.appendChild(menuRow(w, false, () => lassoAct(i), 44)));
    document.body.appendChild(bar);
    const vr = view.getBoundingClientRect(), c = piece.corners, k = piece.knob;
    let top = Infinity, bottom = -Infinity, left = Infinity, rgt = -Infinity;
    for (let i = 0; i < c.length; i += 2) { const [x, y] = toView(c[i], c[i + 1]); top = Math.min(top, y); bottom = Math.max(bottom, y); left = Math.min(left, x); rgt = Math.max(rgt, x); }
    const [kx, ky] = toView(k[0], k[1]); top = Math.min(top, ky); bottom = Math.max(bottom, ky);
    const bw = bar.offsetWidth, bh = bar.offsetHeight, over8 = top - 8 - bh;
    bar.style.left = Math.max(vr.left, Math.min(vr.right - bw, vr.left + (left + rgt) / 2 - bw / 2)) + "px";
    bar.style.top = Math.max(vr.top, Math.min(vr.bottom - bh, vr.top + (over8 >= 0 ? over8 : bottom + 8))) + "px";
  }
  // Put down, Cut, Duplicate, Make Stamp (2: its Stamp's slot then in use, the Lasso put away), Delete or Paste
  // (LassoPieces, ToolSlots.makeStamp), what it changed sent; the piece shown again.
  function lassoAct(act) {
    const found = piece && lassoFound || G.S.slice(), area = shownArea;
    const stamp = act === 2 ? T.lassoStamp(page(), G.sel, G.L.trays) : (T.lassoAct(page(), act), null);
    if (stamp === -1) return say(LW.stampNothing);
    if (act === 4 && !pieceNow()) say(LW.otherSize);
    commit(found, area);
    shownArea = null;
    showPiece();
    lassoFound = piece ? G.S.slice() : null;
    if (stamp !== null) { setMode(null); G.ownTools(stamp); say(LW.stampDone); }
  }
  function putDown() { if (piece) lassoAct(-1); }

  // ---- Text (TextBoxes): a tap places a box or opens one, a drag moves it or sets its width, a long press its menu ----
  function closeText() { if (field) field.blur(); }
  function edit(i, x, y) {
    if (field) return;
    const b = JSON.parse(T.textBox(page(), i, x, y)), [vx, vy] = toView(b.rect[0], b.rect[1]), s = G.V.s / G.dpr, vr = view.getBoundingClientRect();
    field = el("textarea"); field.value = b.text; field.dataset.text = i;
    Object.assign(field.style, { position: "fixed", left: vr.left + vx + "px", top: vr.top + vy + "px", width: (b.rect[2] - b.rect[0]) * s + "px", minHeight: b.line * s + "px",
      font: `${b.bold ? 700 : 400} ${b.em * s}px Duo, monospace`, lineHeight: b.line * s + "px", textAlign: ["left", "center", "right"][b.align],
      border: "1px dashed #000", background: "rgba(255,255,255,.6)", padding: 0, margin: 0, resize: "none", outline: "none", zIndex: 16, overflow: "hidden", touchAction: "manipulation", userSelect: "text", webkitUserSelect: "text" });
    const done = () => {
      if (!field) return;
      const f = field; field = null; f.remove();
      T.textSet(page(), i, f.value, x, y); drawOver();
    };
    field.addEventListener("blur", done);
    field.addEventListener("keydown", e => { e.stopPropagation(); if (e.key === "Escape") field.blur(); });
    field.addEventListener("input", () => { field.style.height = "auto"; field.style.height = field.scrollHeight + "px"; });
    document.body.appendChild(field); drawOver();
    setTimeout(() => field && field.focus(), 0);
  }
  // Words dropped on the page at x, y (a desktop's: a .txt file or plain text, stamp.js): a new box there, as a tap with Text places one.
  function dropText(text, x, y) { closeText(); T.textSet(page(), -1, text, x, y); drawOver(); }
  function textMenu(i) {
    const b = JSON.parse(T.texts(page()))[i], [x0, y0] = toView(b.rect[0], b.rect[1]), [x1, y1] = toView(b.rect[2], b.rect[3]), vr = view.getBoundingClientRect();
    const anchor = { getBoundingClientRect: () => ({ left: vr.left + x0, top: vr.top + y0, right: vr.left + x1, bottom: vr.top + y1 }) };
    rowsMenu(anchor, JSON.parse(T.textMenu(page(), i)), key => {
      if (key === "ink") return makeInk(i);
      T.textAct(page(), i, key); drawOver();
    }, 200);
  }
  // Make Ink (DrawingPad.makeInk): the box's letters drawn here in its face (TextBoxes.Type), laid by the slot in use.
  function makeInk(i) {
    const b = JSON.parse(T.texts(page()))[i], r = b.bounds, w = r[2] - r[0], h = r[3] - r[1];
    const c = new OffscreenCanvas(w, h), ctx = c.getContext("2d");
    ctx.font = `${b.bold ? 700 : 400} ${b.em}px Duo, monospace`; ctx.fillStyle = "#000";
    for (const [text, x, base] of b.lines) ctx.fillText(text, x - r[0], base - r[1]);
    const d = ctx.getImageData(0, 0, w, h).data, cov = new Int8Array(w * h);
    for (let k = 0; k < cov.length; k++) cov[k] = d[4 * k + 3];
    const tool = G.sel < G.C.COUNT ? G.sel : 0, [px, dk] = G.look(tool), found = G.S.slice();
    if (T.textInk(page(), i, tool, px, dk, cov)) commit(found);
    drawOver();
  }

  // ---- What lies over the page on screen (the view's own canvas): the guides, a placing stroke, the Lasso's loop and
  // frame, the text boxes' letters, the Dropper's ring ----
  function drawOver() {
    if (!G.P) return;
    const ctx = over.getContext("2d"), V = G.V, dpr = G.dpr;
    ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, over.width, over.height);
    const g = JSON.parse(T.guides(page()));
    // The guides (MirrorShow): each mirror dashed across the page in its grey.
    if (g.shown) {
      ctx.save(); ctx.setTransform(V.s, 0, 0, V.s, V.x, V.y);
      const tone = g.tone; ctx.strokeStyle = `rgb(${tone},${tone},${tone})`; ctx.lineWidth = Math.max(2 * g.half, dpr / V.s); ctx.setLineDash([g.dash, g.dash]);
      const far = G.W + G.H;
      for (const [x, y, dx, dy] of g.lines) { ctx.beginPath(); ctx.moveTo(x - dx * far, y - dy * far); ctx.lineTo(x + dx * far, y + dy * far); ctx.stroke(); }
      ctx.restore();
    }
    // The text boxes' letters, crisp black over the drawing (TextBoxes.lines), but the one being typed.
    const boxes = JSON.parse(T.texts(page()));
    ctx.save(); ctx.setTransform(V.s, 0, 0, V.s, V.x, V.y); ctx.fillStyle = "#000"; ctx.textBaseline = "alphabetic";
    boxes.forEach((b, i) => {
      if (field && +field.dataset.text === i) return;
      ctx.font = `${b.bold ? 700 : 400} ${b.em}px Duo, monospace`;
      for (const [text, x, base] of b.lines) ctx.fillText(text, x, base);
      if (textOn) { ctx.save(); ctx.strokeStyle = "#888"; ctx.lineWidth = dpr / V.s; ctx.setLineDash([4 * dpr / V.s, 4 * dpr / V.s]); ctx.strokeRect(b.rect[0], b.rect[1], b.rect[2] - b.rect[0], b.rect[3] - b.rect[1]); ctx.restore(); }
    });
    ctx.restore();
    ctx.save(); ctx.scale(dpr, dpr);
    const path = (pts, close) => { ctx.beginPath(); for (let i = 0; i < pts.length; i += 2) { const [x, y] = toView(pts[i], pts[i + 1]); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); } if (close) ctx.closePath(); };
    if (gesture && (gesture.kind === "place" || gesture.kind === "loop") && gesture.pts.length > 2) {
      path(gesture.pts, false);
      if (gesture.kind === "place") { const t = g.placing; ctx.strokeStyle = `rgb(${t},${t},${t})`; ctx.lineWidth = 2; ctx.setLineDash([]); }
      else { ctx.strokeStyle = "#000"; ctx.lineWidth = 1.5; ctx.setLineDash([4, 4]); }
      ctx.stroke();
    }
    if (gesture && gesture.kind === "text" && gesture.rect) {
      const [x0, y0] = toView(gesture.rect[0], gesture.rect[1]), [x1, y1] = toView(gesture.rect[2], gesture.rect[3]);
      ctx.setLineDash([]); ctx.strokeStyle = "#000"; ctx.lineWidth = 3; ctx.strokeRect(x0 - 2, y0 - 2, x1 - x0 + 4, y1 - y0 + 4);
      ctx.strokeStyle = "#fff"; ctx.lineWidth = 1; ctx.strokeRect(x0 - .5, y0 - .5, x1 - x0 + 1, y1 - y0 + 1);
    }
    // The Lasso's frame: the piece's outline, a dot on each corner to scale it, a knob on a stem to turn it.
    if (piece) {
      ctx.setLineDash([4, 4]); ctx.strokeStyle = "#000"; ctx.lineWidth = 1.5;
      path(piece.corners, true); ctx.stroke(); ctx.setLineDash([]);
      for (let i = 0; i < 8; i += 2) { const [x, y] = toView(piece.corners[i], piece.corners[i + 1]); ctx.beginPath(); ctx.arc(x, y, 5, 0, 2 * Math.PI); ctx.fillStyle = "#000"; ctx.fill(); }
      const [kx, ky] = toView(piece.knob[0], piece.knob[1]), [ex, ey] = toView(piece.knob[2], piece.knob[3]);
      ctx.beginPath(); ctx.moveTo(ex, ey); ctx.lineTo(kx, ky); ctx.stroke(); ctx.beginPath(); ctx.arc(kx, ky, 8, 0, 2 * Math.PI); ctx.fill();
    }
    // The Dropper's ring of the grey under the pen, a hole in its middle (DrawLayout.DROPPER_RING).
    if (gesture && gesture.kind === "drop" && gesture.tone >= 0) {
      const [x, y] = gesture.at, t = G.screenGrey(gesture.tone);
      ctx.beginPath(); ctx.arc(x, y, DROP.ring, 0, 2 * Math.PI); ctx.arc(x, y, DROP.hole, 0, 2 * Math.PI, true);
      ctx.fillStyle = `rgb(${t},${t},${t})`; ctx.fill("evenodd"); ctx.strokeStyle = "#000"; ctx.lineWidth = 1; ctx.stroke();
    }
    ctx.restore();
  }
  function viewChanged() { drawOver(); if (piece) showBar(); }

  // ---- The pen on the page while a mode has it ----
  function down(e) {
    if (e.button > 0) return false;
    const touch = e.pointerType === "touch";
    if (touch && !(mode || textOn || placing || piece)) return false;
    const [x, y] = G.pagePoint(e), id = e.pointerId;
    const start = kind => { gesture = Object.assign({ kind, id, x, y, cx: e.clientX, cy: e.clientY, pts: [x, y] }, kind === "drop" ? { tone: -1 } : {}); try { view.setPointerCapture(id); } catch (err) { /* synthetic */ } G.hideRing(); return true; };
    if (piece) {
      const vw = view.offsetWidth * G.dpr, vh = view.offsetHeight * G.dpr, V = G.V;
      if (T.lassoGrab(x, y, pagePx(24), 0, -1, pagePx(40), -V.x / V.s, -V.y / V.s, (vw - V.x) / V.s, (vh - V.y) / V.s)) { start("drag"); showPiece(); return true; }
      putDown();
      if (mode !== "lasso") return false;
    }
    if (placing) return start("place");
    if (textOn) {
      if (field) { field.blur(); return true; }
      start("text");
      gesture.box = T.textHit(page(), x, y);
      gesture.widen = gesture.box >= 0 && T.textEdge(page(), gesture.box, x, y);
      if (gesture.box >= 0) gesture.timer = setTimeout(() => { if (gesture && gesture.kind === "text" && !gesture.rect) { gesture.menu = true; textMenu(gesture.box); } }, G.HOLD.ms);
      return true;
    }
    if (mode === "dropper") { start("drop"); dropAt(e); return true; }
    if (mode === "shapes" && G.sel < G.C.COUNT) {
      start("shape"); gesture.found = G.S.slice(); pending = null;
      const [px, dk] = G.look(G.sel);
      T.shapeDown(page(), G.sel, px, dk, x, y); take(G.P.changed());
      return true;
    }
    if (mode === "lasso") return start("loop");
    return false;
  }
  function dropAt(e) {
    const [x, y] = G.pagePoint(e), r = view.getBoundingClientRect();
    gesture.tone = T.toneAt(page(), x, y); gesture.at = [e.clientX - r.left, e.clientY - r.top];
    drawOver();
  }
  function move(e) {
    if (!gesture || e.pointerId !== gesture.id) return false;
    const [x, y] = G.pagePoint(e), g = gesture;
    if (g.kind === "drag") { T.lassoDrag(x, y); showPiece(); }
    else if (g.kind === "loop" || g.kind === "place") {
      const n = g.pts.length;
      if (Math.hypot(x - g.pts[n - 2], y - g.pts[n - 1]) >= pagePx(3)) { g.pts.push(x, y); drawOver(); }
    } else if (g.kind === "drop") dropAt(e);
    else if (g.kind === "shape") { T.shapeMove(page(), x, y); take(G.P.changed()); }
    else if (g.kind === "text" && !g.menu) {
      if (!g.rect && Math.hypot(e.clientX - g.cx, e.clientY - g.cy) < G.HOLD.slop) return true;
      clearTimeout(g.timer);
      if (g.box < 0) { g.dead = true; return true; }
      g.rect = T.textDrag(page(), g.box, g.widen, g.x, g.y, x, y, false); drawOver();
    }
    return true;
  }
  function up(e, k) {
    if (!gesture || e.pointerId !== gesture.id) return false;
    const g = gesture; gesture = null;
    const [x, y] = G.pagePoint(e), cancelled = k === "pointercancel";
    if (g.kind === "drag") { T.lassoRelease(); showPiece(); }
    else if (g.kind === "loop") {
      if (!cancelled && g.pts.length >= 6) {
        lassoFound = G.S.slice();
        if (!T.lassoLift(page(), new Float32Array(g.pts))) { say(LW.nothing); lassoFound = null; }
      }
      showPiece();
    } else if (g.kind === "place") {
      if (!cancelled) { const said = T.mirrorPlace(page(), new Float32Array(g.pts.filter((v, i) => i % 2 === 0)), new Float32Array(g.pts.filter((v, i) => i % 2 === 1))); if (said) say(said); }
      placing = false; commit(G.S.slice()); marks(); drawOver();
    } else if (g.kind === "drop") {
      if (!cancelled && g.tone >= 0) {
        const tool = G.sel, d = T.drop(tool, g.tone);
        if (tool !== G.C.ERASER) G.dark[tool] = d;
        G.saveStore(); say(T.dropWords(tool, d, g.tone));
      }
      setMode(null); G.showFaces();
    } else if (g.kind === "shape") {
      T.shapeMove(page(), x, y); T.shapeEnd(page()); commit(g.found);
    } else if (g.kind === "text") {
      clearTimeout(g.timer);
      if (g.menu || cancelled || g.dead) { /* its menu, or nothing */ }
      else if (g.rect) { T.textDrag(page(), g.box, g.widen, g.x, g.y, x, y, true); }
      else edit(g.box, g.x, g.y);
      drawOver();
    }
    return true;
  }

  // ---- Hold to Snap (HoldSnap): the pen held still at a stroke's end over a clean shape, the stroke drawn again as it ----
  let snapTimer = 0, snaps = 0;
  function began(live) {
    clearInterval(snapTimer);
    if (live.finger || live.tool >= G.C.COUNT && live.tool !== G.C.FILL || !T.snapStart(page(), G.V.s)) return;
    snapTimer = setInterval(() => {
      const L = G.live;
      if (!L || L !== live) return clearInterval(snapTimer);
      // On the samples' own clock (a pointer's timeStamp), as long after the last one as has passed.
      const clean = T.snapDue(lastT + performance.now() - lastWall);
      if (!clean.length) return;
      clearInterval(snapTimer);
      // The rough stroke finished and taken back (as a finger's, live.html's cancel), the clean one drawn with the same tool.
      pull(L.st.end()); T.snapBack(page()); pull(G.P.changed());
      const [size, dk] = G.look(L.tool);
      L.st = G.P.pen(L.tool, size, dk, 0, false);
      for (let k = 0; k < clean.length; k += 6) T.sampleAt(page(), clean[k], clean[k + 1], clean[k + 2], clean[k + 5], clean[k + 3], clean[k + 4]);
      T.present(page());
      const r = pull(G.P.changed());
      L.box = union(L.box, r); L.snapped = true; snaps++;
    }, WD.tickMs);
  }
  let lastT = 0, lastWall = 0;
  function sampled(samples) { for (const q of samples) { T.snapSample(q[0], q[1], q[2], q[4], q[5], q[3]); lastT = q[3]; lastWall = performance.now(); } }
  function lifted() { clearInterval(snapTimer); T.snapEnd(); }

  // A tool chosen: the column's mode and Text put down (InkWashBar.selectTool).
  function chose() { if (mode) setMode(null); if (textOn) { closeText(); textOn = false; marks(); drawOver(); } }
  return { arranged, viewChanged, down, move, up, chose, held, began, sampled, lifted, dropText, covers: () => mode !== null,
    get mode() { return mode; }, get textOn() { return textOn; }, get placing() { return placing; }, get zen() { return zen; }, get piece() { return piece; }, get snaps() { return snaps; }, drawOver };
}
