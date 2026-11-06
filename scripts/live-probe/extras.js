// Brush Lab's Open (its sheet: a pasted code, this brush's, the library), its picker box lying across with the readout,
// Grey Shift's set and the scratch pad's wet indicator; the drawing screen's Show Kinds, sand timer (its countdown, its
// menu, Time's up) and wet indicator (live.html; docs/superpowers/specs/2026-10-31-browser-app-design.md, M2 and M4).
// Every decision is the Manta's Java through WebEngine (BrushLab, LabLayout, DrawLayout, EraseKind, SandTimer, WetLook);
// this draws them. Brush Lab has no long presses, as on the Manta.
export function install(G) {
  const { T, el, font, icon, vector, drawVector, sized, place, popup, placePopup, closePopup, menuRow, say, lb, iconButton, tall } = G;
  const CW = JSON.parse(T.columnWords()), WW = JSON.parse(T.wetWords()), LAB = -1;  // WebEngine.LAB: Brush Lab's brush as a tool
  const u8 = a => new Uint8Array(a.buffer, a.byteOffset, a.length);
  const style = document.createElement("style");
  style.textContent = `
  .xsheet { position: absolute; left: 0; right: 0; background: #fff; border: 1px solid #000; box-sizing: border-box; z-index: 6; display: flex; flex-direction: column; }
  .xsheet .xrow { display: flex; align-items: center; } .xsheet input { flex: 1; min-width: 0; border: 1px solid #000; outline: none; box-sizing: border-box; padding: 0 8px; }
  .xsheet .code { flex: 1; min-width: 0; border: 1px solid #000; box-sizing: border-box; padding: 0 8px; display: flex; align-items: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; user-select: text; }
  .xsheet .shelf { display: flex; flex-direction: column; } .xsheet .tiles { flex: 1; display: grid; gap: 1px; background: #000; border: 1px solid #000; min-height: 0; }
  .xsheet .tile .cap { border-top: 1px solid #000; }
  .xbox { position: relative; flex: none; } .xbox canvas { position: absolute; transform: rotate(90deg); touch-action: none; }
  .xgreys { display: flex; align-items: center; } .xgreys button { height: 100%; flex: none; }
  .xkinds { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; }
  .xkinds > div { display: flex; flex-direction: column; align-items: stretch; } .xkinds .mrow { white-space: nowrap; }
  .xkinds .eye { display: flex; align-items: center; justify-content: center; }
  .xcount { display: flex; align-items: center; justify-content: center; text-align: center; white-space: pre-line; }`;
  document.head.appendChild(style);

  // ---- Brush Lab's Open: its button in About This Brush's header, its sheet under it (BrushLabActivity.sheet) ----
  let sheetOpen = false, shelfPage = 0, typed = "", said = "";
  function labOpened() { sheetOpen = false; shelfPage = 0; typed = ""; said = ""; }
  function openButton() {
    const w = JSON.parse(T.labSheet(shelfPage)).w, b = lb(w.open, () => { sheetOpen = !sheetOpen; said = ""; G.lab_(T.lab()); }, sheetOpen ? "ink" : "");
    b.setAttribute("aria-label", sheetOpen ? w.close : w.openSaid); b.setAttribute("aria-pressed", String(sheetOpen));
    return b;
  }
  function labShown(root) {
    root.style.position = "relative";
    if (!sheetOpen) return;
    const s = JSON.parse(T.labSheet(shelfPage)), w = s.w, Ll = G.lp.L, sheet = el("div", "xsheet");
    shelfPage = s.page;
    sheet.style.top = Ll.BAR + s.sheetTop + "px"; sheet.style.padding = `${s.top}px ${s.side}px`;
    const label = text => { const t = el("div", "", text); t.style.font = font("LABEL"); t.style.width = s.label + "px"; t.style.flex = "none"; return t; };
    const gap = e => { e.style.marginLeft = s.buttonGap + "px"; return e; };
    // Paste code: the field, Open (the code's brush in the slot, the sheet closed) and Cancel (closed, nothing changed).
    const load = el("div", "xrow"), field = el("input", "search");
    field.placeholder = w.hint; field.value = typed; field.style.height = s.row + "px"; field.style.font = font("BODY");
    field.setAttribute("autocomplete", "off"); field.setAttribute("autocapitalize", "off"); field.spellcheck = false;
    field.oninput = () => { typed = field.value; };
    const openCode = () => {
      if (T.labLoad(field.value)) { typed = ""; sheetOpen = false; said = ""; }
      else said = T.labStatus();
      G.lab_(T.lab());
    };
    field.onkeydown = e => { if (e.key === "Enter") openCode(); };
    const open = gap(tall(lb(w.open, openCode), s.row)), cancel = gap(tall(lb(w.cancel, () => { sheetOpen = false; G.lab_(T.lab()); }), s.row));
    open.setAttribute("aria-label", w.openCode);
    load.append(label(w.paste), field, open, cancel);
    // This brush: its code, to select, and Copy.
    const copyRow = el("div", "xrow"), code = el("div", "code", s.code);
    copyRow.style.marginTop = s.gap + "px"; code.style.height = s.row + "px"; code.style.font = font("CAPTION", 400);
    const copy = gap(tall(iconButton("ic_ph_copy", w.copy, async () => {
      try { await navigator.clipboard.writeText(s.code); said = w.copied; } catch (err) { said = w.noCopy; }
      G.lab_(T.lab());
    }), s.row));
    copyRow.append(label(w.thisBrush), code, copy);
    // The library, a page at a time, newest first: each its tray stroke and name; the one Save writes over with its tab.
    const shelf = el("div", "shelf");
    shelf.style.marginTop = s.gap + "px"; shelf.style.height = s.bar + s.rows * s.card + "px";
    const head = tall(el("div", "bar"), s.bar), t = el("div", "", w.library); t.style.font = font("LABEL");
    head.append(t, el("div", "spring"));
    if (s.pages > 1) {
      const turn = (icon_, words, by, on) => { const b = G.square(icon_, words, () => { shelfPage += by; G.lab_(T.lab()); }, on); b.style.width = s.pageButton + "px"; return b; };
      const n = el("div", "", (s.page + 1) + "/" + s.pages); n.style.font = font("LABEL"); n.style.padding = "0 4px";
      head.append(turn("ic_ph_caret_left", "Previous page", -1, s.page > 0), n, turn("ic_ph_caret_right", "Next page", 1, s.page < s.pages - 1));
    }
    const grid = el("div", "tiles");
    grid.style.gridTemplateColumns = `repeat(${s.columns}, 1fr)`; grid.style.gridTemplateRows = `repeat(${s.rows}, 1fr)`;
    for (const c of s.cards) {
      const tile = el("button", "tile" + (c.now ? " picked" : "")), art = el("canvas"), cap = el("div", "cap"), name = el("div", "", c.name);
      name.style.font = font("CAPTION", 700); cap.append(name); tile.append(art, cap);
      tile.setAttribute("aria-label", w.open + " " + c.name);
      tile.onclick = () => { sheetOpen = false; G.lab_(T.labOpenCard(c.card)); };
      grid.append(tile);
      requestAnimationFrame(() => { const r = art.getBoundingClientRect(); if (r.height > 2) G.toolArt(art, r.width, r.height, null, "shelf " + c.key, (tw, th) => T.labCardTray(c.card, tw, th), false, false); });
    }
    for (let k = s.cards.length; k < s.columns * s.rows; k++) grid.append(el("div", "tile"));
    shelf.append(head, grid);
    // A line under them: a code that didn't read, or Copied.
    const line = el("div", "", said); line.style.font = font("CAPTION"); line.style.marginTop = s.gap + "px"; line.hidden = !said;
    sheet.append(load, copyRow, shelf, line);
    root.append(sheet);
    if (typed) field.focus();
  }

  // ---- Brush Lab's picker box, the drawing screen's turned a quarter (BrushLabActivity.picker), the readout beside it ----
  let labCanvas = null;
  const LAB_BOX = {
    tool: LAB, get w() { return G.lp.L.pickerThick; }, get tall() { return G.lp.L.picker; }, get side() { return G.lp.L.boxSide; },
    look: () => [G.lp.px, G.lp.dark], canvas: () => labCanvas, refused: () => false, hold: null,
    // Turned a quarter clockwise: the box's top at the right, its left at the top.
    at(box, e) { const r = box.getBoundingClientRect(); return [e.clientY - r.top, r.width - (e.clientX - r.left)]; },
    // Set on the lift, stored at once, as the drawing screen's; a Wet ink brush is dipped in a grey picked.
    set(d) {
      const lp = G.lp;
      if (d.shade) { T.look(lp.slot, lp.px, d.value); if (G.pad) T.dip(G.pad.P.id); }
      else T.look(lp.slot, T.sizeAt(lp.slot, d.value), lp.dark);
      G.lab_(T.lab());
    },
    show: () => G.drawBox(LAB_BOX)
  };
  function labBox(Ll, note) {
    const swatches = el("div"), across = el("div", "xrow"), box = el("div", "xbox"), c = el("canvas"), readout = el("canvas");
    swatches.style.width = Ll.picker + Ll.gap + Ll.readout + "px"; swatches.style.marginLeft = Ll.gap + "px"; swatches.style.flex = "none";
    across.style.display = "flex"; across.style.height = Ll.pickerThick + "px";
    box.style.width = Ll.picker + "px"; box.style.height = Ll.pickerThick + "px";
    Object.assign(c.style, { left: (Ll.picker - Ll.pickerThick) / 2 + "px", top: (Ll.pickerThick - Ll.picker) / 2 + "px" });
    c.setAttribute("role", "slider"); box.append(c);
    // The readout at the picker's left, the picker against the edge (the owner, 2026-11-03).
    box.style.marginLeft = Ll.gap + "px"; box.style.flex = readout.style.flex = "none";
    across.append(readout, box);
    note.style.textAlign = "right"; note.style.marginLeft = "0";
    swatches.append(across, note);
    labCanvas = c;
    G.boxInput(c, LAB_BOX);
    G.readoutDot(readout, Ll.readout, Ll.readout, LAB, G.lp.px, G.lp.dark);
    G.drawBox(LAB_BOX);
    return swatches;
  }

  // ---- Grey Shift's set (GreySet): the brush's greys in a row, each in the set or left out; a tap takes it in or out ----
  function greySet(d, height, Ll) {
    const row = tall(el("div", "drow"), height), set = el("div", "xgreys grow"), few = d.greys.length <= Ll.GREYS_FEW;
    set.style.height = "100%";
    d.greys.forEach((g, k) => {
      const b = el("button"), c = el("canvas");
      b.style.width = few ? Ll.GREY_WIDE + "px" : "0"; if (!few) b.style.flex = "1";
      if (k) b.style.marginLeft = Ll.GREY_GAP + "px";
      b.disabled = !d.enabled; b.setAttribute("aria-label", g.said); b.append(c);
      b.onclick = () => G.lab_(T.labGrey(k));
      set.append(b);
      requestAnimationFrame(() => {
        const r = b.getBoundingClientRect(), w = r.width, h = height - Ll.ROW_RULE;
        if (w < 1) return;
        const x = sized(c, w, h), line = 2, side = Math.min(w, h - 2 * Ll.GREY_PAD), left = (w - side) / 2, top = (h - side) / 2, inset = g.in ? line : side * Ll.GREY_OUT, v = G.screenGrey(g.tone);
        x.fillStyle = `rgb(${v},${v},${v})`; x.fillRect(left + inset, top + inset, side - 2 * inset, side - 2 * inset);
        x.strokeStyle = g.in ? "#000" : "#888"; x.lineWidth = line; x.strokeRect(left + line / 2, top + line / 2, side - line, side - line);
      });
    });
    row.append(set, Object.assign(el("div"), { style: `width:${8 + 2 * Ll.LOCK}px;flex:none` }));
    return row;
  }

  // ---- The wet indicator (WetIndicator, its look WetLook's): the drawing screen's (the scratch pad's went 2026-11-03) ----
  // The drop dashed while dry, half full while paint moves, full while wet at rest; a tap dries it as it would dry, only
  // sooner, MonoPaint's blow dryer blowing meanwhile.
  const wets = [{ canvas: null, shown: 0, blowing: -1, hurrying: false, timer: 0 }];
  const wetPage = () => G.P ? G.P.id : -1;
  const wetStroking = () => !!G.live;
  async function wetDraw(w) {
    const c = w.canvas; if (!c || !c.isConnected) return;
    const r = c.getBoundingClientRect(), cw = r.width, ch = r.height, x = sized(c, cw, ch);
    x.fillStyle = "#fff"; x.fillRect(0, 0, cw, ch);
    c.setAttribute("aria-label", WW.said[w.shown]);
    if (w.blowing >= 0) {
      const dryer = await vector("ic_hair_dryer"), box = Math.min(cw, WW.dryer);
      if (dryer && w.blowing >= 0) { x.save(); x.translate((cw - box) / 2, 0); blowDryer(x, dryer, box, ch, w.blowing); x.restore(); }
      return;
    }
    const v = await vector(WW.icons[w.shown]), side = Math.min(WW.icon, cw, ch);
    if (v && w.blowing < 0) drawVector(x, v, Math.round((cw - side) / 2), Math.round((ch - side) / 2), side, side);
  }
  // MonoPaint's blow dryer's frame (BlowDryer.draw, a dp a CSS px): the dryer at the left, shaking, its air blowing right.
  function blowDryer(x, dryer, width, height, frame) {
    const half = Math.min(12, (width - 2) * 12 / 35), cx = 1 + half, cy = height / 2, shake = [0, -1, 0, 1][frame % 4];
    const unit = 2 * half / 24, left = cx - half, top = cy - half + shake, limit = width - 1;
    drawVector(x, dryer, Math.round(left), Math.round(top), Math.round(2 * half), Math.round(2 * half));
    x.strokeStyle = "#000"; x.lineWidth = 1.5; x.lineCap = "round";
    for (const row of [4.5, 9, 13.5]) for (let wave = 0; wave < 2; wave++) {
      const start = left + (24 + (frame * 2 + wave * 4) % 8) * unit, end = Math.min(limit, start + 3 * unit);
      if (end > start) { x.beginPath(); x.moveTo(start, top + row * unit); x.lineTo(end, top + row * unit); x.stroke(); }
    }
  }
  function wetLook(w) {
    clearTimeout(w.timer);
    const r = T.wetLook(wetPage(), w.shown, performance.now(), wetStroking());
    w.hurrying = r[2] === 1;
    if (r[1] >= 0) w.timer = setTimeout(() => wetLook(w), r[1]);
    if (r[0] >= 0 && r[0] !== w.shown) { w.shown = r[0]; wetDraw(w); }
  }
  function wetTap(w) {
    if (wetStroking() || wetPage() < 0) return;
    if (w.shown !== 0) wetDry();
    w.blowing = 0; wetDraw(w);
    const blow = () => {
      if (w.blowing < 0) return;
      wetLook(w);
      w.blowing = w.blowing + 1 < WW.frames ? w.blowing + 1 : w.hurrying ? 0 : -1;
      wetDraw(w);
      if (w.blowing >= 0) setTimeout(blow, WW.frameMs);
    };
    setTimeout(blow, WW.frameMs);
  }
  // Dries what is wet as it would dry left alone, only sooner (DryingTicker.hurry): its ticks run on unpaced (live.html's wetTick).
  function wetDry() {
    const before = G.S.slice();
    if (T.hurry(wetPage())) return G.wetStart();
    const r = G.pull(G.P.changed());
    if (r) G.send(r, [], before);
  }
  function wetCanvas(w) {
    const c = el("canvas");
    c.setAttribute("role", "button"); c.onclick = () => wetTap(w);
    w.canvas = c;
    requestAnimationFrame(() => { wetDraw(w); wetLook(w); });
    return c;
  }
  // A pen or a finger up anywhere: the indicators look again (a stroke may have left paint wet).
  document.addEventListener("pointerup", () => setTimeout(() => wets.forEach(wetLook), 0), true);

  // ---- The column's small rows (InkWashBar): Shapes and the sand timer, Show Kinds and Play, the wet indicator; ----
  // ---- the countdown's row over them (DrawLayout.TIMER)                                                          ----
  const B = {};
  function cell(name, words, drawing, side, x, y, w, h, act) {
    const b = el("button", "at"); place(b, x, y, w, h); b.dataset.x = name; b.setAttribute("aria-label", words);
    if (drawing) { const i = icon(drawing, Math.min(side, w, h)); Object.assign(i.style, { position: "absolute", left: (w - parseFloat(i.style.width)) / 2 + "px", top: (h - parseFloat(i.style.height)) / 2 + "px" }); b.append(i); }
    b.onclick = act; $column().appendChild(b); B[name] = b;
    return b;
  }
  const $column = () => G.$("column");
  function arranged(top) {
    const L = G.L, block = top("BLOCK"), x0 = L.edge, x1 = L.edge + L.half, small = block + L.icon;
    cell("sand", CW.sand, "ic_sand_timer", CW.iconFull, x1, small, L.half, L.wet, timerMenu);
    cell("kinds", CW.kinds, "ic_ph_eye_fill", JSON.parse(T.m4Words()).iconSide, x0, small + L.wet, L.half, L.wet, kindsPopup);
    const wet = cell("wet", WW.said[wets[0].shown], null, 0, L.edge + L.half / 2, small + 2 * L.wet, L.half, L.wet, null);
    const c = wetCanvas(wets[0]); c.style.width = L.half + "px"; c.style.height = L.wet + "px"; c.style.display = "block"; wet.onclick = () => wetTap(wets[0]); c.onclick = null;
    wet.append(c);
    const count = cell("countdown", "", null, 0, L.edge, top("TIMER"), 2 * L.half, CW.timer, timerMenu);
    count.classList.add("xcount"); count.style.font = font("LABEL");
    sandLook();
  }

  // ---- Show Kinds (InkWashBar.kindsPopup): Pen, Paint and Dry Media side by side, each its step's eye under its name; ----
  // ---- a tap on an eye steps it on (Full, Half, Faint, Hidden); open until a tap outside it                       ----
  function kindsPopup() {
    if (G.live || !G.P) return;
    const box = popup(B.kinds);
    const show = () => {
      const m = JSON.parse(T.kindsMenu(G.P.id)), row = el("div", "xkinds");
      for (const c of m.columns) {
        const col = el("div"), name = menuRow(c.name, false, null, m.row), eye = el("button", "eye");
        name.style.padding = `0 ${m.pad}px`; name.disabled = true; name.style.color = "#000";
        eye.style.height = m.eye + "px"; eye.setAttribute("aria-label", c.said); eye.append(icon(c.eye, JSON.parse(T.m4Words()).iconSide));
        eye.onclick = () => {
          say(T.kindsTap(G.P.id, c.column));
          G.pull([0, 0, G.W - 1, G.H - 1]);  // the page shows its kinds anew (the screen's alone)
          show();
        };
        col.append(name, eye); row.append(col);
      }
      box.replaceChildren(row);
    };
    show(); placePopup();
  }
  // Show Kinds' steps on the screen (never in S, the page's own, which a live page sends): the rect as the screen shows it.
  function shown(x, y, w, h, t) { return G.P && T.kinds(G.P.id) ? u8(T.shown(G.P.id, x, y, w, h)) : t; }
  // A stroke of a hidden kind, or an eraser with all it takes hidden, is refused, said ("Pen is hidden, see Show Kinds").
  let lastNote = -1e9;
  function refuses(tool, size, dk, eraseKinds) {
    const note = T.hiddenNote(G.P.id, tool, size, dk, eraseKinds);
    if (!note) return false;
    if (performance.now() - lastNote >= CW.noteAgain) { lastNote = performance.now(); say(note); }
    return true;
  }
  // A tool taken up: a brush whose kind isn't Full says so.
  function chose(tool) { const [size, dk] = G.look(tool), s = T.takenUp(G.P.id, tool, size, dk); if (s) say(s); }

  // ---- The sand timer (SandTimer): its icon where the countdown's row has no countdown; a tap its menu; Time's up ----
  let sandTimer = 0;
  function sandLook() {
    clearTimeout(sandTimer);
    const showing = document.visibilityState === "visible" && !G.shown;
    const r = JSON.parse(T.sandLook(performance.now(), showing, !!G.live));
    const words = r.countdown;
    if (B.countdown) { B.countdown.textContent = words || ""; B.countdown.setAttribute("aria-label", r.said || ""); B.countdown.style.visibility = words ? "" : "hidden"; }
    if (B.sand) B.sand.style.visibility = r.icon ? "" : "hidden";  // its cell kept, so the wet indicator stays put
    if (r.timeUp) timeUp();
    sandTimer = setTimeout(sandLook, r.next >= 0 ? r.next : 1000);
  }
  document.addEventListener("visibilitychange", sandLook);
  // Off and the times, the one set marked, beside the countdown's row; a pick sets it from now.
  function timerMenu() {
    if (G.live) return;
    const m = JSON.parse(T.sandMenu()), box = popup(B.countdown);
    m.labels.forEach((t, i) => box.appendChild(menuRow(t, i === m.chosen, () => { closePopup(); T.sandSet(i, performance.now()); sandLook(); })));
    placePopup();
    const was = G.popped; setTimeout(() => { if (G.popped === was) closePopup(); }, m.offerMs);
  }
  // At zero: Time's up and Done beside the row, until a tap anywhere.
  function timeUp() {
    const m = JSON.parse(T.sandMenu()), box = popup(B.countdown);
    box.append(menuRow(m.timeUp, false, closePopup), menuRow(m.done, false, closePopup));
    placePopup();
  }

  return {
    labOpened, openButton, labShown, labBox, greySet, arranged, shown, refuses, chose,
    hurrying: () => !!G.P && T.hurrying(G.P.id), hurryTick: () => { T.hurryTick(G.P.id); return G.P.changed(); },
    wetChanged: () => wetLook(wets[0]), get sheetOpen() { return sheetOpen; }, wets, sandLook
  };
}
