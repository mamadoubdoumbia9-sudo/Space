/*
 * LOHEN — sim/core/ObjectPool.java
 *
 * 04.12 : zero allocation dans les boucles chaudes (particules, eclats de
 * verre, traces de pas, marqueurs de Echo, segments de cable).
 * Pool generique a capacite fixe, sans croissance — si le pool est vide,
 * on recycle l'objet le plus ancien au lieu d'allouer.
 */
package com.velmora.lohen.sim.core;

public final class ObjectPool<T> {

    /** Fabrique d'objets du pool. */
    public interface Factory<T> {
        T create();
    }

    /** Rappel de recyclage : remet l'objet dans son etat initial. */
    public interface Recycler<T> {
        void reset(T item);
    }

    private final Object[] items;
    private final boolean[] inUse;
    private final Recycler<T> recycler;
    private int freeCursor;
    private int active;
    private int created;
    private int recycledOldest;

    public ObjectPool(Factory<T> factory, Recycler<T> recycler, int capacity) {
        this.recycler = recycler;
        this.items = new Object[Math.max(1, capacity)];
        this.inUse = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = factory.create();
            created++;
        }
    }

    @SuppressWarnings("unchecked")
    public T obtain() {
        for (int i = 0; i < items.length; i++) {
            int idx = (freeCursor + i) % items.length;
            if (!inUse[idx]) {
                inUse[idx] = true;
                active++;
                freeCursor = (idx + 1) % items.length;
                T item = (T) items[idx];
                if (recycler != null) {
                    recycler.reset(item);
                }
                return item;
            }
        }
        /* pool sature : on recycle le plus ancien plutot que d'allouer */
        int idx = freeCursor;
        freeCursor = (idx + 1) % items.length;
        recycledOldest++;
        T item = (T) items[idx];
        if (recycler != null) {
            recycler.reset(item);
        }
        return item;
    }

    public void release(T item) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] == item) {
                if (inUse[i]) {
                    inUse[i] = false;
                    active--;
                }
                if (recycler != null) {
                    recycler.reset(item);
                }
                return;
            }
        }
    }

    public void releaseAll() {
        for (int i = 0; i < items.length; i++) {
            if (inUse[i]) {
                inUse[i] = false;
                if (recycler != null) {
                    @SuppressWarnings("unchecked")
                    T item = (T) items[i];
                    recycler.reset(item);
                }
            }
        }
        active = 0;
        freeCursor = 0;
    }

    @SuppressWarnings("unchecked")
    public T peek(int index) {
        if (index < 0 || index >= items.length) {
            return null;
        }
        return (T) items[index];
    }

    public boolean isInUse(int index) {
        return index >= 0 && index < inUse.length && inUse[index];
    }

    public int capacity() {
        return items.length;
    }

    public int active() {
        return active;
    }

    public int created() {
        return created;
    }

    public int recycledOldest() {
        return recycledOldest;
    }
}
