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

package org.eclipse.jetty.websocket.javax.tests.server;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.websocket.CloseReason;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.javax.tests.WSServer;
import org.eclipse.jetty.websocket.javax.tests.framehandlers.FrameHandlerTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(WorkDirExtension.class)
public class OnMessageReturnTest
{
    @ServerEndpoint(value = "/echoreturn")
    public static class EchoReturnEndpoint
    {
        private javax.websocket.Session session = null;
        public CloseReason close = null;
        public LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

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

    @Test
    public void testEchoReturn() throws Exception
    {
        WSServer wsb = new WSServer(testdir.getPath(), "app");
        wsb.copyWebInf("empty-web.xml");
        wsb.copyClass(EchoReturnEndpoint.class);

        try
        {
            wsb.start();
            URI uri = wsb.getWsUri();

            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);

            WebSocketCoreClient client = new WebSocketCoreClient();
            try
            {
                client.start();

                FrameHandlerTracker clientSocket = new FrameHandlerTracker();
                Future<FrameHandler.CoreSession> clientConnectFuture = client.connect(clientSocket, uri.resolve("/app/echoreturn"));

                // wait for connect
                FrameHandler.CoreSession coreSession = clientConnectFuture.get(5, TimeUnit.SECONDS);
                try
                {
                    // Send message
                    coreSession.sendFrame(new Frame(OpCode.TEXT).setPayload("Hello World"), Callback.NOOP, false);

                    // Confirm response
                    String incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
                    assertThat("Expected message", incomingMessage, is("Hello World"));
                }
                finally
                {
                    coreSession.close(Callback.NOOP);
                }
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
