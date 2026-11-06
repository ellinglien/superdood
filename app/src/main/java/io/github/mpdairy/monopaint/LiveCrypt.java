package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Live pages sealed with the link's key, the part after its # (docs/superpowers/specs/2026-11-01-manta-host-design.md,
 * "Encryption"): AES-256-GCM, a fresh random 12-byte nonce each message, no associated data; sealed is nonce, ciphertext,
 * 16-byte tag. The relay sees only {@link #access}, a hash of the key (domain-separated from the cipher's key, so it
 * doesn't give it away), and passes the sealed bytes on unread. Pure Java with no javax.crypto, so the browser runs the
 * same code (WebEngine, compiled to WebAssembly: plain http on a LAN address has no WebCrypto). Thread-safe: the tables
 * are made once and only read. LiveCryptChecks holds it to FIPS-197, the GCM spec's vectors and javax.crypto.
 */
final class LiveCrypt {
    static final int NONCE = 12, TAG = 16;
    /**
     * Rounds of SHA-256 stretching the key before anything is made of it: a 10-letter key (about 50 bits) then costs a
     * guesser on a GPU centuries, offline, from the access hash a relay or the Wi-Fi sees; about a fifth of a second here.
     */
    static final int ROUNDS = 1 << 16;
    /** The key as a relay may see it: hex SHA-256 of "MBD live access\n" and the stretched key ({@link #stretch}). */
    final String access;
    private final int[] w = new int[60];
    private final long[] hh = new long[16], hl = new long[16];
    /** The key a host's id is marked with ({@link #hostId}): SHA-256 of "MBD live host\n" and the cipher's key. */
    private final byte[] mark;

    LiveCrypt(String key) { this(stretch(key), true); }
    private LiveCrypt(String s, boolean stretched) {
        this(hex(sha256(("MBD live access\n" + s).getBytes(StandardCharsets.UTF_8))), sha256(("MBD live key\n" + s).getBytes(StandardCharsets.UTF_8)));
    }
    /** The key, stretched: SHA-256 of "MBD live stretch\n" and the key, then {@link #ROUNDS} more of the 32 bytes, in hex. */
    static String stretch(String key) {
        byte[] h = sha256(("MBD live stretch\n" + key).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < ROUNDS; i++) h = sha256(h);
        return hex(h);
    }
    /** With the cipher's 32-byte key given whole (the checks' vectors). */
    LiveCrypt(String access, byte[] k) {
        this.access = access;
        byte[] m = "MBD live host\n".getBytes(StandardCharsets.UTF_8), mk = new byte[m.length + 32];
        System.arraycopy(m, 0, mk, 0, m.length); System.arraycopy(k, 0, mk, m.length, 32);
        mark = sha256(mk);
        for (int i = 0; i < 8; i++) w[i] = word(k, 4 * i);
        for (int i = 8, rcon = 1; i < 60; i++) {
            int t = w[i - 1];
            if (i % 8 == 0) { t = sub(t << 8 | t >>> 24) ^ rcon << 24; rcon = xtime(rcon); }
            else if (i % 8 == 4) t = sub(t);
            w[i] = w[i - 8] ^ t;
        }
        int[] h = new int[4];
        block(h);  // H = E(K, 0)
        long vh = (long) h[0] << 32 | h[1] & 0xffffffffL, vl = (long) h[2] << 32 | h[3] & 0xffffffffL;
        // GHASH's 4-bit tables (Shoup's, as mbedTLS lays them out).
        hh[8] = vh; hl[8] = vl;
        for (int i = 4; i > 0; i >>= 1) {
            long t = (vl & 1) != 0 ? 0xe1000000L << 32 : 0;
            vl = vh << 63 | vl >>> 1; vh = vh >>> 1 ^ t;
            hh[i] = vh; hl[i] = vl;
        }
        for (int i = 2; i <= 8; i *= 2) for (int j = 1; j < i; j++) { hh[i + j] = hh[i] ^ hh[j]; hl[i + j] = hl[i] ^ hl[j]; }
    }

    /**
     * A host's id on the relays ({@link LivePresence#ID}): {@code random}, 8 hex digits, then 8 of a hash of it keyed
     * with this key. A relay sees 16 hex digits as a guest's (all random), so it can't tell who is the host; those
     * with the key can ({@link #isHost}).
     */
    String hostId(String random) {
        byte[] r = random.getBytes(StandardCharsets.US_ASCII), m = new byte[32 + r.length];
        System.arraycopy(mark, 0, m, 0, 32); System.arraycopy(r, 0, m, 32, r.length);
        return random + hex(sha256(m)).substring(0, 8);
    }
    /** A new host id, its random part from SecureRandom (the Manta's, fresh each session). */
    String hostId() { byte[] r = new byte[4]; Random.ONE.nextBytes(r); return hostId(hex(r)); }
    /** Whether {@code id} is a host's id marked with this key (a guest's random one is, by chance, once in 2^32). */
    boolean isHost(String id) { return id.matches(LivePresence.ID) && hostId(id.substring(0, 8)).equals(id); }

    /** {@code plain} sealed under a nonce from SecureRandom (the JVM, the Manta; the browser passes its own). */
    byte[] seal(byte[] plain) {
        byte[] nonce = new byte[NONCE];
        Random.ONE.nextBytes(nonce);
        return seal(nonce, plain);
    }
    private static final class Random { static final SecureRandom ONE = new SecureRandom(); }

    /** {@code plain} sealed under {@code nonce} (12 bytes, never used before with this key): nonce, ciphertext, tag. */
    byte[] seal(byte[] nonce, byte[] plain) {
        if (nonce.length != NONCE) throw new IllegalArgumentException("a nonce is 12 bytes");
        byte[] out = new byte[NONCE + plain.length + TAG];
        System.arraycopy(nonce, 0, out, 0, NONCE);
        ctr(nonce, plain, 0, out, NONCE, plain.length);
        tag(out, plain.length, out, NONCE + plain.length);
        return out;
    }

    /** What {@link #seal} sealed with this key; throws when it wasn't (another key, cut short, any bit changed). */
    byte[] open(byte[] sealed) throws IOException {
        int n = sealed.length - NONCE - TAG;
        if (n < 0) throw new IOException("Not sealed");
        byte[] want = new byte[TAG];
        tag(sealed, n, want, 0);
        int diff = 0;
        for (int i = 0; i < TAG; i++) diff |= want[i] ^ sealed[NONCE + n + i];
        if (diff != 0) throw new IOException("Not sealed with this key");
        byte[] plain = new byte[n], nonce = new byte[NONCE];
        System.arraycopy(sealed, 0, nonce, 0, NONCE);
        ctr(nonce, sealed, NONCE, plain, 0, n);
        return plain;
    }

    /** GHASH over the n ciphertext bytes after the nonce in {@code s}, with E(K, J0): the tag, into out at at. */
    private void tag(byte[] s, int n, byte[] out, int at) {
        long[] y = new long[2];
        for (int i = 0; i < n; i += 16) {
            long a = 0, b = 0;
            for (int j = 0; j < 16; j++) {
                long v = i + j < n ? s[NONCE + i + j] & 0xff : 0;
                if (j < 8) a |= v << (56 - 8 * j); else b |= v << (120 - 8 * j);
            }
            y[0] ^= a; y[1] ^= b; mult(y);
        }
        y[1] ^= (long) n * 8; mult(y);  // the lengths: no associated data, then the ciphertext's bits
        int[] j0 = {word(s, 0), word(s, 4), word(s, 8), 1};
        block(j0);
        put((int) (y[0] >>> 32) ^ j0[0], out, at); put((int) y[0] ^ j0[1], out, at + 4);
        put((int) (y[1] >>> 32) ^ j0[2], out, at + 8); put((int) y[1] ^ j0[3], out, at + 12);
    }

    /** y = y·H in GF(2^128). */
    private void mult(long[] y) {
        long xh = y[0], xl = y[1], zh = 0, zl = 0;
        // Nibbles from the last byte's low one to the first byte's high one; z shifts by 4 before each but the first.
        for (int k = 0; k < 32; k++) {
            int i = 15 - (k >> 1), b = (int) ((i >= 8 ? xl >>> ((15 - i) << 3) : xh >>> ((7 - i) << 3)) & 0xff), nib = (k & 1) == 0 ? b & 15 : b >>> 4;
            if (k > 0) {
                int rem = (int) zl & 15;
                zl = zh << 60 | zl >>> 4;
                zh = zh >>> 4 ^ LAST4[rem] << 48;
            }
            zh ^= hh[nib]; zl ^= hl[nib];
        }
        y[0] = zh; y[1] = zl;
    }
    private static final long[] LAST4 = {0x0000, 0x1c20, 0x3840, 0x2460, 0x7080, 0x6ca0, 0x48c0, 0x54e0, 0xe100, 0xfd20, 0xd940, 0xc560, 0x9180, 0x8da0, 0xa9c0, 0xb5e0};

    /** CTR from counter 2 (J0 + 1): n bytes of in from i, XORed with the key stream, into out from o. */
    private void ctr(byte[] nonce, byte[] in, int i, byte[] out, int o, int n) {
        int a = word(nonce, 0), b = word(nonce, 4), c = word(nonce, 8);
        int[] s = new int[4];
        for (int k = 0, count = 2; k < n; k += 16, count++) {
            s[0] = a; s[1] = b; s[2] = c; s[3] = count;
            block(s);
            for (int j = 0; j < 16 && k + j < n; j++) out[o + k + j] = (byte) (in[i + k + j] ^ s[j >> 2] >>> (24 - 8 * (j & 3)));
        }
    }

    /** One AES-256 block encrypted in place, as four big-endian words. */
    private void block(int[] s) {
        int s0 = s[0] ^ w[0], s1 = s[1] ^ w[1], s2 = s[2] ^ w[2], s3 = s[3] ^ w[3];
        for (int r = 1; r < 14; r++) {
            int t0 = T0[s0 >>> 24] ^ T1[s1 >>> 16 & 255] ^ T2[s2 >>> 8 & 255] ^ T3[s3 & 255] ^ w[4 * r];
            int t1 = T0[s1 >>> 24] ^ T1[s2 >>> 16 & 255] ^ T2[s3 >>> 8 & 255] ^ T3[s0 & 255] ^ w[4 * r + 1];
            int t2 = T0[s2 >>> 24] ^ T1[s3 >>> 16 & 255] ^ T2[s0 >>> 8 & 255] ^ T3[s1 & 255] ^ w[4 * r + 2];
            int t3 = T0[s3 >>> 24] ^ T1[s0 >>> 16 & 255] ^ T2[s1 >>> 8 & 255] ^ T3[s2 & 255] ^ w[4 * r + 3];
            s0 = t0; s1 = t1; s2 = t2; s3 = t3;
        }
        s[0] = last(s0, s1, s2, s3) ^ w[56]; s[1] = last(s1, s2, s3, s0) ^ w[57];
        s[2] = last(s2, s3, s0, s1) ^ w[58]; s[3] = last(s3, s0, s1, s2) ^ w[59];
    }
    private static int last(int a, int b, int c, int d) { return S[a >>> 24] << 24 | S[b >>> 16 & 255] << 16 | S[c >>> 8 & 255] << 8 | S[d & 255]; }
    private static int sub(int t) { return last(t, t, t, t); }
    private static int xtime(int b) { return (b << 1 ^ ((b & 0x80) != 0 ? 0x1b : 0)) & 0xff; }

    // The S-box from the field's inverses (FIPS-197 5.1.1), and the round tables from it.
    private static final int[] S = new int[256], T0 = new int[256], T1 = new int[256], T2 = new int[256], T3 = new int[256];
    static {
        int p = 1, q = 1;
        do {
            p = p ^ xtime(p);  // p times 3
            q ^= q << 1; q ^= q << 2; q ^= q << 4; q &= 0xff; if ((q & 0x80) != 0) q ^= 0x09;  // q divided by 3
            int x = q ^ (q << 1 | q >>> 7) ^ (q << 2 | q >>> 6) ^ (q << 3 | q >>> 5) ^ (q << 4 | q >>> 4);
            S[p] = (x ^ 0x63) & 0xff;
        } while (p != 1);
        S[0] = 0x63;
        for (int i = 0; i < 256; i++) {
            int v = S[i], t = xtime(v) << 24 | v << 16 | v << 8 | (xtime(v) ^ v);
            T0[i] = t; T1[i] = t >>> 8 | t << 24; T2[i] = t >>> 16 | t << 16; T3[i] = t >>> 24 | t << 8;
        }
    }

    /** SHA-256 (FIPS 180-4), for the keys: short messages only. */
    static byte[] sha256(byte[] m) {
        int[] h = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
        int blocks = (m.length + 9 + 63) / 64;
        byte[] b = new byte[blocks * 64];
        System.arraycopy(m, 0, b, 0, m.length);
        b[m.length] = (byte) 0x80;
        long bits = (long) m.length * 8;
        for (int i = 0; i < 8; i++) b[b.length - 1 - i] = (byte) (bits >>> 8 * i);
        int[] x = new int[64];
        for (int k = 0; k < blocks; k++) {
            for (int i = 0; i < 16; i++) x[i] = word(b, 64 * k + 4 * i);
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(x[i - 15], 7) ^ Integer.rotateRight(x[i - 15], 18) ^ x[i - 15] >>> 3;
                int s1 = Integer.rotateRight(x[i - 2], 17) ^ Integer.rotateRight(x[i - 2], 19) ^ x[i - 2] >>> 10;
                x[i] = x[i - 16] + s0 + x[i - 7] + s1;
            }
            int a = h[0], bb = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
            for (int i = 0; i < 64; i++) {
                int t1 = hh + (Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25)) + (e & f ^ ~e & g) + K[i] + x[i];
                int t2 = (Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22)) + (a & bb ^ a & c ^ bb & c);
                hh = g; g = f; f = e; e = d + t1; d = c; c = bb; bb = a; a = t1 + t2;
            }
            h[0] += a; h[1] += bb; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) put(h[i], out, 4 * i);
        return out;
    }
    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    private static int word(byte[] b, int i) { return (b[i] & 0xff) << 24 | (b[i + 1] & 0xff) << 16 | (b[i + 2] & 0xff) << 8 | b[i + 3] & 0xff; }
    private static void put(int v, byte[] b, int i) { b[i] = (byte) (v >>> 24); b[i + 1] = (byte) (v >>> 16); b[i + 2] = (byte) (v >>> 8); b[i + 3] = (byte) v; }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(Character.forDigit(x >> 4 & 15, 16)).append(Character.forDigit(x & 15, 16)); return s.toString(); }
}
