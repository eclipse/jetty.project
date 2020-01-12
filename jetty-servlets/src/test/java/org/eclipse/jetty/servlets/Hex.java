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

package org.eclipse.jetty.servlets;

public final class Hex
{
    private static final char[] hexcodes = "0123456789abcdef".toCharArray();

    public static byte[] asByteArray(String id, int size)
    {
        if ((id.length() < 0) || (id.length() > (size * 2)))
        {
            throw new IllegalArgumentException(String.format("Invalid ID length of <%d> expected range of <0> to <%d>", id.length(), (size * 2)));
        }

        byte[] buf = new byte[size];
        byte hex;
        int len = id.length();

        int idx = (int)Math.floor(((size * 2) - (double)len) / 2);
        int i = 0;
        if ((len % 2) != 0)
        { // deal with odd numbered chars
            i -= 1;
        }

        for (; i < len; i++)
        {
            hex = 0;
            if (i >= 0)
            {
                hex = (byte)(Character.digit(id.charAt(i), 16) << 4);
            }
            i++;
            hex += (byte)(Character.digit(id.charAt(i), 16));

            buf[idx] = hex;
            idx++;
        }

        return buf;
    }

    public static String asHex(byte[] buf)
    {
        int len = buf.length;
        char[] out = new char[len * 2];
        for (int i = 0; i < len; i++)
        {
            out[i * 2] = hexcodes[(buf[i] & 0xF0) >> 4];
            out[(i * 2) + 1] = hexcodes[(buf[i] & 0x0F)];
        }
        return String.valueOf(out);
    }

    private Hex()
    {
        /* prevent instantiation */
    }
}
