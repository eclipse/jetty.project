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

package org.eclipse.jetty.websocket.util.messages;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.util.InvalidSignatureException;

public class PartialByteArrayMessageSink extends AbstractMessageSink
{
    public PartialByteArrayMessageSink(CoreSession session, MethodHandle methodHandle)
    {
        super(session, methodHandle);

        // byte[] buf, int offset, int length
        MethodType onMessageType = MethodType.methodType(Void.TYPE, byte[].class, int.class, int.class, boolean.class);
        if (methodHandle.type() != onMessageType)
        {
            throw InvalidSignatureException.build(onMessageType, methodHandle.type());
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            byte[] buffer;
            int offset = 0;
            int length = 0;

            if (frame.hasPayload())
            {
                ByteBuffer payload = frame.getPayload();
                length = payload.remaining();
                buffer = BufferUtil.toArray(payload);
            }
            else
            {
                buffer = new byte[0];
            }

            methodHandle.invoke(buffer, offset, length, frame.isFin());
            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
    }
}
