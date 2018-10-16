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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.BinaryFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.eclipse.jetty.websocket.tests.LocalFuzzer;
import org.eclipse.jetty.websocket.tests.LocalServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test various {@link javax.websocket.Decoder.BinaryStream Decoder.BinaryStream} echo behavior of Java InputStreams
 */
public class InputStreamEchoTest
{
    private static final Logger LOG = Log.getLogger(InputStreamEchoTest.class);
    
    public static class BaseSocket
    {
        @OnError
        public void onError(Throwable cause) throws IOException
        {
            LOG.warn("Error", cause);
        }
    }
    
    @SuppressWarnings("unused")
    @ServerEndpoint("/echo/stream")
    public static class InputStreamSocket extends BaseSocket
    {
        @OnMessage
        public String onStream(InputStream stream) throws IOException
        {
            return IO.toString(stream);
        }
    }
    
    @SuppressWarnings("unused")
    @ServerEndpoint("/echo/stream-param/{param}")
    public static class InputStreamParamSocket extends BaseSocket
    {
        @OnMessage
        public String onStream(InputStream stream, @PathParam("param") String param) throws IOException
        {
            StringBuilder msg = new StringBuilder();
            msg.append(IO.toString(stream));
            msg.append('|');
            msg.append(param);
            return msg.toString();
        }
    }
    
    private static LocalServer server;
    
    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new LocalServer()
        {
            @Override
            protected void configureServletContextHandler(ServletContextHandler context) throws Exception
            {
                ServerContainer container = WebSocketServerContainerInitializer.configureContext(context);
                container.addEndpoint(InputStreamSocket.class);
                container.addEndpoint(InputStreamParamSocket.class);
            }
        };
        server.start();
    }
    
    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }
    
    @Test
    public void testInputStreamSocket() throws Exception
    {
        String requestPath = "/echo/stream";
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BinaryFrame().setPayload("Hello World"));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        
        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("Hello World"));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        
        try (LocalFuzzer session = server.newLocalFuzzer(requestPath))
        {
            session.sendBulk(send);
            session.expect(expect);
        }
    }
    
    @Test
    public void testInputStreamParamSocket() throws Exception
    {
        String requestPath = "/echo/stream-param/Every%20Person";
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BinaryFrame().setPayload("Hello World"));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        
        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new TextFrame().setPayload("Hello World|Every Person"));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        
        try (LocalFuzzer session = server.newLocalFuzzer(requestPath))
        {
            session.sendBulk(send);
            session.expect(expect);
        }
    }
}
