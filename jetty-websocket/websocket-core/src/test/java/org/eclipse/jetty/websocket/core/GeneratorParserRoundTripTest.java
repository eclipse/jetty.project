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

package org.eclipse.jetty.websocket.core;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.websocket.core.internal.Generator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratorParserRoundTripTest
{
    private ByteBufferPool bufferPool = new MappedByteBufferPool();

    @Test
    public void testParserAndGenerator() throws Exception
    {
        Generator gen = new Generator();
        ParserCapture capture = new ParserCapture();

        String message = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

        ByteBuffer out = bufferPool.acquire(8192, false);
        try
        {
            // Generate Buffer
            Frame frame = new Frame(OpCode.TEXT).setPayload(message);
            gen.generateHeader(frame, out);
            gen.generatePayload(frame, out);

            // Parse Buffer
            capture.parse(out);
        }
        finally
        {
            bufferPool.release(out);
        }

        // Validate
        Frame txt = capture.framesQueue.poll(1, TimeUnit.SECONDS);
        assertThat("Text parsed", txt.getPayloadAsUTF8(), is(message));
    }

    @Test
    public void testParserAndGeneratorMasked() throws Exception
    {
        Generator gen = new Generator();
        ParserCapture capture = new ParserCapture(true, Behavior.SERVER);

        String message = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

        ByteBuffer out = bufferPool.acquire(8192, false);
        try
        {
            // Setup Frame
            Frame frame = new Frame(OpCode.TEXT).setPayload(message);

            // Add masking
            byte[] mask = new byte[4];
            Arrays.fill(mask, (byte)0xFF);
            frame.setMask(mask);

            // Generate Buffer
            gen.generateHeader(frame, out);
            gen.generatePayload(frame, out);

            // Parse Buffer
            capture.parse(out);
        }
        finally
        {
            bufferPool.release(out);
        }

        // Validate
        Frame txt = capture.framesQueue.poll(1, TimeUnit.SECONDS);
        assertTrue(txt.isMasked(), "Text.isMasked");
        assertThat("Text parsed", txt.getPayloadAsUTF8(), is(message));
    }
}
