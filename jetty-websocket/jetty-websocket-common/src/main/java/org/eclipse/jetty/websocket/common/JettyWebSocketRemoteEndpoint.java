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

package org.eclipse.jetty.websocket.common;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.ProtocolException;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.BatchMode;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JettyWebSocketRemoteEndpoint implements org.eclipse.jetty.websocket.api.RemoteEndpoint
{
    private final FrameHandler.CoreSession channel;
    private byte messageType = -1;
    private final SharedBlockingCallback blocker = new SharedBlockingCallback();

    public JettyWebSocketRemoteEndpoint(FrameHandler.CoreSession channel)
    {
        this.channel = channel;
    }

    /**
     * Initiate close of the Remote with no status code (no payload)
     *
     * @since 10.0
     */
    public void close()
    {
        try (SharedBlockingCallback.Blocker b = blocker.acquire())
        {
            channel.close(b);
        }
        catch (IOException e)
        {
            channel.close(Callback.NOOP);
        }
    }

    /**
     * Initiate close of the Remote with specified status code and optional reason phrase
     *
     * @param statusCode the status code (must be valid and can be sent)
     * @param reason optional reason code
     * @since 10.0
     */
    public void close(int statusCode, String reason)
    {
        try (SharedBlockingCallback.Blocker b = blocker.acquire())
        {
            channel.close(statusCode, reason, b);
        }
        catch (IOException e)
        {
            channel.close(Callback.NOOP);
        }
    }

    protected FrameHandler.CoreSession getChannel()
    {
        return channel;
    }

    private void sendBlocking(Frame frame) throws IOException
    {
        try (SharedBlockingCallback.Blocker b = blocker.acquire())
        {
            channel.sendFrame(frame, b, BatchMode.OFF);
            b.block();
        }
    }

    @Override
    public void sendBinary(ByteBuffer data) throws IOException
    {
        sendBlocking(new Frame(OpCode.BINARY).setPayload(data));
    }

    @Override
    public void sendPartialBinary(ByteBuffer fragment, boolean isLast) throws IOException
    {
        try (SharedBlockingCallback.Blocker b = blocker.acquire())
        {
            sendPartialBinary(fragment, isLast, b);
            b.block();
        }
    }

    @Override
    public void sendPartialText(String fragment, boolean isLast) throws IOException
    {
        try (SharedBlockingCallback.Blocker b = blocker.acquire())
        {
            sendPartialText(fragment, isLast, b);
            b.block();
        }
    }

    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException
    {
        channel.sendFrame(new Frame(OpCode.PING).setPayload(applicationData), Callback.NOOP, BatchMode.OFF);
    }

    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException
    {
        channel.sendFrame(new Frame(OpCode.PONG).setPayload(applicationData), Callback.NOOP, BatchMode.OFF);
    }

    @Override
    public void sendText(String text) throws IOException
    {
        sendBlocking(new Frame(OpCode.TEXT).setPayload(text));
    }

    @Override
    public void sendBinary(ByteBuffer data, Callback callback)
    {
        channel.sendFrame(new Frame(OpCode.BINARY).setPayload(data), callback, BatchMode.OFF);
    }

    @Override
    public void sendPartialBinary(ByteBuffer fragment, boolean isLast, Callback callback)
    {
        Frame frame;
        switch (messageType)
        {
            case -1: // new message
                frame = new Frame(OpCode.BINARY);
                messageType = OpCode.BINARY;
                break;
            case OpCode.BINARY:
                frame = new Frame(OpCode.CONTINUATION);
                break;
            default:
                callback.failed(new ProtocolException("Attempt to send Partial Binary during active opcode " + messageType));
                return;
        }

        frame.setPayload(fragment);
        frame.setFin(isLast);

        channel.sendFrame(frame, callback, BatchMode.OFF);

        if (isLast)
        {
            messageType = -1;
        }
    }

    @Override
    public void sendPartialText(String fragment, boolean isLast, Callback callback)
    {
        Frame frame;
        switch (messageType)
        {
            case -1: // new message
                frame = new Frame(OpCode.BINARY);
                messageType = OpCode.TEXT;
                break;
            case OpCode.TEXT:
                frame = new Frame(OpCode.CONTINUATION);
                break;
            default:
                callback.failed(new ProtocolException("Attempt to send Partial Text during active opcode " + messageType));
                return;
        }

        frame.setPayload(BufferUtil.toBuffer(fragment, UTF_8));
        frame.setFin(isLast);

        channel.sendFrame(frame, callback, BatchMode.OFF);

        if (isLast)
        {
            messageType = -1;
        }
    }

    @Override
    public void sendText(String text, Callback callback)
    {
        channel.sendFrame(new Frame(OpCode.TEXT).setPayload(text), callback, BatchMode.OFF);
    }
}
