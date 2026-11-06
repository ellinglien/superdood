// Live pages in the browser, the parts every page shares (the probe, probe.html, and the guest's drawing screen,
// live.html): a change's bytes as LiveChange.java writes them, a host's tools told apart (LiveTools.java), Undo of your
// own changes (as the Manta's live Undo, ToneDocument.takeBack), the pixels a stroke changed, and a change drawn on along
// its stroke (LiveReveal.java). Pure; a classic script: Live.
"use strict";
(function (G) {
  const MAXN = 20000, WORK = 1e7, MOST_OWN = 10, SLICES = 6, SHORTEST = 200, LONGEST = 600;  // the last three as LiveProbe and LiveReveal
  // raw = x,y,w,h, w*h greys, then n and n samples of x,y,ms (uint16 each, big-endian); path is a flat [x,y,ms,...];
  // then, only when one is, a byte of flags: 1, an Undo's change, its greys set, lighter too (LiveChange.set); 2, a later
  // part of a stroke sent while it is drawn, one Undo step with the part before (LiveChange.joins); kinds << 2, the
  // marks' kind (KIND_ bits: Pen 1, Paint 2, Dry Media 4), laid on its plane so Show Kinds fades them (LiveChange.kinds).
  function encode(x, y, w, h, greys, path, set, joins, kinds) {
    let k = 1; while (path.length / 3 / k > MAXN) k++;
    const flags = (set ? 1 : 0) | (joins ? 2 : 0) | (kinds & 7) << 2;
    const n = Math.ceil(path.length / 3 / k), raw = new Uint8Array(8 + w * h + 2 + 6 * n + (flags ? 1 : 0)), v = new DataView(raw.buffer);
    if (flags) raw[raw.length - 1] = flags;
    [x, y, w, h].forEach((a, i) => v.setUint16(2 * i, a)); raw.set(greys, 8);
    v.setUint16(8 + w * h, n);
    for (let i = 0; i < n; i++) for (let j = 0; j < 3; j++) v.setUint16(10 + w * h + 6 * i + 2 * j, Math.max(0, Math.min(65535, Math.round(path[3 * i * k + j]))));
    return raw;
  }
  function parse(raw) {
    const v = new DataView(raw.buffer, raw.byteOffset, raw.length), x = v.getUint16(0), y = v.getUint16(2), w = v.getUint16(4), h = v.getUint16(6), g = 8 + w * h;
    if (raw.length < g) throw new Error("cut short");
    const n = raw.length === g ? 0 : v.getUint16(g), end = g + 2 + 6 * n, flags = raw.length === end + 1 ? raw[end] : 0;
    if (n > MAXN || (raw.length > g && raw.length !== end && !(flags >= 1 && flags <= 31))) throw new Error("bad path");
    const path = []; for (let i = 0; i < 3 * n; i++) path.push(v.getUint16(g + 2 + 2 * i));
    return { x, y, w, h, greys: raw.subarray(8, g), path, set: !!(flags & 1), joins: !!(flags & 2), kinds: flags >> 2 };
  }
  // A host's tools (LiveTools.java), not a change: the first eight bytes zero (no change is 0×0).
  const isTools = raw => raw.length >= 8 && raw.subarray(0, 8).every(b => b === 0);
  // Which notebook page the room draws on (LivePage.java), not a change: the tools' eight zero bytes, then its lines.
  // page(raw): {kind, place, previous, next, words, said, path}, or null; pageAsk(kind, n, path): a guest's ask, raw.
  const PAGE = "MBD page 1";
  function page(raw) {
    if (!isTools(raw)) return null;
    const l = new TextDecoder().decode(raw.subarray(8)).split("\n");
    return l[0] !== PAGE || l.length < 8 ? null : { kind: l[1], place: +l[2], previous: l[3] === "1", next: l[4] === "1", words: l[5], said: l[6], path: l[7] };
  }
  function pageAsk(kind, n, path) {
    const t = new TextEncoder().encode([PAGE, kind, n, 0, 0, "", "", path.replace(/\n/g, " "), ""].join("\n")), raw = new Uint8Array(8 + t.length);
    raw.set(t, 8);
    return raw;
  }
  // Undo of an own stroke {before, after} (page greys over its rect), or Redo of an own Undo: each pixel back to before
  // only where the page still shows after; the greys to send (white as 254, 255 clear), the page's new greys in place of now.
  function takeBack(own, now) {
    const greys = new Uint8Array(now.length).fill(255);
    for (let i = 0; i < now.length; i++) if (now[i] === own.after[i] && own.before[i] !== own.after[i]) { now[i] = own.before[i]; greys[i] = Math.min(254, own.before[i]); }
    return greys;
  }
  // Per changed pixel the ms of the nearest sample (earlier on ties), -1 for clear pixels; a long path is thinned to stay under WORK.
  function times(c) {
    const { x, y, w, greys } = c, t = new Int32Array(greys.length).fill(-1); let p = c.path, live = 0;
    for (const g of greys) if (g !== 255) live++;
    const k = Math.max(1, Math.ceil(live * p.length / 3 / WORK)), s = [];
    for (let i = 0; i < p.length; i += 3 * k) s.push(p[i], p[i + 1], p[i + 2]);
    for (let i = 0; i < greys.length; i++) {
      if (greys[i] === 255) continue;
      const px = x + i % w, py = y + (i / w | 0); let best = Infinity;
      for (let j = 0; j < s.length; j += 3) { const d = (s[j] - px) ** 2 + (s[j + 1] - py) ** 2; if (d < best) { best = d; t[i] = s[j + 2]; } }
    }
    return t;
  }
  // A change drawn on as the Manta does (LiveReveal): its changed pixels in SLICES by their nearest sample's time, shown
  // evenly over the stroke's time made SHORTEST to LONGEST ms (over). order: the changed pixels, slice by slice; slice k
  // is order[ends[k]] to order[ends[k + 1]]; n slices (one with no path).
  function reveal(c) {
    const { greys } = c;
    let count = 0; for (let i = 0; i < greys.length; i++) if (greys[i] !== 255) count++;
    const t = c.path.length ? times(c) : null, dur = t ? t.reduce((a, b) => Math.max(a, b), 0) : 0, n = t ? SLICES : 1;
    const slice = i => !t || dur === 0 ? 0 : Math.min(n - 1, Math.floor(t[i] * n / dur)), ends = new Int32Array(n + 1), order = new Int32Array(count);
    for (let i = 0; i < greys.length; i++) if (greys[i] !== 255) ends[slice(i) + 1]++;
    for (let k = 0; k < n; k++) ends[k + 1] += ends[k];
    const at = ends.slice(0, n);
    for (let i = 0; i < greys.length; i++) if (greys[i] !== 255) order[at[slice(i)]++] = i;
    return { count, n, ends, order, over: Math.min(LONGEST, Math.max(SHORTEST, dur)) };
  }
  // What a stroke changed over the rect x, y, w, h of a page W wide: where the engine's tones now (now[j]) differ from
  // those it found (was[j]), each in the 16 greys shown, never lighter than before (the greys the page showed over the
  // rect) where the engine darkened (rounding a tone someone else sent); a pixel shown as it was is left clear. Showing the
  // whole box again turned others' whites (254) and tones into the 16 greys, lighter, so every stroke went as set and
  // wiped what others drew there. greys: 255 where nothing changed; lighter: one goes as set (a lift, a smudge, the eraser).
  function changed(x, y, w, h, W, now, was, before) {
    const greys = new Uint8Array(w * h).fill(255);
    let lighter = false, any = false;
    for (let i = 0; i < greys.length; i++) {
      const j = (y + (i / w | 0)) * W + x + i % w;
      if (now[j] === was[j]) continue;
      let v = Math.min(254, Math.round(now[j] / 17) * 17);
      if (now[j] < was[j]) v = Math.min(v, before[i]);
      if (v === before[i]) continue;
      lighter ||= v > before[i]; greys[i] = v; any = true;
    }
    return { greys, lighter, any };
  }
  const deflate = async raw => new Uint8Array(await new Response(new Blob([raw]).stream().pipeThrough(new CompressionStream("deflate"))).arrayBuffer());
  const inflate = async bytes => new Uint8Array(await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate"))).arrayBuffer());
  // Files dropped on {@code target} (a desktop's) while {@code when()}: {@code take(files, text, event)}, text the words a
  // drop of plain words carries (none with files); the browser never opens them instead. While something it {@code takes}
  // (a drag's item: a file unless said) is dragged over it, a dashed outline over it with the note {@code hint}.
  const dropFiles = (target, when, take, hint, takes = i => i.kind === "file") => {
    let shown = null;
    const off = () => { if (shown) shown.remove(); shown = null; };
    const ok = e => when() && [...e.dataTransfer.items].some(takes);
    target.addEventListener("dragover", e => {
      if (!ok(e)) return off();
      e.preventDefault();
      if (!shown) { shown = document.createElement("div"); shown.className = "drophint"; shown.appendChild(document.createElement("span")).textContent = hint; document.body.appendChild(shown); }
      const r = target.getBoundingClientRect();
      Object.assign(shown.style, { left: r.left + "px", top: r.top + "px", width: r.width + "px", height: r.height + "px" });
    });
    target.addEventListener("dragleave", e => { if (!target.contains(e.relatedTarget)) off(); });
    target.addEventListener("drop", e => {
      off();
      if (!ok(e)) return;
      const files = [...e.dataTransfer.files], text = files.length ? "" : e.dataTransfer.getData("text/plain");
      if (!files.length && !text) return;
      e.preventDefault(); take(files, text, e);
    });
  };
  const api = { dropFiles, MAXN, WORK, MOST_OWN, SLICES, SHORTEST, LONGEST, encode, parse, isTools, page, pageAsk, takeBack, times, reveal, changed, deflate, inflate };
  G.Live = api;
  if (typeof module === "object" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : self);
