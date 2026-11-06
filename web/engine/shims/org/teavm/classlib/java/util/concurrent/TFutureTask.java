package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * java.util.concurrent.FutureTask for one JS thread: run() runs the work once; get() runs it then if no thread has yet,
 * so work handed to a thread (ModularStroke's lift, SettleWorker's jobs) is done, in order, by whoever asks for it.
 */
public class TFutureTask<V> implements TRunnableFuture<V> {
    private Callable<V> work;
    private int state; // 0 new, 1 done, 2 failed, 3 cancelled
    private V result;
    private Throwable failure;

    public TFutureTask(Callable<V> work) { this.work = work; }
    public TFutureTask(Runnable work, V result) { this.work = () -> { work.run(); return result; }; }

    @Override public void run() {
        if (state != 0) return;
        try { set(work.call()); } catch (Throwable t) { setException(t); }
    }
    protected boolean runAndReset() {
        if (state != 0) return false;
        try { work.call(); return true; } catch (Throwable t) { setException(t); return false; }
    }
    protected void set(V v) { if (state == 0) { result = v; state = 1; work = null; done(); } }
    protected void setException(Throwable t) { if (state == 0) { failure = t; state = 2; work = null; done(); } }
    protected void done() { }
    @Override public boolean cancel(boolean interrupt) { if (state != 0) return false; state = 3; work = null; done(); return true; }
    @Override public boolean isCancelled() { return state == 3; }
    @Override public boolean isDone() { return state != 0; }
    @Override public V get() throws ExecutionException {
        run();
        if (state == 3) throw new CancellationException();
        if (state == 2) throw new ExecutionException(failure);
        return result;
    }
    @Override public V get(long timeout, TimeUnit unit) throws ExecutionException { return get(); }
}
