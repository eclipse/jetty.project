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

import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.tests.TrackingEndpoint;
import org.eclipse.jetty.websocket.tests.WSServer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test Echo of Large messages, targeting the {@link javax.websocket.Session#setMaxTextMessageBufferSize(int)} functionality
 */
@Disabled
@ExtendWith(WorkDirExtension.class)
public class LargeAnnotatedTest
{
    @ServerEndpoint(value = "/echo/large")
    public static class LargeEchoConfiguredSocket
    {
        @OnMessage(maxMessageSize = 128 * 1024)
        public String echo(String msg)
        {
            return msg;
        }
    }

    public WorkDir testdir;

    @Test
    public void testEcho() throws Exception
    {
        WSServer wsb = new WSServer(testdir,"app");
        wsb.createWebInf();
        wsb.copyEndpoint(LargeEchoConfiguredSocket.class);

        try
        {
            wsb.start();
            URI uri = wsb.getServerUri();

            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);

            WebSocketClient client = new WebSocketClient();
            try
            {
                client.getPolicy().setMaxTextMessageSize(128*1024);
                client.start();
    
                TrackingEndpoint clientSocket = new TrackingEndpoint("Client");
                
                Future<Session> clientConnectFuture = client.connect(clientSocket,uri.resolve("/app/echo/large"));
                // wait for connect
                Session clientSession = clientConnectFuture.get(1,TimeUnit.SECONDS);
                
                // The message size should be bigger than default, but smaller than the limit that LargeEchoSocket specifies
                byte txt[] = new byte[100 * 1024];
                Arrays.fill(txt,(byte)'o');
                String msg = new String(txt,StandardCharsets.UTF_8);
                clientSession.getRemote().sendString(msg);
                
                // Receive echo
                String incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Expected message",incomingMessage,is(msg));
                
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
