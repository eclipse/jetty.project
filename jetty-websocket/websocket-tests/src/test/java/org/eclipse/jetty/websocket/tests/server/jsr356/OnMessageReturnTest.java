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

package org.eclipse.jetty.websocket.tests.server.jsr356;

import org.eclipse.jetty.io.LeakTrackingByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.tests.TrackingEndpoint;
import org.eclipse.jetty.websocket.tests.WSServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.websocket.CloseReason;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith( WorkDirExtension.class)
public class OnMessageReturnTest
{
    @ServerEndpoint(value = "/echoreturn")
    public static class EchoReturnEndpoint
    {
        private javax.websocket.Session session = null;
        public CloseReason close = null;
        public Queue<String> messageQueue = new ArrayDeque<>();

        public void onClose(CloseReason close)
        {
            this.close = close;
        }

        @OnMessage
        public String onMessage(String message)
        {
            this.messageQueue.offer(message);
            // Return the message
            return message;
        }

        @OnOpen
        public void onOpen(javax.websocket.Session session)
        {
            this.session = session;
        }

        public void sendText(String text) throws IOException
        {
            if (session != null)
            {
                session.getBasicRemote().sendText(text);
            }
        }
    }

    public WorkDir testdir;

    public LeakTrackingByteBufferPool bufferPool = new LeakTrackingByteBufferPool(new MappedByteBufferPool());

    @Test
    public void testEchoReturn() throws Exception
    {
        WSServer wsb = new WSServer(testdir,"app");
        wsb.copyWebInf("empty-web.xml");
        wsb.copyClass(EchoReturnEndpoint.class);

        try
        {
            wsb.start();
            URI uri = wsb.getServerUri();

            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);

            WebSocketClient client = new WebSocketClient(bufferPool);
            try
            {
                client.start();

                TrackingEndpoint clientSocket = new TrackingEndpoint("Client");
                Future<Session> clientConnectFuture = client.connect(clientSocket,uri.resolve("/app/echoreturn"));

                // wait for connect
                Session clientSession = clientConnectFuture.get(5,TimeUnit.SECONDS);

                // Send message
                clientSocket.getRemote().sendString("Hello World");

                // Confirm response
                String incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Expected message",incomingMessage,is("Hello World"));

                clientSession.close();
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            wsb.stop();
        }
    }

}
