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
import javax.websocket.server.ServerEndpointConfig;

import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.websocket.javax.server.config.ContainerDefaultConfigurator;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketConfiguration;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer;

module org.eclipse.jetty.websocket.javax.server
{
    exports org.eclipse.jetty.websocket.javax.server.config;

    requires transitive org.eclipse.jetty.webapp;
    requires transitive org.eclipse.jetty.websocket.javax.client;
    requires org.eclipse.jetty.websocket.servlet;

    provides ServletContainerInitializer with JavaxWebSocketServletContainerInitializer;
    provides ServerEndpointConfig.Configurator with ContainerDefaultConfigurator;
    provides Configuration with JavaxWebSocketConfiguration;
}
