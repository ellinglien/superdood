package org.teavm.classlib.java.util.concurrent.locks;

/** One JS thread: a lock is never contended (QuickStroke's, reached but its render thread never runs; see PilotB). */
public class TReentrantLock {
    private int holds;
    public void lock() { holds++; }
    public boolean tryLock() { holds++; return true; }
    public void unlock() { if (holds == 0) throw new IllegalMonitorStateException(); holds--; }
    public boolean isHeldByCurrentThread() { return holds > 0; }
    public boolean isLocked() { return holds > 0; }
}
