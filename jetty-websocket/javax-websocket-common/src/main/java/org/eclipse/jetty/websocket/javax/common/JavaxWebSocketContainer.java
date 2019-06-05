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

package org.eclipse.jetty.websocket.javax.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.websocket.Extension;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.eclipse.jetty.websocket.core.WebSocketExtensionRegistry;

public abstract class JavaxWebSocketContainer extends ContainerLifeCycle implements javax.websocket.WebSocketContainer
{
    private final static Logger LOG = Log.getLogger(JavaxWebSocketContainer.class);
    private final SessionTracker sessionTracker = new SessionTracker();
    private List<JavaxWebSocketSessionListener> sessionListeners = new ArrayList<>();
    protected FrameHandler.ConfigurationCustomizer defaultCustomizer = new FrameHandler.ConfigurationCustomizer();
    private WebSocketComponents components;

    public JavaxWebSocketContainer(WebSocketComponents components)
    {
        this.components = components;
        addSessionListener(sessionTracker);
        addBean(sessionTracker);
    }

    public abstract Executor getExecutor();

    protected abstract JavaxWebSocketFrameHandlerFactory getFrameHandlerFactory();

    public ByteBufferPool getBufferPool()
    {
        return components.getBufferPool();
    }

    public WebSocketExtensionRegistry getExtensionRegistry()
    {
        return components.getExtensionRegistry();
    }

    public DecoratedObjectFactory getObjectFactory()
    {
        return components.getObjectFactory();
    }

    public long getDefaultAsyncSendTimeout()
    {
        return defaultCustomizer.getWriteTimeout().toMillis();
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize()
    {
        long max = defaultCustomizer.getMaxBinaryMessageSize();
        if (max > (long)Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return (int)max;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout()
    {
        return defaultCustomizer.getIdleTimeout().toMillis();
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize()
    {
        long max = defaultCustomizer.getMaxTextMessageSize();
        if (max > (long)Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return (int)max;
    }

    @Override
    public void setAsyncSendTimeout(long ms)
    {
        defaultCustomizer.setWriteTimeout(Duration.ofMillis(ms));
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max)
    {
        defaultCustomizer.setMaxBinaryMessageSize(max);
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long ms)
    {
        defaultCustomizer.setIdleTimeout(Duration.ofMillis(ms));
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int max)
    {
        defaultCustomizer.setMaxTextMessageSize(max);
    }

    /**
     * {@inheritDoc}
     *
     * @see WebSocketContainer#getInstalledExtensions()
     * @since JSR356 v1.0
     */
    @Override
    public Set<Extension> getInstalledExtensions()
    {
        Set<Extension> ret = new HashSet<>();

        for (String name : getExtensionRegistry().getAvailableExtensionNames())
        {
            ret.add(new JavaxWebSocketExtension(name));
        }

        return ret;
    }

    /**
     * Used in {@link javax.websocket.Session#getOpenSessions()}
     *
     * @return the set of open sessions
     */
    public Set<javax.websocket.Session> getOpenSessions()
    {
        return sessionTracker.getSessions();
    }

    public JavaxWebSocketFrameHandler newFrameHandler(Object websocketPojo, UpgradeRequest upgradeRequest)
    {
        return getFrameHandlerFactory().newJavaxWebSocketFrameHandler(websocketPojo, upgradeRequest);
    }

    /**
     * Register a WebSocketSessionListener with the container
     *
     * @param listener the listener
     */
    public void addSessionListener(JavaxWebSocketSessionListener listener)
    {
        sessionListeners.add(listener);
    }

    /**
     * Remove a WebSocketSessionListener from the container
     *
     * @param listener the listener
     * @return true if listener was present and removed
     */
    public boolean removeSessionListener(JavaxWebSocketSessionListener listener)
    {
        return sessionListeners.remove(listener);
    }

    /**
     * Notify Session Listeners of events
     *
     * @param consumer the consumer to pass to each listener
     */
    public void notifySessionListeners(Consumer<JavaxWebSocketSessionListener> consumer)
    {
        for (JavaxWebSocketSessionListener listener : sessionListeners)
        {
            try
            {
                consumer.accept(listener);
            }
            catch (Throwable x)
            {
                LOG.info("Exception while invoking listener " + listener, x);
            }
        }
    }
}
