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

import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.extensions.Extension;
import org.eclipse.jetty.websocket.core.frames.Frame;
import org.eclipse.jetty.websocket.core.frames.OpCode;

/**
 * Parsing of a frames in WebSocket land.
 */
public class Parser
{
    private enum State
    {
        START,
        PAYLOAD_LEN,
        PAYLOAD_LEN_BYTES,
        MASK,
        MASK_BYTES,
        PAYLOAD
    }

    private static final Logger LOG = Log.getLogger(Parser.class);
    private final WebSocketPolicy policy;
    private final ByteBufferPool bufferPool;

    // State specific
    private State state = State.START;
    private int cursor = 0;
    // Frame
    private Frame frame;
    // payload specific
    private ByteBuffer payload;
    private int payloadLength;
    private ParserDeMasker deMasker = new ParserDeMasker();

    /** 
     * Is there an extension using RSV flag?
     * <p>
     * 
     * <pre>
     *   0100_0000 (0x40) = rsv1
     *   0010_0000 (0x20) = rsv2
     *   0001_0000 (0x10) = rsv3
     * </pre>
     */
    private byte flagsInUse=0x00;
    
    public Parser(WebSocketPolicy wspolicy, ByteBufferPool bufferPool)
    {
        this.bufferPool = bufferPool;
        this.policy = wspolicy;
    }
    
    private void assertSanePayloadLength(long len)
    {
        if (len > this.policy.getMaxAllowedFrameSize())
        {
            throw new MessageTooLargeException("Cannot handle payload lengths larger than " + this.policy.getMaxAllowedFrameSize());
        }
    
        if (frame.getOpCode() == OpCode.CLOSE && (len == 1))
        {
            throw new ProtocolException("Invalid close frame payload length, [" + payloadLength + "]");
        }
    
        if (frame.isControlFrame() && len > Frame.MAX_CONTROL_PAYLOAD)
        {
            throw new ProtocolException("Invalid control frame payload length, [" + payloadLength + "] cannot exceed ["
                    + Frame.MAX_CONTROL_PAYLOAD + "]");
        }
    }

    public void configureFromExtensions(List<? extends Extension> exts)
    {        
        // default
        flagsInUse = 0x00;

        // configure from list of extensions in use
        for (Extension ext : exts)
        {
            if (ext.isRsv1User())
            {
                flagsInUse = (byte)(flagsInUse | 0x40);
            }
            if (ext.isRsv2User())
            {
                flagsInUse = (byte)(flagsInUse | 0x20);
            }
            if (ext.isRsv3User())
            {
                flagsInUse = (byte)(flagsInUse | 0x10);
            }
        }
    }

    public WebSocketPolicy getPolicy()
    {
        return policy;
    }

    public boolean isRsv1InUse()
    {
        return (flagsInUse & 0x40) != 0;
    }

    public boolean isRsv2InUse()
    {
        return (flagsInUse & 0x20) != 0;
    }

    public boolean isRsv3InUse()
    {
        return (flagsInUse & 0x10) != 0;
    }
    
