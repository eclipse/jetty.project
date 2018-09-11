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

package org.eclipse.jetty.websocket.core;

import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.websocket.core.frames.OpCode;
import org.junit.Assert;

public class RawFrameBuilder
{
    public static void putOpFin(ByteBuffer buf, byte opcode, boolean fin)
    {
        byte b = 0x00;
        if (fin)
        {
            b |= 0x80;
        }
        b |= opcode & 0x0F;
        buf.put(b);
    }

    public static void putLengthAndMask(ByteBuffer buf, int length, byte mask[])
    {
        if (mask != null)
        {
            Assert.assertThat("Mask.length",mask.length,is(4));
            putLength(buf,length,(mask != null));
            buf.put(mask);
        }
        else
        {
            putLength(buf,length,false);
        }
    }

    public static void mask(final byte[] data, final byte mask[])
    {
        Assert.assertThat("Mask.length",mask.length,is(4));
        int len = data.length;
        for (int i = 0; i < len; i++)
            data[i] ^= mask[i % 4];
    }

    public static void putLength(ByteBuffer buf, int length, boolean masked)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        byte b = (masked?(byte)0x80:0x00);

        // write the uncompressed length
        if (length > 0xFF_FF)
        {
            buf.put((byte)(b | 0x7F));
            buf.put((byte)0x00);
            buf.put((byte)0x00);
            buf.put((byte)0x00);
            buf.put((byte)0x00);
            buf.put((byte)((length >> 24) & 0xFF));
            buf.put((byte)((length >> 16) & 0xFF));
            buf.put((byte)((length >> 8) & 0xFF));
            buf.put((byte)(length & 0xFF));
        }
        else if (length >= 0x7E)
        {
            buf.put((byte)(b | 0x7E));
            buf.put((byte)(length >> 8));
            buf.put((byte)(length & 0xFF));
        }
        else
        {
            buf.put((byte)(b | length));
        }
    }

    public static void putMask(ByteBuffer buf, byte mask[])
    {
        Assert.assertThat("Mask.length",mask.length,is(4));
        buf.put(mask);
    }
    
    public static void putPayloadLength(ByteBuffer buf, int length)
    {
        putLength(buf,length,true);
    }
    
    public static byte[] buildTextFrame(String message, boolean masked)
    {
        ByteBuffer buffer = BufferUtil.allocate(2048);
        BufferUtil.clearToFill(buffer);
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        RawFrameBuilder.putOpFin(buffer,OpCode.TEXT,true);
        putLength(buffer,bytes.length,masked);
        if (masked)
        {
            byte[] mask = new byte[4];
            // ThreadLocalRandom.current().nextBytes(mask);
            buffer.put(mask);
            mask(bytes,mask);
        }
        buffer.put(bytes);
        BufferUtil.flipToFlush(buffer,0);
        return BufferUtil.toArray(buffer);
    }
    
    public static byte[] buildClose(CloseStatus status,boolean masked)
    {
        ByteBuffer buffer = BufferUtil.allocate(2048);
        BufferUtil.clearToFill(buffer);
        
        byte[] bytes = status==null?null:BufferUtil.toArray(status.asPayloadBuffer());
        RawFrameBuilder.putOpFin(buffer,OpCode.CLOSE,true);
        putLength(buffer,bytes==null?0:bytes.length,masked);
        if (masked)
        {
            byte[] mask = new byte[4];
            ThreadLocalRandom.current().nextBytes(mask);
            buffer.put(mask);
            mask(bytes,mask);
        }
        if (bytes!=null)
            buffer.put(bytes);
        BufferUtil.flipToFlush(buffer,0);
        return BufferUtil.toArray(buffer);
    }
    
}
