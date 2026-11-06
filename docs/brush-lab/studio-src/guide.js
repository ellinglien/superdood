// ---------- 5 · Guide: what each part, choice and dial does, limited to what the app's Brush Lab offers ----------
// Built onto the end of studio.js (as community.js). The tiles are Brush Lab Dials' (dial-demo-src: tiles.js, render.js
// and view.js, in #guide), filtered by APP_FEATURES (docs/brush-lab/app-features.json, written from the app's enums and
// checked against them by StudioGuideChecks) and named with the app's labels. Built the first time the tab opens; its
// own few workers draw only the tiles on screen or near it. Clicking a tile's picture puts that example's part, at
// the step shown, into This brush and opens Adjust. The cards' ⓘ buttons (infoBtn) open it at a part's or dial's entry.
const GUIDE = { view: null, tiles: null, back: null };
// The tiles, made once: the cards' ⓘ buttons read their anchors before the tab is built.
function guideTiles() { return GUIDE.tiles ??= demoTiles(APP_FEATURES); }
// The Guide's entry for part, or for its dial key on choice k (any choice when none): the dial's tile, else the
// part's section (Texture, on Medium's card, is Medium's), else null (After: the Guide has no section for it).
function guideId(part, key, k) {
  const t = key && guideTiles().find(t => !t.choice && t.part === part && t.key === key && (!k || t.on.includes(k)));
  return t ? "g-" + t.anchor : PART_ORDER.includes(cardOf(part)) ? "g-" + cardOf(part) : null;
}
function renderGuide() {
  const back = $("guideback");
  back.hidden = !GUIDE.back; if (GUIDE.back) back.textContent = "Back to " + GUIDE.back.what;
  if (GUIDE.view) return;
  // Only the parts the app's Brush Lab shows, in its order (PART_ORDER; demoTiles makes tiles for those alone): Paper's
  // Texture went on Medium's card (2026-11-01), so it has no section.
  GUIDE.view = dialGuide({ main: $("guidebody"), nav: $("guidenav"), tiles: guideTiles(),
    parts: PART_ORDER.map(p => ({ key: p, label: partLabel(p), blurb: ROLE[p].blurb, intro: PART_INTRO[p] })),
    src: $("engine").textContent + "\n;\n" + $("render").textContent + "\n;\n" + $("guide").textContent,
    scroller: $("guidebody"), idPrefix: "g-", onPick: tryGuideTile });
  GUIDE.view.start(POOL.inPage);
}
// An ⓘ: the Guide at its entry, scrolled to and marked for a moment; Back to returns to the card's ⓘ.
ACT.guideinfo = t => {
  GUIDE.back = { to: t.dataset.to, what: t.getAttribute("aria-label").replace(/^About /, "") };
  // A step in the browser's history, so its back arrow returns to the card too (the link and its brush code stay).
  if (!(history.state && history.state.guideinfo)) history.pushState({ guideinfo: 1 }, "");
  setTab("guide");
  const el = $(t.dataset.to); if (!el) return;
  const mark = el.matches("section") ? el.querySelector(".dg-part-head") : el;
  el.scrollIntoView({ block: el.matches("section") ? "start" : "center" });
  el.tabIndex = -1; el.focus({ preventScroll: true });
  mark.classList.add("g-flash"); setTimeout(() => mark.classList.remove("g-flash"), 1600);
};
ACT.guideback = () => { if (history.state && history.state.guideinfo) history.back(); else backFromGuide(); };
window.addEventListener("popstate", () => { if (ST.tab === "guide" && GUIDE.back) backFromGuide(); });
function backFromGuide() {
  const b = GUIDE.back; setTab("lab");
  const btn = b && [...document.querySelectorAll(`#cards [data-act="guideinfo"][data-to="${b.to}"]`)].find(x => x.getAttribute("aria-label") === "About " + b.what);
  if (btn) btn.focus();
};
function tryGuideTile(t, i) {
  const ex = cloneBrush(t.B0), v = t.values[i];
  if (t.dialKey && v !== null && v !== undefined) ex[t.part].d[t.dialKey] = v;
  const B = cloneBrush(ST.B);
  B[t.part] = normBrush(ex)[t.part];
  if (!t.choice) ST.focus = { part: t.part, key: t.key };
  commit(B, "Tried " + t.title, "Tried " + t.title);
  validFocus();
  setTab("lab");
}
