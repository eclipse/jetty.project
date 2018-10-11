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

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.nio.ByteBuffer;

import static org.eclipse.jetty.websocket.core.OpCode.PONG;

/**
 * Base level implementation of local WebSocket Endpoint Frame handling.
 * <p>
 *    This implementation assumes RFC6455 behavior with HTTP/1.1.
 *    NOTE: The introduction of WebSocket over HTTP/2 might change the behavior and implementation some.
 * </p>
 */
public class AbstractTestFrameHandler implements FrameHandler
{
    private Logger LOG = Log.getLogger(AbstractTestFrameHandler.class);
    private byte partial = OpCode.UNDEFINED;
    private Utf8StringBuilder utf8;
    private ByteBuffer byteBuffer;
    private FrameHandler.CoreSession channel;
    
    public FrameHandler.CoreSession getCoreSession()
    {
        return channel;
    }
    
    @Override
    public void onOpen(CoreSession coreSession)
    {
        this.channel = coreSession;
        onOpen();
    }
    
    public void onOpen()
    {   
    }
    

    @Override
    public void onFrame(Frame frame, Callback callback)
    {
        byte opcode = frame.getOpCode();
        if (LOG.isDebugEnabled())
            LOG.debug("{}: {}", OpCode.name(opcode),BufferUtil.toDetailString(frame.getPayload()));
        switch (opcode)
        {
            case OpCode.PING:
                onPingFrame(frame,callback);
                break;
         
            case OpCode.PONG:
                onPongFrame(frame,callback);
                break;
                
            case OpCode.TEXT:
                onTextFrame(frame,callback);
                break;
                
            case OpCode.BINARY:
                onBinaryFrame(frame,callback);
                break;
                
            case OpCode.CONTINUATION:
                onContinuationFrame(frame,callback);
                break;
                
            case OpCode.CLOSE:
                onCloseFrame(frame,callback);
                break;
        }
    }

    @Override
    public void onError(Throwable cause)
    {
    }

    /**
     * Notification method for when a Ping frame is received.
     * The default implementation sends a Pong frame using the passed callback for completion
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onPingFrame(Frame frame, Callback callback)
    {
        ByteBuffer pongBuf;
        if (frame.hasPayload())
        {
            pongBuf = ByteBuffer.allocate(frame.getPayload().remaining());
            BufferUtil.put(frame.getPayload().slice(), pongBuf);
            BufferUtil.flipToFlush(pongBuf, 0);
        }
        else
        {
            pongBuf = ByteBuffer.allocate(0);
        }

        try
        {
            channel.sendFrame(new Frame(PONG).setPayload(pongBuf), callback, false);
        }
        catch (Throwable t)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Unable to send pong", t);
            callback.failed(t);
        }
    }

    /** 
     * Notification method for when a Pong frame is received.
     * The default implementation just succeeds the callback
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onPongFrame(Frame frame, Callback callback)
    {
        callback.succeeded();
    }

    /** 
     * Notification method for when a Text frame is received.
     * The default implementation accumulates the payload in a Utf8StringBuilder
     * and calls the {@link #onText(Utf8StringBuilder, Callback, boolean)} method.
     * For partial textMessages (fin == false), the {@link #onText(Utf8StringBuilder, Callback, boolean)}
     * may either leave the contents in the Utf8StringBuilder to accumulate with following Continuation
     * frames, or it may be consumed.
     * @see #onText(Utf8StringBuilder, Callback, boolean)
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onTextFrame(Frame frame, Callback callback)
    {
        if (utf8==null)
            utf8 = new Utf8StringBuilder(Math.max(1024,frame.getPayloadLength()*2));
        else
            utf8.reset();

        if (frame.hasPayload())
            utf8.append(frame.getPayload()); // TODO: this should trigger a bad UTF8 exception if sequence is bad which we wrap in a ProtocolException (but not on unfinished sequences)
        
        if (frame.isFin())
            utf8.checkState(); // TODO: this should not be necessary, checkState() shouldn't be necessary to use (the utf8.toString() should trigger on bad utf8 in final octets)
        else
            partial = OpCode.TEXT;
        
        onText(utf8,callback,frame.isFin());            
    }
    
    /** 
     * Notification method for when UTF8 text is received. This method is 
     * called by {@link #onTextFrame(Frame, Callback)} and
     * {@link #onContinuationFrame(Frame, Callback)}.  Implementations
     * may consume partial content with {@link Utf8StringBuilder#takePartialString()}
     * or leave it to accumulate over multiple calls.
     * The default implementation just succeeds the callback.
     * @param utf8 The received text
     * @param callback The callback to indicate completion of frame handling.
     * @param fin True if the current message is completed by this call.
     */
    protected void onText(Utf8StringBuilder utf8, Callback callback, boolean fin)
    {
        callback.succeeded();
    }

