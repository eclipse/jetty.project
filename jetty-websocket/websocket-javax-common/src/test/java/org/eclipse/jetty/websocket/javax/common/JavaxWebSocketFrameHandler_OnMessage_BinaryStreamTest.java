//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
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

package org.eclipse.jetty.websocket.javax.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.javax.common.sockets.TrackingSocket;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JavaxWebSocketFrameHandler_OnMessage_BinaryStreamTest extends AbstractJavaxWebSocketFrameHandlerTest
{
    @SuppressWarnings("Duplicates")
    private TrackingSocket performOnMessageInvocation(TrackingSocket socket, Function<JavaxWebSocketFrameHandler, Void> func) throws Exception
    {
        JavaxWebSocketFrameHandler localEndpoint = newJavaxFrameHandler(socket);

        // This invocation is the same for all tests
        localEndpoint.onOpen(coreSession, Callback.NOOP);

        func.apply(localEndpoint);

        return socket;
    }

    @ClientEndpoint
    public static class MessageStreamSocket extends TrackingSocket
    {
        @OnMessage
        public void onMessage(InputStream stream)
        {
            try
            {
                String msg = IO.toString(stream, StandardCharsets.UTF_8);
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
        TrackingSocket socket = performOnMessageInvocation(new MessageStreamSocket(), (endpoint) ->
        {
            try
            {
                endpoint.onFrame(new Frame(OpCode.BINARY).setPayload("Hello World").setFin(true), Callback.NOOP);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Unexpected error", e);
            }
            return null;
        });

        String event = socket.events.poll(1, TimeUnit.SECONDS);
        assertThat("event", event, is("onMessage(MessageInputStream) = \"Hello World\""));
    }
}
