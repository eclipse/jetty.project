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

package org.eclipse.jetty.io.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.AbstractEndPoint;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.WriteFlusher;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Invocable;

/**
 * A Connection that acts as an interceptor between an EndPoint providing SSL encrypted data
 * and another consumer of an EndPoint (typically an {@link Connection} like HttpConnection) that
 * wants unencrypted data.
 * <p>
 * The connector uses an {@link EndPoint} (typically SocketChannelEndPoint) as
 * it's source/sink of encrypted data.   It then provides an endpoint via {@link #getDecryptedEndPoint()} to
 * expose a source/sink of unencrypted data to another connection (eg HttpConnection).
 * <p>
 * The design of this class is based on a clear separation between the passive methods, which do not block nor schedule any
 * asynchronous callbacks, and active methods that do schedule asynchronous callbacks.
 * <p>
 * The passive methods are {@link DecryptedEndPoint#fill(ByteBuffer)} and {@link DecryptedEndPoint#flush(ByteBuffer...)}. They make best
 * effort attempts to progress the connection using only calls to the encrypted {@link EndPoint#fill(ByteBuffer)} and {@link EndPoint#flush(ByteBuffer...)}
 * methods.  They will never block nor schedule any readInterest or write callbacks.   If a fill/flush cannot progress either because
 * of network congestion or waiting for an SSL handshake message, then the fill/flush will simply return with zero bytes filled/flushed.
 * Specifically, if a flush cannot proceed because it needs to receive a handshake message, then the flush will attempt to fill bytes from the
 * encrypted endpoint, but if insufficient bytes are read it will NOT call {@link EndPoint#fillInterested(Callback)}.
 * <p>
 * It is only the active methods : {@link DecryptedEndPoint#fillInterested(Callback)} and
 * {@link DecryptedEndPoint#write(Callback, ByteBuffer...)} that may schedule callbacks by calling the encrypted
 * {@link EndPoint#fillInterested(Callback)} and {@link EndPoint#write(Callback, ByteBuffer...)}
 * methods.  For normal data handling, the decrypted fillInterest method will result in an encrypted fillInterest and a decrypted
 * write will result in an encrypted write. However, due to SSL handshaking requirements, it is also possible for a decrypted fill
 * to call the encrypted write and for the decrypted flush to call the encrypted fillInterested methods.
 * <p>
 * MOST IMPORTANTLY, the encrypted callbacks from the active methods (#onFillable() and WriteFlusher#completeWrite()) do no filling or flushing
 * themselves.  Instead they simple make the callbacks to the decrypted callbacks, so that the passive encrypted fill/flush will
 * be called again and make another best effort attempt to progress the connection.
 *
 */
public class SslConnection extends AbstractConnection
{
    private static final Logger LOG = Log.getLogger(SslConnection.class);

    private enum Handshake
    {
        INITIAL,
        SUCCEEDED,
        FAILED
    }
    
    private enum FillState 
    {
        IDLE, // Not Filling any data
        INTERESTED, // We have a pending read interest
        NEEDS_WRAP,
        WAIT_FOR_FLUSH,
        FILLING
    }
    
    private enum FlushState 
    { 
        IDLE, // Not flushing any data
        NEEDS_WRITE, // We need to write encrypted data
        WRITING, // We have a pending write of encrypted data
        NEEDS_UNWRAP, // We need to do our own fill
        WAIT_FOR_FILL // Waiting for a fill to happen
    }
    
    private final List<SslHandshakeListener> handshakeListeners = new ArrayList<>();
    private final ByteBufferPool _bufferPool;
    private final SSLEngine _sslEngine;
    private final DecryptedEndPoint _decryptedEndPoint;
    private ByteBuffer _decryptedInput;
    private ByteBuffer _encryptedInput;
    private ByteBuffer _encryptedOutput;
    private final boolean _encryptedDirectBuffers = true;
    private final boolean _decryptedDirectBuffers = false;
    private boolean _renegotiationAllowed;
    private int _renegotiationLimit = -1;
    private boolean _closedOutbound;
    private boolean _allowMissingCloseMessage = true;
    private FlushState _flushState = FlushState.IDLE;
    private FillState _fillState = FillState.IDLE;
    private boolean _filling;
    private boolean _flushing;
    private AtomicReference<Handshake> _handshake = new AtomicReference<>(Handshake.INITIAL);
    private boolean _underFlown;
    
    private abstract class RunnableTask  implements Runnable, Invocable
    {
        private final String _operation;

        protected RunnableTask(String op)
        {
            _operation=op;
        }

        @Override
        public String toString()
        {
            return String.format("SSL:%s:%s:%s",SslConnection.this,_operation,getInvocationType());
        }
    }

