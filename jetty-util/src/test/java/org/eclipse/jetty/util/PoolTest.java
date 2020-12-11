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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.stream.Collectors.toList;
import static org.eclipse.jetty.util.Pool.StrategyType.FIRST;
import static org.eclipse.jetty.util.Pool.StrategyType.RANDOM;
import static org.eclipse.jetty.util.Pool.StrategyType.ROUND_ROBIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PoolTest
{

    interface Factory
    {
        Pool<CloseableHolder> getPool(int maxSize);
    }

    private static class CloseableHolder implements Closeable
    {
        private boolean closed;
        private final String value;

        public CloseableHolder(String value)
        {
            this.value = value;
        }

        @Override
        public void close()
        {
            closed = true;
        }
    }

    public static Stream<Object[]> strategy()
    {
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{(Factory)s -> new Pool<>(FIRST, s)});
        data.add(new Object[]{(Factory)s -> new Pool<>(RANDOM, s)});
        data.add(new Object[]{(Factory)s -> new Pool<>(FIRST, s, true)});
        data.add(new Object[]{(Factory)s -> new Pool<>(ROUND_ROBIN, s)});
        return data.stream();
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testAcquireRelease(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(e1.getPooled().value, equalTo("aaa"));
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));

        assertNull(pool.acquire());

        e1.release();
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        assertThrows(IllegalStateException.class, e1::release);

        Pool<CloseableHolder>.Entry e2 = pool.acquire();
        assertThat(e2.getPooled().value, equalTo("aaa"));
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));

        pool.release(e2);
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        assertThrows(IllegalStateException.class, () -> pool.release(e2));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testRemoveBeforeRelease(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.remove(e1), is(true));
        assertThat(pool.remove(e1), is(false));
        assertThat(pool.release(e1), is(false));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testCloseBeforeRelease(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.size(), is(1));
        pool.close();
        assertThat(pool.size(), is(0));
        assertThat(pool.release(e1), is(false));
        assertThat(e1.getPooled().closed, is(true));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxPoolSize(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        assertThat(pool.size(), is(0));
        assertThat(pool.reserve(-1), notNullValue());
        assertThat(pool.size(), is(1));
        assertThat(pool.reserve(-1), nullValue());
        assertThat(pool.size(), is(1));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testReserve(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);

        // Reserve an entry
        Pool<CloseableHolder>.Entry e1 = pool.reserve(-1);
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(1));
        assertThat(pool.getIdleCount(), is(0));
        assertThat(pool.getInUseCount(), is(0));

        // max reservations
        assertNull(pool.reserve(1));
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(1));
        assertThat(pool.getIdleCount(), is(0));
        assertThat(pool.getInUseCount(), is(0));

        // enable the entry
        e1.enable(new CloseableHolder("aaa"), false);
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        // Reserve another entry
        Pool<CloseableHolder>.Entry e2 = pool.reserve(-1);
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(1));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        // remove the reservation
        e2.remove();
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        // Reserve another entry
        Pool<CloseableHolder>.Entry e3 = pool.reserve(-1);
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(1));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(0));

        // enable and acquire the entry
        e3.enable(new CloseableHolder("bbb"), true);
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getIdleCount(), is(1));
        assertThat(pool.getInUseCount(), is(1));

        // can't reenable
        assertThrows(IllegalStateException.class, () -> e3.enable(new CloseableHolder("xxx"), false));

        // Can't enable acquired entry
        assertThat(pool.acquire(), is(e1));
        assertThrows(IllegalStateException.class, () -> e1.enable(new CloseableHolder("xxx"), false));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testReserveMaxPending(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        assertThat(pool.reserve(0), nullValue());
        assertThat(pool.reserve(1), notNullValue());
        assertThat(pool.reserve(1), nullValue());
        assertThat(pool.reserve(2), notNullValue());
        assertThat(pool.reserve(2), nullValue());
        assertThat(pool.reserve(3), nullValue());
        assertThat(pool.reserve(-1), nullValue());
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testReserveNegativeMaxPending(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        assertThat(pool.reserve(-1), notNullValue());
        assertThat(pool.reserve(-1), notNullValue());
        assertThat(pool.reserve(-1), nullValue());
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testClose(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        CloseableHolder holder = new CloseableHolder("aaa");
        pool.reserve(-1).enable(holder, false);
        assertThat(pool.isClosed(), is(false));
        pool.close();
        pool.close();

        assertThat(pool.isClosed(), is(true));
        assertThat(pool.size(), is(0));
        assertThat(pool.acquire(), nullValue());
        assertThat(pool.reserve(-1), nullValue());
        assertThat(holder.closed, is(true));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testRemove(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.remove(e1), is(true));
        assertThat(pool.remove(e1), is(false));
        assertThat(pool.release(e1), is(false));
        assertThat(pool.acquire(), nullValue());
        assertThrows(NullPointerException.class, () -> pool.remove(null));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testValuesSize(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);

        assertThat(pool.size(), is(0));
        assertThat(pool.values().isEmpty(), is(true));
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);
        pool.reserve(-1).enable(new CloseableHolder("bbb"), false);
        assertThat(pool.values().stream().map(Pool.Entry::getPooled).map(closeableHolder -> closeableHolder.value).collect(toList()), equalTo(Arrays.asList("aaa", "bbb")));
        assertThat(pool.size(), is(2));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testValuesContainsAcquiredEntries(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);

        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);
        pool.reserve(-1).enable(new CloseableHolder("bbb"), false);
        assertThat(pool.acquire(), notNullValue());
        assertThat(pool.acquire(), notNullValue());
        assertThat(pool.acquire(), nullValue());
        assertThat(pool.values().isEmpty(), is(false));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testAcquireAt(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);

        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);
        pool.reserve(-1).enable(new CloseableHolder("bbb"), false);

        assertThat(pool.acquireAt(2), nullValue());
        assertThat(pool.acquireAt(0), notNullValue());
        assertThat(pool.acquireAt(0), nullValue());
        assertThat(pool.acquireAt(1), notNullValue());
        assertThat(pool.acquireAt(1), nullValue());
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxUsageCount(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxUsageCount(3);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.release(e1), is(true));
        e1 = pool.acquire();
        assertThat(pool.release(e1), is(true));
        e1 = pool.acquire();
        assertThat(pool.release(e1), is(false));
        assertThat(pool.acquire(), nullValue());
        assertThat(pool.size(), is(1));
        assertThat(pool.remove(e1), is(true));
        assertThat(pool.remove(e1), is(false));
        assertThat(pool.size(), is(0));
        Pool<CloseableHolder>.Entry e1Copy = e1;
        assertThat(pool.release(e1Copy), is(false));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxMultiplex(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        pool.setMaxMultiplex(3);

        Map<String, AtomicInteger> counts = new HashMap<>();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        counts.put("a", a);
        counts.put("b", b);
        pool.reserve(-1).enable(new CloseableHolder("a"), false);
        pool.reserve(-1).enable(new CloseableHolder("b"), false);

        counts.get(pool.acquire().getPooled().value).incrementAndGet();
        counts.get(pool.acquire().getPooled().value).incrementAndGet();
        counts.get(pool.acquire().getPooled().value).incrementAndGet();
        counts.get(pool.acquire().getPooled().value).incrementAndGet();

        assertThat(a.get(), greaterThan(0));
        assertThat(a.get(), lessThanOrEqualTo(3));
        assertThat(b.get(), greaterThan(0));
        assertThat(b.get(), lessThanOrEqualTo(3));

        counts.get(pool.acquire().getPooled().value).incrementAndGet();
        counts.get(pool.acquire().getPooled().value).incrementAndGet();

        assertThat(a.get(), is(3));
        assertThat(b.get(), is(3));

        assertNull(pool.acquire());
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testRemoveMultiplexed(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(e1, notNullValue());
        Pool<CloseableHolder>.Entry e2 = pool.acquire();
        assertThat(e2, notNullValue());
        assertThat(e2, sameInstance(e1));
        assertThat(e2.getUsageCount(), is(2));

        assertThat(pool.values().stream().findFirst().get().isIdle(), is(false));

        assertThat(pool.remove(e1), is(false));
        assertThat(pool.values().stream().findFirst().get().isIdle(), is(false));
        assertThat(pool.values().stream().findFirst().get().isClosed(), is(true));
        assertThat(pool.remove(e1), is(true));
        assertThat(pool.size(), is(0));

        assertThat(pool.remove(e1), is(false));

        assertThat(pool.release(e1), is(false));

        assertThat(pool.remove(e1), is(false));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMultiplexRemoveThenAcquireThenReleaseRemove(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        Pool<CloseableHolder>.Entry e2 = pool.acquire();

        assertThat(pool.remove(e1), is(false));
        assertThat(e1.isClosed(), is(true));
        assertThat(pool.acquire(), nullValue());
        assertThat(pool.release(e2), is(false));
        assertThat(pool.remove(e2), is(true));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testNonMultiplexRemoveAfterAcquire(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.remove(e1), is(true));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMultiplexRemoveAfterAcquire(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        Pool<CloseableHolder>.Entry e2 = pool.acquire();

        assertThat(pool.remove(e1), is(false));
        assertThat(pool.remove(e2), is(true));
        assertThat(pool.size(), is(0));

        assertThat(pool.release(e1), is(false));
        assertThat(pool.size(), is(0));

        Pool<CloseableHolder>.Entry e3 = pool.acquire();
        assertThat(e3, nullValue());

        assertThat(pool.release(e2), is(false));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testReleaseThenRemoveNonEnabledEntry(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        Pool<CloseableHolder>.Entry e = pool.reserve(-1);
        assertThat(pool.size(), is(1));
        assertThat(pool.release(e), is(false));
        assertThat(pool.size(), is(1));
        assertThat(pool.remove(e), is(true));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testRemoveNonEnabledEntry(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        Pool<CloseableHolder>.Entry e = pool.reserve(-1);
        assertThat(pool.size(), is(1));
        assertThat(pool.remove(e), is(true));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMultiplexMaxUsageReachedAcquireThenRemove(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.setMaxUsageCount(3);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e0 = pool.acquire();

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.release(e1), is(true));
        Pool<CloseableHolder>.Entry e2 = pool.acquire();
        assertThat(pool.release(e2), is(true));
        assertThat(pool.acquire(), nullValue());

        assertThat(pool.remove(e0), is(true));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMultiplexMaxUsageReachedAcquireThenReleaseThenRemove(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.setMaxUsageCount(3);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e0 = pool.acquire();

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(pool.release(e1), is(true));
        Pool<CloseableHolder>.Entry e2 = pool.acquire();
        assertThat(pool.release(e2), is(true));
        assertThat(pool.acquire(), nullValue());

        assertThat(pool.release(e0), is(false));
        assertThat(pool.values().stream().findFirst().get().isIdle(), is(true));
        assertThat(pool.values().stream().findFirst().get().isClosed(), is(false));
        assertThat(pool.size(), is(1));
        assertThat(pool.remove(e0), is(true));
        assertThat(pool.size(), is(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testUsageCountAfterReachingMaxMultiplexLimit(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.setMaxUsageCount(10);
        pool.reserve(-1).enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry e1 = pool.acquire();
        assertThat(e1.getUsageCount(), is(1));
        Pool<CloseableHolder>.Entry e2 = pool.acquire();
        assertThat(e2, sameInstance(e1));
        assertThat(e1.getUsageCount(), is(2));
        assertThat(pool.acquire(), nullValue());
        assertThat(e1.getUsageCount(), is(2));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testDynamicMaxUsageCountChangeOverflowMaxInt(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        Pool<CloseableHolder>.Entry entry = pool.reserve(-1);
        entry.enable(new CloseableHolder("aaa"), false);
        entry.setUsageCount(Integer.MAX_VALUE);

        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
        assertThat(pool.release(acquired1), is(true));

        pool.setMaxUsageCount(1);
        Pool<CloseableHolder>.Entry acquired2 = pool.acquire();
        assertThat(acquired2, nullValue());
        assertThat(entry.getPooled().closed, is(true));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testDynamicMaxUsageCountChangeSweep(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        Pool<CloseableHolder>.Entry entry1 = pool.reserve(-1);
        entry1.enable(new CloseableHolder("aaa"), false);
        Pool<CloseableHolder>.Entry entry2 = pool.reserve(-1);
        entry2.enable(new CloseableHolder("bbb"), false);

        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
        assertThat(pool.release(acquired1), is(true));

        assertThat(pool.size(), is(2));
        pool.setMaxUsageCount(1);
        assertThat(pool.size(), is(1));
        assertThat(entry1.getPooled().closed ^ entry2.getPooled().closed, is(true));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxDuration(Factory factory) throws Exception
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        pool.setMaxDuration(50, TimeUnit.MILLISECONDS);
        Pool<CloseableHolder>.Entry entry1 = pool.reserve(-1);
        entry1.enable(new CloseableHolder("aaa"), false);

        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
        assertThat(pool.release(acquired1), is(true));
        assertThat(pool.size(), is(1));

        // entry1 expires while acquired
        Pool<CloseableHolder>.Entry acquired2 = pool.acquire();
        assertThat(acquired1, notNullValue());
        Thread.sleep(55);
        assertThat(pool.release(acquired1), is(false));
        assertThat(pool.size(), is(1)); // release does not remove expired objects

        // entry2 expires before acquisition
        Pool<CloseableHolder>.Entry entry2 = pool.reserve(-1);
        entry2.enable(new CloseableHolder("bbb"), false);
        assertThat(pool.size(), is(2));
        Thread.sleep(55);
        assertThat(pool.acquire(), nullValue());
        assertThat(pool.size(), is(0)); // acquire removes & closes expired objects
        assertThat(entry2.getPooled().closed, is(true));
        assertThat(acquired1.getPooled().closed, is(true));

        // entry1 is manually removed
        assertThat(pool.remove(acquired2), is(false)); // was removed by above acquire
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxDurationBeginsAfterEnable(Factory factory) throws Exception
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        pool.setMaxDuration(50, TimeUnit.MILLISECONDS);
        Pool<CloseableHolder>.Entry entry1 = pool.reserve(-1);

        Thread.sleep(55);

        // expiration starts ticking only after the entry is enabled
        entry1.enable(new CloseableHolder("aaa"), false);
        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testDynamicMaxDuration(Factory factory) throws Exception
    {
        Pool<CloseableHolder> pool = factory.getPool(2);
        pool.setMaxDuration(50, TimeUnit.MILLISECONDS);
        Pool<CloseableHolder>.Entry entry1 = pool.reserve(-1);
        entry1.enable(new CloseableHolder("aaa"), false);

        // changing the expiration between acquire and release expands the lifespan of pooled objects
        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
        Thread.sleep(55);
        pool.setMaxDuration(1000, TimeUnit.MILLISECONDS);
        assertThat(pool.release(acquired1), is(true));
        assertThat(pool.size(), is(1));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testMaxDurationWithMultiplex(Factory factory) throws Exception
    {
        Pool<CloseableHolder> pool = factory.getPool(1);
        pool.setMaxMultiplex(2);
        pool.setMaxDuration(50, TimeUnit.MILLISECONDS);
        Pool<CloseableHolder>.Entry entry1 = pool.reserve(-1);
        entry1.enable(new CloseableHolder("aaa"), false);

        // entry1 expires while acquired twice
        Pool<CloseableHolder>.Entry acquired1 = pool.acquire();
        assertThat(acquired1, notNullValue());
        Pool<CloseableHolder>.Entry acquired2 = pool.acquire();
        assertThat(acquired2, notNullValue());
        Thread.sleep(55);
        assertThat(pool.release(acquired1), is(false));
        assertThat(pool.release(acquired2), is(false));
        assertThat(pool.remove(acquired1), is(true));
        assertThat(pool.remove(acquired2), is(false));
        assertThat(pool.size(), is(0));

        // entry2 expires while acquired
        Pool<CloseableHolder>.Entry entry2 = pool.reserve(-1);
        entry2.enable(new CloseableHolder("aaa"), false);
        Pool<CloseableHolder>.Entry acquired3 = pool.acquire();
        assertThat(acquired3, notNullValue());
        Thread.sleep(55);
        Pool<CloseableHolder>.Entry acquired4 = pool.acquire(); // still multiplex-acquirable, but expired
        assertThat(acquired4, nullValue());
        assertThat(acquired3.getPooled().closed, is(false)); // still in use, acquire does not close
        assertThat(pool.size(), is(1));
        assertThat(pool.release(acquired3), is(false));
        assertThat(pool.remove(acquired3), is(true));
        assertThat(pool.size(), is(0));
    }

    @Test
    public void testConfigLimits()
    {
        assertThrows(IllegalArgumentException.class, () -> new Pool<CloseableHolder>(FIRST, 1).setMaxMultiplex(0));
        assertThrows(IllegalArgumentException.class, () -> new Pool<CloseableHolder>(FIRST, 1).setMaxMultiplex(-1));
        assertThrows(IllegalArgumentException.class, () -> new Pool<CloseableHolder>(FIRST, 1).setMaxUsageCount(0));
    }

    @ParameterizedTest
    @MethodSource(value = "strategy")
    public void testAcquireWithCreator(Factory factory)
    {
        Pool<CloseableHolder> pool = factory.getPool(2);

        assertThat(pool.size(), is(0));
        assertThat(pool.acquire(e -> null), nullValue());
        assertThat(pool.size(), is(0));

        Pool<CloseableHolder>.Entry e1 = pool.acquire(e -> new CloseableHolder("e1"));
        assertThat(e1.getPooled().value, is("e1"));
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));

        assertThat(pool.acquire(e -> null), nullValue());
        assertThat(pool.size(), is(1));

        Pool<CloseableHolder>.Entry e2 = pool.acquire(e -> new CloseableHolder("e2"));
        assertThat(e2.getPooled().value, is("e2"));
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(2));

        Pool<CloseableHolder>.Entry e3 = pool.acquire(e -> new CloseableHolder("e3"));
        assertThat(e3, nullValue());
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(2));

        assertThat(pool.acquire(e ->
        {
            throw new IllegalStateException();
        }), nullValue());

        e2.release();
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));

        Pool<CloseableHolder>.Entry e4 = pool.acquire(e -> new CloseableHolder("e4"));
        assertThat(e4.getPooled().value, is("e2"));
        assertThat(pool.size(), is(2));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(2));

        pool.remove(e1);
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));

        assertThrows(IllegalStateException.class, () -> pool.acquire(e ->
        {
            throw new IllegalStateException();
        }));
        assertThat(pool.size(), is(1));
        assertThat(pool.getReservedCount(), is(0));
        assertThat(pool.getInUseCount(), is(1));
    }

    @Test
    public void testRoundRobinStrategy()
    {
        Pool<AtomicInteger> pool = new Pool<>(ROUND_ROBIN, 4);

        Pool<AtomicInteger>.Entry e1 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e2 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e3 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e4 = pool.acquire(e -> new AtomicInteger());
        assertNull(pool.acquire(e -> new AtomicInteger()));

        pool.release(e1);
        pool.release(e2);
        pool.release(e3);
        pool.release(e4);

        Pool<AtomicInteger>.Entry last = null;
        for (int i = 0; i < 8; i++)
        {
            Pool<AtomicInteger>.Entry e = pool.acquire();
            if (last != null)
                assertThat(e, not(sameInstance(last)));
            e.getPooled().incrementAndGet();
            pool.release(e);
            last = e;
        }

        assertThat(e1.getPooled().get(), is(2));
        assertThat(e2.getPooled().get(), is(2));
        assertThat(e3.getPooled().get(), is(2));
        assertThat(e4.getPooled().get(), is(2));
    }

    @Test
    public void testRandomStrategy()
    {
        Pool<AtomicInteger> pool = new Pool<>(RANDOM, 4);

        Pool<AtomicInteger>.Entry e1 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e2 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e3 = pool.acquire(e -> new AtomicInteger());
        Pool<AtomicInteger>.Entry e4 = pool.acquire(e -> new AtomicInteger());
        assertNull(pool.acquire(e -> new AtomicInteger()));

        pool.release(e1);
        pool.release(e2);
        pool.release(e3);
        pool.release(e4);

        for (int i = 0; i < 400; i++)
        {
            Pool<AtomicInteger>.Entry e = pool.acquire();
            e.getPooled().incrementAndGet();
            pool.release(e);
        }

        assertThat(e1.getPooled().get(), greaterThan(10));
        assertThat(e2.getPooled().get(), greaterThan(10));
        assertThat(e3.getPooled().get(), greaterThan(10));
        assertThat(e4.getPooled().get(), greaterThan(10));
    }
}
