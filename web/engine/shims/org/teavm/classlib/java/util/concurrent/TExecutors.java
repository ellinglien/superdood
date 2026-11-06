package org.teavm.classlib.java.util.concurrent;

public final class TExecutors {
    private TExecutors() { }
    public static TExecutorService newFixedThreadPool(int n) { return new TThreadPoolExecutor(); }
    public static TExecutorService newFixedThreadPool(int n, TThreadFactory threads) { return new TThreadPoolExecutor(); }
    public static TExecutorService newSingleThreadExecutor() { return new TThreadPoolExecutor(); }
    public static TExecutorService newSingleThreadExecutor(TThreadFactory threads) { return new TThreadPoolExecutor(); }
}
