//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.DumpableCollection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Locker;

/**
 * A fast pool of objects, with optional support for
 * multiplexing, max usage count and several optimized strategies plus
 * an optional {@link ThreadLocal} cache of the last release entry.
 * <p>
 * When the method {@link #close()} is called, all {@link Closeable}s in the pool
 * are also closed.
 * </p>
 * @param <T>
 */
public class Pool<T> implements AutoCloseable, Dumpable
{
    private static final Logger LOGGER = Log.getLogger(Pool.class);

    private final List<Entry> entries = new CopyOnWriteArrayList<Entry>()
    {
        @Override
        public ListIterator<Entry> listIterator(int index)
        {
            try
            {
                return super.listIterator(index);
            }
            catch (IndexOutOfBoundsException e)
            {
                return listIterator();
            }
        }
    };

    private final int maxEntries;
    private final AtomicInteger pending = new AtomicInteger();
    private final Strategy strategy;

    /*
     * The cache is used to avoid hammering on the first index of the entry list.
     * Caches can become poisoned (i.e.: containing entries that are in use) when
     * the release isn't done by the acquiring thread or when the entry pool is
     * undersized compared to the load applied on it.
     * When an entry can't be found in the cache, the global list is iterated
     * with the normal strategy so the cache has no visible effect besides performance.
     */
    private final Locker locker = new Locker();
    private final ThreadLocal<Entry> cache;
    private final AtomicInteger nextIndex;
    private volatile boolean closed;
    private volatile int maxMultiplex = 1;
    private volatile int maxUsageCount = -1;

    public enum Strategy
    {
        /**
         * A strategy that looks for an entry always starting from the first entry.
         * It will favour the early entries in the pool, but may contend on them more.
         */
        FIRST,

        /**
         * A strategy that looks for an entry by iterating from a random starting
         * index.  No entries are favoured and contention is reduced.
         */
        RANDOM,

        /**
         * A strategy that uses the {@link Thread#getId()} of the current thread
         * to select a starting point for an entry search.  Whilst not as performant as
         * using the {@link ThreadLocal} cache, it may be suitable when the pool is substantially smaller
         * than the number of available threads.
         * No entries are favoured and contention is reduced.
         */
        THREAD_ID,

        /**
         * A strategy that looks for an entry by iterating from a starting point
         * that is incremented on every search. This gives similar results to the
         * random strategy but with more predictable behaviour.
         * No entries are favoured and contention is reduced.
         */
        ROUND_ROBIN,
    }

    /**
     * Construct a Pool with a specified lookup strategy and no
     * {@link ThreadLocal} cache.
     *
     * @param strategy The strategy to used for looking up entries.
     * @param maxEntries the maximum amount of entries that the pool will accept.
     */
    public Pool(Strategy strategy, int maxEntries)
    {
        this(strategy, maxEntries, false);
    }

    /**
     * Construct a Pool with the specified thread-local cache size and
     * an optional {@link ThreadLocal} cache.
     * @param strategy The strategy to used for looking up entries.
     * @param maxEntries the maximum amount of entries that the pool will accept.
     * @param cache True if a {@link ThreadLocal} cache should be used to try the most recently released entry.
     */
    public Pool(Strategy strategy, int maxEntries, boolean cache)
    {
        this.maxEntries = maxEntries;
        this.strategy = strategy;
        this.cache = cache ? new ThreadLocal() : null;
        nextIndex = strategy == Strategy.ROUND_ROBIN ? new AtomicInteger() : null;
    }

    public int getReservedCount()
    {
        return pending.get();
    }

    public int getIdleCount()
    {
        return (int)entries.stream().filter(Entry::isIdle).count();
    }

    public int getInUseCount()
    {
        return (int)entries.stream().filter(Entry::isInUse).count();
    }

    public int getMaxEntries()
    {
        return maxEntries;
    }

    public int getMaxMultiplex()
    {
        return maxMultiplex;
    }

    public final void setMaxMultiplex(int maxMultiplex)
    {
        if (maxMultiplex < 1)
            throw new IllegalArgumentException("Max multiplex must be >= 1");
        this.maxMultiplex = maxMultiplex;
    }

