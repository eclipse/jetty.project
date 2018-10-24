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

package org.eclipse.jetty.websocket.jsr356;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.jsr356.util.TextUtil;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JavaxWebSocketBasicRemote extends JavaxWebSocketRemoteEndpoint implements RemoteEndpoint.Basic
{
    private static final Logger LOG = Log.getLogger(JavaxWebSocketBasicRemote.class);

    protected JavaxWebSocketBasicRemote(JavaxWebSocketSession session, FrameHandler.CoreSession channel)
    {
        super(session, channel);
    }

    @Override
    public OutputStream getSendStream() throws IOException
    {
        return newMessageOutputStream();
    }

    @Override
    public Writer getSendWriter() throws IOException
    {
        return newMessageWriter();
    }

    @Override
    public void sendBinary(ByteBuffer data) throws IOException
    {
        assertMessageNotNull(data);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("sendBinary({})", BufferUtil.toDetailString(data));
        }
        try (SharedBlockingCallback.Blocker b = session.getBlocking().acquire())
        {
            sendFrame(new Frame(OpCode.BINARY).setPayload(data), b, false);
        }
    }

    @Override
    public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException
    {
        assertMessageNotNull(partialByte);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("sendBinary({},{})", BufferUtil.toDetailString(partialByte), isLast);
        }
        try (SharedBlockingCallback.Blocker b = session.getBlocking().acquire())
        {
            Frame frame;
            switch (messageType)
            {
                case -1:
                    // New message!
                    frame = new Frame(OpCode.BINARY);
                    break;
                case OpCode.TEXT:
                    throw new IllegalStateException("Cannot send a partial BINARY message: TEXT message in progress");
                case OpCode.BINARY:
                    frame = new Frame(OpCode.CONTINUATION);
                    break;
                default:
                    throw new IllegalStateException("Cannot send a partial BINARY message: unrecognized active message type " + OpCode.name(messageType));
            }

            frame.setPayload(partialByte);
            frame.setFin(isLast);
            sendFrame(frame, b, false);
        }
    }

    @Override
    public void sendObject(Object data) throws IOException, EncodeException
    {
        try (SharedBlockingCallback.Blocker b = session.getBlocking().acquire())
        {
            super.sendObject(data, b);
        }
    }

    @Override
    public void sendText(String text) throws IOException
    {
        assertMessageNotNull(text);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("sendText({})", TextUtil.hint(text));
        }
        try (SharedBlockingCallback.Blocker b = session.getBlocking().acquire())
        {
            sendFrame(new Frame(OpCode.TEXT).setPayload(text), b, false);
        }
    }

    @Override
    public void sendText(String partialMessage, boolean isLast) throws IOException
    {
        assertMessageNotNull(partialMessage);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("sendText({},{})", TextUtil.hint(partialMessage), isLast);
        }
        try (SharedBlockingCallback.Blocker b = session.getBlocking().acquire())
        {
            Frame frame;
            switch (messageType)
            {
                case -1:
                    // New message!
                    frame = new Frame(OpCode.TEXT);
                    break;
                case OpCode.TEXT:
                    frame = new Frame(OpCode.CONTINUATION);
                    break;
                case OpCode.BINARY:
                    throw new IllegalStateException("Cannot send a partial TEXT message: BINARY message in progress");
                default:
                    throw new IllegalStateException("Cannot send a partial TEXT message: unrecognized active message type " + OpCode.name(messageType));
            }

            frame.setPayload(BufferUtil.toBuffer(partialMessage, UTF_8));
            frame.setFin(isLast);
            sendFrame(frame, b, false);
        }
    }
}
