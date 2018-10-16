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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.tests.LocalFuzzer;
import org.eclipse.jetty.websocket.tests.WSServer;
import org.eclipse.jetty.websocket.tests.server.jsr356.sockets.IdleTimeoutOnOpenEndpoint;
import org.eclipse.jetty.websocket.tests.server.jsr356.sockets.IdleTimeoutOnOpenSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IdleTimeoutTest
{
    private static WSServer server;
    
    @BeforeAll
    public static void setupServer() throws Exception
    {
        server = new WSServer(MavenTestingUtils.getTargetTestingPath(IdleTimeoutTest.class.getName()), "app");
        server.copyWebInf("idle-timeout-config-web.xml");
        // the endpoint (extends javax.websocket.Endpoint)
        server.copyClass(IdleTimeoutOnOpenEndpoint.class);
        // the configuration that adds the endpoint
        server.copyClass(IdleTimeoutContextListener.class);
        // the annotated socket
        server.copyClass(IdleTimeoutOnOpenSocket.class);
        
        server.start();
        
        WebAppContext webapp = server.createWebAppContext();
        server.deployWebapp(webapp);
        // wsb.dump();
    }
    
    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }
    
    private void assertConnectionTimeout(String requestPath) throws Exception
    {
        try (LocalFuzzer session = server.newLocalFuzzer(requestPath))
        {
            // wait 1 second to allow timeout to fire off
            TimeUnit.SECONDS.sleep(1);
            
            session.sendFrames(new TextFrame().setPayload("You shouldn't be there"));
            
            BlockingQueue<WebSocketFrame> framesQueue = session.getOutputFrames();
            WebSocketFrame frame = framesQueue.poll(1, TimeUnit.SECONDS);
            assertThat("Frame.opCode", frame.getOpCode(), is(OpCode.CLOSE));
            CloseInfo closeInfo = new CloseInfo(frame);
            assertThat("Close.statusCode", closeInfo.getStatusCode(), is(StatusCode.SHUTDOWN));
            assertThat("Close.reason", closeInfo.getReason(), containsString("Timeout"));
        }
    }
    
    @Test
    public void testAnnotated() throws Exception
    {
        assertConnectionTimeout("/app/idle-onopen-socket");
    }
    
    @Test
    public void testEndpoint() throws Exception
    {
        assertConnectionTimeout("/app/idle-onopen-endpoint");
    }
}
