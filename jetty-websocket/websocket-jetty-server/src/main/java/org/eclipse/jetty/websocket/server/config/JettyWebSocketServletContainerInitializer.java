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

package org.eclipse.jetty.websocket.server.config;

import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.listener.ContainerInitializer;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.servlet.WebSocketMapping;
import org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter;

/**
 * ServletContext configuration for Jetty Native WebSockets API.
 */
public class JettyWebSocketServletContainerInitializer implements ServletContainerInitializer
{
    private static final Logger LOG = Log.getLogger(JettyWebSocketServletContainerInitializer.class);

    public interface Configurator
    {
        void accept(ServletContext servletContext, JettyWebSocketServerContainer container);
    }

    /**
     * Configure the {@link ServletContextHandler} to call the {@link JettyWebSocketServletContainerInitializer}
     * during the {@link ServletContext} initialization phase.
     *
     * @param context the context to add listener to.
     * @param configurator a lambda that is called to allow the {@link WebSocketMapping} to
     * be configured during {@link ServletContext} initialization phase
     */
    public static void configure(ServletContextHandler context, Configurator configurator)
    {
        context.addEventListener(
            ContainerInitializer
                .asContextListener(new JettyWebSocketServletContainerInitializer())
                .afterStartup((servletContext) ->
                {
                    if (configurator != null)
                    {
                        JettyWebSocketServerContainer container = JettyWebSocketServerContainer.getContainer(servletContext);
                        configurator.accept(servletContext, container);
                    }
                }));
    }

    /**
     * Immediately initialize the {@link ServletContextHandler} with the default {@link JettyWebSocketServerContainer}.
     *
     * <p>
     * This method is typically called from {@link #onStartup(Set, ServletContext)} itself or from
     * another dependent {@link ServletContainerInitializer} that requires minimal setup to
     * be performed.
     * </p>
     * <p>
     * This method SHOULD NOT BE CALLED by users of Jetty.
     * Use the {@link #configure(ServletContextHandler, Configurator)} method instead.
     * </p>
     * <p>
     * This will return the default {@link JettyWebSocketServerContainer} if already initialized,
     * and not create a new {@link JettyWebSocketServerContainer} each time it is called.
     * </p>
     *
     * @param context the context to work with
     * @return the default {@link JettyWebSocketServerContainer}
     */
    public static JettyWebSocketServerContainer initialize(ServletContextHandler context)
    {
        WebSocketComponents components = WebSocketComponents.ensureWebSocketComponents(context.getServletContext());
        FilterHolder filterHolder = WebSocketUpgradeFilter.ensureFilter(context.getServletContext());
        WebSocketMapping mapping = WebSocketMapping.ensureMapping(context.getServletContext(), WebSocketMapping.DEFAULT_KEY);
        JettyWebSocketServerContainer container = JettyWebSocketServerContainer.ensureContainer(context.getServletContext());

        if (LOG.isDebugEnabled())
            LOG.debug("configureContext {} {} {} {}", container, mapping, filterHolder, components);

        return container;
    }

    @Override
    public void onStartup(Set<Class<?>> c, ServletContext context)
    {
        ServletContextHandler contextHandler = ServletContextHandler.getServletContextHandler(context, "Jetty WebSocket SCI");
        JettyWebSocketServletContainerInitializer.initialize(contextHandler);
    }
}
