//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.tests.server;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.tests.SimpleServletServer;
import org.eclipse.jetty.websocket.tests.TrackingEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WebSocketOverSSLTest
{
    public static final int CONNECT_TIMEOUT = 15000;
    public static final int FUTURE_TIMEOUT_SEC = 30;

    private static SimpleServletServer server;

    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new SessionServlet());
        server.enableSsl(true);
        server.start();
    }

    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    /**
     * Test the requirement of issuing socket and receiving echo response
     * @throws Exception on test failure
     */
    @Test
    public void testEcho() throws Exception
    {
        assertThat("server scheme",server.getWsUri().getScheme(),is("wss"));
        HttpClient httpClient = new HttpClient(server.getSslContextFactory());
        WebSocketClient client = new WebSocketClient(httpClient);
        client.addManaged(httpClient);
        try
        {
            client.start();

            TrackingEndpoint clientSocket = new TrackingEndpoint("Client");
            URI requestUri = server.getWsUri();
            Future<Session> fut = client.connect(clientSocket,requestUri);

            // wait for connect
            Session session = fut.get(FUTURE_TIMEOUT_SEC,TimeUnit.SECONDS);

            // Generate text frame
            String msg = "this is an echo ... cho ... ho ... o";
            RemoteEndpoint remote = session.getRemote();
            remote.sendText(msg);

            // Read frame (hopefully text frame)
            String captured = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat("Text Message",captured,is(msg));

            // Shutdown the socket
            clientSocket.close(StatusCode.NORMAL, "Normal");
        }
        finally
        {
            client.stop();
        }
    }

    /**
     * Test that server session reports as secure
     * @throws Exception on test failure
     */
    @Test
    public void testServerSessionIsSecure() throws Exception
    {
        assertThat("server scheme",server.getWsUri().getScheme(),is("wss"));
        HttpClient httpClient = new HttpClient(server.getSslContextFactory());
        WebSocketClient client = new WebSocketClient(httpClient);
        client.addManaged(httpClient);
        try
        {
            client.setConnectTimeout(CONNECT_TIMEOUT);
            client.start();
    
            TrackingEndpoint clientSocket = new TrackingEndpoint("Client");
            URI requestUri = server.getWsUri();
            Future<Session> fut = client.connect(clientSocket,requestUri);

            // wait for connect
            Session session = fut.get(FUTURE_TIMEOUT_SEC,TimeUnit.SECONDS);

            // Generate text frame
            RemoteEndpoint remote = session.getRemote();
            remote.sendText("session.isSecure");

            // Read frame (hopefully text frame)
            String captured = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat("Server.session.isSecure",captured,is("session.isSecure=true"));

            // Shutdown the socket
            clientSocket.close(StatusCode.NORMAL, "Normal");
        }
        finally
        {
            client.stop();
        }
    }

    /**
     * Test that server session.upgradeRequest.requestURI reports correctly
     * @throws Exception on test failure
     */
    @Test
    public void testServerSessionRequestURI() throws Exception
    {
        assertThat("server scheme",server.getWsUri().getScheme(),is("wss"));
        HttpClient httpClient = new HttpClient(server.getSslContextFactory());
        WebSocketClient client = new WebSocketClient(httpClient);
        client.addManaged(httpClient);
        try
        {
            client.setConnectTimeout(CONNECT_TIMEOUT);
            client.start();
    
            TrackingEndpoint clientSocket = new TrackingEndpoint("Client");
            URI requestUri = server.getWsUri().resolve("/deep?a=b");
            Future<Session> fut = client.connect(clientSocket,requestUri);

            // wait for connect
            Session session = fut.get(FUTURE_TIMEOUT_SEC,TimeUnit.SECONDS);

            // Generate text frame
            RemoteEndpoint remote = session.getRemote();
            remote.sendText("session.upgradeRequest.requestURI");

            // Read frame (hopefully text frame)
            String captured = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            String expected = String.format("session.upgradeRequest.requestURI=%s",requestUri.toASCIIString());
            assertThat("session.upgradeRequest.requestURI",captured,is(expected));

            // Shutdown the socket
            clientSocket.close(StatusCode.NORMAL, "Normal");
        }
        finally
        {
            client.stop();
        }
    }
}
