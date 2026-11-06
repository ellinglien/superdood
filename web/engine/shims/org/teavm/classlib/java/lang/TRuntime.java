package org.teavm.classlib.java.lang;

/**
 * TeaVM's own java.lang.Runtime (0.16.0's, as it is) with maxMemory, which ToneDocument and WatercolorSession size their
 * budgets by, and one processor: JS runs the engine on one thread, so Cores, WetWatercolor and the rest work in place.
 */
public class TRuntime {
    private static final TRuntime instance = new TRuntime();
    private static Version version;
    public void exit(int status) { }
    public long freeMemory() { return Integer.MAX_VALUE; }
    public void gc() { System.gc(); }
    public static TRuntime getRuntime() { return instance; }
    public long totalMemory() { return Integer.MAX_VALUE; }
    public long maxMemory() { return Integer.MAX_VALUE; }
    public int availableProcessors() { return 1; }
    public static Version version() { if (version == null) version = new Version(); return version; }
    public static final class Version implements Comparable<Version> {
        private Version() { }
        public int feature() { return 21; }
        @Override public int compareTo(Version o) { return 0; }
    }
}
