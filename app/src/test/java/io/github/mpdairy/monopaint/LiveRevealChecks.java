package io.github.mpdairy.monopaint;

import java.util.*;

/**
 * Live pages' probe, step 2c ({@link LiveReveal}): a stroke drawn left to right shows its left end first, and drawn back
 * its right end; each pixel near as the nearest sample is (against brute force); the slices hold the change's pixels,
 * each once; no path, one slice at once; and the time a 1000 × 1000 change with 500 samples takes.
 */
public final class LiveRevealChecks {
    public static void main(String[] args) {
        direction(); nearest(); slices(); noPath();
        long ms = timed();
        System.out.println("PASS: live reveal: drawn left to right, the left end shows first, drawn back the right; each pixel's sample as near as the nearest; slices hold each changed pixel once, over " + LiveReveal.SHORTEST + " to " + LiveReveal.LONGEST + " ms; no path, one slice at once; 1000×1000 with 500 samples in " + ms + " ms");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A line along y = 5 from x 10 to 90 in {@code samples} samples, {@code backward} from 90; 16 ms apart. */
    static LiveChange line(boolean backward, int samples) {
        int w = 100, h = 10;
        int[] greys = new int[w * h];
        Arrays.fill(greys, -1);
        for (int x = 10; x <= 90; x++) for (int y = 4; y <= 6; y++) greys[y * w + x] = 0;
        int[] path = new int[3 * samples];
        for (int s = 0; s < samples; s++) {
            int x = 10 + 80 * s / (samples - 1);
            path[3 * s] = 200 + (backward ? 100 - x : x); path[3 * s + 1] = 305; path[3 * s + 2] = 16 * s;
        }
        return new LiveChange(200, 300, w, h, greys, path);
    }

    static void direction() {
        for (boolean back : new boolean[]{false, true}) {
            int[] t = LiveReveal.times(line(back, 21));
            int left = t[5 * 100 + 10], right = t[5 * 100 + 90];
            check(back ? right < left : left < right, (back ? "back" : "forward") + ": left " + left + ", right " + right);
            check(t[0] == -1, "clear pixels: no time");
        }
    }

    static void nearest() {
        Random random = new Random(7);
        int w = 60, h = 40, n = 25;
        int[] greys = new int[w * h], path = new int[3 * n];
        for (int i = 0; i < greys.length; i++) greys[i] = random.nextInt(3) == 0 ? -1 : random.nextInt(255);
        for (int s = 0; s < n; s++) { path[3 * s] = 100 + random.nextInt(w + 20) - 10; path[3 * s + 1] = 50 + random.nextInt(h + 20) - 10; path[3 * s + 2] = 10 * s; }
        LiveChange c = new LiveChange(100, 50, w, h, greys, path);
        int[] t = LiveReveal.times(c);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (greys[i] < 0) { check(t[i] == -1, "clear"); continue; }
            double best = Double.MAX_VALUE, got = Double.MAX_VALUE;
            for (int s = 0; s < n; s++) {
                double d = Math.hypot(100 + x - path[3 * s], 50 + y - path[3 * s + 1]);
                best = Math.min(best, d);
                if (path[3 * s + 2] == t[i]) got = d;
            }
            check(got <= best + 1.5, "pixel " + x + "," + y + ": its sample " + got + " away, the nearest " + best);
        }
    }

    static void slices() {
        LiveChange c = line(false, 21);
        int[][] slices = LiveReveal.slices(c, 6);
        check(slices.length == 6, "six");
        for (int i = 0; i < c.greys.length; i++) {
            int in = 0, grey = -1;
            for (int[] s : slices) if (s[i] >= 0) { in++; grey = s[i]; }
            check(c.greys[i] < 0 ? in == 0 : in == 1 && grey == c.greys[i], "pixel " + i + " in " + in + " slices");
        }
        for (int k = 0; k < 6; k++) check(Arrays.stream(slices[k]).anyMatch(g -> g >= 0), "slice " + k + " holds some");
        check(Arrays.equals(LiveReveal.at(c, 6), new long[]{0, 64, 128, 192, 256, 320}), "320 ms of stroke over 320: " + Arrays.toString(LiveReveal.at(c, 6)));
        check(LiveReveal.at(line(false, 3), 6)[5] == LiveReveal.SHORTEST, "a quick stroke stretched to the shortest");
        check(LiveReveal.at(line(false, 81), 6)[5] == LiveReveal.LONGEST, "a slow one squeezed to the longest");
    }

    static void noPath() {
        LiveChange c = new LiveChange(0, 0, 2, 1, new int[]{-1, 4});
        int[][] slices = LiveReveal.slices(c, 6);
        check(slices.length == 1 && Arrays.equals(slices[0], c.greys) && slices[0] != c.greys, "one slice, the change");
        check(Arrays.equals(LiveReveal.at(c, 6), new long[]{0}), "at once");
    }

    /** The slowest of three runs of 1000 × 1000 with 500 samples, ms; the Manta is some ten times slower. */
    static long timed() {
        Random random = new Random(3);
        int[] greys = new int[1000 * 1000], path = new int[3 * 500];
        for (int i = 0; i < greys.length; i++) greys[i] = random.nextInt(2) == 0 ? -1 : 0;
        for (int s = 0; s < 500; s++) { path[3 * s] = 2 * s; path[3 * s + 1] = (int) (500 + 400 * Math.sin(s / 40.0)); path[3 * s + 2] = 4 * s; }
        LiveChange c = new LiveChange(0, 0, 1000, 1000, greys, path);
        long worst = 0;
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            LiveReveal.slices(c, 6);
            worst = Math.max(worst, (System.nanoTime() - start) / 1_000_000);
        }
        return worst;
    }
}
