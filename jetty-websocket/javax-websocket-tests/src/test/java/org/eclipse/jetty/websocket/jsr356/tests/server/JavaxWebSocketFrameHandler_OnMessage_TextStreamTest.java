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

package org.eclipse.jetty.websocket.jsr356.tests.server;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.websocket.core.DummyCoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.jsr356.JavaxWebSocketFrameHandler;
import org.eclipse.jetty.websocket.jsr356.UpgradeRequest;
import org.eclipse.jetty.websocket.jsr356.UpgradeRequestAdapter;
import org.eclipse.jetty.websocket.jsr356.UpgradeResponse;
import org.eclipse.jetty.websocket.jsr356.UpgradeResponseAdapter;
import org.eclipse.jetty.websocket.jsr356.tests.WSEventTracker;
import org.junit.jupiter.api.Test;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JavaxWebSocketFrameHandler_OnMessage_TextStreamTest extends AbstractJavaxWebSocketServerFrameHandlerTest
{
    @SuppressWarnings("Duplicates")
    private <T extends WSEventTracker> T performOnMessageInvocation(T socket, Consumer<JavaxWebSocketFrameHandler> func) throws Exception
    {
        UpgradeRequest request = new UpgradeRequestAdapter(URI.create("http://localhost:8080/msg/foo"));
        UpgradeResponse response = new UpgradeResponseAdapter();
        CompletableFuture<Session> futureSession = new CompletableFuture<>();

        // Establish endpoint function
        JavaxWebSocketFrameHandler frameHandler = container.newFrameHandler(socket, request, response, futureSession);
        frameHandler.onOpen(new DummyCoreSession());
        func.accept(frameHandler);
        return socket;
    }

    @ServerEndpoint("/msg")
    public static class MessageStreamSocket extends WSEventTracker
    {
        @OnMessage
        public void onMessage(Reader stream)
        {
            try
            {
                String msg = IO.toString(stream);
                addEvent("onMessage(%s) = \"%s\"", stream.getClass().getSimpleName(), msg);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    
    @Test
    public void testInvokeMessageStream() throws Exception
    {
        MessageStreamSocket socket = performOnMessageInvocation(new MessageStreamSocket(), (endpoint) ->
        {
            try
            {
                endpoint.onFrame(new Frame(OpCode.TEXT).setPayload("Hello World").setFin(true), Callback.NOOP);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        String msg = socket.events.poll(1, TimeUnit.SECONDS);
        assertThat("Message", msg, is("onMessage(MessageReader) = \"Hello World\""));
    }
    
    @ServerEndpoint("/msg/{param}")
    public static class MessageStreamParamSocket extends WSEventTracker
    {
        @OnMessage
        public String onMessage(Reader stream, @PathParam("param") String param) throws IOException
        {
            try
            {
                String msg = IO.toString(stream);
                addEvent("onMessage(%s,%s) = \"%s\"", stream.getClass().getSimpleName(), param, msg);
                return msg;
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    
    @Test
    public void testInvokeMessageStreamParam() throws Exception
    {
        uriParams.put("param", "foo");
        MessageStreamParamSocket socket = performOnMessageInvocation(new MessageStreamParamSocket(), (endpoint) ->
        {
            try
            {
                endpoint.onFrame(new Frame(OpCode.TEXT).setPayload("Hello World").setFin(true), Callback.NOOP);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        String msg = socket.events.poll(1, TimeUnit.SECONDS);
        assertThat("Message", msg, is("onMessage(MessageReader,foo) = \"Hello World\""));
    }
    
}