    private final Runnable _runFillable = new RunnableTask("runFillable")
    {
        @Override
        public void run()
        {
            _decryptedEndPoint.getFillInterest().fillable();
        }

        @Override
        public InvocationType getInvocationType()
        {
            return getDecryptedEndPoint().getFillInterest().getCallbackInvocationType();
        }
    };

    private final Callback _sslReadCallback = new Callback()
    {
        @Override
        public void succeeded()
        {
            onFillable();
        }

        @Override
        public void failed(final Throwable x)
        {
            onFillInterestedFailed(x);
        }

        @Override
        public InvocationType getInvocationType()
        {
            return getDecryptedEndPoint().getFillInterest().getCallbackInvocationType();
        }

        @Override
        public String toString()
        {
            return String.format("SSLC.NBReadCB@%x{%s}", SslConnection.this.hashCode(),SslConnection.this);
        }
    };

    public SslConnection(ByteBufferPool byteBufferPool, Executor executor, EndPoint endPoint, SSLEngine sslEngine)
    {
        // This connection does not execute calls to onFillable(), so they will be called by the selector thread.
        // onFillable() does not block and will only wakeup another thread to do the actual reading and handling.
        super(endPoint, executor);
        this._bufferPool = byteBufferPool;
        this._sslEngine = sslEngine;
        this._decryptedEndPoint = newDecryptedEndPoint();
    }

    public void addHandshakeListener(SslHandshakeListener listener)
    {
        handshakeListeners.add(listener);
    }

    public boolean removeHandshakeListener(SslHandshakeListener listener)
    {
        return handshakeListeners.remove(listener);
    }

    protected DecryptedEndPoint newDecryptedEndPoint()
    {
        return new DecryptedEndPoint();
    }

    public SSLEngine getSSLEngine()
    {
        return _sslEngine;
    }

    public DecryptedEndPoint getDecryptedEndPoint()
    {
        return _decryptedEndPoint;
    }

    public boolean isRenegotiationAllowed()
    {
        return _renegotiationAllowed;
    }

    public void setRenegotiationAllowed(boolean renegotiationAllowed)
    {
        _renegotiationAllowed = renegotiationAllowed;
    }

    /**
     * @return The number of renegotions allowed for this connection.  When the limit
     * is 0 renegotiation will be denied. If the limit is less than 0 then no limit is applied. 
     */
    public int getRenegotiationLimit()
    {
        return _renegotiationLimit;
    }

    /**
     * @param renegotiationLimit The number of renegotions allowed for this connection.  
     * When the limit is 0 renegotiation will be denied. If the limit is less than 0 then no limit is applied.
     * Default -1.
     */
    public void setRenegotiationLimit(int renegotiationLimit)
    {
        _renegotiationLimit = renegotiationLimit;
    }

    public boolean isAllowMissingCloseMessage()
    {
        return _allowMissingCloseMessage;
    }

    public void setAllowMissingCloseMessage(boolean allowMissingCloseMessage)
    {
        this._allowMissingCloseMessage = allowMissingCloseMessage;
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        getDecryptedEndPoint().getConnection().onOpen();
    }

    @Override
    public void onClose()
    {
        _decryptedEndPoint.getConnection().onClose();
        super.onClose();
    }

    @Override
    public void close()
    {
        getDecryptedEndPoint().getConnection().close();
    }

    @Override
    public boolean onIdleExpired()
    {
        return getDecryptedEndPoint().getConnection().onIdleExpired();
    }

    @Override
    public void onFillable()
    {
        // onFillable means that there are encrypted bytes ready to be filled.
        // however we do not fill them here on this callback, but instead wakeup
        // the decrypted readInterest and/or writeFlusher so that they will attempt
        // to do the fill and/or flush again and these calls will do the actually
        // filling.

        if (LOG.isDebugEnabled())
            LOG.debug("onFillable enter {}", SslConnection.this);

        // We have received a close handshake, close the end point to send FIN.
        if (_decryptedEndPoint.isInputShutdown())
            _decryptedEndPoint.close();

        _decryptedEndPoint.onFillable();

        if (LOG.isDebugEnabled())
            LOG.debug("onFillable exit {}", SslConnection.this);
    }

    @Override
    public void onFillInterestedFailed(Throwable cause)
    {
        _decryptedEndPoint.onFillableFail(cause==null?new IOException():cause);
    }

    @Override
    public String toConnectionString()
    {
        ByteBuffer b = _encryptedInput;
        int ei=b==null?-1:b.remaining();
        b = _encryptedOutput;
        int eo=b==null?-1:b.remaining();
        b = _decryptedInput;
        int di=b==null?-1:b.remaining();

        Connection connection = _decryptedEndPoint.getConnection();
        return String.format("%s@%x{%s,eio=%d/%d,di=%d,fill=%s,flush=%s}~>%s=>%s",
                getClass().getSimpleName(),
                hashCode(),
                _sslEngine.getHandshakeStatus(),
                ei,eo,di,
                _fillState,_flushState,
                _decryptedEndPoint.toEndPointString(),
                connection instanceof AbstractConnection ? ((AbstractConnection)connection).toConnectionString() : connection);
    }

