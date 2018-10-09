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

package org.eclipse.jetty.websocket.core.internal;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class FrameFlusher extends IteratingCallback
{
    public static final Frame FLUSH_FRAME = new Frame(OpCode.BINARY);
    private static final Logger LOG = Log.getLogger(FrameFlusher.class);

    private final ByteBufferPool bufferPool;
    private final EndPoint endPoint;
    private final int bufferSize;
    private final Generator generator;
    private final int maxGather;
    private final Deque<Entry> queue = new ArrayDeque<>();
    private final List<Entry> entries;
    private final List<ByteBuffer> buffers;
    private boolean closed;
    private Throwable terminated;
    private ByteBuffer batchBuffer=null;

    public FrameFlusher(ByteBufferPool bufferPool, Generator generator, EndPoint endPoint, int bufferSize, int maxGather)
    {
        this.bufferPool = bufferPool;
        this.endPoint = endPoint;
        this.bufferSize = bufferSize;
        this.generator = Objects.requireNonNull(generator);
        this.maxGather = maxGather;
        this.entries = new ArrayList<>(maxGather);
        this.buffers = new ArrayList<>((maxGather * 2) + 1);
    }

    public void enqueue(Frame frame, Callback callback, boolean batch)
    {
        Entry entry = new Entry(frame, callback, batch);

        Throwable closed;
        synchronized (this)
        {
            closed = terminated;
            if (closed == null)
            {
                byte opCode = frame.getOpCode();
                if (opCode == OpCode.PING || opCode == OpCode.PONG)
                    queue.offerFirst(entry);
                else
                    queue.offerLast(entry);
            }
        }

        if (closed == null)
            iterate();
        else
            notifyCallbackFailure(callback, closed);
    }

    @Override
    protected Action process() throws Throwable
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Flushing {}", this);

        boolean flush = false;
        synchronized (this)
        {
            // Succeed entries from previous call to process
            // and clear batchBuffer if we wrote it.
            if (succeedEntries() && batchBuffer!=null)
                BufferUtil.clear(batchBuffer);

            if (closed)
                return Action.SUCCEEDED;

            if (terminated != null)
                throw terminated;

            while (!queue.isEmpty() && entries.size() <= maxGather)
            {
                Entry entry = queue.poll();
                entries.add(entry);
                if (entry.frame==FLUSH_FRAME)
                {
                    flush = true;
                    break;
                }

                int batchSpace = BufferUtil.space(batchBuffer);

                boolean batch = entry.batch
                    && !entry.frame.isControlFrame()
                    && entry.frame.getPayloadLength() < bufferSize/4
                    && (batchSpace-Generator.MAX_HEADER_LENGTH)>=entry.frame.getPayloadLength();

                if (batch)
                {
                    // Acquire a batchBuffer if we don't have one
                    if (batchBuffer==null)
                    {
                        batchBuffer = bufferPool.acquire(bufferSize, true);
                        buffers.add(batchBuffer);
                    }

                    // generate the frame into the batchBuffer
                    entry.generateHeaderBytes(batchBuffer);
                    ByteBuffer payload = entry.frame.getPayload();
                    if (BufferUtil.hasContent(payload))
                        BufferUtil.append(batchBuffer, payload);
                }
                else if (batchSpace>=Generator.MAX_HEADER_LENGTH)
                {
                    // Use the batch space for our header
                    entry.generateHeaderBytes(batchBuffer);
                    flush = true;

                    // Add the payload to the list of buffers
                    ByteBuffer payload = entry.frame.getPayload();
                    if (BufferUtil.hasContent(payload))
                    {
                        buffers.add(payload);
                        break;
                    }
                }
                else
                {
                    // Add headers and payload to the list of buffers
                    buffers.add(entry.generateHeaderBytes());
                    flush = true;
                    ByteBuffer payload = entry.frame.getPayload();
                    if (BufferUtil.hasContent(payload))
                        buffers.add(payload);
                }
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("{} processed {} entries flush=%b batch=%s: {}",
                this,
                entries.size(),
                flush,
                BufferUtil.toDetailString(batchBuffer),
                entries);

        if (entries.isEmpty())
        {
            releaseAggregate();
            succeedEntries();
            return Action.IDLE;
        }

        if (flush)
        {
            endPoint.write(this, buffers.toArray(new ByteBuffer[buffers.size()]));
            buffers.clear();
        }
        else
        {
            // We just aggregated the entries, so we need to succeed their callbacks.
            succeeded();
        }

        return Action.SCHEDULED;

    }

    private int getQueueSize()
    {
        synchronized (this)
        {
            return queue.size();
        }
    }

    @Override
    public void succeeded()
    {
        succeedEntries();
        super.succeeded();
    }

    private boolean succeedEntries()
    {
        boolean hadEntries = false;
        for (Entry entry : entries)
        {
            hadEntries = true;
            notifyCallbackSuccess(entry.callback);
            entry.release();
            if (entry.frame.getOpCode() == OpCode.CLOSE)
            {
                terminate(new ClosedChannelException(), true);
                endPoint.shutdownOutput();
            }
        }
        entries.clear();
        return hadEntries;
    }

    @Override
    public void onCompleteFailure(Throwable failure)
    {
        releaseAggregate();

        Throwable closed;
        synchronized (this)
        {
            closed = terminated;
            if (closed == null)
                terminated = failure;
            entries.addAll(queue);
            queue.clear();
        }

        for (Entry entry : entries)
        {
            notifyCallbackFailure(entry.callback, failure);
            entry.release();
        }
        entries.clear();
    }

    private void releaseAggregate()
    {
        if (BufferUtil.isEmpty(batchBuffer))
        {
            bufferPool.release(batchBuffer);
            batchBuffer = null;
        }
    }

    public void terminate(Throwable cause, boolean close)
    {
        Throwable reason;
        synchronized (this)
        {
            closed = close;
            reason = terminated;
            if (reason == null)
                terminated = cause;
        }
        if (LOG.isDebugEnabled())
            LOG.debug("{} {}", reason == null ? "Terminating" : "Terminated", this);
        if (reason == null && !close)
            iterate();
    }

    protected void notifyCallbackSuccess(Callback callback)
    {
        try
        {
            if (callback != null)
            {
                callback.succeeded();
            }
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying success of callback " + callback, x);
        }
    }

    protected void notifyCallbackFailure(Callback callback, Throwable failure)
    {
        try
        {
            if (callback != null)
            {
                callback.failed(failure);
            }
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying failure of callback " + callback, x);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x[queueSize=%d,aggregate=%s,terminated=%s]",
                             getClass().getSimpleName(),
                             hashCode(),
                             getQueueSize(),
                             BufferUtil.toDetailString(batchBuffer),
                             terminated);
    }

    private class Entry extends FrameEntry
    {
        private ByteBuffer headerBuffer;

        private Entry(Frame frame, Callback callback, boolean batch)
        {
            super(frame, callback, batch);
        }

        private ByteBuffer generateHeaderBytes()
        {
            return headerBuffer = generator.generateHeaderBytes(frame);
        }

        private void generateHeaderBytes(ByteBuffer buffer)
        {
            int pos = BufferUtil.flipToFill(buffer);
            generator.generateHeaderBytes(frame, buffer);
            BufferUtil.flipToFlush(buffer,pos);
        }

        private void release()
        {
            if (headerBuffer != null)
            {
                generator.getBufferPool().release(headerBuffer);
                headerBuffer = null;
            }
        }

        @Override
        public String toString()
        {
            return String.format("%s[%s,%s,%b,%s]", getClass().getSimpleName(), frame, callback, batch, terminated);
        }
    }
}
