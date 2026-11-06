// ---------- Community library: brushes people shared, from superdood.org's service (superdood.org's server code (not in this repo), studio.NOTES.md) ----------
// Built onto the end of studio.js (build.cjs), so it sees the page's names. Only the page at superdood.org/studio/
// talks to the service, same origin; opened from a file or anywhere else, the tab shows the eight examples and says
// it needs the website. Rows are other people's input: every code must read as a brush before it is shown, and
// everything shown is escaped or set as text.
const COMM_SITE = "https://superdood.org/studio/";
const COMM_API = /^https?:$/.test(location.protocol) && location.pathname.startsWith("/studio/") ? "/studio/api/" : null;
const COMM = { state: COMM_API ? "wait" : "off", items: [], shown: [], more: false, busy: false, confirm: false, sig: "", shared: new Set(), reported: new Set() };
async function commFetch(path, body) {
  const r = await fetch(COMM_API + path, body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : { cache: "no-store" });
  let j = {}; try { j = await r.json(); } catch (e) { /* not JSON: an error page */ }
  return { ok: r.ok, status: r.status, j };
}
// A row as shown, or null: a code that reads, a name, a maker, a note, all short strings.
function commRow(x) {
  if (!x || typeof x !== "object" || typeof x.code !== "string" || x.code.length > 240 || typeof x.id !== "string") return null;
  const str = (v, n) => typeof v === "string" ? v.slice(0, n) : "";
  const B = fromCode(x.code, str(x.name, 40)); if (!B || !B.name) return null;
  return { id: x.id, code: x.code, name: B.name, maker: str(x.maker, 40), note: str(x.note, 200), at: Number.isFinite(x.at) ? x.at : 0, B };
}
async function loadCommunity(more) {
  if (!COMM_API || COMM.busy) return;
  COMM.busy = true;
  try {
    const last = more && COMM.items.length ? COMM.items[COMM.items.length - 1].at : 0;
    const r = await commFetch("brushes?limit=48" + (last ? "&before=" + last : ""));
    if (!r.ok || !Array.isArray(r.j.brushes)) throw new Error("bad answer");
    const rows = r.j.brushes.map(commRow).filter(Boolean);
    COMM.items = more ? COMM.items.concat(rows) : rows; COMM.more = !!r.j.more; COMM.state = "live";
  } catch (e) { if (!more) { COMM.state = "down"; COMM.items = []; } }
  finally { COMM.busy = false; commChanged(); }
}
const startCommunity = () => loadCommunity(false);
function commChanged() {
  if (ST.tab === "community") renderCommunity();
  if (ST.shareOpen) renderPublish();
}
function commNote() {
  const n = $("commnote");
  n.textContent = COMM.state === "off" ? "Shared brushes need the website: " : COMM.state === "down" ? "Library unavailable." : COMM.state === "live" && !COMM.items.length ? "No brushes shared yet." : "";
  if (COMM.state === "off") { const a = document.createElement("a"); a.href = COMM_SITE; a.target = "_blank"; a.rel = "noopener"; a.textContent = "superdood.org/studio"; n.appendChild(a); }
}
function renderCommunity() {
  const sg = $("sharedgrid"), live = COMM.state === "live" && COMM.items.length > 0, list = live ? COMM.items : [];
  $("commtag").hidden = live;
  commNote();
  sg.classList.toggle("live", live);
  const sig = live ? list.map(x => x.id + (COMM.reported.has(x.id) ? "!" : "")).join(",") + (COMM.more ? "+" : "") : "examples";
  if (sig !== COMM.sig) {
    COMM.sig = sig; COMM.shown = list;
    sg.innerHTML = live ? list.map((x, i) => `<article><span class="frame"><canvas data-cm="${i}"></canvas></span><span class="tname">${escH(x.name)}</span>` +
      `<span class="who">by ${escH(x.maker || "anonymous")}</span><span class="tcap">${escH(x.note || captionOf(x.B))}</span><span class="cacts"><button type="button" class="btn" data-act="commtry" data-i="${i}">Try</button>` +
      `<button type="button" class="del" data-act="commreport" data-i="${i}"${COMM.reported.has(x.id) ? " disabled>Reported" : ">Report"}</button></span></article>`).join("") +
      (COMM.more ? `<button type="button" class="btn more" data-act="commmore">Show More</button>` : "")
      : EXAMPLES.map((b, i) => `<article><span class="frame"><canvas data-ex="${i}"></canvas></span><span class="tname">${escH(b.name)}</span>` +
      `<span class="who">Example</span><span class="tcap">${escH(captionOf(b))}</span><button type="button" class="btn" data-act="extry" data-i="${i}">Try</button></article>`).join("");
  }
  // Eight in a row where they fit, else four (two rows), two on a phone.
  const w = $("s-community").clientWidth || innerWidth;
  sg.style.gridTemplateColumns = `repeat(${w / 8 >= 140 ? 8 : w >= 560 ? 4 : 2}, minmax(0, 1fr))`;
  sg.querySelectorAll("canvas[data-ex]").forEach(c => drawSample(c, "ex:" + c.dataset.ex, EXAMPLES[+c.dataset.ex], "loop", 3));
  sg.querySelectorAll("canvas[data-cm]").forEach(c => { const x = COMM.shown[+c.dataset.cm]; if (x) drawSample(c, "cm:" + x.id, x.B, "loop", 3); });
}
// The Share panel's Share to Community: a short form (who, a note), live only on the website.
function renderPublish() {
  const b = $("pubbtn"), code = appCode(ST.B), done = COMM.shared.has(code);
  b.disabled = !COMM_API || done || COMM.busy;
  b.textContent = done ? "Shared" : "Share to Community";
  if (!COMM_API) { $("pubnote").textContent = "Sharing needs the website."; COMM.confirm = false; }
  if (done) COMM.confirm = false;
  $("pubconfirm").hidden = !COMM.confirm; b.hidden = COMM.confirm;
}
async function publishBrush() {
  if (!COMM_API || COMM.busy) return;
  const code = appCode(ST.B);
  let reload = false;
  COMM.busy = true; $("pubyes").disabled = true; $("pubnote").textContent = "";
  try {
    const name = cleanName($("pubname").value || "") || ST.B.name;
    const r = await commFetch("submit", { code, name: name.slice(0, 40), maker: $("pubmaker").value.trim(), note: $("pubtext").value.trim(), website: $("pubsite").value });
    if (r.ok) {
      COMM.shared.add(code); COMM.confirm = false;
      // The library's name: one another shared brush has gets a number (the server's, as the app's library does).
      const as = typeof r.j.name === "string" && r.j.name ? r.j.name.slice(0, 40) : name;
      setStatus(r.j.status === "pending" ? "Sent as " + as + ". Waiting for review." : "Shared as " + as + ".");
      reload = r.j.status === "published";
    } else $("pubnote").textContent = typeof r.j.error === "string" ? r.j.error.slice(0, 80) : "Not shared.";
  } catch (e) { $("pubnote").textContent = "Not shared. Offline?"; }
  finally { COMM.busy = false; $("pubyes").disabled = false; renderPublish(); if (reload) loadCommunity(false); }
}
async function reportBrush(i) {
  const x = COMM.shown[i]; if (!x || COMM.reported.has(x.id)) return;
  COMM.reported.add(x.id); renderCommunity();
  try { const r = await commFetch("report", { id: x.id }); setStatus(r.ok ? "Reported." : "Not reported."); }
  catch (e) { setStatus("Not reported."); }
}
Object.assign(ACT, {
  commtry: t => { const x = COMM.shown[+t.dataset.i]; if (x) { const B = cloneBrush(x.B); setBase(B); commit(B, B.name, "Tried " + B.name); setTab("lab"); } },
  commreport: t => reportBrush(+t.dataset.i),
  commmore: () => loadCommunity(true),
  // The naming step first: the brush's name in the field, selected to type over (the rename field's way).
  publish: () => { COMM.confirm = true; renderPublish(); const i = $("pubname"); i.value = ST.B.name; i.focus(); i.select(); },
  pubyes: () => publishBrush(),
  pubno: () => { COMM.confirm = false; $("pubnote").textContent = ""; renderPublish(); }
});
