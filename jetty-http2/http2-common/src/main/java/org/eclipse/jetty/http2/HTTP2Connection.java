//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.Executor;

import org.eclipse.jetty.http2.parser.Parser;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ConcurrentArrayQueue;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Invocable;
import org.eclipse.jetty.util.thread.Invocable.InvocationType;
import org.eclipse.jetty.util.thread.strategy.ExecuteProduceConsume;
import org.eclipse.jetty.util.thread.strategy.ProduceExecuteConsume;

public class HTTP2Connection extends AbstractConnection
{
    protected static final Logger LOG = Log.getLogger(HTTP2Connection.class);

    private final Queue<Runnable> tasks = new ConcurrentArrayQueue<>();
    private final ByteBufferPool byteBufferPool;
    private final Parser parser;
    private final ISession session;
    private final int bufferSize;
    private final HTTP2Producer producer = new HTTP2Producer();
    private final ExecutionStrategy strategy;
    private final ExecutionStrategy nonBlockingStrategy;

    public HTTP2Connection(ByteBufferPool byteBufferPool, Executor executor, EndPoint endPoint, Parser parser, ISession session, int bufferSize)
    {
        super(endPoint, executor);
        this.byteBufferPool = byteBufferPool;
        this.parser = parser;
        this.session = session;
        this.bufferSize = bufferSize;
        this.strategy = new ProduceExecuteConsume(producer, executor);
        this.nonBlockingStrategy = new ExecuteProduceConsume(producer, executor);
    }

    public ISession getSession()
    {
        return session;
    }


    protected Parser getParser()
    {
        return parser;
    }

    protected void setInputBuffer(ByteBuffer buffer)
    {
        producer.buffer = buffer;
    }

    @Override
    public void onOpen()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("HTTP2 Open {} ", this);
        super.onOpen();
        strategy.produce();
    }

    @Override
    public void onClose()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("HTTP2 Close {} ", this);
        super.onClose();
    }

    @Override
    public void onFillable()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("HTTP2 onFillable {} ", this);
        if (Invocable.isNonBlockingInvocation())
            nonBlockingStrategy.produce();
        else
            strategy.produce();
    }

    private int fill(EndPoint endPoint, ByteBuffer buffer)
    {
        try
        {
            if (endPoint.isInputShutdown())
                return -1;
            return endPoint.fill(buffer);
        }
        catch (IOException x)
        {
            LOG.debug("Could not read from " + endPoint, x);
            return -1;
        }
    }

    @Override
    public boolean onIdleExpired()
    {
        boolean close = session.onIdleTimeout();
        boolean idle = isFillInterested();
        if (close && idle)
            session.close(ErrorCode.NO_ERROR.code, "idle_timeout", Callback.NOOP);
        return false;
    }

    protected void offerTask(Runnable task, boolean dispatch)
    {
        tasks.offer(task);
        
        // Because producing calls parse and parse can call offerTask, we have to make sure
        // we use the same strategy otherwise produce can be reentrant and that messes with 
        // the release mechanism.  TODO is this test sufficient to protect from this?
        ExecutionStrategy s = Invocable.isNonBlockingInvocation()?nonBlockingStrategy:strategy;
        if (dispatch)
            // TODO Why again is this necessary?
            s.dispatch();
        else
            s.produce();
    }

    @Override
    public void close()
    {
        // We don't call super from here, otherwise we close the
        // endPoint and we're not able to read or write anymore.
        session.close(ErrorCode.NO_ERROR.code, "close", Callback.NOOP);
    }

    protected class HTTP2Producer implements ExecutionStrategy.Producer
    {
        private final Callback fillableCallback = new FillableCallback();
        private ByteBuffer buffer;

        @Override
        public synchronized Runnable produce()
        {            
            Runnable task = tasks.poll();
            if (LOG.isDebugEnabled())
                LOG.debug("Dequeued task {}", task);
            if (task != null)
                return task;

            if (isFillInterested())
                return null;

            if (buffer == null)
                buffer = byteBufferPool.acquire(bufferSize, false); // TODO: make directness customizable
            boolean looping = BufferUtil.hasContent(buffer);
            while (true)
            {
                if (looping)
                {
                    while (buffer.hasRemaining())
                        parser.parse(buffer);

                    task = tasks.poll();
                    if (LOG.isDebugEnabled())
                        LOG.debug("Dequeued new task {}", task);
                    if (task != null)
                    {
                        release();
                        return task;
                    }
                }

                int filled = fill(getEndPoint(), buffer);
                if (LOG.isDebugEnabled())
                    LOG.debug("Filled {} bytes", filled);

                if (filled == 0)
                {
                    release();
                    getEndPoint().fillInterested(fillableCallback);
                    return null;
                }
                else if (filled < 0)
                {
                    release();
                    session.onShutdown();
                    return null;
                }

                looping = true;
            }
        }

        private void release()
        {
            if (buffer != null && !buffer.hasRemaining())
            {
                byteBufferPool.release(buffer);
                buffer = null;
            }
        }
    }

    private class FillableCallback implements Callback
    {
        @Override
        public InvocationType getInvocationType()
        {
            return InvocationType.EITHER;
        }
        
        @Override
        public void succeeded()
        {
            onFillable();
        }

        @Override
        public void failed(Throwable x)
        {
            onFillInterestedFailed(x);
        }
    }
}
