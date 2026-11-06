package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** A pool that runs each piece of work as it is handed in (one JS thread): the order the engine's one-at-a-time workers keep. */
public class TThreadPoolExecutor implements TExecutorService {
    private boolean shut;
    public TThreadPoolExecutor() { }
    public TThreadPoolExecutor(int core, int most, long keep, TimeUnit unit, BlockingQueue<Runnable> queue) { }
    public TThreadPoolExecutor(int core, int most, long keep, TimeUnit unit, BlockingQueue<Runnable> queue, TThreadFactory threads) { }
    @Override public void execute(Runnable r) { r.run(); }
    @Override public <T> TFuture<T> submit(Callable<T> task) { TFutureTask<T> f = new TFutureTask<>(task); f.run(); return f; }
    @Override public TFuture<?> submit(Runnable task) { return submit(task, null); }
    @Override public <T> TFuture<T> submit(Runnable task, T result) { TFutureTask<T> f = new TFutureTask<>(task, result); f.run(); return f; }
    @Override public void shutdown() { shut = true; }
    @Override public boolean isShutdown() { return shut; }
    public void allowCoreThreadTimeOut(boolean value) { }
}
