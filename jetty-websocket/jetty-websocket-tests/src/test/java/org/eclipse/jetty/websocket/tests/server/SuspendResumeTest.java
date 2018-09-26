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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.common.io.SuspendToken;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.tests.Fuzzer;
import org.eclipse.jetty.websocket.tests.SimpleServletServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.fail;

public class SuspendResumeTest
{
    @WebSocket
    public static class BackPressureEchoSocket
    {
        private Session session;
        
        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            this.session = session;
        }
        
        @OnWebSocketMessage
        public void onMessage(String message)
        {
            SuspendToken suspendToken = this.session.suspend();
            this.session.getRemote().sendString(message,
                    new WriteCallback()
                    {
                        @Override
                        public void writeSuccess()
                        {
                            suspendToken.resume();
                        }
                        
                        @Override
                        public void writeFailed(Throwable t)
                        {
                            fail(t.getMessage());
                        }
                    });
        }
    }
    
    public static class BackPressureEchoCreator implements WebSocketCreator
    {
        @Override
        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
        {
            return new BackPressureEchoSocket();
        }
    }
    
    public static class BackPressureServlet extends WebSocketServlet
    {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void configure(WebSocketServletFactory factory)
        {
            factory.setCreator(new BackPressureEchoCreator());
        }
    }
    
    private static SimpleServletServer server;
    
    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new SimpleServletServer(new BackPressureServlet());
        server.start();
    }
    
    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }
    
    @Test
    public void testSuspendResume_Bulk() throws Exception
    {
        assertTimeout(Duration.ofMillis(10000), ()->
        {
            List<Frame> send = new ArrayList<>();
            send.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            send.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            send.add(new Frame(OpCode.CLOSE));

            List<Frame> expect = new ArrayList<>();
            expect.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            expect.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            expect.add(new Frame(OpCode.CLOSE));

            try (Fuzzer session = server.newNetworkFuzzer())
            {
                session.sendBulk(send);
                session.expect(expect);
            }
        });
    }
    
    @Test
    public void testSuspendResume_SmallBuffers() throws Exception
    {
        assertTimeout(Duration.ofMillis(10000), ()->
        {
            List<Frame> send = new ArrayList<>();
            send.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            send.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            send.add(new Frame(OpCode.CLOSE));

            List<Frame> expect = new ArrayList<>();
            expect.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            expect.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            expect.add(new Frame(OpCode.CLOSE));

            try (Fuzzer session = server.newNetworkFuzzer())
            {
                session.sendSegmented(send, 2);
                session.expect(expect);
            }
        });
    }

    @Test
    public void testSuspendResume_AsFrames() throws Exception
    {
        assertTimeout(Duration.ofMillis(10000), ()->
        {
            List<Frame> send = new ArrayList<>();
            send.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            send.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            send.add(new Frame(OpCode.CLOSE));

            List<Frame> expect = new ArrayList<>();
            expect.add(new Frame(OpCode.TEXT).setPayload("echo1"));
            expect.add(new Frame(OpCode.TEXT).setPayload("echo2"));
            expect.add(new Frame(OpCode.CLOSE));

            try (Fuzzer session = server.newNetworkFuzzer())
            {
                session.sendFrames(send);
                session.expect(expect);
            }
        });
    }
}
