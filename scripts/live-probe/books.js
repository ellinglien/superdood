// The Manta's notebooks on a live page (live.html; Share on Wi-Fi; docs/superpowers/specs/2026-11-01-manta-host-design.md,
// "Notebooks from a computer or iPad"). Menu → Notebook Explorer shows the Manta's shelf (LiveBooks: its covers and names,
// laid out by NotebookLayout through WebBooks.shelf); a tile's tap opens that notebook on the
// Manta and so here (LivePage "open"), its hold downloads the notebook's PDF or file. While the Manta has a notebook open,
// the page bar's ‹ 3 / 24 › turns the Manta's page (LivePage "turn") and Menu → Export Page downloads the page shown. One
// page for the room, the Manta's: a page message from it ("shown") starts a blank page here before the page comes whole.
// The Wi-Fi gone, the page is covered and takes no strokes until the connection is back. The shelf's Import sends a
// notebook's file, a picture or a PDF up, each a new notebook on the Manta (LiveBooks.add). Every ask and upload is
// sealed with the link's key (crypt: LiveCrypt), the notebook's or file's name in it too, and every answer comes sealed
// (LiveHost.book, added).
export function install(G) {
  const { T, el, font, icon, popup, placePopup, closePopup, menuRow, holdable, say, sized, key, crypt } = G;
  const B = T.books(), W = JSON.parse(B.menuWords());
  const px = n => n + "px", dpr = () => window.devicePixelRatio || 1;
  const at = (e, x, y, w, h) => { Object.assign(e.style, { position: "absolute", left: px(x), top: px(y), width: px(w), height: px(h) }); return e; };
  const text = (words, style) => { const t = el("div", "", words); t.style.font = font(style); return t; };
  const q = (path, more) => "path=" + encodeURIComponent(path) + (more ? "&" + more : "");
  // A route of the Manta's notebooks asked, the query sealed (q=, base64url); its answer opened: the name to save it
  // under (empty: not a download), then the bytes. Throws when refused or not sealed with the key.
  async function got(route, query) {
    const r = await fetch(route + "?key=" + key + (query ? "&q=" + sealedQuery(crypt, query) : ""));
    if (!r.ok) throw new Error(r.status);
    const b = crypt.open(new Uint8Array(await r.arrayBuffer())), nl = b.indexOf(10);
    return { name: new TextDecoder().decode(b.subarray(0, nl)), bytes: b.subarray(nl + 1) };
  }
  const dayOf = ms => { const d = new Date(ms); return Math.floor((d - d.getTimezoneOffset() * 60000) / 864e5); };

  // ---- The page the Manta shows (LivePage "shown"), and asking it to open or turn ----
  let shown = null, asking = 0, connected = true;
  function page(p) {
    if (p.kind !== "shown") return true;  // another guest's ask: the Manta answers it
    if (!shown || p.path !== shown.path || p.place !== shown.place) G.newPage();
    shown = p; asking = 0;
    showBar(); cover();
    return true;
  }
  // Nothing is drawn here until the Manta answers (its page shown), a few seconds at most: no mark lands on the page left.
  async function ask(kind, n, path) {
    asking = Date.now(); cover();
    setTimeout(() => { if (asking && Date.now() - asking >= 4000) { asking = 0; cover(); } }, 4100);
    try { await fetch("send?key=" + key + "&from=" + G.me, { method: "POST", body: crypt.seal(await Live.deflate(Live.pageAsk(kind, n, path))) }); }
    catch (err) { asking = 0; cover(); }
  }

  // ---- Covering the page: the Wi-Fi gone (said), or the Manta turning (not said) ----
  const shield = el("div"); shield.id = "books-shield";
  Object.assign(shield.style, { zIndex: "2", display: "none", alignItems: "center", justifyContent: "center", background: "rgba(255,255,255,.6)", touchAction: "none" });
  document.body.appendChild(shield);
  function cover() {
    const v = document.getElementById("view").getBoundingClientRect();
    at(shield, v.left, v.top, v.width, v.height);
    shield.style.display = connected && !asking ? "none" : "flex";
    shield.style.background = connected ? "transparent" : "rgba(255,255,255,.6)";
    shield.replaceChildren(...(connected ? [] : [text(W.notConnected, "HEADING")]));
  }
  // live.html's status (LivePresence) says when the relay's line drops and comes back.
  function setConnected(on) { if (on !== connected) { connected = on; cover(); } }

  // ---- The page bar's ‹ 3 / 24 › ----
  let bar = null;
  function showBar() {
    const L = G.L, C = G.C;
    if (!L || !shown || !shown.path) { if (bar) bar.hidden = true; return; }
    bar ??= (() => { const d = el("div"); d.id = "turn"; Object.assign(d.style, { display: "flex", alignItems: "center" }); document.body.appendChild(d); return d; })();
    at(bar, L.side + L.strip[1], 0, L.strip[0] - L.strip[1], L.pageBar - C.PAGE_RULE); bar.hidden = false; bar.style.zIndex = "1";
    const prev = el("button", "sq"), next = el("button", "sq"), num = text(shown.words.toUpperCase(), "LABEL");
    prev.append(icon("ic_ph_caret_left", 18)); next.append(icon("ic_ph_caret_right", 18));
    prev.setAttribute("aria-label", W.previous); next.setAttribute("aria-label", W.next);
    prev.style.height = next.style.height = "100%"; prev.style.width = next.style.width = "32px";
    prev.disabled = !shown.previous; next.disabled = !shown.next;
    const turn = by => { if (!G.live && !asking && connected) ask("turn", by, ""); };
    prev.onclick = () => turn(-1); next.onclick = () => turn(1);
    num.style.flex = "1"; num.style.textAlign = "center"; num.setAttribute("aria-label", shown.said);
    bar.replaceChildren(prev, num, next);
  }
  function arranged() { showBar(); cover(); }

  // ---- The Manta's shelf (NotebookScreens.shelf) ----
  const root = el("div"); root.id = "books"; root.hidden = true;
  Object.assign(root.style, { position: "fixed", inset: "0", zIndex: "15", background: "#fff", overflow: "auto" });
  document.body.appendChild(root);
  let shelfPage = 0;
  let onShelf = false;
  function hide() { onShelf = false; root.hidden = true; root.replaceChildren(); closePopup(); }
  async function showShelf() {
    let lines;
    try { lines = new TextDecoder().decode((await got("books")).bytes).split("\n").filter(Boolean).map(l => l.split("\t")); }
    catch (err) { say(W.notConnected); return; }
    const list = lines.map(([path, ms, pages, bad]) => [path, dayOf(+ms), pages, bad].join("\t")).join("\n");
    const S = JSON.parse(B.shelf(innerWidth, innerHeight, list, shelfPage, dayOf(Date.now()))), l = S.l;
    shelfPage = S.page;
    closePopup(); root.hidden = false; root.replaceChildren(); root.scrollTop = 0; onShelf = true;
    const head = at(el("div"), l.margin, l.margin, innerWidth - 2 * l.margin, l.bar);
    Object.assign(head.style, { display: "flex", alignItems: "center", gap: "8px" });
    const back = el("button", "lb"); back.style.font = font("LABEL"); back.append(icon("ic_ph_caret_left", 22), el("span", "", W.back)); back.onclick = hide;
    const heading = text(S.w.heading, "HEADING"); heading.setAttribute("role", "heading");
    // Import (the browser app's shelf's): a notebook's file, a picture or a PDF sent up, each a new notebook on the Manta.
    const imp = el("button", "lb boxed", W.import), spring = el("div"); spring.style.flex = "1";
    imp.style.font = font("LABEL"); imp.style.minHeight = px(l.button); imp.onclick = importFiles;
    head.append(back, heading, spring, imp); root.appendChild(head);
    const top = l.margin + l.bar + l.gap;
    // No New Notebook tile: a notebook is made on the Manta from here only by Import; the rest move up one.
    S.tiles.filter(t => t.path != null).forEach((t, k) => {
      const c = k % l.columns, r = Math.floor(k / l.columns);
      const tile = at(el("button", "tile"), l.margin + c * (l.tileWidth + l.columnGap), top + r * (l.tileHeight + l.rowGap), l.tileWidth, l.tileHeight);
      Object.assign(tile.style, { background: "none", display: "flex", flexDirection: "column", textAlign: "left", padding: "0", border: "0" });
      tile.setAttribute("aria-label", t.said);
      const face = el("canvas"); sized(face, l.tileWidth, l.coverHeight); face.style.border = "2px solid #000"; face.style.boxSizing = "border-box";
      const name = text(t.name, "BODY"); Object.assign(name.style, { marginTop: px(l.underCover), overflow: "hidden", display: "-webkit-box", webkitBoxOrient: "vertical", webkitLineClamp: String(l.nameLines) });
      const about = text(t.about, "CAPTION"); about.style.color = "#444";
      tile.append(face, name, about);
      coverOf(face, t.path, l.tileWidth, l.coverHeight);
      tile.onclick = () => { hide(); ask("open", 0, t.path); };
      holdable(tile, () => {
        const box = popup(tile, 200);
        box.append(menuRow(W.bookChoices[0], false, () => { closePopup(); download("books/pdf", q(t.path)); }),
          menuRow(W.bookChoices[1], false, () => { closePopup(); download("books/file", q(t.path)); }));
        placePopup();
      });
      root.appendChild(tile);
    });
    if (S.pages > 1) {
      const y = top + l.rows * l.tileHeight + (l.rows - 1) * l.rowGap + l.gap, row = at(el("div"), 0, y, innerWidth, l.bar);
      Object.assign(row.style, { display: "flex", alignItems: "center", justifyContent: "center" });
      const before = el("button", "sq"), after = el("button", "sq");
      before.append(icon("ic_ph_caret_left", 24)); after.append(icon("ic_ph_caret_right", 24));
      before.setAttribute("aria-label", S.w.newer); after.setAttribute("aria-label", S.w.older);
      before.disabled = S.page === 0; after.disabled = S.page >= S.pages - 1;
      before.onclick = () => { shelfPage--; showShelf(); }; after.onclick = () => { shelfPage++; showShelf(); };
      const words = text(S.pageWords, "LABEL"); words.style.width = "96px"; words.style.textAlign = "center";
      row.append(before, words, after); root.appendChild(row);
    }
  }
  function importFiles() {
    const input = el("input"); input.type = "file"; input.multiple = true;
    input.accept = [".mpaint", ...W.importable, "image/*", "application/pdf"].join(",");
    input.onchange = () => importList([...input.files]);
    input.click();
  }
  async function importList(files) {
    for (const f of files) { const said = await sendUp(crypt, W, f.name, f); if (said) say(said); }
    showShelf();
  }
  Live.dropFiles(root, () => onShelf, importList, W.dropImport);  // files dropped on the shelf: Import's
  // A tile's cover as the Manta's shelf draws it, at the screen's pixels: greys, a byte a pixel (LiveBooks).
  async function coverOf(c, path, w, h) {
    const tw = Math.max(1, Math.min(1024, Math.round(w * dpr()))), th = Math.max(1, Math.min(1024, Math.round(h * dpr())));
    try {
      const g = (await got("books/cover", q(path, "w=" + tw + "&h=" + th))).bytes, img = new ImageData(tw, th);
      for (let i = 0; i < g.length && i < tw * th; i++) { img.data[4 * i] = img.data[4 * i + 1] = img.data[4 * i + 2] = g[i]; img.data[4 * i + 3] = 255; }
      const off = document.createElement("canvas"); off.width = tw; off.height = th; off.getContext("2d").putImageData(img, 0, 0);
      const ctx = c.getContext("2d"); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.drawImage(off, 0, 0, c.width, c.height);
    } catch (err) { /* the outline stays */ }
  }
  // A download, opened here and saved under the name the Manta gives it; Safari on an iPad saves it to Files.
  async function download(route, query) {
    let f;
    try { f = await got(route, query); } catch (err) { say(W.notConnected); return; }
    const a = el("a"); a.href = URL.createObjectURL(new Blob([f.bytes])); a.download = f.name;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 60000);
  }

  // ---- Menu's rows (m4.js fileMenu): [Settings: none here, Notebook Explorer, Export Page while a Manta page is shown] ----
  function menuRows() {
    const rows = [null, [null, showShelf]];
    if (shown && shown.path && shown.place >= 0) rows.push([W.exportPage, () => {
      const anchor = document.getElementById("menu") || document.body, box = popup(anchor, 200);
      // The PNG and Layers then their sizes (B.sizeWords, the Manta's page's), size= an ExportSize's index.
      const get = (k, size) => download("books/page", q(shown.path, "place=" + shown.place + "&as=" + k + (size == null ? "" : "&size=" + size)));
      W.pageChoices.forEach((c, k) => box.appendChild(menuRow(c, false, () => {
        closePopup();
        const sizes = JSON.parse(B.sizeWords(k, G.W, G.H));
        if (!sizes.length) return get(k);
        const more = popup(anchor, 340);
        sizes.forEach((words, i) => more.appendChild(menuRow(words, false, () => { closePopup(); get(k, i); })));
        placePopup();
      })));
      placePopup();
    }]);
    return rows;
  }

  return { page, menuRows, arranged, showShelf, connected: setConnected, get shown() { return shown; }, get covered() { return shield.style.display !== "none"; } };
}

// A query sealed with the link's key, as LiveHost's q= takes it: base64url.
const sealedQuery = (crypt, query) => btoa(String.fromCharCode(...crypt.seal(new TextEncoder().encode(query)))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
// A file sent up to the Manta (LiveHost's POST /books/add, LiveBooks.add): made a new notebook there, never over one.
// Sealed: its name in the query, the file itself; the answer (its path there) opened, else it didn't go.
// The note to show when it isn't, or null. W: WebBooks.menuWords.
async function sendUp(crypt, W, name, body) {
  if (body.size > W.mostAdded) return W.tooLarge;
  try {
    const sealed = crypt.seal(new Uint8Array(await body.arrayBuffer()));
    const r = await fetch("books/add?key=" + crypt.access + "&q=" + sealedQuery(crypt, "name=" + encodeURIComponent(name)), { method: "POST", body: sealed });
    if (!r.ok) return r.status === 413 ? W.tooLarge : r.status === 415 ? W.unreadable : W.saveFailed;
    crypt.open(new Uint8Array(await r.arrayBuffer()));
    return null;
  } catch (err) { return err instanceof TypeError ? W.notConnected : W.saveFailed; }
}
