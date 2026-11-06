// The click-through for studio.html (headless Chrome through puppeteer-core): no console errors, the workers draw, a
// dial changed moves the sweep and before/after, every tab works, codes round-trip, the page remembers, and it draws
// without workers too. node check.cjs → one line a check, then a count; exit 1 on any failure.
"use strict";
const path = require("path");
const PUP = [process.env.PUPPETEER_CORE, path.join(require("os").homedir(), "node_modules", "puppeteer-core"), "puppeteer-core"].filter(Boolean);
let puppeteer = null; for (const p of PUP) { try { puppeteer = require(p); break; } catch (e) { /* next */ } }
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PAGE = "file://" + path.join(__dirname, "..", "studio.html");
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (cond, what) => { n++; if (!cond) fail++; console.log((cond ? "ok   " : "FAIL ") + what); };
(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  const p = await b.newPage(), errs = [], net = [];
  p.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs.push(m.type() + ": " + m.text()); });
  p.on("pageerror", e => errs.push("pageerror: " + e.message));
  p.on("request", r => { const u = r.url(); if (!/^(file|data|blob):/.test(u) && !/^https:\/\/fonts\.(googleapis|gstatic)\.com\//.test(u)) net.push(u); });
  await p.setViewport({ width: 1440, height: 940 });
  const open = async q => {
    await p.goto(PAGE + (q || ""), { waitUntil: "load" });
    await p.waitForFunction(() => document.documentElement.dataset.ready === "1", { timeout: 20000 });
  };
  const idle = () => p.waitForFunction(() => STUDIO.POOL.q.length === 0 && STUDIO.POOL.flight.size === 0, { timeout: 30000 }).then(() => sleep(150));
  const ev = (fn, ...a) => p.evaluate(fn, ...a);
  const click = async sel => { await ev(s => { const el = document.querySelector(s); if (!el) throw new Error("no " + s); el.click(); }, sel); await sleep(60); };
  // A canvas's pixels, hashed: to see that a picture was drawn again.
  const sig = sel => ev(s => [...document.querySelectorAll(s)].map(c => { if (!c.width) return "0"; const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let h = 0x811c9dc5; for (let i = 0; i < d.length; i += 4) h = Math.imul(h ^ d[i], 16777619); return c.width + "x" + c.height + ":" + (h >>> 0).toString(36); }).join(" "), sel);
  const inked = sel => ev(s => { const c = document.querySelector(s); const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 200) k++; return k; }, sel);

  await open(); await ev(() => { try { localStorage.clear(); } catch (e) { } }); await open(); await idle();
  ok(await ev(() => STUDIO.POOL.workers.length > 0 && !STUDIO.POOL.inPage), "workers started (" + await ev(() => STUDIO.POOL.workers.length) + ")");
  ok(await ev(() => STUDIO.POOL.done > 20), "workers drew the tiles (" + await ev(() => STUDIO.POOL.done) + " pictures)");
  // The app's engine (web/engine, WebAssembly GC: this Chrome has it) draws them, and the test page.
  ok(await ev(() => STUDIO.wasm && [...STUDIO.POOL.cache.values()].filter(c => c.wasm).length > 20), "the app's engine (WebAssembly) drew them (" + await ev(() => [...STUDIO.POOL.cache.values()].filter(c => c.wasm).length) + ")");
  ok(await inked("#idloop") > 20, "the name's loop is drawn");
  ok(await ev(() => document.querySelectorAll("#cards .card").length) === 7, "the app's six parts and Erasable shown at once");
  ok(await ev(() => document.querySelectorAll("#cards input[type=range]").length) > 15, "every dial a slider");
  ok(await ev(() => document.querySelectorAll("#cards .dial.def").length > 5 && document.querySelectorAll("#cards .dial:not(.def)").length > 0), "dials at their defaults are quieter");
  ok(await ev(() => [...document.querySelectorAll("#cards canvas[data-sw]")].every(c => c.width > 0)), "every choice swatch drawn");
  ok(await ev(() => document.querySelectorAll("#sweep .tile").length) === 7, "the sweep has seven steps");
  ok(await ev(() => [...document.querySelectorAll("#tabs button")].map(b => b.textContent).join(",")) === "Community library,Random,Guide" && await ev(() => STUDIO.ST.tab) === "lab", "the top tabs: Community library, Random, Guide; Adjust first open");
  ok(await ev(() => [...document.querySelectorAll("#change button")].map(b => b.textContent + (b.getAttribute("aria-pressed") === "true" ? "*" : "")).join(",")) === "Adjust*,Close-up,Mix" && await ev(() => /^THIS BRUSH$/i.test(document.querySelector("#brushbar > h2").textContent) && document.querySelector("#s-lab h2").textContent === "Adjust" && !!document.querySelector("#s-lab [data-act=randomize]") && !!document.querySelector("#s-lab #stylebtn") && !document.querySelector("#brushbar [data-act=randomize]")), "the sidebar: This brush, then Adjust (filled), Close-up, Mix; Adjust heads the parts with Style and Randomize");
  ok(await ev(() => [...document.querySelectorAll("#cards .card")].every(c => { const d = c.querySelector(".cdesc"); return d && d.textContent.length > 10 && d.textContent.length < 50 && !/\byou/i.test(d.textContent); })), "each part card says what the part does, in one short line");
  ok(await ev(() => STUDIO.ST.lib.some(b => b.name === "zealous-shallot")), "the owner's favourite is in the library on first open");
  ok(await ev(() => STUDIO.ST.layout && STUDIO.ST.layout.n >= 2 && document.querySelectorAll("#cards .ccol").length === STUDIO.ST.layout.n), "the cards packed into " + await ev(() => STUDIO.ST.layout.n) + " columns" + (await ev(() => STUDIO.ST.layout.labels) ? ", swatches labelled" : ""));
  ok(await ev(() => { const l = document.querySelector(".partscol"); return l.scrollHeight <= l.clientHeight + 1; }), "every card in view, none scrolled");

  // ---------- a dial changed: the sweep follows it, before/after shows the change ----------
  const sweep0 = await sig("#sweep canvas");
  await ev(() => { const r = document.querySelector('#cards input[data-part="medium"][data-key="bloom"]'); r.value = String(+r.max * .9); r.dispatchEvent(new Event("input", { bubbles: true })); r.dispatchEvent(new Event("change", { bubbles: true })); });
  await idle();
  ok(await ev(() => STUDIO.ST.focus.key) === "bloom" && await ev(() => document.getElementById("sweeptitle").textContent) === "Bloom", "dragging a dial focuses it: the sweep is Bloom's");
  ok(await sig("#sweep canvas") !== sweep0, "the sweep tiles are drawn again");
  ok(/^Bloom .+ to .+/.test(await ev(() => document.getElementById("bawhat").textContent)), "before/after names the change: " + await ev(() => document.getElementById("bawhat").textContent));
  const pct = await ev(() => document.getElementById("aftercap").textContent);
  ok(/After: \d+% changed/.test(pct) && !/After: 0%/.test(pct), "changed pixels counted: " + pct);
  ok(await sig("#before") !== await sig("#after"), "before and after differ");
  const h0 = await ev(() => STUDIO.H.nodes.length);
  await click("#sweep .tile:first-child"); await idle();
  ok(await ev(() => STUDIO.ST.B.medium.d.bloom) === 0 && await ev(() => STUDIO.H.nodes.length) === h0 + 1, "a sweep tile takes its value, a history node");
  ok(await ev(() => document.querySelector("#sweep .tile:first-child").getAttribute("aria-pressed")) === "true", "the step in use is marked");
  // Another dial focused by its name: the sweep moves, before/after compares it with its default or far end.
  await click('#cards .dname[data-key="wet"]'); await idle();
  ok(await ev(() => document.getElementById("sweeptitle").textContent) === "Wet" && /^Wet /.test(await ev(() => document.getElementById("bawhat").textContent)), "a dial's name focuses it");
  ok(await ev(() => [...document.querySelectorAll("#cards .card")].map(c => c.dataset.part + ":" + c.querySelector(".plabel").textContent).join(",")) === "hand:Control,tip:Tip,medium:Medium,erase:Erasable,carry:Affects Page,life:Drying,later:After",
    "the cards in the app's order with its labels: Control, Tip, Medium (then Erasable), Affects Page, Drying, After; no Paper card");
  ok(await ev(() => !!document.querySelector('#cards .card[data-part="medium"] [data-dial="paper.tooth"] .dname') && document.querySelector('#cards .card[data-part="medium"] [data-dial="paper.tooth"] .dname').textContent === "Texture"), "Texture is a row of Medium's card");
  await click('#cards .dname[data-part="paper"][data-key="tooth"]'); await idle();
  ok(await ev(() => document.getElementById("sweeptitle").textContent + "|" + document.getElementById("sweeppart").textContent) === "Texture|Medium", "Texture's sweep says Medium, its card");
  await click('#cards .choice[data-part="carry"][data-k="lift"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.carry.k) === "lift" && /^Affects Page: lift/.test(await ev(() => STUDIO.H.nodes.at(-1).what)), "a choice swatch switches the part");
  await click('#cards .card[data-part="tip"] [data-act="rollpart"]'); await idle();
  ok(await ev(() => STUDIO.H.nodes.at(-1).what.startsWith("Tip:")), "a part's die rolls it");
  await click('#cards [data-act="lockpart"][data-id="tip"]');
  ok(await ev(() => STUDIO.ST.locks.parts.has("tip")), "a part's lock");
  ok(await ev(() => document.querySelector('#cards .card[data-part="tip"] [data-act="rollpart"]').disabled), "a locked part can't be rolled");
  await click('#cards [data-act="lockdial"][data-id="medium.erase"]');

  // ---------- Randomize, Undo, Style, and the dice, keeping every lock ----------
  const snap = () => ev(() => { const B0 = STUDIO.ST.B, B = STUDIO.readAnyCode(STUDIO.encodeM(B0)).b; B.name = B0.name; return { code: STUDIO.encodeM(B), name: B.name, style: B.style, parts: Object.fromEntries(["tip", "medium", "carry", "paper", "life", "hand"].map(p => [p, JSON.stringify(B[p])])), erase: B.medium.d.erase }; });
  ok(await ev(() => !!document.querySelector('#s-lab [data-act="randomize"] svg') && document.getElementById("stylename").textContent.startsWith("Style: ") && ["newbrush", "library", "save", "saveasnew", "undo", "revert", "share"].every(a => document.querySelector(`#brushbar [data-act="${a}"]`)) && !!document.querySelector('#s-lab [data-act="styles"]') && !document.querySelector('#top [data-act="save"]')), "the sidebar: the loop and name, New, Open, Save, Save as New, Undo, Revert, Share; Style and Randomize (with the dice) on Adjust");
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.ST.locks.parts.add("carry"); STUDIO.ST.locks.parts.add("hand"); STUDIO.ST.locks.dials.add("medium.erase"); STUDIO.refresh(); });
  let before = await snap(), hn = await ev(() => STUDIO.H.nodes.length), changedAll = true, keptAll = true;
  for (let i = 0; i < 6; i++) {
    const a = await snap();
    await click('#s-lab [data-act="randomize"]'); await idle();
    const b2 = await snap();
    if (b2.code === a.code) changedAll = false;
    if (b2.parts.carry !== a.parts.carry || b2.parts.hand !== a.parts.hand || b2.erase !== a.erase || b2.style !== a.style) keptAll = false;
  }
  ok(changedAll && await ev(() => STUDIO.H.nodes.length) === hn + 6, "Randomize rolls a new brush, one history step each");
  ok(keptAll, "Randomize keeps the style, locked parts (Affects Page, Control) and a locked dial (Erasable)");
  const r6 = await snap();
  await click('#brushbar [data-act="undo"]'); await idle();
  const u1 = await snap();
  ok(u1.code !== r6.code && await ev(() => STUDIO.H.nodes.find(n => n.id === STUDIO.H.cur).what) === "Randomized", "Undo steps back one roll");
  for (let i = 0; i < 5; i++) { await click('#brushbar [data-act="undo"]'); await idle(); }
  ok((await snap()).code === before.code, "Undo all the way back to before the rolls");
  await click("#stylebtn");
  ok(await ev(() => !document.getElementById("styles").hidden && document.querySelectorAll("#styles .chip").length === STYLE_NAMES.length), "the style picker lists every style");
  await click('#styles [data-style="Charcoal"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.style === "Charcoal" && STUDIO.ST.B.medium.k === "charcoal" && document.getElementById("styles").hidden), "picking a style rolls a brush in it");
  { const sp = await snap(); ok(sp.parts.carry === before.parts.carry, "a style's roll keeps the locked parts" + (sp.parts.carry === before.parts.carry ? "" : ": " + sp.parts.carry + " vs " + before.parts.carry)); }
  await click('#s-lab [data-act="randomize"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.style) === "Charcoal", "Randomize stays in the picked style");
  // A part's die: that part only, its dials rolled; a dial's die: that dial only.
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.setChoice("medium", "wash"); });
  await ev(() => STUDIO.commit(Object.assign(STUDIO.ST.B, {}), "x")); await idle();
  let partOnly = true, partMoved = 0;
  for (const part of ["tip", "medium", "life", "hand"]) {
    const a = await snap();
    await click(`#cards .card[data-part="${part}"] [data-act="rollpart"]`); await idle();
    const b2 = await snap();
    if (b2.parts[part] !== a.parts[part]) partMoved++;
    for (const q of Object.keys(a.parts)) if (q !== part && a.parts[q] !== b2.parts[q]) partOnly = false;
  }
  ok(partOnly && partMoved >= 3, "a part's die rolls that part only (" + partMoved + " of 4 moved)");
  const dkey = await ev(() => { const keys = choiceOf("medium", STUDIO.ST.B.medium.k).dials.filter(k => !DIALS.medium[k].choices && !DIALS.medium[k].hide && k !== "erase"); return keys[0]; });
  const da = await ev(() => JSON.parse(JSON.stringify(STUDIO.ST.B)));
  await click(`#cards [data-act="rolldial"][data-part="medium"][data-key="${dkey}"]`); await idle();
  ok(await ev((a, k) => { const B = STUDIO.ST.B; return Object.keys(B.medium.d).every(x => x === k || B.medium.d[x] === a.medium.d[x]) && ["tip", "carry", "paper", "life", "hand"].every(p => JSON.stringify(B[p]) === JSON.stringify(a[p])); }, da, dkey), "a dial's die rolls that dial only (" + dkey + ")");
  await click(`#cards [data-act="lockdial"][data-id="medium.${dkey}"]`);
  ok(await ev(k => document.querySelector(`#cards [data-act="rolldial"][data-part="medium"][data-key="${k}"]`).disabled, dkey), "a locked dial's die is off");
  const keptV = await ev(k => STUDIO.ST.B.medium.d[k], dkey);
  await click('#cards .card[data-part="medium"] [data-act="rollpart"]'); await idle();
  ok(await ev((k, v) => STUDIO.ST.B.medium.k !== "wash" || STUDIO.ST.B.medium.d[k] === v, dkey, keptV), "a part's die keeps its locked dial");
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.refresh(); });

  // ---------- Save and Save as New, as the app's Brush Lab; Revert goes back to what was saved ----------
  const saveTxt = () => ev(() => document.getElementById("savebtn").textContent.trim() + (document.getElementById("savebtn").classList.contains("primary") ? "|solid" : "|outline"));
  const asNewSolid = () => ev(() => document.getElementById("saveasnewbtn").classList.contains("primary"));
  const saveShown = () => ev(() => !document.getElementById("savebtn").hidden);
  ok(await ev(() => [...document.querySelectorAll("#brushacts button")].map(b => b.textContent.trim()).join(",")) === "New,Open,Save,Save as New", "the app's row: New, Open, Save, Save as New");
  ok(!(await saveShown()) && await asNewSolid() && await ev(() => document.querySelector('#brushbar [data-act="saveasnew"]').textContent === "Save as New"), "a brush not from the library: only Save as New, solid");
  const libA = await ev(() => STUDIO.ST.lib.length);
  await click('#brushbar [data-act="saveasnew"]');
  ok(await saveShown() && await saveTxt() === "Saved|outline" && !(await asNewSolid()) && await ev(() => STUDIO.isSaved()) && await ev(() => STUDIO.ST.lib.length) === libA + 1, "Save as New adds it; then Save (Saved, outlined) and Save as New, plain");
  ok(await ev(() => document.getElementById("revertbtn").disabled && document.getElementById("idedited").textContent === "" && document.getElementById("idsuggest").hidden), "Revert off with nothing to revert; not Edited, no suggestion");
  const savedCode = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await ev(() => STUDIO.setChoice("paper", "felt")); await idle();
  ok(await saveTxt() === "Save|solid" && !(await ev(() => document.getElementById("revertbtn").disabled)) && await ev(() => document.getElementById("idedited").textContent) === "Edited", "a change: Save solid again, Revert on, Edited under the name");
  const hr = await ev(() => STUDIO.H.nodes.length);
  await click('#brushbar [data-act="revert"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === savedCode && await ev(() => STUDIO.H.nodes.length) === hr + 1 && await ev(() => STUDIO.H.nodes.at(-1).what) === "Reverted", "Revert returns to the saved brush, as a history step");
  await click('#brushbar [data-act="undo"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.paper.k) === "felt", "Revert can be undone");
  await click('#brushbar [data-act="revert"]'); await idle();

  // ---------- names: one brush a name in the library, the suggestion, the rename field, New ----------
  const own = await ev(() => STUDIO.ST.B.name), libB = await ev(() => STUDIO.ST.lib.length);
  // An edited brush is offered a name saying what it now is (the app's, else brushNameOf), passing by a name the library has.
  await ev(() => STUDIO.setChoice("medium", STUDIO.ST.B.medium.k === "wash" ? "ink" : "wash")); await idle();
  const said = await ev(() => STUDIO.appName(STUDIO.ST.B) || brushNameOf(STUDIO.ST.B));
  ok(await ev(n => STUDIO.suggestion() === (n === STUDIO.ST.B.name ? null : n) && document.getElementById("idsuggest").hidden === !STUDIO.suggestion() && (!STUDIO.suggestion() || document.getElementById("idsuggest").textContent === "Suggested: " + n), said), "an edited brush: Suggested: " + said);
  await ev(n => { STUDIO.ST.lib.push(Object.assign(cloneBrush(STUDIO.ST.B), { name: n.toUpperCase() })); STUDIO.refresh(); }, said);
  ok(await ev(n => STUDIO.suggestion() === null || STUDIO.suggestion().toLowerCase() !== n.toLowerCase(), said), "a suggestion the library has (case aside) is passed by: " + await ev(() => STUDIO.suggestion()));
  await ev(() => { STUDIO.ST.lib.pop(); STUDIO.refresh(); });
  // The rename field: opens on the suggestion (else the name), all selected; Escape keeps the name, Enter takes it.
  await click("#idname");
  ok(await ev(() => { const i = document.getElementById("idinput"); return !document.getElementById("idform").hidden && document.getElementById("idname").hidden && i.value === (STUDIO.suggestion() || STUDIO.ST.B.name) && document.activeElement === i && i.selectionStart === 0 && i.selectionEnd === i.value.length && document.querySelector('#idform [data-act="renamedone"]').textContent === "Done"; }), "the rename field opens on the suggested name, selected, with Done");
  await p.keyboard.press("Escape"); await sleep(60);
  ok(await ev(n => document.getElementById("idform").hidden && STUDIO.ST.B.name === n, own), "Escape leaves the name as it was");
  // A typed name another library brush has, case aside: Save as New numbers it.
  await click("#idname"); await ev(n => { document.getElementById("idinput").value = n.toUpperCase(); }, own); await p.keyboard.press("Enter"); await sleep(60);
  ok(await ev(n => STUDIO.ST.B.name === n.toUpperCase() && STUDIO.ST.named && document.getElementById("idsuggest").hidden, own), "a name typed: no suggestion while it stands");
  await click('#brushbar [data-act="saveasnew"]');
  ok(await ev(n => STUDIO.ST.B.name === n.toUpperCase() + " 2" && STUDIO.ST.lib[0].name === n.toUpperCase() + " 2" && STUDIO.ST.lib.some(b => b.name === n), own) && await ev(() => STUDIO.ST.lib.length) === libB + 1, "a taken name gets \" 2\" (" + await ev(() => STUDIO.ST.B.name) + "), the original left alone");
  ok(await ev(() => STUDIO.freeName(STUDIO.ST.B.name) === STUDIO.ST.B.name.replace(/ 2$/, " 3")), "a numbered taken name gets the next number, not a second one");
  // Save writes over the brush it was opened from, in its place, keeping that one's name.
  const at = await ev(n => STUDIO.ST.lib.findIndex(b => b.name === n), own);
  await click('[data-act="library"]'); await click(`#libbody [data-act="libload"][data-src="s"][data-i="${at}"]`); await idle();
  ok(await saveShown() && await saveTxt() === "Saved|outline" && await ev(n => STUDIO.ST.B.name === n && STUDIO.ST.from === n, own), "a library brush opened: Save and Save as New");
  await ev(() => STUDIO.setChoice("paper", STUDIO.ST.B.paper.k === "felt" ? "plain" : "felt")); await idle();
  const libC = await ev(() => STUDIO.ST.lib.length), edCode = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('#brushbar [data-act="save"]');
  ok(await ev((n, i, c, len) => STUDIO.ST.lib.length === len && STUDIO.ST.lib[i].name === n && STUDIO.encodeM(STUDIO.ST.lib[i]) === c && STUDIO.isSaved(), own, at, edCode, libC) && await saveTxt() === "Saved|outline", "Save writes over it in its place, its name kept");
  // A suggestion tapped is a name given; Save as New adds a copy under it, the opened one left alone.
  await ev(() => STUDIO.setChoice("medium", STUDIO.ST.B.medium.k === "wash" ? "ink" : "wash")); await idle();
  const sug = await ev(() => STUDIO.suggestion());
  if (sug) await click("#idsuggest");
  await click('#brushbar [data-act="saveasnew"]');
  ok(await ev((s, n, i, len) => STUDIO.ST.lib.length === len + 1 && STUDIO.ST.lib[0].name === STUDIO.ST.B.name && (s ? STUDIO.ST.B.name === s : / \d+$/.test(STUDIO.ST.B.name)) &&STUDIO.ST.lib[i + 1].name === n && STUDIO.ST.from === STUDIO.ST.lib[0].name, sug, own, at, libC), "Save as New after a change: a copy " + (sug ? "under the suggestion it took (" + sug + ")" : "numbered") + ", the opened one left alone");
  // New: a fresh brush in the style, not from the library: only Save as New again.
  const style = await ev(() => STUDIO.ST.B.style);
  await click('#brushbar [data-act="newbrush"]'); await idle();
  ok(await ev(s => STUDIO.ST.B.style === s && STUDIO.ST.from === null && document.getElementById("savebtn").hidden && document.getElementById("saveasnewbtn").classList.contains("primary") && /^New .+ brush\.$/.test(document.getElementById("status").textContent), style), "New: a fresh " + style + " brush, only Save as New");

  // ---------- the test page ----------
  const r = await ev(() => { const b = document.getElementById("test").getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
  const ink0 = await inked("#test");
  await p.mouse.move(r[0] + r[2] * .4, r[1] + r[3] * .7); await p.mouse.down();
  for (let i = 1; i <= 30; i++) { const u = i / 30; await p.mouse.move(r[0] + r[2] * (.4 + .5 * u), r[1] + r[3] * (.7 - .4 * Math.sin(u * 4))); await sleep(5); }
  await p.mouse.up(); await sleep(200);
  ok(await inked("#test") > ink0 + 200 && await ev(() => !!STUDIO.TP.page), "drawing on the test page (the app's engine)");
  ok(await ev(() => STUDIO.TP.log.length) === 1 && !(await ev(() => document.getElementById("lastbtn").disabled)), "the stroke is kept for Close-up");
  const t1 = await sig("#test");
  await click('#cards .choice[data-part="medium"][data-k="ink"]'); await sleep(200);
  ok(await sig("#test") !== t1, "the test page is drawn again with the new brush");
  await click('[data-act="eraser"]');
  await p.mouse.move(r[0] + r[2] * .5, r[1] + r[3] * .2); await p.mouse.down();
  for (let i = 1; i <= 20; i++) { await p.mouse.move(r[0] + r[2] * (.5 + .02 * i), r[1] + r[3] * (.2 + .03 * i)); await sleep(5); }
  await p.mouse.up(); await sleep(100);
  ok(await ev(() => STUDIO.TP.log.at(-1).er === true), "the eraser");
  await click('[data-act="eraser"]');
  await click('[data-act="clear"]');
  ok(await ev(() => STUDIO.TP.log.length) === 0, "Clear");

  // ---------- Close-up ----------
  await click('[data-tab="tune"]'); await idle();
  ok(await inked("#strip") > 100, "the stroke is drawn");
  ok(await inked("#close") > 100, "the close-up shows its pixels");
  const k0 = await ev(() => STUDIO.TUNE.key), s0 = await sig("#strip");
  const cr = await ev(() => { const b = document.querySelector('.curve[data-c="P"] .frame').getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
  await p.mouse.move(cr[0] + cr[2] * .5, cr[1] + cr[3] * .2); await p.mouse.down();
  for (let i = 1; i <= 8; i++) { await p.mouse.move(cr[0] + cr[2] * .5, cr[1] + cr[3] * (.2 + .08 * i)); await sleep(20); }
  await p.mouse.up(); await idle();
  ok(await ev(() => STUDIO.TUNE.key) !== k0 && await sig("#strip") !== s0, "dragging the pressure curve draws the stroke again");
  for (const z of [1, 4]) { const a = await sig("#close"); await click(`#zooms [data-z="${z}"]`); ok(await sig("#close") !== a, z + "× close-up"); }
  await click('[data-act="laststroke"]'); await idle();
  ok(await ev(() => STUDIO.TUNE.geo.length) > 10, "Use my last stroke");
  ok(await ev(() => document.querySelectorAll("#hist button").length) === await ev(() => STUDIO.H.nodes.length), "every change in the history");
  const firstCode = await ev(() => STUDIO.H.nodes[0].code);
  await click("#hist li:first-child button"); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === firstCode, "jumping back in the history");
  await ev(() => STUDIO.setChoice("paper", "laid")); await idle();
  ok(await ev(() => { const n = STUDIO.H.nodes.at(-1); return n.parent === STUDIO.H.nodes[0].id; }), "a change after jumping back branches");
  ok(await ev(() => [...document.querySelectorAll("#hist button")].some(b => parseInt(b.style.paddingLeft) > 10)), "the branch is indented");
  await click('[data-act="comparepick"]'); await click("#hist li:nth-child(1) button"); await click("#hist li:nth-child(2) button"); await idle();
  ok(await ev(() => !document.getElementById("comparesec").hidden && document.querySelectorAll("#compare figure").length === 2), "compare two side by side");
  const tn = await ev(() => STUDIO.H.nodes.length), tc = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('#s-lab [data-act="randomize"]'); await idle();
  ok(await ev(() => STUDIO.H.nodes.length) === tn + 1 && await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== tc && await ev(() => document.querySelector("#hist button.cur .what").textContent) === "Randomized", "a Randomize in Close-up's history: a new step, shown");
  await click('#brushbar [data-act="undo"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === tc, "Close-up's Undo");
  const libN = await ev(() => STUDIO.ST.lib.length);
  await click('#s-tune [data-act="saveasnew"]');
  ok(await ev(() => STUDIO.ST.lib.length) === libN + 1, "save as new: " + await ev(() => STUDIO.ST.B.name));

  // ---------- Mix ----------
  await click('[data-tab="mix"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#cross .tile").length) === await ev(() => CHOICES.tip.length * CHOICES.medium.length), "every tip × every medium (" + await ev(() => CHOICES.tip.length + " × " + CHOICES.medium.length) + ")");
  ok(await ev(() => [...document.querySelectorAll("#cross canvas")].every(c => c.width > 0)), "every cross tile drawn");
  await ev(() => { const s = document.getElementById("crosscol"); s.value = "carry"; s.dispatchEvent(new Event("change", { bubbles: true })); }); await idle();
  ok(await ev(() => document.querySelectorAll("#cross .tile").length) === await ev(() => CHOICES.tip.length * CHOICES.carry.filter(c => c.k !== "shape").length) && await ev(() => [...document.getElementById("crosscol").options].map(o => o.textContent).join(",")) === "Control,Tip,Medium,Affects Page,Drying", "rows and columns can be any two parts, the app's (tips × Affects Page)");
  await click('#cross .tile[data-r="hake"][data-c="lift"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.tip.k === "hake" && STUDIO.ST.B.carry.k === "lift"), "a cross tile is tried");
  // ---------- Roll ----------
  await click('[data-tab="roll"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#wild .tile").length === 12 && /12 random brushes/.test(document.querySelector("#s-roll h2").textContent)), "Roll: 12 random brushes, said so");
  ok(await ev(() => { const s = document.getElementById("s-roll"), btn = s.querySelector('[data-act="wild"]'); return btn.textContent === "Generate other random brushes" && btn.getBoundingClientRect().top > document.getElementById("wild").getBoundingClientRect().top && document.getElementById("wildlocks").getBoundingClientRect().top < document.getElementById("wild").getBoundingClientRect().top && !document.querySelector('#brushbar [data-act="randomize"], #brushbar [data-act="styles"]') && ![...document.querySelectorAll("#change button")].some(b => b.getAttribute("aria-pressed") === "true"); }), "Random: the button at the foot of the grid, locks above, no sidebar button filled");
  const tipNow = await ev(() => STUDIO.ST.B.tip.k);
  ok(await ev(() => [...document.querySelectorAll("#wildlocks .chip")].map(c => c.textContent).join(",")) === "Control,Tip,Medium,Affects Page,Drying,After,Erasable", "Random's locks: the app's parts in its order (After too), then Erasable");
  await ev(() => STUDIO.ST.locks.parts.clear()); await click('#wildlocks [data-part="tip"]');
  await click('[data-act="wild"]'); await idle();
  ok(await ev(t => STUDIO.ST.wild.every(b => b.tip.k === t), tipNow), "a locked part stays in every roll");
  ok(await ev(() => [...document.querySelectorAll("#wild canvas")].every(c => c.width > 0)), "every roll drawn");
  await click('#wild .tile[data-i="3"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.name === STUDIO.ST.wild[3].name), "a roll is tried");

  // ---------- Guide: Brush Lab Dials' tiles, only what the app's Brush Lab offers, drawn as they scroll into view ----------
  await click('[data-tab="guide"]');
  await p.waitForFunction(() => GUIDE.view && GUIDE.view.TILES.some(t => t.frames.some(Boolean)), { timeout: 30000 });
  ok(await ev(() => !document.getElementById("s-guide").hidden && [...document.querySelectorAll("#guidebody .dg-part h2")].map(h => h.textContent).join(",")) === "Control,Tip,Medium,Affects Page,Drying" &&
    await ev(() => [...document.querySelectorAll("#guidenav a")].map(a => a.textContent).join(",")) === "Control,Tip,Medium,Affects Page,Drying", "Guide opens: Control, Tip, Medium, Affects Page, Drying, each a section and a link (no Paper: its Texture is on Medium)");
  ok(await ev(() => GUIDE.view.TILES.filter(t => t.frames.some(Boolean)).length < GUIDE.view.TILES.length / 2), "Guide draws only the tiles near the screen (" + await ev(() => GUIDE.view.TILES.filter(t => t.frames.some(Boolean)).length + " of " + GUIDE.view.TILES.length) + ")");
  for (const k of ["tip", "medium", "life", "hand", "carry"]) {
    await ev(k => document.querySelector('#guidenav a[href="#g-' + k + '"]').click(), k);
    await p.waitForFunction(k => GUIDE.view.TILES.some(t => t.part === k && t.frames.some(Boolean)), { timeout: 60000 }, k).catch(() => {});
  }
  ok(await ev(() => ["tip", "medium", "life", "hand", "carry"].every(k => GUIDE.view.TILES.some(t => t.part === k && t.frames.some(Boolean) && !t.failed) && document.querySelector("#g-" + k + " .dg-tile"))), "Guide draws a tile in every section as it scrolls into view");
  ok(await ev(() => location.hash === "" && document.querySelector("#guidenav a.dg-here").textContent === "Affects Page"), "a part's link scrolls to it and fills, leaving the page's link alone");
  { const bad = await ev(() => GUIDE.view.TILES.filter(t => { const c = APP_FEATURES[t.part] && APP_FEATURES[t.part].choices[t.k]; return !c || (t.choice ? t.title !== c.label : !(t.key in c.dials) || t.title !== c.dials[t.key]); }).map(t => t.part + " " + t.title));
    ok(!bad.length, "every Guide tile is a choice or dial the app offers, under the app's label" + (bad.length ? ": " + bad.join(", ") : "")); }
  ok(await ev(() => !GUIDE.view.TILES.some(t => ["takes", "plume", "trange"].includes(t.key) || t.k === "handhatch") && GUIDE.view.TILES.some(t => t.title === "Takes Ink") === false), "no lab-only choice or dial in Guide (Takes Ink, Plume, Tilt Range, Hand Hatch)");
  ok(await ev(() => ["Press to Push", "Press to Lift", "Clear Water", "Smear", "Blend Mode"].every(n => GUIDE.view.TILES.some(t => t.title === n && t.say))), "Guide has Smudge's Press to Push and Press to Lift, Clear Water, Smear and Blend Mode, each saying what it does");
  { const g = await ev(() => GUIDE.view.TILES.find(t => t.part === "medium" && t.key === "wet").id);
    await ev(id => document.querySelector('#guidebody [data-tile="' + id + '"]').scrollIntoView(), g);
    await p.waitForFunction(id => GUIDE.view.TILES[id].frames.some(Boolean), { timeout: 30000 }, g);
    const h1 = await ev(() => STUDIO.H.nodes.length);
    await click('#guidebody [data-tile="' + g + '"] .dg-frame'); await idle();
    ok(await ev(() => STUDIO.ST.tab === "lab" && STUDIO.ST.focus.part === "medium" && STUDIO.ST.focus.key === "wet" && STUDIO.ST.B.medium.k === GUIDE.view.TILES.find(t => t.part === "medium" && t.key === "wet").k) && await ev(() => STUDIO.H.nodes.length) === h1 + 1,
      "a Guide tile clicked puts its example's part in This brush, focused on its dial, one history step"); }
  // ---------- the cards' ⓘ: each opens the Guide at its part's or dial's entry ----------
  await ev(() => STUDIO.setTab("lab")); await idle();
  ok(await ev(() => [...document.querySelectorAll("#cards .card")].every(c => (c.dataset.part === "later") !== !!c.querySelector('.chead button.icn.info[data-act="guideinfo"]')) &&
    [...document.querySelectorAll("#cards .card:not([data-part=later]) .dial")].every(d => d.classList.contains("solo") || d.querySelector('.dn button.info[aria-label="About ' + d.querySelector(".dname").textContent + '"]'))),
    "every card but After has an ⓘ in its header, every dial one by its name (About …)");
  ok(await ev(() => [...document.querySelectorAll('#cards [data-act="guideinfo"]')].every(b => { const e = document.getElementById(b.dataset.to); return e && document.getElementById("guidebody").contains(e); })), "every ⓘ on the cards lands on a Guide entry that exists");
  { const bad = await ev(() => { const out = [];
      for (const part of ["hand", "tip", "medium", "carry", "life"]) for (const c of CHOICES[part]) { const A = APP_FEATURES[part].choices[c.k]; if (!A) continue;
        for (const key of Object.keys(A.dials)) { const id = guideId(part, key, c.k), e = id && document.getElementById(id);
          if (!e || (DIALS[part][key] && !DIALS[part][key].hide && !e.classList.contains("dg-tile") && GUIDE.view.TILES.some(t => !t.choice && t.part === part && t.key === key))) out.push(part + "." + c.k + "." + key + ">" + id); } }
      return out; });
    ok(!bad.length, "every dial of every choice the app offers has an ⓘ entry, its own tile where the Guide has one" + (bad.length ? ": " + bad.slice(0, 8).join(", ") : "")); }
  { const to = await ev(() => document.querySelector('#cards .card[data-part="tip"] .dial [data-act="guideinfo"]').dataset.to), lab = await ev(() => document.querySelector('#cards .card[data-part="tip"] .dial [data-act="guideinfo"]').getAttribute("aria-label").slice(6));
    await click('#cards .card[data-part="tip"] .dial [data-act="guideinfo"]'); await sleep(200);
    ok(await ev(id => { const e = document.getElementById(id), r = e.getBoundingClientRect(), g = document.getElementById("guidebody").getBoundingClientRect();
      return STUDIO.ST.tab === "guide" && e.classList.contains("dg-tile") && r.top >= g.top - 1 && r.bottom <= g.bottom + 1 && e.classList.contains("g-flash") && document.activeElement === e; }, to) &&
      await ev(() => { const b = document.getElementById("guideback"); return !b.hidden && b.textContent; }) === "Back to " + lab, "a Tip dial's ⓘ opens the Guide on its tile, in view and marked, with Back to " + lab + " (" + to + ")");
    await click("#guideback");
    ok(await ev(id => STUDIO.ST.tab === "lab" && document.activeElement && document.activeElement.dataset.to === id, to), "Back to returns to the card, on the ⓘ");
    await click('#cards .card[data-part="medium"] .chead [data-act="guideinfo"]'); await sleep(200);
    ok(await ev(() => STUDIO.ST.tab === "guide" && document.querySelector("#g-medium .dg-part-head").classList.contains("g-flash")), "Medium's ⓘ opens its section");
    await ev(() => STUDIO.setTab("community")); await ev(() => STUDIO.setTab("guide"));
    ok(await ev(() => document.getElementById("guideback").hidden), "Back to only after an ⓘ"); await ev(() => STUDIO.setTab("lab")); }

  // ---------- Share (a panel from the brush bar) and the Community library ----------
  await click('#brushbar [data-act="share"]');
  ok(await ev(() => !document.getElementById("sharepanel").hidden && document.getElementById("qr").width > 100), "Share opens the code and its QR code");
  const code = await ev(() => document.getElementById("code").value);
  ok(await ev(c => { const r = STUDIO.readAnyCode(c); return !!r && STUDIO.encodeM(r.b) === c && c === STUDIO.encodeM(STUDIO.ST.B); }, code), "the brush code is this brush's and reads back to itself");
  ok(await ev(() => { const b = document.getElementById("pubbtn"); return b.textContent === "Share to Community" && b.disabled && document.getElementById("pubnote").textContent === "Sharing needs the website."; }), "from a file: Share to Community off, needs the website");
  ok(await ev(() => { const b = document.getElementById("dltablet"); return !!b && /Download for tablet/.test(b.textContent) && /Document\/Superdood/.test(document.getElementById("sharepanel").textContent); }), "Share has Download for tablet, with where to save it");
  await click('#dltablet');
  ok(await ev(() => STUDIO.ST.lastDownload === STUDIO.ST.B.name.replace(/[\\/:*?"<>|]+/g, "").trim() + ".inkwash.txt"), "Download for tablet names the file <brush>.inkwash.txt");
  ok(await ev(() => document.getElementById("exportbtn").textContent === "Export" && /^A Procreate set of this brush and the saved ones\. Drying and Affects Page stay in Superdood\.$/.test(document.getElementById("exportnote").textContent)), "Share has Export, a Procreate set, saying what stays behind");
  await click('#exportbtn');
  await p.waitForFunction(() => STUDIO.ST.lastExport, { timeout: 60000 });
  ok(await ev(() => { const x = STUDIO.ST.lastExport; return x.file === "Superdood.brushset" && x.n === STUDIO.exportList().length && x.n >= 2 && x.bytes > 20000 && document.getElementById("status").textContent === "Set exported." && !document.getElementById("exportbtn").disabled; }),
    "Export downloads Superdood.brushset: this brush and the saved ones (" + await ev(() => JSON.stringify(STUDIO.ST.lastExport).slice(0, 200)) + ")");
  await click('#sharepanel [data-act="share"]');
  ok(await ev(() => document.getElementById("sharepanel").hidden), "Share closes");
  await click('[data-act="library"]');
  await ev(() => { document.getElementById("paste").value = "nonsense"; }); await click('[data-act="loadcode"]');
  ok(await ev(() => document.getElementById("loadnote").textContent) === "That isn't a brush code.", "a bad code is refused");
  const other = await ev(() => STUDIO.encodeM(STUDIO.BASICS[1]));
  await ev(c => { document.getElementById("paste").value = c; }, other); await click('[data-act="loadcode"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === other && await ev(() => document.getElementById("library").hidden), "Open a pasted code, from the library");
  await click('[data-tab="community"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#sharedgrid article").length === 8 && [...document.querySelectorAll("#sharedgrid .who")].every(w => w.textContent === "Example") && /Community library/.test(document.querySelector("#s-community h2").textContent) && !document.getElementById("commtag").hidden && document.getElementById("commtag").textContent === "Examples" && document.getElementById("commnote").textContent === "Shared brushes need the website: superdood.org/studio" && document.querySelector("#commnote a").href === "https://superdood.org/studio/"), "from a file: the Community library shows eight examples, so labelled, and links the website");
  const ex = await ev(() => document.querySelector("#sharedgrid .tname").textContent);
  await click('#sharedgrid [data-act="extry"]'); await idle();
  ok(await ev(() => STUDIO.ST.tab) === "lab" && await ev(() => STUDIO.ST.B.name) === ex, "Try puts a community brush in the Lab");

  // ---------- the library and remembering ----------
  await click('#brushbar [data-act="saveasnew"]');
  await click('[data-act="library"]');
  ok(await ev(() => !document.getElementById("library").hidden && document.querySelectorAll("#libbody .libitem").length >= 10), "the library opens");
  await click('#libbody [data-act="libload"][data-src="b"][data-i="0"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.name) === "Ink", "a basic tool loads");
  const keep = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await open(); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === keep && await ev(() => STUDIO.ST.lib.length) >= 2 && await ev(() => STUDIO.H.nodes.length) > 5, "remembered after a reload");
  ok(await ev(() => STUDIO.ST.tab) === "lab", "the tab remembered");
  // The link's brush: studio.html#<share code> opens it (a Print tool), and a new hash opens that one (a Wet one).
  { const benDay = "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef",
      wet = "wet-gouache.M80b70000000054_2zz00_0_000_7_83z0000000000000000nz0.2fj";
    await p.goto("about:blank"); await open("#" + benDay); await idle();
    ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === benDay && await ev(() => STUDIO.ST.B.style) === "Print", "the link's code opens: " + await ev(() => STUDIO.ST.B.name));
    await ev(c => { location.hash = c; }, wet); await sleep(300); await idle();
    ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === wet && await ev(() => STUDIO.ST.B.life.k) === "wetpaint", "a new hash opens its brush: " + await ev(() => STUDIO.ST.B.name));
    await p.goto("about:blank"); await open(); await idle(); }

  // A Basic tool the lab's model can't hold all of (Metalpoint's After, Tarnish): loaded, its code is the app's; edited,
  // saved as new and the page opened again, its After is still there (render.js appCode, WebEngine.keptCode).
  const metal = "metalpoint.M403l000000003m_40000_0_1ee_0_85e7700000000000000nz0_0_di0hp.2gn", after = "_0_di0hp.2gn";
  await ev(c => STUDIO.loadCode(c), metal); await idle();
  ok(await ev(c => STUDIO.encodeM(STUDIO.ST.B) === c, metal) && await inked("#idloop") > 20, "a Basic tool with After loads as the app writes it, and is drawn");
  await ev(() => STUDIO.setChoice("paper", STUDIO.ST.B.paper.k === "felt" ? "plain" : "felt")); await idle();
  const metalEd = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('[data-act="saveasnew"]'); await open(); await idle();
  ok(metalEd !== metal && metalEd.endsWith(after) && await ev(c => STUDIO.ST.lib.some(b => STUDIO.encodeM(b).replace(/^[^.]*/, "") === c.replace(/^[^.]*/, "")), metalEd), "edited and saved, its After kept after the page opens again (" + metalEd + ")");

  // The app's model's cards (WebEngine.studioParts, studioEdit): After's choices and dials, the newer tips and prints,
  // each edited through the app's model and kept as the app writes it.
  // The app's model's parts of a code but one, its choices and dials (held aside: that's the lab's model's).
  const rest = (code, skip) => ev((c, k) => { const A = STUDIO.appParts(STUDIO.readAnyCode(c).b); delete A[k]; for (const p of Object.values(A)) delete p.held; return JSON.stringify(A); }, code, skip);
  ok(await ev(() => [...document.querySelectorAll('.card[data-part="later"] .choice')].map(c => c.dataset.label).join(",")) === "Off,Float,Mold,Fading,Lemon Juice,Salt,Plastic Wrap,Feathering,Letterpress,Foxing,Cracks,Tarnish,Torn Paper,Cross-Stitch" &&
    await ev(() => document.querySelector('.card[data-part="later"] .cname').textContent) === "Tarnish" && await ev(() => document.querySelectorAll('.card[data-part="later"] input[type=range]').length) === 2,
    "After's card: the app's choices in its order, Tarnish in use, its two dials" + await ev(() => " (" + [...document.querySelectorAll('.card[data-part="later"] .choice')].map(c => c.dataset.label).join(",") + "; " + document.querySelector('.card[data-part="later"] .cname').textContent + ")"));
  ok(await ev(() => [...document.querySelectorAll('.card[data-part="later"] canvas[data-sw]')].every(c => c.width > 0)), "After's swatches drawn");
  const tarnished = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('.card[data-part="later"] [data-act="appchoice"][data-k="cracks"]'); await idle();
  const cracked = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  ok(await ev(() => document.querySelector('.card[data-part="later"] .cname').textContent) === "Cracks" && cracked !== tarnished && await rest(cracked, "later") === await rest(tarnished, "later") && /^After: tarnish to cracks$/.test(await ev(() => document.getElementById("bawhat").textContent)),
    "After: Cracks chosen through the app's model, the rest of the code kept (" + cracked + ")");
  await ev(() => { const r = document.querySelector('.card[data-part="later"] input[type=range]'); r.value = String(+r.max); r.dispatchEvent(new Event("input", { bubbles: true })); r.dispatchEvent(new Event("change", { bubbles: true })); }); await idle();
  const dialled = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  ok(dialled !== cracked && await ev(c => STUDIO.encodeM(STUDIO.readAnyCode(c).b) === c, dialled) && await ev(() => STUDIO.H.nodes.length > 2 && /to/.test(document.getElementById("bawhat").textContent)), "After's dial slid and let go: one change, its code read back the same");
  await click('[data-act="undo"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === cracked, "Undo takes After's dial back");
  // The app's cards lock and roll as the lab's: a Brayer dial's and After's dice, through the app's model; locked, a part
  // survives Randomize; unlocked, After keeps its choice.
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); });
  await click('.card[data-part="tip"] [data-act="appchoice"][data-k="roller"]'); await idle();
  {
  const appCtl = p => ev(q => { const c = document.querySelector('.card[data-part="' + q + '"]'), rows = [...c.querySelectorAll(".dial")];
    return !!c.querySelector('.chead [data-act="lockpart"]') && !!c.querySelector('.chead [data-act="approllpart"]') && rows.length > 0 && rows.every(r => r.querySelector('[data-act="lockdial"]') && r.querySelector('[data-act="approlldial"]')); }, p);
  ok(await ev(() => document.querySelector('.card[data-part="tip"] .cname').textContent) === "Brayer" && await appCtl("tip") && await appCtl("later"), "Brayer's and After's cards: a lock and a die on the part and on every dial");
  const rolled = async (part, key) => { const was = await ev(() => STUDIO.encodeM(STUDIO.ST.B)); for (let i = 0; i < 4; i++) { await click('.card[data-part="' + part + '"] [data-act="approlldial"][data-key="' + key + '"]'); await idle(); if (await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== was) break; } return was; };
  const bkey = await ev(() => document.querySelector('.card[data-part="tip"] input[type=range][data-app]').dataset.key);
  let was = await rolled("tip", bkey);
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== was && await rest(await ev(() => STUDIO.encodeM(STUDIO.ST.B)), "tip") === await rest(was, "tip") && await ev(() => STUDIO.appParts(STUDIO.ST.B).tip.choices.find(c => c.on).k) === "roller", "a Brayer dial's die rolls it through the app's model, the rest kept (" + bkey + ")");
  const lkey = await ev(() => document.querySelector('.card[data-part="later"] input[type=range][data-app]').dataset.key);
  was = await rolled("later", lkey);
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== was && await rest(await ev(() => STUDIO.encodeM(STUDIO.ST.B)), "later") === await rest(was, "later") && await ev(() => STUDIO.appParts(STUDIO.ST.B).later.name) === "Cracks", "an After dial's die rolls it, the rest kept (" + lkey + ")");
  await click('.card[data-part="later"] [data-act="lockdial"][data-id="later.' + lkey + '"]');
  ok(await ev(k => STUDIO.ST.locks.dials.has("later." + k) && document.querySelector('.card[data-part="later"] [data-act="approlldial"][data-key="' + k + '"]').disabled, lkey), "After's dial locked: its die off");
  const appOf = (p, c) => ev((q, x) => { const P = STUDIO.appParts(STUDIO.readAnyCode(x).b)[q]; return JSON.stringify([P.choices.find(c => c.on).k, P.dials.map(d => d.key + "=" + d.shown)]); }, p, c);
  await click('.card[data-part="tip"] [data-act="lockpart"]');
  was = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('#s-lab [data-act="randomize"]'); await idle();
  let now = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  ok(now !== was && await appOf("tip", now) === await appOf("tip", was) && await ev(() => STUDIO.appParts(STUDIO.ST.B).later.name) === "Cracks" && (await appOf("later", now)).includes(JSON.parse(await appOf("later", was))[1].find(x => x.startsWith(lkey + "="))),
    "Randomize keeps the locked Brayer and After's locked dial, After on (" + now + ")");
  await click('.card[data-part="tip"] [data-act="lockpart"]'); await click('.card[data-part="later"] [data-act="lockpart"]');
  ok(await ev(() => document.querySelector('.card[data-part="later"] [data-act="approllpart"]').disabled), "After locked: its die off");
  was = now; await click('#s-lab [data-act="randomize"]'); await idle(); now = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  ok(now !== was && await appOf("later", now) === await appOf("later", was), "a locked After survives Randomize as it was");
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.refresh(); });
  was = now; await click('#s-lab [data-act="randomize"]'); await idle(); now = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  ok(JSON.parse(await appOf("later", now))[0] === "cracks", "unlocked, After keeps its choice through Randomize, its dials rolled");
  }
  // A newer tip and a newer print, chosen on the lab's cards, then edited on the app's.
  await click('.card[data-part="tip"] [data-act="appchoice"][data-k="ruling"]'); await idle();
  ok(await ev(() => document.querySelector('.card[data-part="tip"] .cname').textContent) === "Ruling Pen" && await ev(() => !document.querySelector('.card[data-part="tip"] [data-act="rollpart"]')) && await ev(() => STUDIO.appParts(STUDIO.ST.B).tip.choices.find(c => c.on).k) === "ruling", "a newer tip (Ruling Pen) chosen: its card is the app's");
  const ruled = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await ev(() => { const r = document.querySelector('.card[data-part="tip"] input[type=range]'); r.value = String((+r.min + +r.max) / 3); r.dispatchEvent(new Event("input", { bubbles: true })); r.dispatchEvent(new Event("change", { bubbles: true })); }); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== ruled && await rest(await ev(() => STUDIO.encodeM(STUDIO.ST.B)), "tip") === await rest(ruled, "tip"), "its dial edited through the app's model, the other parts left alone");
  await click('.card[data-part="tip"] [data-act="appchoice"][data-k="round"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.tip.k === "round" && !!document.querySelector('.card[data-part="tip"] [data-act="rollpart"]') && !STUDIO.appParts(STUDIO.ST.B).tip.held), "back to Round: the lab's card again");
  await ev(() => STUDIO.setChoice("medium", "ink")); await idle();
  await click('.card[data-part="medium"] [data-act="appopt"][data-key="screen"][data-v="15"]'); await idle();
  ok(await ev(() => STUDIO.appParts(STUDIO.ST.B).medium.dials.find(d => d.key === "screen").shown) === "Risograph" && await ev(() => !!document.querySelector('.card[data-part="medium"] [data-act="appopt"][data-v="16"]')), "a newer print (Risograph) chosen; Medium's card is the app's");
  const riso = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('[data-act="saveasnew"]'); await open(); await idle();
  ok(await ev(c => STUDIO.ST.lib.some(b => STUDIO.encodeM(b).replace(/^[^.]*/, "") === c.replace(/^[^.]*/, "")), riso) && await ev(c => { const A = STUDIO.appParts(STUDIO.readAnyCode(c).b); return A.later.name === "Cracks" && A.medium.held; }, riso),
    "saved, kept after the page opens again: the print and After (" + riso + ")");

  // ---------- without workers ----------
  await ev(() => STUDIO.setTab("lab"));
  await open("?inpage"); await idle();
  ok(await ev(() => STUDIO.POOL.inPage && STUDIO.POOL.workers.length === 0 && STUDIO.POOL.done > 10), "draws on the page without workers");
  ok(await ev(() => [...document.querySelectorAll("#sweep canvas")].every(c => c.width > 0)), "the sweep without workers");
  // ---------- the JS copy (a browser without WebAssembly GC; ?js forces it) ----------
  await open("?js"); await idle();
  ok(await ev(() => !STUDIO.wasm && !STUDIO.TP.page && STUDIO.POOL.done > 10 && [...STUDIO.POOL.cache.values()].every(c => !c.wasm)), "?js: the JS copy draws the pictures and the test page");

  // ---------- narrow screens ----------
  for (const w of [1280, 1600, 1024, 768, 390]) {
    await p.setViewport({ width: w, height: 900 }); await sleep(400);
    for (const t of ["community", "roll", "guide", "lab", "tune", "mix"]) {
      await ev(x => STUDIO.setTab(x), t); await sleep(150);
      const over = await ev(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      if (over > 0) ok(false, `${t} at ${w} px: ${over} px too wide`);
    }
    ok(true, "no sideways scroll at " + w + " px");
  }

  // ---------- the Community library on the website: the page served at http://studio.test/studio/ under
  // superdood.nginx's Content-Security-Policy for it, its /studio/api/ answered here (superdood.org's server code (not in this repo) is the real one) ----------
  const codes = await ev(() => [1, 2, 4, 5].map(i => STUDIO.encodeM(STUDIO.BASICS[i])));
  const now = Date.now(), XSS = "<img src=x onerror=__xss=1>", SITE = "http://studio.test/studio/";
  const CSP = (() => { try { return require("fs").readFileSync(path.join(__dirname, "..", "community-server", "superdood.nginx"), "utf8").match(/~\^\/studio\/\s+"([^"]+)"/)[1]; } catch (e) { return null; } })();  // superdood.nginx is not in the public repo
  const rows = [
    { id: "a1", code: codes[0], name: XSS, maker: "<b>Ann</b>", note: "Soft and wet.", at: now - 1000 },
    { id: "bad1", code: "<script>alert(1)</script>", name: "bad", maker: "", note: "", at: now - 2000 },
    { id: 7, code: codes[1], name: "no id", at: now - 3000 },
    { id: "b2", code: codes[1], name: "Old one", maker: "", note: "", at: now - 90000 }
  ];
  const older = [{ id: "c3", code: codes[2], name: "Older", maker: "Zed", note: "", at: now - 900000 }];
  const api = { gets: [], posts: [], next: null, other: [] };
  const studioHtml = require("fs").readFileSync(path.join(__dirname, "..", "studio.html"));
  const p2 = await b.newPage(), errs2 = [];
  // The refused share's 400 is expected; Chrome logs it.
  p2.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/status of 400/.test(m.text())) errs2.push(m.type() + ": " + m.text()); });
  p2.on("pageerror", e => errs2.push("pageerror: " + e.message));
  await p2.setRequestInterception(true);
  p2.on("request", r => {
    const u = r.url(), json = (status, o) => r.respond({ status, contentType: "application/json", body: JSON.stringify(o) });
    if (!u.startsWith("http://studio.test/")) { if (!/^(data|blob):/.test(u) && !/^https:\/\/fonts\.(googleapis|gstatic)\.com\//.test(u)) api.other.push(u); return r.continue(); }
    const q = new URL(u);
    if (q.pathname === "/studio/") return r.respond({ status: 200, contentType: "text/html", headers: CSP ? { "Content-Security-Policy": CSP } : {}, body: studioHtml });
    if (q.pathname === "/favicon.ico") return r.respond({ status: 204, body: "" });   // the browser's own
    if (q.pathname === "/studio/api/brushes" && r.method() === "GET") {
      api.gets.push(q.search);
      return q.searchParams.get("before") ? json(200, { brushes: older, more: false }) : json(200, { brushes: rows, more: true });
    }
    if (r.method() === "POST" && (q.pathname === "/studio/api/submit" || q.pathname === "/studio/api/report")) {
      const body = JSON.parse(r.postData() || "{}"); api.posts.push({ path: q.pathname.slice(12), body });
      if (q.pathname.endsWith("report")) return json(200, { ok: true });
      // As the server: a name a shared brush has, case aside, comes back with " 2".
      const name = rows.some(x => String(x.name).toLowerCase() === String(body.name).toLowerCase()) ? body.name + " 2" : body.name;
      const ans = api.next || [201, { status: "published", id: "new1", name }]; api.next = null;
      if (ans[0] === 201 && ans[1].status === "published") rows.unshift({ id: "new1", code: body.code, name, maker: body.maker, note: body.note, at: Date.now() });
      return json(...ans);
    }
    api.other.push(u); json(404, { error: "not found" });
  });
  await p2.setViewport({ width: 1440, height: 940 });
  const ev2 = (fn, ...a) => p2.evaluate(fn, ...a);
  const click2 = async sel => { await ev2(s => { const el = document.querySelector(s); if (!el) throw new Error("no " + s); el.click(); }, sel); await sleep(120); };
  const idle2 = () => p2.waitForFunction(() => STUDIO.POOL.q.length === 0 && STUDIO.POOL.flight.size === 0, { timeout: 30000 }).then(() => sleep(150));
  await p2.goto(SITE, { waitUntil: "load" });
  await p2.waitForFunction(() => document.documentElement.dataset.ready === "1" && STUDIO.COMM.state === "live", { timeout: 20000 });
  await ev2(() => STUDIO.setTab("community")); await sleep(100); await idle2();
  ok(await ev2(() => STUDIO.COMM.items.length === 2 && document.querySelectorAll("#sharedgrid article").length === 2), "site: the shared brushes listed, rows that don't read as a brush left out");
  ok(await ev2(x => !document.querySelector("#sharedgrid img, #sharedgrid b, #sharedgrid script") && window.__xss === undefined && document.querySelector("#sharedgrid .tname").textContent === x, XSS), "site: names shown as text, never as markup");
  ok(await ev2(() => [...document.querySelectorAll("#sharedgrid .who")].map(e => e.textContent).join("|") === "by <b>Ann</b>|by anonymous" && document.querySelector("#sharedgrid .tcap").textContent === "Soft and wet."), "site: by whom (anonymous without a name), and the note");
  ok(await ev2(() => document.getElementById("commtag").hidden && document.getElementById("commnote").textContent === "" && [...document.querySelectorAll("#sharedgrid canvas[data-cm]")].every(c => c.width > 0)), "site: no Examples tag; every shared brush's loop drawn");
  await click2('#sharedgrid [data-act="commmore"]'); await idle2();
  ok(api.gets.length === 2 && api.gets[1] === "?limit=48&before=" + (now - 90000) && await ev2(() => document.querySelectorAll("#sharedgrid article").length === 3 && !document.querySelector('#sharedgrid [data-act="commmore"]')), "site: Show More asks for older ones and adds them");
  await click2('#sharedgrid [data-act="commreport"][data-i="1"]');
  ok(api.posts.length === 1 && api.posts[0].path === "report" && api.posts[0].body.id === "b2" && await ev2(() => { const r = document.querySelector('#sharedgrid [data-act="commreport"][data-i="1"]'); return r.disabled && r.textContent === "Reported"; }), "site: Report sends the brush's id, then reads Reported");
  const tried = await ev2(() => STUDIO.COMM.shown[0].name);
  await click2('#sharedgrid [data-act="commtry"][data-i="0"]'); await idle2();
  ok(await ev2(n => STUDIO.ST.tab === "lab" && STUDIO.ST.B.name === n && STUDIO.encodeM(STUDIO.ST.B) === STUDIO.encodeM(Object.assign(STUDIO.readAnyCode(STUDIO.COMM.shown[0].code).b, { name: n })), tried), "site: Try makes it This brush, on Adjust");
  await ev2(() => STUDIO.randomize()); await idle2();
  await click2('#brushbar [data-act="share"]');
  ok(await ev2(() => { const b = document.getElementById("pubbtn"); return !b.disabled && !b.hidden && b.textContent === "Share to Community" && document.getElementById("pubconfirm").hidden; }), "site: Share to Community is live");
  await click2("#pubbtn");
  ok(await ev2(() => { const i = document.getElementById("pubname"); return i.value === STUDIO.ST.B.name && document.activeElement === i && i.selectionStart === 0 && i.selectionEnd === i.value.length && i.parentElement.textContent.startsWith("Name"); }), "site: Share asks for the name first: the brush's, selected to type over");
  ok(await ev2(() => !document.getElementById("pubconfirm").hidden && document.getElementById("pubbtn").hidden && document.getElementById("pubmaker").placeholder === "anonymous" && /appear right away and are free for anyone to use\. Names are checked\./.test(document.getElementById("pubconfirm").textContent)) && api.posts.length === 1, "site: Share opens the form (name optional, the small print), nothing sent yet");
  await click2('[data-act="pubno"]');
  ok(await ev2(() => document.getElementById("pubconfirm").hidden && !document.getElementById("pubbtn").hidden) && api.posts.length === 1, "site: Cancel sends nothing");
  api.next = [400, { error: "Please choose another name." }];
  await click2("#pubbtn"); await ev2(() => { document.getElementById("pubmaker").value = "bad word"; }); await click2('[data-act="pubyes"]');
  ok(await ev2(() => document.getElementById("pubnote").textContent === "Please choose another name." && !document.getElementById("pubconfirm").hidden), "site: a refused share says why, the form stays");
  // A name typed that a shared brush has ("Old one", case aside): the server numbers it and Studio says so.
  const pubCode = await ev2(() => STUDIO.encodeM(STUDIO.ST.B)), pubName = "old ONE", ownName = await ev2(() => STUDIO.ST.B.name);
  await ev2(n => { document.getElementById("pubname").value = "  " + n + " "; document.getElementById("pubmaker").value = "  Elling "; document.getElementById("pubtext").value = "Soft wash"; }, pubName); await click2('[data-act="pubyes"]'); await sleep(200);
  const sent = api.posts[api.posts.length - 1];
  ok(sent.path === "submit" && sent.body.code === pubCode && sent.body.name === pubName && sent.body.maker === "Elling" && sent.body.note === "Soft wash" && sent.body.website === "" && Object.keys(sent.body).sort().join() === "code,maker,name,note,website", "site: Share sends the code, name, who, note and the empty honeypot");
  ok(await ev2(() => { const b = document.getElementById("pubbtn"); return b.disabled && b.textContent === "Shared" && document.getElementById("pubconfirm").hidden && document.getElementById("status").textContent === "Shared as old ONE 2."; }), "site: then Shared, said so under the library's name (a taken one with \" 2\")");
  ok(await ev2(n => STUDIO.ST.B.name === n, ownName), "site: the brush keeps its own name here");
  await ev2(() => STUDIO.setTab("community")); await sleep(200);
  ok(await ev2(n => document.querySelector("#sharedgrid .tname").textContent === n && document.querySelector("#sharedgrid .who").textContent === "by Elling", pubName + " 2"), "site: the shared brush shows first, at once");
  await ev2(() => STUDIO.randomize()); await idle2(); api.next = [201, { status: "pending", id: "p1", name: "Pending One" }];
  await click2('#brushbar [data-act="share"]'); await click2('#brushbar [data-act="share"]'); await click2("#pubbtn"); await click2('[data-act="pubyes"]'); await sleep(200);
  ok(await ev2(() => document.getElementById("status").textContent === "Sent as Pending One. Waiting for review." && document.getElementById("pubbtn").textContent === "Shared"), "site: a share with flagged words says it waits for review");
  for (const w of [1440, 390]) {
    await p2.setViewport({ width: w, height: 900 }); await ev2(() => STUDIO.setTab("community")); await sleep(300);
    ok(await ev2(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth && (innerWidth < 1180 || document.documentElement.scrollHeight <= innerHeight + 1)), "site: the Community library fits at " + w + " px");
  }
  ok(!errs2.length, "site: no console errors" + (errs2.length ? ": " + errs2.slice(0, 5).join(" | ") : ""));
  ok(!api.other.length, "site: nothing loaded but the page, its /studio/api/ and the fonts" + (api.other.length ? ": " + api.other.join(", ") : ""));
  await p2.close();
  await idle();
  ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 5).join(" | ") : ""));
  ok(!net.length, "from a file: nothing loaded from the network but the fonts" + (net.length ? ": " + net.join(", ") : ""));
  await b.close();
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
