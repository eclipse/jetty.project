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

package org.eclipse.jetty.util.component;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FutureCallback;

/**
 * <p>Jetty components that wish to be part of a Graceful shutdown implement this interface so that
 * the {@link Graceful#shutdown()} method will be called to initiate a shutdown.    Shutdown operations
 * can fall into the following categories:</p>
 * <ul>
 *     <li>Preventing new load from being accepted (eg connectors stop accepting connections)</li>
 *     <li>Preventing existing load expanding (eg stopping existing connections accepting new requests)</li>
 *     <li>Waiting for existing load to complete (eg waiting for active request count to reduce to 0)</li>
 *     <li>Performing cleanup operations that may take time (eg closing an SSL connection)</li>
 * </ul>
 * <p>The {@link Future} returned by the the shutdown call will be completed to indicate the shutdown operation is completed.
 * Some shutdown operations may be instantaneous and always return a completed future.
 * </p><p>
 * Graceful shutdown is typically orchestrated by the doStop methods of Server or ContextHandler (for a full or partial
 * shutdown respectively).
 * </p>
 */
public interface Graceful
{
    Phase getShutdownPhase();

    Future<Void> shutdown();

    boolean isShutdown();

    /**
     The Phase of a Graceful is used to sort the discovered Gracefuls so that
     UNAVAILABE Gracefuls are shutdown before COMPLETING Gracefuls,
     which are shutdown before CLEANUP Gracefuls.
     */
    enum Phase
    {
        UNAVAILABLE,  // Make the component unavailable for future tasks (eg stop acceptor)
        COMPLETING,   // Gracefully wait for current tasks to complete (eg request complete)
        CLEANUP       // Time limited Shutdown operations (eg connection close)
    }

    /**
     * A utility Graceful that uses an {@link AtomicReference to a } {@link Future}.
     * A call to {@link #shutdown()} will initialize the future by calling
     * the abstract {@link #newFuture()} method.
     */
    abstract class GracefulFuture<T extends Future<Void>> implements Graceful
    {
        final AtomicReference<T> _future = new AtomicReference<>();
        final Phase _phase;

        protected abstract T newFuture();

        protected abstract void doShutdown(T future);

        public GracefulFuture()
        {
            this(Phase.COMPLETING);
        }

        public GracefulFuture(Phase phase)
        {
            _phase = phase;
        }

        @Override
        public Phase getShutdownPhase()
        {
            return _phase;
        }

        @Override
        public Future<Void> shutdown()
        {
            T future = _future.get();
            if (future == null)
            {
                future = newFuture();
                if (_future.compareAndSet(null, future))
                    doShutdown(future);
                else
                    future = _future.get();
            }
            return future;
        }

        @Override
        public boolean isShutdown()
        {
            T future = _future.get();
            return future != null;
        }

        public T getFuture()
        {
            return _future.get();
        }

        public void reset()
        {
            _future.set(null);
        }

        @Override
        public String toString()
        {
            T future = _future.get();
            return String.format("%s@%x{%s}", getClass(), hashCode(), future == null ? null : future.isDone() ? "Shutdown" : "ShuttingDown");
        }
    }

    /**
     * A utility Graceful that uses a {@link FutureCallback} to indicate if shutdown is completed.
     * By default a new uncompleted  {@link FutureCallback} is returned, but the {@link #newFuture()} method
     * can be overloaded to return a specific FutureCallback (eg an already completed one).
     * {@link Callback#failed(Throwable)} call to be completed.
     */
    class Shutdown extends GracefulFuture<FutureCallback>
    {
        protected FutureCallback newFuture()
        {
            return new FutureCallback();
        }

        protected void doShutdown(FutureCallback future)
        {
            future.succeeded();
        }
    }
}
