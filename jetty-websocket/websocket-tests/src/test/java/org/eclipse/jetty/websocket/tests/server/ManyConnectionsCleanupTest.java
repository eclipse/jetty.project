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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.tests.Defaults;
import org.eclipse.jetty.websocket.tests.SimpleServletServer;
import org.eclipse.jetty.websocket.tests.TrackingEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Tests various close scenarios that should result in Open Session cleanup
 */
public class ManyConnectionsCleanupTest
{
    static class AbstractCloseSocket extends WebSocketAdapter
    {
        public CountDownLatch closeLatch = new CountDownLatch(1);
        public String closeReason = null;
        public int closeStatusCode = -1;
        public List<Throwable> errors = new ArrayList<>();

        @Override
        public void onWebSocketClose(int statusCode, String reason)
        {
            LOG.debug("onWebSocketClose({}, {})", statusCode, reason);
            this.closeStatusCode = statusCode;
            this.closeReason = reason;
            closeLatch.countDown();
        }

        @Override
        public void onWebSocketError(Throwable cause)
        {
            errors.add(cause);
        }
    }

    @SuppressWarnings("serial")
    public static class CloseServlet extends WebSocketServlet implements WebSocketCreator
    {
        private WebSocketServerFactory serverFactory;
        private AtomicInteger calls = new AtomicInteger(0);

        @Override
        public void configure(WebSocketServletFactory factory)
        {
            factory.setCreator(this);
            if (factory instanceof WebSocketServerFactory)
            {
                this.serverFactory = (WebSocketServerFactory) factory;
            }
        }

        @Override
        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
        {
            if (req.hasSubProtocol("fastclose"))
            {
                closeSocket = new FastCloseSocket(calls);
                return closeSocket;
            }

            if (req.hasSubProtocol("fastfail"))
            {
                closeSocket = new FastFailSocket(calls);
                return closeSocket;
            }

            if (req.hasSubProtocol("container"))
            {
                closeSocket = new ContainerSocket(serverFactory, calls);
                return closeSocket;
            }
            return new RFC6455Socket();
        }
    }

    /**
     * On Message, return container information
     */
    public static class ContainerSocket extends AbstractCloseSocket
    {
        private static final Logger LOG = Log.getLogger(ManyConnectionsCleanupTest.ContainerSocket.class);
        private final WebSocketServerFactory container;
        private final AtomicInteger calls;
        private Session session;

        public ContainerSocket(WebSocketServerFactory container, AtomicInteger calls)
        {
            this.container = container;
            this.calls = calls;
        }

        @Override
        public void onWebSocketText(String message)
        {
            LOG.debug("onWebSocketText({})", message);
            calls.incrementAndGet();
            if (message.equalsIgnoreCase("openSessions"))
            {
                Collection<WebSocketSession> sessions = container.getOpenSessions();

                StringBuilder ret = new StringBuilder();
                ret.append("openSessions.size=").append(sessions.size()).append('\n');
                int idx = 0;
                for (WebSocketSession sess : sessions)
                {
                    ret.append('[').append(idx++).append("] ").append(sess.toString()).append('\n');
                }
                session.getRemote().sendStringByFuture(ret.toString());
                session.close(StatusCode.NORMAL, "ContainerSocket");
            }
            else if (message.equalsIgnoreCase("calls"))
            {
                session.getRemote().sendStringByFuture(String.format("calls=%,d", calls.get()));
            }
        }

        @Override
        public void onWebSocketConnect(Session sess)
        {
            LOG.debug("onWebSocketConnect({})", sess);
            this.session = sess;
        }
    }

    /**
     * On Connect, close socket
     */
    public static class FastCloseSocket extends AbstractCloseSocket
    {
        private static final Logger LOG = Log.getLogger(ManyConnectionsCleanupTest.FastCloseSocket.class);
        private final AtomicInteger calls;

        public FastCloseSocket(AtomicInteger calls)
        {
            this.calls = calls;
        }

        @Override
        public void onWebSocketConnect(Session sess)
        {
            LOG.debug("onWebSocketConnect({})", sess);
            calls.incrementAndGet();
            sess.close(StatusCode.NORMAL, "FastCloseServer");
        }
    }

    /**
     * On Connect, throw unhandled exception
     */
    public static class FastFailSocket extends AbstractCloseSocket
    {
        private static final Logger LOG = Log.getLogger(ManyConnectionsCleanupTest.FastFailSocket.class);
        private final AtomicInteger calls;

        public FastFailSocket(AtomicInteger calls)
        {
            this.calls = calls;
        }

        @Override
        public void onWebSocketConnect(Session sess)
        {
            LOG.debug("onWebSocketConnect({})", sess);
            calls.incrementAndGet();
            // Test failure due to unhandled exception
            // this should trigger a fast-fail closure during open/connect
            throw new RuntimeException("Intentional FastFail");
        }
    }

    private static final Logger LOG = Log.getLogger(ManyConnectionsCleanupTest.class);

    private static SimpleServletServer server;
    private static AbstractCloseSocket closeSocket;

    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new CloseServlet());
        server.start();
    }

    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    private WebSocketClient client;

    @BeforeEach
    public void startClient() throws Exception
    {
        client = new WebSocketClient();
        client.start();
    }

    @AfterEach
    public void stopClient() throws Exception
    {
        client.stop();
    }

    /**
     * Test session tracking (open + close + cleanup) (bug #474936)
     *
     * @throws Exception on test failure
     */
    @Test
    public void testOpenSessionCleanup(TestInfo testInfo) throws Exception
    {
        int iterationCount = 100;

        try(StacklessLogging ignore = new StacklessLogging(FastFailSocket.class, WebSocketSession.class))
        {
            for (int requests = 0; requests < iterationCount; requests++)
            {
                fastFail(testInfo);
                fastClose(testInfo);
                dropConnection(testInfo);
            }
        }

        URI wsUri = server.getServerUri();

        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo.getDisplayName());
        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("container");
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri, upgradeRequest);

        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        clientSession.getRemote().sendString("calls");
        clientSession.getRemote().sendString("openSessions");

        String incomingMessage;
        incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat("Should only have 1 open session", incomingMessage, containsString("calls=" + ((iterationCount * 2) + 1)));

        incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat("Should only have 1 open session", incomingMessage, containsString("openSessions.size=1\n"));

        clientSocket.awaitCloseEvent("Client");
        clientSocket.assertCloseInfo("Client", StatusCode.NORMAL, anything());
    }

    @SuppressWarnings("Duplicates")
    private void fastClose(TestInfo testInfo) throws Exception
    {
        client.setMaxIdleTimeout(1000);
        URI wsUri = server.getServerUri();

        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo.getDisplayName());
        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("fastclose");
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri, upgradeRequest);

        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        clientSocket.awaitCloseEvent("Client");
        clientSocket.assertCloseInfo("Client", StatusCode.NORMAL, anything());

        clientSession.close();
    }

    private void fastFail(TestInfo testInfo) throws Exception
    {
        client.setMaxIdleTimeout(1000);
        URI wsUri = server.getServerUri();

        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo.getDisplayName());
        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("fastfail");
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri, upgradeRequest);

        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        clientSocket.awaitCloseEvent("Client");
        clientSocket.assertCloseInfo("Client", StatusCode.SERVER_ERROR, anything());

        clientSession.close();
    }

    @SuppressWarnings("Duplicates")
    private void dropConnection(TestInfo testInfo) throws Exception
    {
        client.setMaxIdleTimeout(1000);
        URI wsUri = server.getServerUri();

        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo.getDisplayName());
        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("container");
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri, upgradeRequest);

        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        clientSession.close();
    }

}