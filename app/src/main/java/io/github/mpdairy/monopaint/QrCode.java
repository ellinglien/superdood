package io.github.mpdairy.monopaint;

import java.nio.charset.StandardCharsets;

/**
 * A QR code for Share on Wi-Fi's link ({@link LiveShare}), pure Java: byte mode, error correction L, versions 1 to 5
 * (one block each, so no interleaving), up to 106 bytes. Written from the standard (ISO/IEC 18004) as Nayuki's QR Code
 * generator lays it out; no code copied.
 * shortcut: mask 0 always, never the best-scoring of the eight (any mask scans; the e-ink screen is crisp). Upgrade if
 * a camera struggles with it.
 */
final class QrCode {
    private static final int[] DATA = {0, 19, 34, 55, 80, 108}, CHECK = {0, 7, 10, 15, 20, 26};
    private QrCode() {}

    /** {@code text}'s modules, [y][x], true dark (no quiet zone), or null when it is too long. */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int v = 1;
        while (v <= 5 && bytes.length > DATA[v] - 2) v++;
        if (v > 5) return null;
        byte[] data = new byte[DATA[v]];
        int[] at = {0};
        put(data, at, 4, 4); put(data, at, bytes.length, 8);
        for (byte b : bytes) put(data, at, b & 255, 8);
        put(data, at, 0, Math.min(4, data.length * 8 - at[0]));
        int n = (at[0] + 7) / 8;
        for (int i = n; i < data.length; i++) data[i] = (byte) ((i - n) % 2 == 0 ? 0xEC : 0x11);
        byte[] check = remainder(data, divisor(CHECK[v]));
        byte[] all = new byte[data.length + check.length];
        System.arraycopy(data, 0, all, 0, data.length); System.arraycopy(check, 0, all, data.length, check.length);

        int size = 17 + 4 * v;
        boolean[][] dark = new boolean[size][size], fixed = new boolean[size][size];
        for (int i = 0; i < size; i++) { set(dark, fixed, 6, i, i % 2 == 0); set(dark, fixed, i, 6, i % 2 == 0); }
        finder(dark, fixed, 3, 3); finder(dark, fixed, size - 4, 3); finder(dark, fixed, 3, size - 4);
        if (v >= 2) {
            int p = 4 * v + 10;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) set(dark, fixed, p + dx, p + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
        // Format: level L (01), mask 0, its BCH check, masked.
        int format = 1 << 3, r = format;
        for (int i = 0; i < 10; i++) r = (r << 1) ^ ((r >>> 9) * 0x537);
        int bits = (format << 10 | r) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(dark, fixed, 8, i, bit(bits, i));
        set(dark, fixed, 8, 7, bit(bits, 6)); set(dark, fixed, 8, 8, bit(bits, 7)); set(dark, fixed, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(dark, fixed, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(dark, fixed, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(dark, fixed, 8, size - 15 + i, bit(bits, i));
        set(dark, fixed, 8, size - 8, true);
        // The codewords up and down two columns at a time from the right, past the timing column; then mask 0.
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) for (int j = 0; j < 2; j++) {
                int x = right - j, y = ((right + 1) & 2) == 0 ? size - 1 - vert : vert;
                if (fixed[y][x]) continue;
                if (i < all.length * 8) { dark[y][x] = bit(all[i >>> 3], 7 - (i & 7)); i++; }
                if ((x + y) % 2 == 0) dark[y][x] = !dark[y][x];
            }
        }
        return dark;
    }

    private static void put(byte[] data, int[] at, int value, int count) {
        for (int i = count - 1; i >= 0; i--, at[0]++) if (((value >>> i) & 1) != 0) data[at[0] >>> 3] |= (byte) (0x80 >>> (at[0] & 7));
    }
    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }
    private static void set(boolean[][] dark, boolean[][] fixed, int x, int y, boolean on) { dark[y][x] = on; fixed[y][x] = true; }
    private static void finder(boolean[][] dark, boolean[][] fixed, int cx, int cy) {
        int size = dark.length;
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy));
            if (x >= 0 && x < size && y >= 0 && y < size) set(dark, fixed, x, y, d != 2 && d != 4);
        }
    }

    /** Reed-Solomon over GF(256), polynomial 0x11D: the generator of {@code degree}, highest term dropped. */
    private static byte[] divisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) { result[j] = (byte) times(result[j] & 255, root); if (j + 1 < degree) result[j] ^= result[j + 1]; }
            root = times(root, 2);
        }
        return result;
    }
    private static byte[] remainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 255;
            System.arraycopy(result, 1, result, 0, result.length - 1); result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) times(divisor[i] & 255, factor);
        }
        return result;
    }
    private static int times(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
        return z;
    }
}
