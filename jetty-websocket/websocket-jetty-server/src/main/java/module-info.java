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

import javax.servlet.ServletContainerInitializer;

import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketConfiguration;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

module org.eclipse.jetty.websocket.jetty.server
{
    exports org.eclipse.jetty.websocket.server;
    exports org.eclipse.jetty.websocket.server.config;

    requires jetty.servlet.api;
    requires transitive org.eclipse.jetty.webapp;
    requires transitive org.eclipse.jetty.websocket.jetty.common;
    requires transitive org.eclipse.jetty.websocket.servlet;

    // Only required if using JMX.
    requires static org.eclipse.jetty.jmx;

    provides ServletContainerInitializer with JettyWebSocketServletContainerInitializer;
    provides Configuration with JettyWebSocketConfiguration;
}