    /** 
     * Notification method for when a Binary frame is received.
     * The default implementation accumulates the payload in a ByteBuffer
     * and calls the {@link #onBinary(ByteBuffer, Callback, boolean)} method.
     * For partial textMessages (fin == false), the {@link #onBinary(ByteBuffer, Callback, boolean)}
     * may either leave the contents in the ByteBuffer to accumulate with following Continuation
     * frames, or it may be consumed.
     * @see #onBinary(ByteBuffer, Callback, boolean)
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onBinaryFrame(Frame frame, Callback callback)
    {
        if (frame.isFin())
        {
            onBinary(frame.getPayload(),callback,true);
        }
        else
        {
            partial = OpCode.BINARY;
            
            // TODO use the pool?
            if (byteBuffer==null)
                byteBuffer = BufferUtil.allocate(Math.max(1024,frame.getPayloadLength()*2));
            else
                BufferUtil.clear(byteBuffer);

            if (frame.hasPayload())
                BufferUtil.append(byteBuffer,frame.getPayload());
            
            onBinary(byteBuffer,callback,false);
        }
    }

    /** 
     * Notification method for when binary data is received. This method is 
     * called by {@link #onBinaryFrame(Frame, Callback)} and
     * {@link #onContinuationFrame(Frame, Callback)}.  Implementations
     * may consume partial content from the {@link ByteBuffer}
     * or leave it to accumulate over multiple calls.
     * The default implementation just succeeds the callback.
     * @param payload The received data
     * @param callback The callback to indicate completion of frame handling.
     * @param fin True if the current message is completed by this call.
     */
    protected void onBinary(ByteBuffer payload, Callback callback, boolean fin)
    {
        callback.succeeded();
    }

    /** 
     * Notification method for when a Continuation frame is received.
     * The default implementation will call either {@link #onText(Utf8StringBuilder, Callback, boolean)}
     * or {@link #onBinary(ByteBuffer, Callback, boolean)} as appropriate, accumulating
     * payload as necessary.
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onContinuationFrame(Frame frame, Callback callback)
    {
        if (partial==OpCode.UNDEFINED)
        {
            callback.failed(new IllegalStateException());
            return;
        }
            
        switch(partial)
        {
            case OpCode.TEXT:
                if (frame.hasPayload())
                    utf8.append(frame.getPayload());

                if (frame.isFin())
                    utf8.checkState();
                    
                onText(utf8,callback,frame.isFin());
                break;

            case OpCode.BINARY:
                if (frame.hasPayload())
                {
                    int factor = frame.isFin()?1:3;
                    BufferUtil.compact(byteBuffer);
                    if (BufferUtil.space(byteBuffer) < frame.getPayloadLength())
                        byteBuffer = BufferUtil.ensureCapacity(byteBuffer,byteBuffer.capacity()+Math.max(byteBuffer.capacity(), frame.getPayloadLength()*factor));
                    BufferUtil.append(byteBuffer,frame.getPayload());
                }
                    
                onBinary(byteBuffer,callback,frame.isFin());
                break;
                
            default:
                callback.failed(new IllegalStateException());

        }
    }

    /** 
     * Notification method for when a Close frame is received.
     * The default implementation responds with a close frame when necessary.
     * @param frame The received frame
     * @param callback The callback to indicate completion of frame handling.
     */
    protected void onCloseFrame(Frame frame, Callback callback)
    {
        int respond;
        String reason=null;


        int code = frame.hasPayload() ? new CloseStatus(frame.getPayload()).getCode() : -1;
        
        switch(code)
        {
            case -1:
                respond = CloseStatus.NORMAL;
                break;
                
            case CloseStatus.NORMAL:
            case CloseStatus.SHUTDOWN:
            case CloseStatus.PROTOCOL:
            case CloseStatus.BAD_DATA:
            case CloseStatus.BAD_PAYLOAD:
            case CloseStatus.POLICY_VIOLATION:
            case CloseStatus.MESSAGE_TOO_LARGE:
            case CloseStatus.EXTENSION_ERROR:
            case CloseStatus.SERVER_ERROR:
                respond = 0;
                break;

            default:
                if (code>=3000 && code<=4999)
                {
                    respond = code;
                }
                else
                {
                    respond = CloseStatus.PROTOCOL;
                    reason = "invalid " + code + " close received";
                }
                break;
        }
        
        if (respond>0)
            channel.close(respond,reason,callback);
        else
            callback.succeeded();
    }

    @Override
    public void onClosed(CloseStatus closeStatus)
    {
    }
}
