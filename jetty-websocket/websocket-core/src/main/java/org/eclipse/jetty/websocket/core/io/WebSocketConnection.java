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

package org.eclipse.jetty.websocket.core.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.Generator;
import org.eclipse.jetty.websocket.core.OutgoingFrames;
import org.eclipse.jetty.websocket.core.Parser;
import org.eclipse.jetty.websocket.core.WebSocketBehavior;
import org.eclipse.jetty.websocket.core.WebSocketChannel;
import org.eclipse.jetty.websocket.core.WebSocketPolicy;
import org.eclipse.jetty.websocket.core.WebSocketTimeoutException;
import org.eclipse.jetty.websocket.core.frames.Frame;

/**
 * Provides the implementation of {@link org.eclipse.jetty.io.Connection} that is suitable for WebSocket
 */
public class WebSocketConnection extends AbstractConnection implements SuspendToken, Connection.UpgradeTo, Dumpable, OutgoingFrames
{
    private final Logger LOG = Log.getLogger(this.getClass());

    /**
     * Minimum size of a buffer is the determined to be what would be the maximum framing header size (not including payload)
     */
    private static final int MIN_BUFFER_SIZE = Generator.MAX_HEADER_LENGTH;

    private final ByteBufferPool bufferPool;
    private final Generator generator;
    private final Parser parser;

    // Connection level policy (before the session and local endpoint has been created)
    private final WebSocketPolicy policy;
    private final AtomicBoolean suspendToken;
    private final Flusher flusher;
    private final Random random;

    private WebSocketChannel channel;

    // Read / Parse variables
    private AtomicBoolean fillAndParseScope = new AtomicBoolean(false);
    private ByteBuffer networkBuffer;

    /**
     * Create a WSConnection.
     * <p>
     * It is assumed that the WebSocket Upgrade Handshake has already
     * completed successfully before creating this connection.
     * </p>
     */
    public WebSocketConnection(EndPoint endp,
                               Executor executor,
                               ByteBufferPool bufferPool,
                               WebSocketChannel channel)
    {
        this(endp, executor, bufferPool, channel, true);
    }

    /**
     * Create a WSConnection.
     * <p>
     * It is assumed that the WebSocket Upgrade Handshake has already
     * completed successfully before creating this connection.
     * </p>
     */
    public WebSocketConnection(EndPoint endp,
                               Executor executor,
                               ByteBufferPool bufferPool,
                               WebSocketChannel channel,
                               boolean validating)
    {
        super(endp, executor);

        Objects.requireNonNull(endp, "EndPoint");
        Objects.requireNonNull(channel, "Channel");
        Objects.requireNonNull(executor, "Executor");
        Objects.requireNonNull(bufferPool, "ByteBufferPool");

        this.bufferPool = bufferPool;

        this.policy = channel.getPolicy();
        this.channel = channel;

        this.generator = new Generator(policy, bufferPool, validating);
        this.parser = new Parser(policy, bufferPool);
        this.suspendToken = new AtomicBoolean(false);
        this.flusher = new Flusher(policy.getOutputBufferSize(), generator, endp);
        this.setInputBufferSize(policy.getInputBufferSize());
        this.setMaxIdleTimeout(policy.getIdleTimeout());

        this.generator.configureFromExtensions(channel.getExtensionStack().getExtensions());

        this.random = this.policy.getBehavior() == WebSocketBehavior.CLIENT ? new Random(endp.hashCode()) : null;
    }

    @Override
    public Executor getExecutor()
    {
        return super.getExecutor();
    }

    public ByteBufferPool getBufferPool()
    {
        return bufferPool;
    }

    public Generator getGenerator()
    {
        return generator;
    }

    public Parser getParser()
    {
        return parser;
    }

    public WebSocketPolicy getPolicy()
    {
        return policy;
    }

    public InetSocketAddress getLocalAddress()
    {
        return getEndPoint().getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress()
    {
        return getEndPoint().getRemoteAddress();
    }


    /**
     * Physical connection disconnect.
     * <p>
     * Not related to WebSocket close handshake.
     */
    @Override
    public void onClose()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("onClose() of physical connection");
        
        // TODO review all close paths
        IOException e = new IOException("Closed"); //TODO can we get original exception?
        flusher.terminate(e,true);
        channel.onClosed(e);
        super.onClose();
    }

