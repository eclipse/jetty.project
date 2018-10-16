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

package org.eclipse.jetty.websocket.tests;

import org.eclipse.jetty.io.LeakTrackingByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.Parser;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratorParserRoundtripTest
{
    public LeakTrackingByteBufferPool bufferPool = new LeakTrackingByteBufferPool(new MappedByteBufferPool());
    
    @Test
    public void testParserAndGenerator() throws Exception
    {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();
        Generator gen = new Generator(policy,bufferPool);
        ParserCapture capture = new ParserCapture();
        Parser parser = new Parser(policy,bufferPool,capture);

        String message = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

        ByteBuffer out = bufferPool.acquire(8192,false);
        try
        {
            // Generate Buffer
            BufferUtil.flipToFill(out);
            WebSocketFrame frame = new TextFrame().setPayload(message);
            ByteBuffer header = gen.generateHeaderBytes(frame);
            ByteBuffer payload = frame.getPayload();
            out.put(header);
            out.put(payload);

            // Parse Buffer
            BufferUtil.flipToFlush(out,0);
            parser.parse(out);
        }
        finally
        {
            bufferPool.release(out);
        }

        // Validate
        TextFrame txt = (TextFrame)capture.framesQueue.poll(1, TimeUnit.SECONDS);
        assertThat("Text parsed",txt.getPayloadAsUTF8(),is(message));

    }

    @Test
    public void testParserAndGeneratorMasked() throws Exception
    {
        Generator gen = new Generator(WebSocketPolicy.newClientPolicy(),bufferPool);
        ParserCapture capture = new ParserCapture();
        Parser parser = new Parser(WebSocketPolicy.newServerPolicy(),bufferPool,capture);

        String message = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

        ByteBuffer out = bufferPool.acquire(8192,false);
        BufferUtil.flipToFill(out);
        try
        {
            // Setup Frame
            WebSocketFrame frame = new TextFrame().setPayload(message);

            // Add masking
            byte mask[] = new byte[4];
            Arrays.fill(mask,(byte)0xFF);
            frame.setMask(mask);

            // Generate Buffer
            ByteBuffer header = gen.generateHeaderBytes(frame);
            ByteBuffer payload = frame.getPayload();
            out.put(header);
            out.put(payload);

            // Parse Buffer
            BufferUtil.flipToFlush(out,0);
            parser.parse(out);
        }
        finally
        {
            bufferPool.release(out);
        }

        // Validate
        TextFrame txt = (TextFrame)capture.framesQueue.poll(1, TimeUnit.SECONDS);
        assertTrue(txt.isMasked(), "Text.isMasked");
        assertThat("Text parsed",txt.getPayloadAsUTF8(),is(message));

    }
}