    public int getMaxUsageCount()
    {
        return maxUsageCount;
    }

    public final void setMaxUsageCount(int maxUsageCount)
    {
        if (maxUsageCount == 0)
            throw new IllegalArgumentException("Max usage count must be != 0");
        this.maxUsageCount = maxUsageCount;
    }

    /**
     * Create a new disabled slot into the pool.
     * The returned entry must ultimately have the {@link Entry#enable(Object, boolean)}
     * method called or be removed via {@link Pool.Entry#remove()} or
     * {@link Pool#remove(Pool.Entry)}.
     *
     * @param allotment the desired allotment, where each entry handles an allotment of maxMultiplex,
     * or a negative number to always trigger the reservation of a new entry.
     * @return a disabled entry that is contained in the pool,
     * or null if the pool is closed or if the pool already contains
     * {@link #getMaxEntries()} entries, or the allotment has already been reserved
     */
    public Entry reserve(int allotment)
    {
        try (Locker.Lock l = locker.lock())
        {
            if (closed)
                return null;

            int space = maxEntries - entries.size();
            if (space <= 0)
                return null;

            // The pending count is an AtomicInteger that is only ever incremented here with
            // the lock held.  Thus the pending count can be reduced immediately after the
            // test below, but never incremented.  Thus the allotment limit can be enforced.
            if (allotment >= 0 && (pending.get() * getMaxMultiplex()) >= allotment)
                return null;
            pending.incrementAndGet();

            Entry entry = new Entry();
            entries.add(entry);
            return entry;
        }
    }

    /**
     * Acquire the entry from the pool at the specified index. This method bypasses the thread-local mechanism.
     *
     * @param idx the index of the entry to acquire.
     * @return the specified entry or null if there is none at the specified index or if it is not available.
     */
    @Deprecated
    public Entry acquireAt(int idx)
    {
        if (closed)
            return null;

        try
        {
            Entry entry = entries.get(idx);
            if (entry.tryAcquire())
                return entry;
        }
        catch (IndexOutOfBoundsException e)
        {
            // no entry at that index
        }
        return null;
    }

    /**
     * Acquire an entry from the pool.
     * Only enabled entries will be returned from this method and their enable method must not be called.
     * @return an entry from the pool or null if none is available.
     */
    public Entry acquire()
    {
        if (closed)
            return null;

        int size = entries.size();
        if (size == 0)
            return null;

        if (cache != null)
        {
            Pool<T>.Entry entry = cache.get();
            if (entry != null && entry.tryAcquire())
                return entry;
        }

        int index = startIndex(size);

        for (int tries = size; tries-- > 0;)
        {
            try
            {
                Pool<T>.Entry entry = entries.get(index);
                if (entry != null && entry.tryAcquire())
                    return entry;
            }
            catch (IndexOutOfBoundsException e)
            {
                LOGGER.ignore(e);
            }
            index = (index + 1) % size;
        }
        return null;
    }

