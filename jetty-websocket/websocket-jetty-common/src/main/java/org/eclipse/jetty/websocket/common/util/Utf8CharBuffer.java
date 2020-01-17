//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.common.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Utf8Appendable;

/**
 * A CharBuffer wrapped with the Utf8Appendable logic.
 */
public class Utf8CharBuffer extends Utf8Appendable
{
    /**
     * Convenience method to wrap a ByteBuffer with a {@link Utf8CharBuffer}
     *
     * @param buffer the buffer to wrap
     * @return the Utf8ByteBuffer for the provided ByteBuffer
     */
    public static Utf8CharBuffer wrap(ByteBuffer buffer)
    {
        return new Utf8CharBuffer(buffer.asCharBuffer());
    }

    private final CharBuffer buffer;

    private Utf8CharBuffer(CharBuffer buffer)
    {
        super(buffer);
        this.buffer = buffer;
    }

    public void append(char[] cbuf, int offset, int size)
    {
        append(BufferUtil.toDirectBuffer(new String(cbuf, offset, size), StandardCharsets.UTF_8));
    }

    public void append(int c)
    {
        buffer.append((char)c);
    }

    public void clear()
    {
        buffer.position(0);
        buffer.limit(buffer.capacity());
    }

    public ByteBuffer getByteBuffer()
    {
        // remember settings
        int limit = buffer.limit();
        int position = buffer.position();

        // flip to flush
        buffer.limit(buffer.position());
        buffer.position(0);

        // get byte buffer
        ByteBuffer bb = StandardCharsets.UTF_8.encode(buffer);

        // restor settings
        buffer.limit(limit);
        buffer.position(position);

        return bb;
    }

    @Override
    public int length()
    {
        return buffer.capacity();
    }

    @Override
    public String getPartialString()
    {
        throw new UnsupportedOperationException("Cannot get Partial String from Utf8CharBuffer");
    }

    public int remaining()
    {
        return buffer.remaining();
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append("Utf8CharBuffer@").append(hashCode());
        str.append("[p=").append(buffer.position());
        str.append(",l=").append(buffer.limit());
        str.append(",c=").append(buffer.capacity());
        str.append(",r=").append(buffer.remaining());
        str.append("]");
        return str.toString();
    }
}