    @Override
    public boolean onIdleExpired()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("onIdleExpired()");

        channel.processError(new WebSocketTimeoutException("Connection Idle Timeout"));
        return true;
    }

    protected boolean onFrame(org.eclipse.jetty.websocket.core.frames.Frame frame)
    {
        AtomicBoolean result = new AtomicBoolean(false);

        if (LOG.isDebugEnabled())
            LOG.debug("onFrame({})", frame);

        channel.assertValid(frame, true);
        channel.onReceiveFrame(frame, new Callback()
        {
            @Override
            public void succeeded()
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("onFrame({}).succeed()", frame);

                parser.release(frame);
                if (!result.compareAndSet(false, true))
                {
                    // callback has been notified asynchronously
                    fillAndParse(); // TODO dispatch?
                }
            }

            @Override
            public void failed(Throwable cause)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("onFrame(" + frame + ").fail()", cause);
                parser.release(frame);

                // notify session & endpoint
                channel.processError(cause);
            }
        });

        if (result.compareAndSet(false, true))
        {
            // callback hasn't been notified yet
            return false;
        }

        return true;
    }

    private ByteBuffer getNetworkBuffer()
    {
        synchronized (this)
        {
            if (networkBuffer == null)
            {
                networkBuffer = bufferPool.acquire(getInputBufferSize(), true);
            }
            return networkBuffer;
        }
    }

    private void releaseNetworkBuffer(ByteBuffer buffer)
    {
        synchronized (this)
        {
            assert (!buffer.hasRemaining());
            bufferPool.release(buffer);
            networkBuffer = null;
        }
    }

    @Override
    public void onFillable()
    {
        if(LOG.isDebugEnabled())
            LOG.debug("onFillable()");
        fillAndParse();
    }

    @Override
    public void fillInterested()
    {
        // Handle situation where prefill buffer (from upgrade) has created network buffer,
        // but there is no actual read interest (yet)
        if (BufferUtil.hasContent(networkBuffer))
        {
            fillAndParse();
        }
        else
        {
            super.fillInterested();
        }
    }

    private void fillAndParse()
    {
        boolean interested = false;
        
        // TODO get rid of this
        if(!fillAndParseScope.compareAndSet(false,true))
        {
            LOG.warn("fillAndParseScope failure!!!!!!!!!");
            return;
        }
        
        try
        {
            while (getEndPoint().isOpen())
            {
                if (suspendToken.get())
                {
                    return;
                }

                ByteBuffer nBuffer = getNetworkBuffer();
                
                // Parse and handle frames
                while(BufferUtil.hasContent(nBuffer))
                {
                    Frame frame = parser.parse(nBuffer);
                    if (frame==null)
                        break;
                    if (!onFrame(frame))
                        return;
                }

                // The handling of a close frame may have closed the end point
                if (getEndPoint().isInputShutdown())
                    return;

                int filled = getEndPoint().fill(nBuffer);

                if (LOG.isDebugEnabled())
                    LOG.debug("endpointFill() filled={}: {}", filled, BufferUtil.toDetailString(nBuffer));

                if (filled < 0)
                {
                    releaseNetworkBuffer(nBuffer);
                    channel.onClosed(null);
                    return;
                }

                if (filled == 0)
                {
                    releaseNetworkBuffer(nBuffer);
                    interested = true;
                    return;
                }
            }
        }
        catch (Throwable t)
        {
            channel.processError(t);
        }
        finally
        {
            fillAndParseScope.set(false);
            if (interested)
                fillInterested();
        }
    }


    /**
     * Extra bytes from the initial HTTP upgrade that need to
     * be processed by the websocket parser before starting
     * to read bytes from the connection
     *
     * @param prefilled the bytes of prefilled content encountered during upgrade
     */
    protected void setInitialBuffer(ByteBuffer prefilled)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("set Initial Buffer - {}", BufferUtil.toDetailString(prefilled));
        }

        if ((prefilled != null) && (prefilled.hasRemaining()))
        {
            networkBuffer = bufferPool.acquire(prefilled.remaining(), true);
            BufferUtil.clearToFill(networkBuffer);
            BufferUtil.put(prefilled, networkBuffer);
            BufferUtil.flipToFlush(networkBuffer, 0);
        }
    }

    /**
     * Physical connection Open.
     */
    @Override
    public void onOpen()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("onOpen() {}",this);

        // Connection Settings
        long channelIdleTimeout = channel.getIdleTimeout(TimeUnit.MILLISECONDS);
        if (channelIdleTimeout >= -1)
        {
            this.setMaxIdleTimeout(channelIdleTimeout);
        }

        // Open Channel
        channel.onOpen();
        super.onOpen();
    }

    /**
     * Event for no activity on connection (read or write)
     * @return true to signal that the endpoint must be closed, false to keep the endpoint open
     */
    @Override
    protected boolean onReadTimeout(Throwable timeout)
    {
        channel.processError(new WebSocketTimeoutException("Timeout on Read"));
        return false;
    }

    @Override
    public void resume()
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("resume()");
        }

        if (suspendToken.compareAndSet(true, false))
        {
            // Do not fillAndParse again, if we are actively in a fillAndParse
            // TODO this is a race!
            if (!fillAndParseScope.get())
            {
                fillAndParse();
            }
        }
    }

    @Override
    public void setInputBufferSize(int inputBufferSize)
    {
        if (inputBufferSize < MIN_BUFFER_SIZE)
        {
            throw new IllegalArgumentException("Cannot have buffer size less than " + MIN_BUFFER_SIZE);
        }
        super.setInputBufferSize(inputBufferSize);
    }

    public void setMaxIdleTimeout(long ms)
    {
        if (ms >= 0)
        {
            getEndPoint().setIdleTimeout(ms);
        }
    }

    public SuspendToken suspend()
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("suspend()");
        }

        suspendToken.set(true);
        return this;
    }

    @Override
    public String dump()
    {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        out.append(toString()).append(System.lineSeparator());
    }

    @Override
    public String toConnectionString()
    {
        return String.format("%s@%x[%s,p=%s,f=%s,g=%s]",
                getClass().getSimpleName(),
                hashCode(),
                getPolicy().getBehavior(),
                parser,
                flusher,
                generator);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;

        EndPoint endp = getEndPoint();
        if (endp != null)
        {
            result = prime * result + endp.getLocalAddress().hashCode();
            result = prime * result + endp.getRemoteAddress().hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WebSocketConnection other = (WebSocketConnection) obj;
        EndPoint endp = getEndPoint();
        EndPoint otherEndp = other.getEndPoint();
        if (endp == null)
        {
            if (otherEndp != null)
                return false;
        }
        else if (!endp.equals(otherEndp))
            return false;
        return true;
    }

    /**
     * Extra bytes from the initial HTTP upgrade that need to
     * be processed by the websocket parser before starting
     * to read bytes from the connection
     */
    @Override
    public void onUpgradeTo(ByteBuffer prefilled)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("onUpgradeTo({})", BufferUtil.toDetailString(prefilled));
        }

        setInitialBuffer(prefilled);
    }

    @Override
    public void sendFrame(org.eclipse.jetty.websocket.core.frames.Frame frame, Callback callback, BatchMode batchMode)
    {
        if (getPolicy().getBehavior()==WebSocketBehavior.CLIENT)
        {
            Frame wsf = (Frame)frame;
            wsf.setMasked(true);
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            wsf.setMask(mask);
        }
        flusher.enqueue(frame,callback,batchMode);
    }


    private class Flusher extends FrameFlusher
    {
        private Flusher(int bufferSize, Generator generator, EndPoint endpoint)
        {
            super(bufferPool, generator, endpoint, bufferSize, 8);
        }

        @Override
        public void onCompleteFailure(Throwable x)
        {
            super.onCompleteFailure(x);
            channel.processError(x);
        }
    }
}
