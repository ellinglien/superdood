package org.teavm.classlib.java.util.concurrent;

/** java.util.concurrent.Future, missing from TeaVM's class library (the pilot's shims; see TFutureTask). */
public interface TFuture<V> {
    boolean cancel(boolean interrupt);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, java.util.concurrent.ExecutionException;
    V get(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, TTimeoutException;
}
