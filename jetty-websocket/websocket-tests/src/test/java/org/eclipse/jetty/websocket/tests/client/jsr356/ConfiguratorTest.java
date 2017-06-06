//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.tests.client.jsr356;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.tests.SimpleServletServer;
import org.eclipse.jetty.websocket.tests.jsr356.JsrTrackingEndpoint;
import org.eclipse.jetty.websocket.tests.servlets.EchoServlet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests of {@link javax.websocket.ClientEndpointConfig.Configurator}
 */
public class ConfiguratorTest
{
    public class TrackingConfigurator extends ClientEndpointConfig.Configurator
    {
        public HandshakeResponse response;
        public Map<String, List<String>> request;

        @Override
        public void afterResponse(HandshakeResponse hr)
        {
            this.response = hr;
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers)
        {
            this.request = headers;
        }
    }

    private static SimpleServletServer server;
    
    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new EchoServlet());
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    @Test
    public void testEndpointHandshakeInfo() throws Exception
    {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        JsrTrackingEndpoint clientSocket = new JsrTrackingEndpoint();

        // Build Config
        TrackingConfigurator configurator = new TrackingConfigurator();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(configurator)
                .build();

        // Connect
        Session session = container.connectToServer(clientSocket,config,server.getServerUri());

        // Send Simple Message
        session.getBasicRemote().sendText("Echo");

        // Wait for echo
        String echoed = clientSocket.messageQueue.poll(5,TimeUnit.SECONDS);
        assertThat("Echoed Message", echoed, is("Echo"));

        // Validate client side configurator use
        assertThat("configurator.request",configurator.request,notNullValue());
        assertThat("configurator.response",configurator.response,notNullValue());
    }
}
