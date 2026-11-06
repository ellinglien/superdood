package org.teavm.classlib.java.util.concurrent;

/** java.util.concurrent.LinkedBlockingQueue as TeaVM's LinkedBlockingDeque (SettleWorker's pool takes one, never reads it). */
public class TLinkedBlockingQueue<E> extends java.util.concurrent.LinkedBlockingDeque<E> {
    public TLinkedBlockingQueue() { }
}
