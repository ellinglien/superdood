package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Live pages' sealing ({@link LiveCrypt}) against the GCM spec's AES-256 test cases 13 to 15
 * (McGrew and Viega, no associated data), FIPS 180-4's SHA-256 vectors and javax.crypto's AES/GCM over many lengths;
 * a changed bit, another key or a cut-short message refused; nonces never the same twice. Prints how long a 4 MB
 * change takes to seal and to open on this JVM.
 */
final class LiveCryptChecks {
    public static void main(String[] args) throws Exception {
        check(hex(LiveCrypt.sha256(new byte[0])).equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), "SHA-256 of nothing");
        check(hex(LiveCrypt.sha256("abc".getBytes(StandardCharsets.US_ASCII))).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA-256 of abc");
        check(hex(LiveCrypt.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes(StandardCharsets.US_ASCII))).equals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"), "SHA-256 over two blocks");

        // The GCM spec's AES-256 cases through a cipher keyed directly (the vectors' keys aren't a link's).
        String zero = "0000000000000000000000000000000000000000000000000000000000000000", k15 = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308";
        check(gcm(zero, "000000000000000000000000", "").equals("530f8afbc74536b9a963b4f1c4cb738b"), "GCM test case 13");
        check(gcm(zero, "000000000000000000000000", "00000000000000000000000000000000").equals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919"), "GCM test case 14");
        check(gcm(k15, "cafebabefacedbaddecaf888", "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255")
                .equals("522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" + "b094dac5d93471bdec1a502270e3cc6c"), "GCM test case 15");

        // A link's key: stretched (java.security's SHA-256 agrees), then its access and cipher key are SHA-256 of that
        // with their own prefixes; javax.crypto agrees on every length.
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest("MBD live stretch\nk7qmw2xr".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < LiveCrypt.ROUNDS; i++) h = md.digest(h);
        String stretched = hex(h);
        long t0 = System.nanoTime();
        LiveCrypt c = new LiveCrypt("k7qmw2xr");
        System.out.println("  a key stretched in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        check(LiveCrypt.stretch("k7qmw2xr").equals(stretched) && LiveCrypt.ROUNDS >= 1 << 16, "the key stretched: 65536 rounds of SHA-256");
        check(c.access.equals(hex(LiveCrypt.sha256(("MBD live access\n" + stretched).getBytes(StandardCharsets.UTF_8)))) && c.access.length() == 64 && !c.access.contains("k7qmw2xr"), "the access: a hash of the stretched key");
        byte[] key = LiveCrypt.sha256(("MBD live key\n" + stretched).getBytes(StandardCharsets.UTF_8));
        // A host's id (LivePresence.ID): its random half, then a hash of it keyed with the cipher's key, by the JDK's SHA-256.
        java.security.MessageDigest mark = java.security.MessageDigest.getInstance("SHA-256");
        mark.update("MBD live host\n".getBytes(StandardCharsets.UTF_8)); mark.update(key);
        java.security.MessageDigest id = java.security.MessageDigest.getInstance("SHA-256");
        id.update(mark.digest()); id.update("0a1b2c3d".getBytes(StandardCharsets.US_ASCII));
        check(c.hostId("0a1b2c3d").equals("0a1b2c3d" + hex(id.digest()).substring(0, 8)) && !c.hostId("0a1b2c3d").equals(new LiveCrypt("k7qmw2xs").hostId("0a1b2c3d")),
                "a host's id: marked with the key, by the JDK's SHA-256; another key's differs");
        Random r = new Random(7);
        for (int n = 0; n <= 300; n++) {
            byte[] plain = new byte[n], nonce = new byte[12];
            r.nextBytes(plain); r.nextBytes(nonce);
            byte[] ours = c.seal(nonce, plain), theirs = javax(key, nonce, plain);
            check(Arrays.equals(Arrays.copyOfRange(ours, 12, ours.length), theirs) && Arrays.equals(Arrays.copyOf(ours, 12), nonce), "javax.crypto's AES/GCM bytes, " + n + " bytes");
            check(Arrays.equals(c.open(ours), plain), "opened again, " + n + " bytes");
        }
        byte[] big = new byte[4 << 20], nonce = new byte[12];
        r.nextBytes(big); r.nextBytes(nonce);
        byte[] sealed = c.seal(nonce, big);
        check(Arrays.equals(Arrays.copyOfRange(sealed, 12, sealed.length), javax(key, nonce, big)), "javax.crypto's bytes, 4 MB");

        // Fail closed: any bit changed, another key, cut short.
        byte[] small = c.seal("a change".getBytes(StandardCharsets.UTF_8));
        for (int bit = 0; bit < small.length * 8; bit++) {
            byte[] bad = small.clone(); bad[bit / 8] ^= 1 << bit % 8;
            check(refused(c, bad), "a changed bit refused: " + bit);
        }
        check(refused(new LiveCrypt("k7qmw2xs"), small) && refused(new LiveCrypt(""), small), "another key refused");
        check(refused(c, Arrays.copyOf(small, small.length - 1)) && refused(c, new byte[27]) && refused(c, new byte[0]), "cut short refused");
        check(Arrays.equals(c.open(c.seal(new byte[0])), new byte[0]), "nothing sealed opens as nothing");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(hex(Arrays.copyOf(c.seal(new byte[1]), 12)));
        check(seen.size() == 1000, "a new nonce each time");

        long sealMs = Long.MAX_VALUE, openMs = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long t = System.nanoTime(); sealed = c.seal(big); sealMs = Math.min(sealMs, (System.nanoTime() - t) / 1000000);
            t = System.nanoTime(); c.open(sealed); openMs = Math.min(openMs, (System.nanoTime() - t) / 1000000);
        }
        System.out.println("PASS: live sealing: SHA-256 (FIPS 180-4), AES-256-GCM test cases 13 to 15, javax.crypto's AES/GCM over 0 to 300 bytes and 4 MB; a changed bit, another key, cut short refused; a new nonce each time; a host's id marked with the key. 4 MB sealed in " + sealMs + " ms, opened in " + openMs + " ms on this JVM");
    }

    static String gcm(String key, String nonce, String plain) { return hex(Arrays.copyOfRange(new LiveCrypt("", unhex(key)).seal(unhex(nonce), unhex(plain)), 12, 12 + plain.length() / 2 + 16)); }
    static byte[] javax(byte[] key, byte[] nonce, byte[] plain) throws Exception {
        Cipher g = Cipher.getInstance("AES/GCM/NoPadding");
        g.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return g.doFinal(plain);
    }
    static boolean refused(LiveCrypt c, byte[] sealed) { try { c.open(sealed); return false; } catch (IOException e) { return true; } }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    static byte[] unhex(String s) { byte[] b = new byte[s.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16); return b; }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: live sealing: " + what); }
}