    private void releaseEncryptedOutputBuffer()
    {
        if (!Thread.holdsLock(_decryptedEndPoint))
            throw new IllegalStateException();
        if (_encryptedOutput != null && !_encryptedOutput.hasRemaining())
        {
            _bufferPool.release(_encryptedOutput);
            _encryptedOutput = null;
        }
    }

    public class DecryptedEndPoint extends AbstractEndPoint
    {
        private final Callback _incompleteWriteCallback = new IncompleteWriteCallback();
        
        public DecryptedEndPoint()
        {
            // Disable idle timeout checking: no scheduler and -1 timeout for this instance.
            super(null);
            super.setIdleTimeout(-1);
        }

        @Override
        public long getIdleTimeout()
        {
            return getEndPoint().getIdleTimeout();
        }

        @Override
        public void setIdleTimeout(long idleTimeout)
        {
            getEndPoint().setIdleTimeout(idleTimeout);
        }

        @Override
        public boolean isOpen()
        {
            return getEndPoint().isOpen();
        }

        @Override
        public InetSocketAddress getLocalAddress()
        {
            return getEndPoint().getLocalAddress();
        }

        @Override
        public InetSocketAddress getRemoteAddress()
        {
            return getEndPoint().getRemoteAddress();
        }

        @Override
        protected WriteFlusher getWriteFlusher()
        {
            return super.getWriteFlusher();
        }

        @Override
        protected void onIncompleteFlush()
        {
            ByteBuffer write = null;
            boolean fill_interest = false;
            synchronized(_decryptedEndPoint)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("onIncompleteFlush {} {}", SslConnection.this, BufferUtil.toDetailString(_encryptedOutput));
                
                switch(_flushState)
                {
                    case NEEDS_WRITE:
                        _flushState = FlushState.WRITING;
                        write = _encryptedOutput==null?BufferUtil.EMPTY_BUFFER:_encryptedOutput;
                        break;

                    case NEEDS_UNWRAP:
                        if (_sslEngine.getHandshakeStatus()==HandshakeStatus.NEED_UNWRAP)
                        {
                            _flushState = FlushState.WAIT_FOR_FILL;
                            fill_interest = true;
                        }
                        else
                        {
                            // TODO The state has moved on, so just try to write
                            // but perhaps we should not duplicate state?
                            // perhaps would could handle NEEDS_UNWRAP in fill exit???
                            write = _encryptedOutput==null?BufferUtil.EMPTY_BUFFER:_encryptedOutput;
                        }
                        break;
                 
                    default:
                        break;
                }
            }

            if (write!=null)
                getEndPoint().write(_incompleteWriteCallback, write);
            if (fill_interest)
                ensureFillInterested();
        }


