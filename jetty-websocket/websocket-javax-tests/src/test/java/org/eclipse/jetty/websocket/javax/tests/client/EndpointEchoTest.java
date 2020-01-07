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

package org.eclipse.jetty.websocket.javax.tests.client;

import java.util.concurrent.TimeUnit;
import javax.websocket.ContainerProvider;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketSession;
import org.eclipse.jetty.websocket.javax.tests.LocalServer;
import org.eclipse.jetty.websocket.javax.tests.WSEndpointTracker;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EndpointEchoTest
{
    private static LocalServer server;

    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new LocalServer();
        server.start();
        server.getServerContainer().addEndpoint(LocalServer.TextEchoSocket.class);
    }

    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    public static class ClientEndpoint extends WSEndpointTracker implements MessageHandler.Whole<String>
    {
        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
            super.onOpen(session, config);
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String message)
        {
            super.onWsText(message);
        }
    }

    @Test
    public void testEchoInstance() throws Exception
    {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        ClientEndpoint clientEndpoint = new ClientEndpoint();
        assertThat(clientEndpoint, Matchers.instanceOf(javax.websocket.Endpoint.class));
        // Issue connect using instance of class that extends Endpoint
        Session session = container.connectToServer(clientEndpoint, null, server.getWsUri().resolve("/echo/text"));
        session.getBasicRemote().sendText("Echo");

        String resp = clientEndpoint.messageQueue.poll(1, TimeUnit.SECONDS);
        assertThat("Response echo", resp, is("Echo"));
        session.close();
        clientEndpoint.awaitCloseEvent("Client");
    }

    @Test
    public void testEchoClassRef() throws Exception
    {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // Issue connect using class reference (class extends Endpoint)
        Session session = container.connectToServer(ClientEndpoint.class, null, server.getWsUri().resolve("/echo/text"));
        session.getBasicRemote().sendText("Echo");

        JavaxWebSocketSession jsrSession = (JavaxWebSocketSession)session;
        Object obj = jsrSession.getEndpoint();

        assertThat("session.endpoint", obj, Matchers.instanceOf(ClientEndpoint.class));
        ClientEndpoint endpoint = (ClientEndpoint)obj;
        String resp = endpoint.messageQueue.poll(1, TimeUnit.SECONDS);
        assertThat("Response echo", resp, is("Echo"));

        session.close();
        endpoint.awaitCloseEvent("Client");
    }
}
