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

package org.eclipse.jetty.websocket.javax.common.messages;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.MessageTooLargeException;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketSession;

public class StringMessageSink extends AbstractMessageSink
{
    private static final Logger LOG = Log.getLogger(StringMessageSink.class);
    private Utf8StringBuilder utf;
    private int size;

    public StringMessageSink(JavaxWebSocketSession session, MethodHandle methodHandle)
    {
        super(session, methodHandle);
        this.size = 0;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            if (frame.hasPayload())
            {
                ByteBuffer payload = frame.getPayload();

                size += payload.remaining();
                if (session.getMaxTextMessageBufferSize() > 0 && size > session.getMaxTextMessageBufferSize())
                {
                    throw new MessageTooLargeException(String.format("Binary message too large: (actual) %,d > (configured max text buffer size) %,d",
                        size, session.getMaxTextMessageBufferSize()));
                }

                if (utf == null)
                    utf = new Utf8StringBuilder(1024);

                if (LOG.isDebugEnabled())
                    LOG.debug("Raw Payload {}", BufferUtil.toDetailString(payload));

                // allow for fast fail of BAD utf (incomplete utf will trigger on messageComplete)
                utf.append(payload);
            }

            if (frame.isFin())
            {
                // notify event
                if (utf != null)
                    methodHandle.invoke(utf.toString());
                else
                    methodHandle.invoke("");

                // reset
                size = 0;
                utf = null;
            }

            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
    }
}