        protected void onFillable()
        {
            try
            {
                // If we are handshaking, then wake up any waiting write as well as it may have been blocked on the read
                boolean complete_write = false;
                synchronized(_decryptedEndPoint)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("onFillable {}", SslConnection.this);
                    
                    _fillState = FillState.IDLE;
                    switch(_flushState)
                    {
                        case WAIT_FOR_FILL:
                            complete_write = true;
                            break;
                        default:
                            break;
                    }      
                }

                // Ensure a fill is always done if needed then wake up any fill interest
                if (complete_write)
                    fill(BufferUtil.EMPTY_BUFFER);
                getFillInterest().fillable();
            }
            catch (Throwable e)
            {
                close(e);
            }         
        }
        

        protected void onFillableFail(Throwable failure)
        {
            // If we are handshaking, then wake up any waiting write as well as it may have been blocked on the read
            boolean fail = false;
            synchronized(_decryptedEndPoint)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("onFillableFail {}", SslConnection.this, failure);
                
                _fillState = FillState.IDLE;
                switch(_flushState)
                {
                    case WAIT_FOR_FILL:
                        _flushState = FlushState.IDLE;
                        fail = true;
                        break;
                    default:
                        break;
                } 
            }


            // wake up whoever is doing the fill
            getFillInterest().onFail(failure);
            
            // Try to complete the write
            if (fail)
            {
                if (!getWriteFlusher().onFail(failure))
                    close(failure);
            }
        }
        
        
        @Override
        protected void needsFillInterest()
        {
            boolean fillable;
            ByteBuffer write = null;
            boolean interest = true;
            synchronized(_decryptedEndPoint)
            {
                // Do we already have some app data, then app can fill now so return true
                fillable = (BufferUtil.hasContent(_decryptedInput))
                        // or if we have encryptedInput and have not underflowed yet, the it is worth trying a fill
                        || BufferUtil.hasContent(_encryptedInput) && !_underFlown;

                if (LOG.isDebugEnabled())
                    LOG.debug("needFillInterest {} fillable={} {}", SslConnection.this, fillable, BufferUtil.toHexSummary(_encryptedOutput));
                                
                // If we have no encrypted data to decrypt OR we have some, but it is not enough
                if (!fillable)
                {
                    // Are we actually write blocked?
                    switch(_fillState)
                    {
                        case NEEDS_WRAP:
                            switch (_flushState)
                            {
                                case NEEDS_WRITE:
                                case IDLE:
                                    if (BufferUtil.hasContent(_encryptedOutput))
                                    {
                                        // then write it and then fill
                                        _flushState = FlushState.WRITING;
                                        write = _encryptedOutput;
                                        _fillState = FillState.WAIT_FOR_FLUSH;
                                        interest = false;
                                    }
                                    else
                                    {
                                        // No data to write, so try filling again
                                        fillable = true;
                                        interest = false;
                                        _fillState = FillState.IDLE;
                                    }
                                    break;
                                    
                                case NEEDS_UNWRAP:
                                    // TODO should we check again?
                                    _flushState = FlushState.WAIT_FOR_FILL;
                                    _fillState = FillState.INTERESTED;
                                    break;
                                    
                                default:
                                    _fillState = FillState.WAIT_FOR_FLUSH;
                                    break;   
                            }
                            break;
                            
                        default:
                            break;
                    }
                }
            }
            
            if (write!=null)
                getEndPoint().write(_incompleteWriteCallback, write);
            
            if (fillable)
                getExecutor().execute(_runFillable);
            else if (interest)
                ensureFillInterested();
        }

        @Override
        public void setConnection(Connection connection)
        {
            if (connection instanceof AbstractConnection)
            {
                AbstractConnection a = (AbstractConnection)connection;
                if (a.getInputBufferSize()<_sslEngine.getSession().getApplicationBufferSize())
                    a.setInputBufferSize(_sslEngine.getSession().getApplicationBufferSize());
            }
            super.setConnection(connection);
        }

        public SslConnection getSslConnection()
        {
            return SslConnection.this;
        }

        @Override
        public int fill(ByteBuffer buffer) throws IOException
        {
            try
            {
                synchronized(_decryptedEndPoint)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("fill enter {} {}", SslConnection.this, BufferUtil.toDetailString(buffer));
                    
                    Throwable failure = null;
                    try
                    {
                        _filling = true;

                        switch (_fillState)
                        {
                            case IDLE:
                                break;

                            case FILLING:
                                _fillState = FillState.IDLE;
                                break;

                            default:
                                return 0;
                        }

                        // Do we already have some decrypted data?
                        if (BufferUtil.hasContent(_decryptedInput))
                            return BufferUtil.append(buffer,_decryptedInput);

                        // We will need a network buffer
                        if (_encryptedInput == null)
                            _encryptedInput = _bufferPool.acquire(_sslEngine.getSession().getPacketBufferSize(), _encryptedDirectBuffers);
                        else
                            BufferUtil.compact(_encryptedInput);

                        // We also need an app buffer, but can use the passed buffer if it is big enough
                        ByteBuffer app_in;
                        boolean used_passed_buffer = false;
                        if (BufferUtil.space(buffer) > _sslEngine.getSession().getApplicationBufferSize())
                        {
                            app_in = buffer;
                            used_passed_buffer = true;
                        }
                        else if (_decryptedInput == null)
                            app_in = _decryptedInput = _bufferPool.acquire(_sslEngine.getSession().getApplicationBufferSize(), _decryptedDirectBuffers);
                        else
                            app_in = _decryptedInput;

                        // loop filling and unwrapping until we have something
                        filling: while (true)
                        {
                            // Let's try reading some encrypted data... even if we have some already.
                            int net_filled = getEndPoint().fill(_encryptedInput);

                            if (net_filled > 0 && _handshake.get() == Handshake.INITIAL && _sslEngine.isOutboundDone())
                                throw new SSLHandshakeException("Closed during handshake");

                            unwrapping: while (true)
                            {
                                // Let's unwrap even if we have no net data because in that
                                // case we want to fall through to the handshake handling
                                int pos = BufferUtil.flipToFill(app_in);
                                SSLEngineResult unwrapResult;
                                try
                                {
                                    unwrapResult = _sslEngine.unwrap(_encryptedInput, app_in);
                                }
                                finally
                                {
                                    BufferUtil.flipToFlush(app_in, pos);
                                }
                                
                                HandshakeStatus handshakeStatus = _sslEngine.getHandshakeStatus();
                                HandshakeStatus unwrapHandshakeStatus = unwrapResult.getHandshakeStatus();
                                Status unwrapResultStatus = unwrapResult.getStatus();
                                
                                if (LOG.isDebugEnabled())
                                    LOG.debug("unwrap {} {} {}", 
                                        net_filled, 
                                        unwrapResult.toString().replace('\n',' '),
                                        BufferUtil.toDetailString(buffer));
                      
                                // Extra check on unwrapResultStatus == OK with zero bytes consumed
                                // or produced is due to an SSL client on Android (see bug #454773).
                                _underFlown = unwrapResultStatus == Status.BUFFER_UNDERFLOW ||
                                        unwrapResultStatus == Status.OK && unwrapResult.bytesConsumed() == 0 && unwrapResult.bytesProduced() == 0;

                                if (_underFlown)
                                {
                                    if (net_filled < 0 && _sslEngine.getUseClientMode())
                                        closeInbound();
                                    if (net_filled <= 0)
                                        return net_filled;
                                }

                                switch (unwrapResultStatus)
                                {
                                    case CLOSED:
                                    {
                                        switch (handshakeStatus)
                                        {
                                            case NOT_HANDSHAKING:
                                                // We were not handshaking, so just tell the app we are closed
                                                return -1;
                                            
                                            case NEED_TASK:
                                                _sslEngine.getDelegatedTask().run();
                                                continue;

                                            case NEED_WRAP:
                                                // We need to send some handshake data (probably the close handshake).
                                                // We return -1 so that the application can drive the close by flushing
                                                // or shutting down the output.
                                                return -1;

                                            case NEED_UNWRAP:
                                                // We expected to read more, but we got closed.
                                                // Return -1 to indicate to the application to drive the close.
                                                return -1;

                                            default:
                                                throw new IllegalStateException();
                                        }
                                    }
                                    case BUFFER_UNDERFLOW:
                                    case OK:
                                    {
                                        if (unwrapHandshakeStatus == HandshakeStatus.FINISHED)
                                            handshakeSucceeded();

                                        // Check whether re-negotiation is allowed
                                        if (!allowRenegotiate(handshakeStatus))
                                            return -1;
                                        
                                        // If bytes were produced, don't bother with the handshake status;
                                        // pass the decrypted data to the application, which will perform
                                        // another call to fill() or flush().
                                        if (unwrapResult.bytesProduced() > 0)
                                        {
                                            if (used_passed_buffer)
                                                return unwrapResult.bytesProduced();
                                            return BufferUtil.append(buffer,_decryptedInput);
                                        }

                                        switch (handshakeStatus)
                                        {
                                            case NOT_HANDSHAKING:
                                                if (_underFlown)
                                                    continue filling;
                                                continue unwrapping;
                                                
                                            case NEED_TASK:
                                                _sslEngine.getDelegatedTask().run();
                                                continue unwrapping;
                                                
                                            case NEED_WRAP:
                                                // if we were called from flush, just return
                                                if (_flushing)
                                                    return 0;
                                                
                                                // If nobody else is flushing, let's try ourselves
                                                if (_flushState==FlushState.IDLE)
                                                {
                                                    if (LOG.isDebugEnabled())
                                                        LOG.debug("flush from fill {}", SslConnection.this);
                                                    if (flush(BufferUtil.EMPTY_BUFFER))
                                                    {
                                                        // The flush wrapped to the encrypted bytes so continue to fill.
                                                        if (_underFlown)
                                                            continue filling;
                                                        continue unwrapping;
                                                    }
                                                }
                                                                                                
                                                // We need to arrange a flush
                                                _fillState = FillState.NEEDS_WRAP;
                                                return 0;
                                            
                                            case NEED_UNWRAP:
                                                if (_underFlown)
                                                    continue filling;
                                                continue unwrapping;
                                            
                                            default:
                                                throw new IllegalStateException();
                                        }
                                    }
                                    default:
                                    {
                                        throw new IllegalStateException();
                                    }
                                }
                            }
                        }
                    }
                    catch (Throwable x)
                    {
                        handshakeFailed(x);
                        failure = x;
                        throw x;
                    }
                    finally
                    {
                        _filling = false;

                        switch (_flushState)
                        {
                            case WAIT_FOR_FILL:
                                if (LOG.isDebugEnabled())
                                    LOG.debug("execute completeWrite {}",failure);
                                _flushState = FlushState.IDLE;
                                if (failure==null)
                                    getExecutor().execute(()->_decryptedEndPoint.getWriteFlusher().completeWrite());
                                else
                                    getExecutor().execute(new FailEncryptedWrite(failure));                           
                                break;
                            default:
                                break;
                        }

                        if (_encryptedInput != null && !_encryptedInput.hasRemaining())
                        {
                            _bufferPool.release(_encryptedInput);
                            _encryptedInput = null;
                        }
                        if (_decryptedInput != null && !_decryptedInput.hasRemaining())
                        {
                            _bufferPool.release(_decryptedInput);
                            _decryptedInput = null;
                        }

                        if (LOG.isDebugEnabled())
                            LOG.debug("fill exit {}", SslConnection.this);
                    }
                }
            }
            catch (Throwable x)
            {
                close(x);
                throw x;
            }
        }

        private void handshakeSucceeded()
        {
            if (_handshake.compareAndSet(Handshake.INITIAL, Handshake.SUCCEEDED))
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("handshake succeeded {} {} {}/{}",SslConnection.this,
                        _sslEngine.getUseClientMode() ? "client" : "resumed server",
                            _sslEngine.getSession().getProtocol(),_sslEngine.getSession().getCipherSuite());
                notifyHandshakeSucceeded(_sslEngine);
            }
            else if (_handshake.get() == Handshake.SUCCEEDED)
            {
                if (_renegotiationLimit>0)
                    _renegotiationLimit--;
            }
        }

        private void handshakeFailed(Throwable failure)
        {
            if (_handshake.compareAndSet(Handshake.INITIAL, Handshake.FAILED))
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("handshake failed {} {}", SslConnection.this, failure);
                if (!(failure instanceof SSLHandshakeException))
                    failure = new SSLHandshakeException(failure.getMessage()).initCause(failure);
                notifyHandshakeFailed(_sslEngine, failure);
            }
        }

        private void terminateInput()
        {
            try
            {
                _sslEngine.closeInbound();
            }
            catch (Throwable x)
            {
                LOG.ignore(x);
            }
        }

        private void closeInbound() throws SSLException
        {
            HandshakeStatus handshakeStatus = _sslEngine.getHandshakeStatus();
            try
            {
                _sslEngine.closeInbound();
            }
            catch (SSLException x)
            {
                if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING && !isAllowMissingCloseMessage())
                    throw x;
                else
                    LOG.ignore(x);
            }
        }

        
        @Override
        public boolean flush(ByteBuffer... appOuts) throws IOException
        {
            try
            {
                synchronized(_decryptedEndPoint)
                {
                    if (LOG.isDebugEnabled())
                    {
                        LOG.debug("flush enter {}", SslConnection.this);
                        for (ByteBuffer b : appOuts)
                            LOG.debug("buffer {}", BufferUtil.toDetailString(b));
                    }
                    
                    try
                    {
                        _flushing = true;
                        
                        switch(_flushState)
                        {
                            case IDLE:
                                break;
                                
                            default:
                                return false;
                        }

                        // We will need a network buffer
                        if (_encryptedOutput == null)
                            _encryptedOutput = _bufferPool.acquire(_sslEngine.getSession().getPacketBufferSize(), _encryptedDirectBuffers);

                        while (true)
                        {
                            // We call sslEngine.wrap to try to take bytes from appOut buffers and encrypt them into the _netOut buffer
                            BufferUtil.compact(_encryptedOutput);
                            int pos = BufferUtil.flipToFill(_encryptedOutput);
                            SSLEngineResult wrapResult;
                            try
                            {
                                wrapResult = _sslEngine.wrap(appOuts, _encryptedOutput);
                            }
                            finally
                            {
                                BufferUtil.flipToFlush(_encryptedOutput, pos);
                            }
                            if (LOG.isDebugEnabled())
                                LOG.debug("wrap {} {}", wrapResult.toString().replace('\n',' '), BufferUtil.toHexSummary(_encryptedOutput));

                            Status wrapResultStatus = wrapResult.getStatus();

                            boolean allConsumed=true;
                            for (ByteBuffer b : appOuts)
                                if (BufferUtil.hasContent(b))
                                    allConsumed=false;

                            // and deal with the results returned from the sslEngineWrap
                            switch (wrapResultStatus)
                            {
                                case CLOSED:
                                {
                                    // The SSL engine has close, but there may be close handshake that needs to be written
                                    if (BufferUtil.hasContent(_encryptedOutput))
                                    {
                                        // This is an optimization to try to flush here rather than let onIncompleteFlush do a write 
                                        getEndPoint().flush(_encryptedOutput);                                        
                                        if (BufferUtil.hasContent(_encryptedOutput))
                                        {
                                            _flushState = FlushState.NEEDS_WRITE;
                                            return false;
                                        }
                                    }
                                    
                                    getEndPoint().shutdownOutput();
                                    if (allConsumed)
                                    {
                                        _flushState = FlushState.IDLE;
                                        return true;
                                    }
                                    throw new IOException("Broken pipe");
                                }
                                
                                case BUFFER_UNDERFLOW:
                                    throw new IllegalStateException();
                                
                                default:
                                {
                                    if (wrapResult.getHandshakeStatus() == HandshakeStatus.FINISHED)
                                        handshakeSucceeded();

                                    HandshakeStatus handshakeStatus = _sslEngine.getHandshakeStatus();

                                    // Check whether re-negotiation is allowed
                                    if (!allowRenegotiate(handshakeStatus))
                                    {
                                        getEndPoint().shutdownOutput();
                                        if (allConsumed)
                                        {
                                            _flushState = FlushState.IDLE;
                                            return true;
                                        }
                                        throw new IOException("Broken pipe");
                                    }

                                    // if we have net bytes, let's try to flush them
                                    if (BufferUtil.hasContent(_encryptedOutput))
                                    {
                                        boolean flushed = getEndPoint().flush(_encryptedOutput);
                                        if (LOG.isDebugEnabled())
                                            LOG.debug("flushed {} {}",flushed,BufferUtil.toHexSummary(_encryptedOutput));
                                    }

                                    // But we also might have more to do for the handshaking state.
                                    switch (handshakeStatus)
                                    {
                                        case NOT_HANDSHAKING:
                                            if (BufferUtil.hasContent(_encryptedOutput))
                                            {
                                                _flushState = FlushState.NEEDS_WRITE;
                                                return false;
                                            }
                                            if (allConsumed)
                                            {
                                                _flushState = FlushState.IDLE;
                                                return true;
                                            }

                                            // TODO can we spin here?
                                            continue;

                                        case NEED_TASK:
                                            // run the task and continue
                                            _sslEngine.getDelegatedTask().run();
                                            continue;

                                        case NEED_WRAP:
                                            // Hey we just wrapped! Who knows what the sslEngine is thinking, so continue and we will wrap again
                                            continue;

                                        case NEED_UNWRAP:
                                            // If we have encrypted data to write, let's just write it and hope the unwrap happens in the meantime
                                            if (BufferUtil.hasContent(_encryptedOutput))
                                            {
                                                _flushState = FlushState.NEEDS_WRITE;
                                                return false;
                                            }
                                            
                                            // If we have consumed all the data and flushed the encrypted data, then we don't care about the unwrap for now
                                            if (allConsumed)
                                            {
                                                _flushState = FlushState.IDLE;
                                                return true;
                                            }
                                            
                                            // If we were called from fill, just return
                                            if (_filling)
                                                return false;
                                            
                                            // If nobody else is filling, let's try a fill ourselves
                                            if (_fillState==FillState.IDLE)
                                            {
                                                if (LOG.isDebugEnabled())
                                                    LOG.debug("fill from flush {}", SslConnection.this);

                                                // Try to fill ourselves
                                                fill(BufferUtil.EMPTY_BUFFER);

                                                // if after the fill() we no longer need the unwrap, then lets continue;
                                                if (_sslEngine.getHandshakeStatus() != HandshakeStatus.NEED_UNWRAP)
                                                    continue;
                                            }
                                            
                                            // We need to arrange a fill ourselves
                                            _flushState = FlushState.NEEDS_UNWRAP;                                                
                                            return false;

                                        case FINISHED:
                                            throw new IllegalStateException();
                                    }
                                }
                            }
                        }
                    }
                    catch (Throwable x)
                    {
                        handshakeFailed(x);
                        throw x;
                    }
                    finally
                    {
                        _flushing = false;
                        releaseEncryptedOutputBuffer();
                        if (LOG.isDebugEnabled())
                            LOG.debug("flush exit {}", SslConnection.this);
                    }
                }
            }
            catch (Throwable x)
            {
                close(x);
                throw x;
            }
        }
        
        @Override
        public void doShutdownOutput()
        {
            try
            {
                boolean flush = false;
                boolean close = false;
                synchronized(_decryptedEndPoint)
                {
                    boolean ishut = isInputShutdown();
                    boolean oshut = isOutputShutdown();
                    if (LOG.isDebugEnabled())
                        LOG.debug("shutdownOutput: {} oshut={}, ishut={} {}", SslConnection.this, oshut, ishut);

                    if (oshut)
                        return;

                    if (!_closedOutbound)
                    {
                        _closedOutbound=true; // Only attempt this once
                        _sslEngine.closeOutbound();
                        flush = true;
                    }

                    // TODO review close logic here
                    if (ishut)
                        close = true;
                }

                if (flush)
                    flush(BufferUtil.EMPTY_BUFFER); // Send the TLS close message.
                if (close)
                    getEndPoint().close();
                else
                    ensureFillInterested();
            }
            catch (Throwable x)
            {
                LOG.ignore(x);
                getEndPoint().close();
            }
        }

        private void ensureFillInterested()
        {
            if (LOG.isDebugEnabled())
                LOG.debug("fillInterested SSL NB {}",SslConnection.this);
            SslConnection.this.tryFillInterested(_sslReadCallback);
        }

        @Override
        public boolean isOutputShutdown()
        {
            return _sslEngine.isOutboundDone() || getEndPoint().isOutputShutdown();
        }

        @Override
        public void doClose()
        {
            // First send the TLS Close Alert, then the FIN.
            doShutdownOutput();
            getEndPoint().close();
            super.doClose();
        }

        @Override
        public Object getTransport()
        {
            return getEndPoint();
        }

        @Override
        public boolean isInputShutdown()
        {
            return getEndPoint().isInputShutdown() || _sslEngine.isInboundDone();
        }

        private void notifyHandshakeSucceeded(SSLEngine sslEngine)
        {
            SslHandshakeListener.Event event = null;
            for (SslHandshakeListener listener : handshakeListeners)
            {
                if (event == null)
                    event = new SslHandshakeListener.Event(sslEngine);
                try
                {
                    listener.handshakeSucceeded(event);
                }
                catch (Throwable x)
                {
                    LOG.info("Exception while notifying listener " + listener, x);
                }
            }
        }

        private void notifyHandshakeFailed(SSLEngine sslEngine, Throwable failure)
        {
            SslHandshakeListener.Event event = null;
            for (SslHandshakeListener listener : handshakeListeners)
            {
                if (event == null)
                    event = new SslHandshakeListener.Event(sslEngine);
                try
                {
                    listener.handshakeFailed(event, failure);
                }
                catch (Throwable x)
                {
                    LOG.info("Exception while notifying listener " + listener, x);
                }
            }
        }

        @Override
        public String toString()
        {
            return super.toEndPointString();
        }

        private boolean allowRenegotiate(HandshakeStatus handshakeStatus)
        {   
            if (_handshake.get() == Handshake.INITIAL || handshakeStatus == HandshakeStatus.NOT_HANDSHAKING)
                return true;
        
            if (!isRenegotiationAllowed())
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Renegotiation denied {}", SslConnection.this);
                terminateInput();
                return false;
            }
            
            if (getRenegotiationLimit()==0)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Renegotiation limit exceeded {}", SslConnection.this);
                terminateInput();
                return false;
            }
            
            return true;
        }

        private final class IncompleteWriteCallback implements Callback, Invocable
        {
            @Override
            public void succeeded()
            {            
                boolean fillable;
                synchronized(_decryptedEndPoint)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("IncompleteWriteCB.succeeded {}", SslConnection.this);
        
                    releaseEncryptedOutputBuffer();
                    _flushState = FlushState.IDLE;
                    switch(_fillState)
                    {
                        case NEEDS_WRAP:
                        case WAIT_FOR_FLUSH:
                            fillable = true;
                            _fillState = FillState.FILLING;
                            break;
        
                        default:
                            fillable = false;
                            break;
                    }
                }
                
                _decryptedEndPoint.getWriteFlusher().completeWrite();

                if (fillable)
                {
                    try
                    {
                        _decryptedEndPoint.fill(BufferUtil.EMPTY_BUFFER);
                        _decryptedEndPoint.getFillInterest().fillable();
                    }
                    catch (IOException e)
                    {
                        close(e);
                    }
                }  
            }
        
            @Override
            public void failed(final Throwable x)
            {
                boolean fail_fill_interest;
                synchronized(_decryptedEndPoint)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("IncompleteWriteCB failed {}", SslConnection.this, x);
        
                    BufferUtil.clear(_encryptedOutput);
                    releaseEncryptedOutputBuffer();
                    
                    _flushState = FlushState.IDLE;
                    switch(_fillState)
                    {
                        case NEEDS_WRAP:
                        case WAIT_FOR_FLUSH:
                            fail_fill_interest = true;
                            _fillState = FillState.IDLE;
                            break;
        
                        default:
                            fail_fill_interest = false;
                            break;
                    }
                }
        
                getExecutor().execute(()->
                {
                    if (fail_fill_interest)
                        _decryptedEndPoint.getFillInterest().onFail(x);
                    _decryptedEndPoint.getWriteFlusher().onFail(x);
                });
            }
        
            @Override
            public InvocationType getInvocationType()
            {
                return _decryptedEndPoint.getWriteFlusher().getCallbackInvocationType();
            }
        
            @Override
            public String toString()
            {
                return String.format("SSL@%h.DEP.writeCallback",SslConnection.this);
            }
        }
    }
    
    private class FailEncryptedWrite extends RunnableTask
    {
        private final Throwable failure;
    
        private FailEncryptedWrite(Throwable failure)
        {
            super("runFailWrite");
            this.failure = failure;
        }
    
        @Override
        public void run()
        {
            _decryptedEndPoint.getWriteFlusher().onFail(failure);
        }
    
        @Override
        public InvocationType getInvocationType()
        {
            return _decryptedEndPoint.getWriteFlusher().getCallbackInvocationType();
        }
    }
}
