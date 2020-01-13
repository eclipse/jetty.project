//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.component.DumpableCollection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * <p>A connection pool that validates connections before
 * making them available for use.</p>
 * <p>Connections that have just been opened are not validated.
 * Connections that are {@link #release(Connection) released} will
 * be validated.</p>
 * <p>Validation by reading from the EndPoint is not reliable,
 * since the TCP FIN may arrive just after the validation read.</p>
 * <p>This class validates connections by putting them in a
 * "quarantine" for a configurable timeout, where they cannot
 * be used to send requests. When the timeout expires, the
 * quarantined connection is made idle and therefore available
 * to send requests.</p>
 * <p>The existing HttpClient mechanism to detect server closes
 * will trigger and close quarantined connections, before they
 * are made idle (and reusable) again.</p>
 * <p>There still is a small chance that the timeout expires,
 * the connection is made idle and available again, it is used
 * to send a request exactly when the server decides to close.
 * This case is however unavoidable and may be mitigated by
 * tuning the idle timeout of the servers to be larger than
 * that of the client.</p>
 */
public class ValidatingConnectionPool extends DuplexConnectionPool
{
    private static final Logger LOG = Log.getLogger(ValidatingConnectionPool.class);

    private final Scheduler scheduler;
    private final long timeout;
    private final Map<Connection, Holder> quarantine;

    public ValidatingConnectionPool(Destination destination, int maxConnections, Callback requester, Scheduler scheduler, long timeout)
    {
        super(destination, maxConnections, requester);
        this.scheduler = scheduler;
        this.timeout = timeout;
        this.quarantine = new HashMap<>(maxConnections);
    }

    @ManagedAttribute(value = "The number of validating connections", readonly = true)
    public int getValidatingConnectionCount()
    {
        return quarantine.size();
    }

    @Override
    public boolean release(Connection connection)
    {
        lock();
        try
        {
            if (!getActiveConnections().remove(connection))
                return false;
            Holder holder = new Holder(connection);
            holder.task = scheduler.schedule(holder, timeout, TimeUnit.MILLISECONDS);
            quarantine.put(connection, holder);
            if (LOG.isDebugEnabled())
                LOG.debug("Validating for {}ms {}", timeout, connection);
        }
        finally
        {
            unlock();
        }

        released(connection);
        return true;
    }

    @Override
    public boolean remove(Connection connection)
    {
        Holder holder;
        lock();
        try
        {
            holder = quarantine.remove(connection);
        }
        finally
        {
            unlock();
        }

        if (holder == null)
            return super.remove(connection);

        if (LOG.isDebugEnabled())
            LOG.debug("Removed while validating {}", connection);

        boolean cancelled = holder.cancel();
        if (cancelled)
            return remove(connection, true);

        return super.remove(connection);
    }

    @Override
    protected void dump(Appendable out, String indent, Object... items) throws IOException
    {
        DumpableCollection toDump = new DumpableCollection("quarantine", quarantine.values());
        super.dump(out, indent, Stream.concat(Stream.of(items), Stream.of(toDump)));
    }

    @Override
    public String toString()
    {
        int size;
        lock();
        try
        {
            size = quarantine.size();
        }
        finally
        {
            unlock();
        }
        return String.format("%s[v=%d]", super.toString(), size);
    }

    private class Holder implements Runnable
    {
        private final long timestamp = System.nanoTime();
        private final AtomicBoolean done = new AtomicBoolean();
        private final Connection connection;
        public Scheduler.Task task;

        public Holder(Connection connection)
        {
            this.connection = connection;
        }

        @Override
        public void run()
        {
            if (done.compareAndSet(false, true))
            {
                boolean closed = isClosed();
                lock();
                try
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Validated {}", connection);
                    quarantine.remove(connection);
                    if (!closed)
                        deactivate(connection);
                }
                finally
                {
                    unlock();
                }

                idle(connection, closed);
                proceed();
            }
        }

        public boolean cancel()
        {
            if (done.compareAndSet(false, true))
            {
                task.cancel();
                return true;
            }
            return false;
        }

        @Override
        public String toString()
        {
            return String.format("%s[validationLeft=%dms]",
                connection,
                timeout - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timestamp)
            );
        }
    }
}
