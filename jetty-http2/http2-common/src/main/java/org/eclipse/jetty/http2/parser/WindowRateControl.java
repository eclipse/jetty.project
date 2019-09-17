//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2.parser;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>An implementation of {@link RateControl} that limits the number of
 * events within a time period.</p>
 * <p>Events are kept in a queue and for each event the queue is first
 * drained of the old events outside the time window, and then the new
 * event is added to the queue. The size of the queue is maintained
 * separately in an AtomicInteger and if it exceeds the max
 * number of events then {@link #onEvent(Object)} returns {@code false}.</p>
 */
public class WindowRateControl implements RateControl
{
    private final Queue<Long> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final int maxEvents;
    private final long window;

    public WindowRateControl(int maxEvents, Duration window)
    {
        this.maxEvents = maxEvents;
        this.window = window.toNanos();
    }

    @Override
    public boolean onEvent(Object event)
    {
        long now = System.nanoTime();
        while (true)
        {
            Long time = events.peek();
            if (time == null)
                break;
            if (now < time)
                break;
            if (events.remove(time))
                size.decrementAndGet();
        }
        events.add(now + window);
        return size.incrementAndGet() <= maxEvents;
    }
}
