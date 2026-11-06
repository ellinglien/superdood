package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;

/** java.util.concurrent.ExecutorService, as much as the engine uses: work runs at once, on the caller's (the one) thread. */
public interface TExecutorService extends java.util.concurrent.Executor {
    <T> TFuture<T> submit(Callable<T> task);
    TFuture<?> submit(Runnable task);
    <T> TFuture<T> submit(Runnable task, T result);
    void shutdown();
    boolean isShutdown();
}