    private int startIndex(int size)
    {
        switch (strategy)
        {
            case FIRST:
                return 0;
            case RANDOM:
                return ThreadLocalRandom.current().nextInt(size);
            case ROUND_ROBIN:
                return nextIndex.getAndUpdate(c -> Math.max(0, c + 1)) % size;
            case THREAD_ID:
                return (int)(Thread.currentThread().getId() % size);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    /**
     * Utility method to acquire an entry from the pool,
     * reserving and creating a new entry if necessary.
     *
     * @param creator a function to create the pooled value for a reserved entry.
     * @return an entry from the pool or null if none is available.
     */
    public Entry acquire(Function<Pool<T>.Entry, T> creator)
    {
        Entry entry = acquire();
        if (entry != null)
            return entry;

        entry = reserve(-1);
        if (entry == null)
            return null;

        T value;
        try
        {
            value = creator.apply(entry);
        }
        catch (Throwable th)
        {
            remove(entry);
            throw th;
        }

        if (value == null)
        {
            remove(entry);
            return null;
        }

        return entry.enable(value, true) ? entry : null;
    }

    /**
     * This method will return an acquired object to the pool. Objects
     * that are acquired from the pool but never released will result
     * in a memory leak.
     *
     * @param entry the value to return to the pool
     * @return true if the entry was released and could be acquired again,
     * false if the entry should be removed by calling {@link #remove(Pool.Entry)}
     * and the object contained by the entry should be disposed.
     * @throws NullPointerException if value is null
     */
    public boolean release(Entry entry)
    {
        if (closed)
            return false;

        boolean released = entry.tryRelease();
        if (released && cache != null)
            cache.set(entry);
        return released;
    }

    /**
     * Remove a value from the pool.
     *
     * @param entry the value to remove
     * @return true if the entry was removed, false otherwise
     */
    public boolean remove(Entry entry)
    {
        if (closed)
            return false;

        if (!entry.tryRemove())
        {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Attempt to remove an object from the pool that is still in use: {}", entry);
            return false;
        }

        boolean removed = entries.remove(entry);
        if (!removed && LOGGER.isDebugEnabled())
            LOGGER.debug("Attempt to remove an object from the pool that does not exist: {}", entry);

        return removed;
    }

    public boolean isClosed()
    {
        return closed;
    }

    @Override
    public void close()
    {
        List<Entry> copy;
        try (Locker.Lock l = locker.lock())
        {
            closed = true;
            copy = new ArrayList<>(entries);
            entries.clear();
        }

        // iterate the copy and close its entries
        for (Entry entry : copy)
        {
            if (entry.tryRemove() && entry.pooled instanceof Closeable)
                IO.close((Closeable)entry.pooled);
        }
    }

    public int size()
    {
        return entries.size();
    }

    public Collection<Entry> values()
    {
        return Collections.unmodifiableCollection(entries);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        Dumpable.dumpObjects(out, indent, this,
            new DumpableCollection("entries", entries));
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x[size=%d closed=%s pending=%d]",
            getClass().getSimpleName(),
            hashCode(),
            entries.size(),
            closed,
            pending.get());
    }

    public class Entry
    {
        // hi: positive=open/maxUsage counter; negative=closed; MIN_VALUE pending
        // lo: multiplexing counter
        private final AtomicBiInteger state;

        // The pooled item.  This is not volatile as it is set once and then never changed.
        // Other threads accessing must check the state field above first, so a good before/after
        // relationship exists to make a memory barrier.
        private T pooled;

        Entry()
        {
            this.state = new AtomicBiInteger(Integer.MIN_VALUE, 0);
        }

        /** Enable a reserved entry {@link Entry}.
         * An entry returned from the {@link #reserve(int)} method must be enabled with this method,
         * once and only once, before it is usable by the pool.
         * The entry may be enabled and not acquired, in which case it is immediately available to be
         * acquired, potentially by another thread; or it can be enabled and acquired atomically so that
         * no other thread can acquire it, although the acquire may still fail if the pool has been closed.
         * @param pooled The pooled item for the entry
         * @param acquire If true the entry is atomically enabled and acquired.
         * @return true If the entry was enabled.
         * @throws IllegalStateException if the entry was already enabled
         */
        public boolean enable(T pooled, boolean acquire)
        {
            Objects.requireNonNull(pooled);

            if (state.getHi() != Integer.MIN_VALUE)
            {
                if (state.getHi() == -1)
                    return false; // Pool has been closed
                throw new IllegalStateException("Entry already enabled: " + this);
            }
            this.pooled = pooled;
            int usage = acquire ? 1 : 0;
            if (!state.compareAndSet(Integer.MIN_VALUE, usage, 0, usage))
            {
                this.pooled = null;
                if (state.getHi() == -1)
                    return false; // Pool has been closed
                throw new IllegalStateException("Entry already enabled: " + this);
            }
            pending.decrementAndGet();
            return true;
        }

        public T getPooled()
        {
            return pooled;
        }

        /**
         * Release the entry.
         * This is equivalent to calling {@link Pool#release(Pool.Entry)} passing this entry.
         * @return true if released.
         */
        public boolean release()
        {
            return Pool.this.release(this);
        }

        /**
         * Remove the entry.
         * This is equivalent to calling {@link Pool#remove(Pool.Entry)} passing this entry.
         * @return true if remove.
         */
        public boolean remove()
        {
            return Pool.this.remove(this);
        }

        /**
         * Try to acquire the entry if possible by incrementing both the usage
         * count and the multiplex count.
         * @return true if the usage count is &lt;= maxUsageCount and
         * the multiplex count is maxMultiplex and the entry is not closed,
         * false otherwise.
         */
        boolean tryAcquire()
        {
            while (true)
            {
                long encoded = state.get();
                int usageCount = AtomicBiInteger.getHi(encoded);
                boolean closed = usageCount < 0;
                int multiplexingCount = AtomicBiInteger.getLo(encoded);
                int currentMaxUsageCount = maxUsageCount;
                if (closed || multiplexingCount >= maxMultiplex || (currentMaxUsageCount > 0 && usageCount >= currentMaxUsageCount))
                    return false;

                if (state.compareAndSet(encoded, usageCount + 1, multiplexingCount + 1))
                    return true;
            }
        }

        /**
         * Try to release the entry if possible by decrementing the multiplexing
         * count unless the entity is closed.
         * @return true if the entry was released,
         * false if {@link #tryRemove()} should be called.
         */
        boolean tryRelease()
        {
            int newMultiplexingCount;
            int usageCount;
            while (true)
            {
                long encoded = state.get();
                usageCount = AtomicBiInteger.getHi(encoded);
                boolean closed = usageCount < 0;
                if (closed)
                    return false;

                newMultiplexingCount = AtomicBiInteger.getLo(encoded) - 1;
                if (newMultiplexingCount < 0)
                    throw new IllegalStateException("Cannot release an already released entry");

                if (state.compareAndSet(encoded, usageCount, newMultiplexingCount))
                    break;
            }

            int currentMaxUsageCount = maxUsageCount;
            boolean overUsed = currentMaxUsageCount > 0 && usageCount >= currentMaxUsageCount;
            return !(overUsed && newMultiplexingCount == 0);
        }

        public boolean isOverUsed()
        {
            int currentMaxUsageCount = maxUsageCount;
            int usageCount = state.getHi();
            return currentMaxUsageCount > 0 && usageCount >= currentMaxUsageCount;
        }

        /**
         * Try to mark the entry as removed.
         * @return true if the entry has to be removed from the containing pool, false otherwise.
         */
        boolean tryRemove()
        {
            while (true)
            {
                long encoded = state.get();
                int usageCount = AtomicBiInteger.getHi(encoded);
                int multiplexCount = AtomicBiInteger.getLo(encoded);
                int newMultiplexCount = Math.max(multiplexCount - 1, 0);

                boolean removed = state.compareAndSet(usageCount, -1, multiplexCount, newMultiplexCount);
                if (removed)
                {
                    if (usageCount == Integer.MIN_VALUE)
                        pending.decrementAndGet();
                    return newMultiplexCount == 0;
                }
            }
        }

        public boolean isClosed()
        {
            return state.getHi() < 0;
        }

        public boolean isIdle()
        {
            long encoded = state.get();
            return AtomicBiInteger.getHi(encoded) >= 0 && AtomicBiInteger.getLo(encoded) == 0;
        }

        public boolean isInUse()
        {
            long encoded = state.get();
            return AtomicBiInteger.getHi(encoded) >= 0 && AtomicBiInteger.getLo(encoded) > 0;
        }

        public int getUsageCount()
        {
            return Math.max(state.getHi(), 0);
        }

        @Override
        public String toString()
        {
            long encoded = state.get();
            int usageCount = AtomicBiInteger.getHi(encoded);
            int multiplexCount = AtomicBiInteger.getLo(encoded);

            String state = usageCount < 0 ? "CLOSED" : multiplexCount == 0 ? "IDLE" : "INUSE";

            return String.format("%s@%x{%s, usage=%d, multiplex=%d/%d, pooled=%s}",
                getClass().getSimpleName(),
                hashCode(),
                state,
                Math.max(usageCount, 0),
                Math.max(multiplexCount, 0),
                getMaxMultiplex(),
                pooled);
        }
    }
}