    /**
     * Parse the buffer.
     *
     * @param buffer the buffer to parse from.
     * @return Frame or null if not enough data for a complete frame.
     * @throws WebSocketException if unable to parse properly
     */
    public Frame parse(ByteBuffer buffer) throws WebSocketException
    {
        try
        {
            if (LOG.isDebugEnabled())
                LOG.debug("{} Parsing {}", getPolicy().getBehavior(), BufferUtil.toDetailString(buffer));
           
            // parse through
            while (buffer.hasRemaining())
            {
                switch (state)
                {
                    case START:
                    {
                        frame = null;
                        payload = null;

                        // peek at byte
                        byte b = buffer.get();
                        boolean fin = ((b & 0x80) != 0);

                        byte opcode = (byte)(b & 0x0F);

                        if (!OpCode.isKnown(opcode))
                            throw new ProtocolException("Unknown opcode: " + opcode);

                        if (LOG.isDebugEnabled())
                            LOG.debug("{} OpCode {}, fin={} rsv={}{}{}",
                                    getPolicy().getBehavior(),
                                    OpCode.name(opcode),
                                    fin,
                                    (((b & 0x40) != 0)?'1':'.'),
                                    (((b & 0x20) != 0)?'1':'.'),
                                    (((b & 0x10) != 0)?'1':'.'));

                        // base framing flags
                        switch(opcode)
                        {
                            case OpCode.TEXT:
                                frame = new Frame(OpCode.TEXT);
                                break;
                            case OpCode.BINARY:
                                frame = new Frame(OpCode.BINARY);
                                break;
                            case OpCode.CONTINUATION:
                                frame = new Frame(OpCode.CONTINUATION);
                                break;
                            case OpCode.CLOSE:
                                frame = new Frame(OpCode.CLOSE);
                                // control frame validation
                                if (!fin)
                                    throw new ProtocolException("Fragmented Close Frame [" + OpCode.name(opcode) + "]");
                                break;
                            case OpCode.PING:
                                frame = new Frame(OpCode.PING);
                                // control frame validation
                                if (!fin)
                                    throw new ProtocolException("Fragmented Ping Frame [" + OpCode.name(opcode) + "]");
                                break;
                            case OpCode.PONG:
                                frame = new Frame(OpCode.PONG);
                                // control frame validation
                                if (!fin)
                                    throw new ProtocolException("Fragmented Pong Frame [" + OpCode.name(opcode) + "]");
                                break;
                        }

                        frame.setFin(fin);

                        // Are any flags set?
                        if ((b & 0x70) != 0)
                        {
                            int  not_in_use = (isRsv1InUse()?0:0x40) + (isRsv2InUse()?0:0x20) + (isRsv3InUse()?0:0x10);
                            /*
                             * RFC 6455 Section 5.2
                             * 
                             * MUST be 0 unless an extension is negotiated that defines meanings for non-zero values. If a nonzero value is received and none of the
                             * negotiated extensions defines the meaning of such a nonzero value, the receiving endpoint MUST _Fail the WebSocket Connection_.
                             */
                            if ((b & not_in_use) != 0)
                            {
                                String err = String.format("RSV bits %x not in use %x",b,not_in_use);
                                if(LOG.isDebugEnabled())
                                    LOG.debug(getPolicy().getBehavior() + " " + err + ": Remaining buffer: {}", BufferUtil.toDetailString(buffer));
                                throw new ProtocolException(err);
                            }
                            
                            // TODO yuck
                            if ((b & 0x40) != 0)
                                frame.setRsv1(true);
                            if ((b & 0x20) != 0)
                                frame.setRsv2(true);
                            if ((b & 0x10) != 0)
                                frame.setRsv3(true);
                        }

                        state = State.PAYLOAD_LEN;
                        break;
                    }

                    case PAYLOAD_LEN:
                    {
                        byte b = buffer.get();
                        frame.setMasked((b & 0x80) != 0);
                        payloadLength = (byte)(0x7F & b);

                        if (payloadLength == 127) // 0x7F
                        {
                            // length 8 bytes (extended payload length)
                            payloadLength = 0;
                            state = State.PAYLOAD_LEN_BYTES;
                            cursor = 8;
                            break; // continue onto next state
                        }
                        else if (payloadLength == 126) // 0x7E
                        {
                            // length 2 bytes (extended payload length)
                            payloadLength = 0;
                            state = State.PAYLOAD_LEN_BYTES;
                            cursor = 2;
                            break; // continue onto next state
                        }

                        assertSanePayloadLength(payloadLength);
                        if (frame.isMasked())
                        {
                            state = State.MASK;
                        }
                        else
                        {
                            // special case for empty payloads (no more bytes left in buffer)
                            if (payloadLength == 0)
                            {
                                state = State.START;
                                assertBehavior();
                                return frame;
                            }

                            deMasker.reset(frame);
                            state = State.PAYLOAD;
                        }

                        break;
                    }

                    case PAYLOAD_LEN_BYTES:
                    {
                        byte b = buffer.get();
                        --cursor;
                        payloadLength |= (b & 0xFF) << (8 * cursor);
                        if (cursor == 0)
                        {
                            assertSanePayloadLength(payloadLength);
                            if (frame.isMasked())
                            {
                                state = State.MASK;
                            }
                            else
                            {
                                // special case for empty payloads (no more bytes left in buffer)
                                if (payloadLength == 0)
                                {
                                    state = State.START;
                                    assertBehavior();
                                    return frame;
                                }

                                deMasker.reset(frame);
                                state = State.PAYLOAD;
                            }
                        }
                        break;
                    }

                    case MASK:
                    {
                        byte m[] = new byte[4];
                        frame.setMask(m);
                        if (buffer.remaining() >= 4)
                        {
                            buffer.get(m,0,4);
                            // special case for empty payloads (no more bytes left in buffer)
                            if (payloadLength == 0)
                            {
                                state = State.START;
                                assertBehavior();
                                return frame;
                            }

                            deMasker.reset(frame);
                            state = State.PAYLOAD;
                        }
                        else
                        {
                            state = State.MASK_BYTES;
                            cursor = 4;
                        }
                        break;
                    }

                    case MASK_BYTES:
                    {
                        byte b = buffer.get();
                        frame.getMask()[4 - cursor] = b;
                        --cursor;
                        if (cursor == 0)
                        {
                            // special case for empty payloads (no more bytes left in buffer)
                            if (payloadLength == 0)
                            {
                                state = State.START;
                                assertBehavior();
                                return frame;
                            }

                            deMasker.reset(frame);
                            state = State.PAYLOAD;
                        }
                        break;
                    }

                    case PAYLOAD:
                    {
                        frame.assertValid();
                        if (parsePayload(buffer))
                        {
                            state = State.START;
                            // we have a frame!
                            assertBehavior();
                            return frame;
                        }
                        break;
                    }
                }
            }
        }
        catch (Throwable t)
        {
            if (LOG.isDebugEnabled())
                LOG.debug(getPolicy().getBehavior() + " Parsed Error: [" + buffer.remaining() + " bytes left in read buffer]", t);
            
            buffer.position(buffer.limit()); // consume remaining
            
            // let session know
            WebSocketException wse;
            if(t instanceof WebSocketException)
                wse = (WebSocketException) t;
            else
                wse = new WebSocketException(t);
                
            throw wse;
        }
        finally
        {
            if (LOG.isDebugEnabled())
                LOG.debug("{} Parse exit: {} {} {}", getPolicy().getBehavior(), state, frame, BufferUtil.toDetailString(buffer));
            
        }
        
        return null;
    }
    
