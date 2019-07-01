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

package org.eclipse.jetty.websocket.core.extensions;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.toolchain.test.ByteBufferAssert;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.websocket.core.Behavior;
import org.eclipse.jetty.websocket.core.Extension;
import org.eclipse.jetty.websocket.core.ExtensionConfig;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.IncomingFramesCapture;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.TestMessageHandler;
import org.eclipse.jetty.websocket.core.WebSocketExtensionRegistry;
import org.eclipse.jetty.websocket.core.internal.ExtensionStack;
import org.eclipse.jetty.websocket.core.internal.Negotiated;
import org.eclipse.jetty.websocket.core.internal.Parser;
import org.eclipse.jetty.websocket.core.internal.WebSocketCoreSession;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ExtensionTool
{
    public class Tester
    {
        private String requestedExtParams;
        private ExtensionConfig extConfig;
        private Extension ext;
        private Parser parser;
        private IncomingFramesCapture capture;

        private Tester(String parameterizedExtension)
        {
            this.requestedExtParams = parameterizedExtension;
            this.extConfig = ExtensionConfig.parse(parameterizedExtension);
            Class<?> extClass = factory.getExtension(extConfig.getName());
            assertThat("extClass", extClass, notNullValue());

            this.capture = new IncomingFramesCapture();
            this.parser = new Parser(new MappedByteBufferPool());
        }

        public String getRequestedExtParams()
        {
            return requestedExtParams;
        }

        public void assertNegotiated(String expectedNegotiation)
        {
            this.ext = factory.newInstance(objectFactory, bufferPool, extConfig);
            this.ext.setNextIncomingFrames(capture);
            this.ext.setWebSocketCoreSession(newWebSocketCoreSession());
        }

        public void parseIncomingHex(String... rawhex)
        {
            int parts = rawhex.length;
            byte net[];

            for (int i = 0; i < parts; i++)
            {
                String hex = rawhex[i].replaceAll("\\s*(0x)?", "");
                net = TypeUtil.fromHexString(hex);

                ByteBuffer buffer = ByteBuffer.wrap(net);
                while (BufferUtil.hasContent(buffer))
                {
                    Frame frame = parser.parse(buffer);
                    if (frame == null)
                        break;
                    ext.onFrame(frame, Callback.from(() ->
                    {
                    }, Assertions::fail));
                }
            }
        }

        public void assertHasFrames(String... textFrames)
        {
            Frame frames[] = new Frame[textFrames.length];
            for (int i = 0; i < frames.length; i++)
            {
                frames[i] = new Frame(OpCode.TEXT).setPayload(textFrames[i]);
            }
            assertHasFrames(frames);
        }

        public void assertHasFrames(Frame... expectedFrames)
        {
            int expectedCount = expectedFrames.length;
            assertThat("Frame Count", capture.frames.size(), is(expectedCount));

            for (int i = 0; i < expectedCount; i++)
            {
                Frame actual = capture.frames.poll();

                String prefix = String.format("frame[%d]", i);
                assertThat(prefix + ".opcode", actual.getOpCode(), Matchers.is(expectedFrames[i].getOpCode()));
                assertThat(prefix + ".fin", actual.isFin(), Matchers.is(expectedFrames[i].isFin()));
                assertThat(prefix + ".rsv1", actual.isRsv1(), is(false));
                assertThat(prefix + ".rsv2", actual.isRsv2(), is(false));
                assertThat(prefix + ".rsv3", actual.isRsv3(), is(false));

                ByteBuffer expected = expectedFrames[i].getPayload().slice();
                assertThat(prefix + ".payloadLength", actual.getPayloadLength(), is(expected.remaining()));
                ByteBufferAssert.assertEquals(prefix + ".payload", expected, actual.getPayload().slice());
            }
        }
    }

    private final DecoratedObjectFactory objectFactory;
    private final ByteBufferPool bufferPool;
    private final WebSocketExtensionRegistry factory;

    public ExtensionTool(ByteBufferPool bufferPool)
    {
        this.objectFactory = new DecoratedObjectFactory();
        this.bufferPool = bufferPool;
        this.factory = new WebSocketExtensionRegistry();
    }

    public Tester newTester(String parameterizedExtension)
    {
        return new Tester(parameterizedExtension);
    }

    private WebSocketCoreSession newWebSocketCoreSession()
    {
        ByteBufferPool bufferPool = new MappedByteBufferPool();
        ExtensionStack exStack = new ExtensionStack(new WebSocketExtensionRegistry(), Behavior.SERVER);
        exStack.negotiate(new DecoratedObjectFactory(), bufferPool, new LinkedList<>(), new LinkedList<>());
        WebSocketCoreSession coreSession = new WebSocketCoreSession(new TestMessageHandler(), Behavior.SERVER, Negotiated.from(exStack));
        return coreSession;
    }
}
