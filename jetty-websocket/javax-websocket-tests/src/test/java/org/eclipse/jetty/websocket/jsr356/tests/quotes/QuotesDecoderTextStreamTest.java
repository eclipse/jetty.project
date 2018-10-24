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

package org.eclipse.jetty.websocket.jsr356.tests.quotes;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.jsr356.server.JavaxWebSocketServerContainerInitializer;
import org.eclipse.jetty.websocket.jsr356.tests.Fuzzer;
import org.eclipse.jetty.websocket.jsr356.tests.LocalServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests a {@link javax.websocket.Decoder.TextStream} automatic decoding to a Socket onMessage parameter
 */
public class QuotesDecoderTextStreamTest
{
    @ServerEndpoint(value = "/quotes/echo/string", decoders = QuotesDecoder.class)
    public static class QuotesEchoStringSocket
    {
        @SuppressWarnings("unused")
        @OnMessage
        public String onQuotes(Quotes q)
        {
            StringBuilder buf = new StringBuilder();
            buf.append("Author: ").append(q.getAuthor()).append('\n');
            for (String quote : q.getQuotes())
            {
                buf.append("Quote: ").append(quote).append('\n');
            }
            return buf.toString();
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
                ServerContainer container = JavaxWebSocketServerContainerInitializer.configureContext(context);
                container.addEndpoint(QuotesEchoStringSocket.class);
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
    public void testQuoteEchoString_Bulk() throws Exception
    {
        List<Frame> send = QuotesUtil.loadAsWebSocketFrames("quotes-ben.txt");
        send.add(CloseStatus.toFrame(CloseStatus.NORMAL));

        try (Fuzzer session = server.newNetworkFuzzer("/quotes/echo/string"))
        {
            session.sendBulk(send);

            BlockingQueue<Frame> framesQueue = session.getOutputFrames();
            Frame frame = framesQueue.poll(1, TimeUnit.SECONDS);
            assertThat("Frame.opCode", frame.getOpCode(), is(OpCode.TEXT));
            assertThat("Frame.text-payload", frame.getPayloadAsUTF8(), allOf(
                containsString("Author: Benjamin Franklin"),
                containsString("Quote: Our new Constitution is now established")
            ));
        }
    }

    @Test
    public void testQuoteEchoString_SmallSegments() throws Exception
    {
        List<Frame> send = QuotesUtil.loadAsWebSocketFrames("quotes-ben.txt");
        send.add(CloseStatus.toFrame(CloseStatus.NORMAL));

        try (Fuzzer session = server.newNetworkFuzzer("/quotes/echo/string"))
        {
            session.sendSegmented(send, 3);

            BlockingQueue<Frame> framesQueue = session.getOutputFrames();
            Frame frame = framesQueue.poll(1, TimeUnit.SECONDS);
            assertThat("Frame.opCode", frame.getOpCode(), is(OpCode.TEXT));
            assertThat("Frame.text-payload", frame.getPayloadAsUTF8(), allOf(
                containsString("Author: Benjamin Franklin"),
                containsString("Quote: Our new Constitution is now established")
            ));
        }
    }

    @Test
    public void testQuoteEchoString_FrameWise() throws Exception
    {
        List<Frame> send = QuotesUtil.loadAsWebSocketFrames("quotes-ben.txt");
        send.add(CloseStatus.toFrame(CloseStatus.NORMAL));

        try (Fuzzer session = server.newNetworkFuzzer("/quotes/echo/string"))
        {
            session.sendFrames(send);

            BlockingQueue<Frame> framesQueue = session.getOutputFrames();
            Frame frame = framesQueue.poll(1, TimeUnit.SECONDS);
            assertThat("Frame.opCode", frame.getOpCode(), is(OpCode.TEXT));
            assertThat("Frame.text-payload", frame.getPayloadAsUTF8(), allOf(
                containsString("Author: Benjamin Franklin"),
                containsString("Quote: Our new Constitution is now established")
            ));
        }
    }
}
