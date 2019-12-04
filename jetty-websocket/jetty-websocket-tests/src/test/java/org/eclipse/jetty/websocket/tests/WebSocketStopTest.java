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

package org.eclipse.jetty.websocket.tests;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketStopTest
{
    public class UpgradeServlet extends JettyWebSocketServlet
    {
        @Override
        protected void configure(JettyWebSocketServletFactory factory)
        {
            factory.setCreator(((req, resp) -> serverSocket));
        }
    }

    private Server server = new Server();
    private WebSocketClient client = new WebSocketClient();
    private EventSocket serverSocket = new EventSocket();
    private ServerConnector connector;

    @BeforeEach
    public void start() throws Exception
    {
        connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        contextHandler.addServlet(new ServletHolder(new UpgradeServlet()), "/");
        server.setHandler(contextHandler);

        JettyWebSocketServletContainerInitializer.configure(contextHandler, null);

        server.start();
        client.start();
    }

    @AfterEach
    public void stop() throws Exception
    {
        client.stop();
        server.stop();
    }

    @Test
    public void stopWithOpenSessions() throws Exception
    {
        final URI uri = new URI("ws://localhost:" + connector.getLocalPort() + "/");

        // Connect to two sessions to the server.
        EventSocket clientSocket1 = new EventSocket();
        EventSocket clientSocket2 = new EventSocket();
        assertNotNull(client.connect(clientSocket1, uri).get(5, TimeUnit.SECONDS));
        assertNotNull(client.connect(clientSocket2, uri).get(5, TimeUnit.SECONDS));
        assertTrue(clientSocket1.openLatch.await(5, TimeUnit.SECONDS));
        assertTrue(clientSocket2.openLatch.await(5, TimeUnit.SECONDS));

        // WS client is stopped and closes sessions with SHUTDOWN code.
        client.stop();
        assertTrue(clientSocket1.closeLatch.await(5, TimeUnit.SECONDS));
        assertTrue(clientSocket2.closeLatch.await(5, TimeUnit.SECONDS));
        assertThat(clientSocket1.statusCode, is(StatusCode.SHUTDOWN));
        assertThat(clientSocket2.statusCode, is(StatusCode.SHUTDOWN));
    }

    @Test
    public void testWriteAfterStop() throws Exception
    {
        URI uri = new URI("ws://localhost:" + connector.getLocalPort() + "/");
        EventSocket clientSocket = new EventSocket();

        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.addExtensions("permessage-deflate");
        Session session = client.connect(clientSocket, uri, upgradeRequest).get(5, TimeUnit.SECONDS);
        clientSocket.session.getRemote().sendString("init deflater");
        assertThat(serverSocket.messageQueue.poll(5, TimeUnit.SECONDS), is("init deflater"));
        session.close(StatusCode.NORMAL, null);

        // make sure both sides are closed
        clientSocket.session.close();
        assertTrue(clientSocket.closeLatch.await(5, TimeUnit.SECONDS));
        assertTrue(serverSocket.closeLatch.await(5, TimeUnit.SECONDS));

        // check we closed normally
        assertThat(clientSocket.statusCode, is(StatusCode.NORMAL));
        assertThat(serverSocket.statusCode, is(StatusCode.NORMAL));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> session.getRemote().sendString("this should fail before ExtensionStack"));
        assertThat(failure.getMessage(), is("CLOSED"));
    }
}