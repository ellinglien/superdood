package org.teavm.classlib.java.util.concurrent.atomic;

/**
 * java.util.concurrent.atomic.AtomicReferenceArray, which TeaVM's class library lacks (PrintScreen uses it): TeaVM maps
 * this class in its place by name. One thread in JS, so a plain array is atomic.
 */
public class TAtomicReferenceArray<E> {
    private final Object[] array;
    public TAtomicReferenceArray(int length) { array = new Object[length]; }
    public TAtomicReferenceArray(E[] from) { array = from.clone(); }
    public final int length() { return array.length; }
    @SuppressWarnings("unchecked") public final E get(int i) { return (E) array[i]; }
    public final void set(int i, E value) { array[i] = value; }
    public final void lazySet(int i, E value) { array[i] = value; }
    @SuppressWarnings("unchecked") public final E getAndSet(int i, E value) { E was = (E) array[i]; array[i] = value; return was; }
    public final boolean compareAndSet(int i, E expect, E update) {
        if (array[i] != expect) return false;
        array[i] = update; return true;
    }
}
