// Live pages' sealing for the checks, on Node's own AES-256-GCM: LiveCrypt.java's format (nonce, ciphertext, tag; the
// keys SHA-256 of the link's key, stretched, with their prefixes) made by a second implementation the app's is held to.
"use strict";
const crypto = require("crypto");
const sha = s => crypto.createHash("sha256").update(s).digest();
// The key stretched first (LiveCrypt.stretch), once a key.
const stretched = new Map(), str = key => { if (!stretched.has(key)) { let h = sha("MBD live stretch\n" + key); for (let i = 0; i < 1 << 16; i++) h = sha(h); stretched.set(key, h.toString("hex")); } return stretched.get(key); };
const access = key => sha("MBD live access\n" + str(key)).toString("hex");
function seal(key, raw) {
  const nonce = crypto.randomBytes(12), c = crypto.createCipheriv("aes-256-gcm", sha("MBD live key\n" + str(key)), nonce);
  return Buffer.concat([nonce, c.update(raw), c.final(), c.getAuthTag()]);
}
// Throws unless sealed with the key.
function open(key, s) {
  s = Buffer.from(s);
  const d = crypto.createDecipheriv("aes-256-gcm", sha("MBD live key\n" + str(key)), s.subarray(0, 12));
  d.setAuthTag(s.subarray(s.length - 16));
  return Buffer.concat([d.update(s.subarray(12, s.length - 16)), d.final()]);
}
// A host's id (LiveCrypt.hostId, LivePresence.ID): 8 random hex digits, then 8 of a hash of them keyed with the key.
function hostId(key, random = crypto.randomBytes(4).toString("hex")) {
  const mark = sha(Buffer.concat([Buffer.from("MBD live host\n"), sha("MBD live key\n" + str(key))]));
  return random + crypto.createHash("sha256").update(Buffer.concat([mark, Buffer.from(random)])).digest("hex").slice(0, 8);
}
// A /books route's query, sealed as books.js asks it (q=, base64url).
const query = (key, q) => seal(key, Buffer.from(q)).toString("base64url");
module.exports = { access, seal, open, query, hostId };