    private void assertBehavior()
    {
        if (policy.getBehavior() == WebSocketBehavior.SERVER)
        {
            /* Parsing on server.
             *
             * Then you MUST make sure all incoming frames are masked!
             *
             * Technically, this test is in violation of RFC-6455, Section 5.1
             * http://tools.ietf.org/html/rfc6455#section-5.1
             *
             * But we can't trust the client at this point, so Jetty opts to close
             * the connection as a Protocol error.
             */
            if (!frame.isMasked())
            {
                throw new ProtocolException("Client MUST mask all frames (RFC-6455: Section 5.1)");
            }
        }
        else if(policy.getBehavior() == WebSocketBehavior.CLIENT)
        {
            // Required by RFC-6455 / Section 5.1
            if (frame.isMasked())
            {
                throw new ProtocolException("Server MUST NOT mask any frames (RFC-6455: Section 5.1)");
            }
        }
    }
    
    public void release(org.eclipse.jetty.websocket.core.frames.Frame frame)
    {
        if (frame.hasPayload())
        {
            bufferPool.release(frame.getPayload());
        }
    }
    
    /**
     * Implementation specific parsing of a payload
     * 
     * @param buffer
     *            the payload buffer
     * @return true if payload is done reading, false if incomplete
     */
    private boolean parsePayload(ByteBuffer buffer)
    {        
        if (payloadLength == 0)
        {
            return true;
        }

        if (buffer.hasRemaining())
        {
            // Create a small window of the incoming buffer to work with.
            // this should only show the payload itself, and not any more
            // bytes that could belong to the start of the next frame.
            int bytesSoFar = payload == null ? 0 : payload.position();
            int bytesExpected = payloadLength - bytesSoFar;
            int bytesAvailable = buffer.remaining();
            int windowBytes = Math.min(bytesAvailable, bytesExpected);
            int limit = buffer.limit();
            buffer.limit(buffer.position() + windowBytes);
            ByteBuffer window = buffer.slice();
            buffer.limit(limit);
            buffer.position(buffer.position() + window.remaining());

            if (LOG.isDebugEnabled())
            {
                LOG.debug("{} Raw Payload: {}",getPolicy().getBehavior(), BufferUtil.toDetailString(window));
            }

            deMasker.process(window);

            if (payload == null)
            {
                payload = bufferPool.acquire(payloadLength,false);
                BufferUtil.clearToFill(payload);
            }
            
            // Copy the payload.
            payload.put(window);

            // if the payload is complete
            if (payload.position() == payloadLength)
            {
                BufferUtil.flipToFlush(payload, 0);

                if(frame.getOpCode() == OpCode.CLOSE)
                    new CloseStatus(payload); // verifies frame TODO remember CloseStatus?

                frame.setPayload(payload);
                // notify that frame is complete
                return true;
            }
        }
        // frame not (yet) complete
        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Parser@").append(Integer.toHexString(hashCode()));
        builder.append("[").append(policy.getBehavior());
        builder.append(",s=").append(state);
        builder.append(",c=").append(cursor);
        builder.append(",len=").append(payloadLength);
        builder.append(",f=").append(frame);
        builder.append("]");
        return builder.toString();
    }
}
