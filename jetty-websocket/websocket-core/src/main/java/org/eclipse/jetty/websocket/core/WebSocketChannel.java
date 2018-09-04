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

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Utf8Appendable;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.core.extensions.ExtensionStack;
import org.eclipse.jetty.websocket.core.frames.Frame;
import org.eclipse.jetty.websocket.core.frames.OpCode;
import org.eclipse.jetty.websocket.core.io.BatchMode;
import org.eclipse.jetty.websocket.core.io.FrameFlusher;
import org.eclipse.jetty.websocket.core.io.WebSocketConnection;

/**
 * The Core WebSocket Session.
 *
 */
public class WebSocketChannel implements IncomingFrames, FrameHandler.CoreSession, Dumpable
{
    private Logger LOG = Log.getLogger(this.getClass());
    private final static CloseStatus NO_CODE = new CloseStatus(CloseStatus.NO_CODE);

    private final WebSocketChannelState state = new WebSocketChannelState();
    private final WebSocketPolicy policy;
    private final FrameHandler handler;
    private final ExtensionStack extensionStack;
    private final String subprotocol;
    private final AttributesMap attributes = new AttributesMap();

    
    private WebSocketConnection connection;

    public WebSocketChannel(FrameHandler handler,
    		WebSocketPolicy policy,
    		ExtensionStack extensionStack,
    		String subprotocol)
    {
        this.handler = handler;
        this.policy = policy;
        this.extensionStack = extensionStack;
        this.subprotocol = subprotocol;
        extensionStack.connect(new IncomingState(),new OutgoingState());
    }


    public ExtensionStack getExtensionStack()
    {
        return extensionStack;
    }

    public FrameHandler getHandler()
    {
        return handler;
    }

    @Override
    public String getSubprotocol()
    {
        return subprotocol;
    }

    @Override
    public long getIdleTimeout(TimeUnit units)
    {
        return TimeUnit.MILLISECONDS.convert(getPolicy().getIdleTimeout(),units);
    }
    
    @Override
    public void setIdleTimeout(long timeout, TimeUnit units)
    {
        getConnection().getEndPoint().setIdleTimeout(units.toMillis(timeout));
    }

    public SocketAddress getLocalAddress()
    {
        return getConnection().getEndPoint().getLocalAddress();
    }

    public SocketAddress getRemoteAddress()
    {
        return getConnection().getEndPoint().getRemoteAddress();
    }
    
    @Override
    public boolean isOpen()
    {
        return state.isOutOpen();
    }

    public void setWebSocketConnection(WebSocketConnection connection)
    {
        this.connection = connection;
    }

    /**
     * Send Close Frame with no payload.
     *
     * @param callback the callback on successful send of close frame
     */
    @Override
    public void close(Callback callback)
    {
        close(NO_CODE, callback, BatchMode.OFF);
    }

    /**
     * Send Close Frame with specified Status Code and optional Reason
     *
     * @param statusCode a valid WebSocket status code
     * @param reason an optional reason phrase
     * @param callback the callback on successful send of close frame
     */
    @Override
    public void close(int statusCode, String reason, Callback callback)
    {
        close(new CloseStatus(statusCode, reason), callback, BatchMode.OFF);
    }

    private void close(CloseStatus closeStatus, Callback callback, BatchMode batchMode)
    {        
        // TODO guard for multiple closes?
        if (state.onCloseOut(closeStatus))
        {
            callback = new Callback.Nested(callback)
            {
                @Override
                public void completed()
                {
                    try
                    {
                        handler.onClosed(state.getCloseStatus());
                    }
                    catch(Throwable e)
                    {
                        try
                        {
                            handler.onError(e);
                        }
                        catch(Throwable e2)
                        {
                            e.addSuppressed(e2);
                            LOG.warn(e);
                        }
                    }
                    finally
                    {
                        connection.close();
                    }
                }
            };
        }

        if (LOG.isDebugEnabled())
        {
            LOG.debug("close({}, {}, {})", closeStatus, callback, batchMode);
        }

        extensionStack.sendFrame(closeStatus.toFrame(),callback,batchMode);
    }
    
    public WebSocketPolicy getPolicy()
    {
        return policy;
    }


    public void onClosed(Throwable cause)
    {
        if (state.onClosed(cause))
        {
            // Forward Errors to Local WebSocket EndPoint
            try
            {
                handler.onError(cause);
            }
            catch(Throwable e)
            {
                cause.addSuppressed(e);
                LOG.warn(cause);
            }

            try
            {
                handler.onClosed(new CloseStatus(CloseStatus.NO_CLOSE, cause==null?null:cause.toString()));
            }
            catch (Exception e)
            {
                LOG.warn(e);
            }
        }

    }


    /**
     * Process an Error event seen by the Session and/or Connection
     *
     * @param cause the cause
     */
    public void processError(Throwable cause)
    {
        // Forward Errors to Local WebSocket EndPoint
        try
        {
            handler.onError(cause);
        }
        catch(Throwable e)
        {
            cause.addSuppressed(e);
            LOG.warn(cause);
        }

        //TODO review everything below
        if (cause instanceof Utf8Appendable.NotUtf8Exception)
        {
            close(WebSocketConstants.BAD_PAYLOAD, cause.getMessage(), Callback.NOOP);
        }
        else if (cause instanceof SocketTimeoutException)
        {
            // A path often seen in Windows
            close(WebSocketConstants.SHUTDOWN, cause.getMessage(), Callback.NOOP);
        }
        else if (cause instanceof IOException)
        {
            close(WebSocketConstants.PROTOCOL, cause.getMessage(), Callback.NOOP);
        }
        else if (cause instanceof SocketException)
        {
            // A path unique to Unix
            close(WebSocketConstants.SHUTDOWN, cause.getMessage(), Callback.NOOP);
        }
        else if (cause instanceof CloseException)
        {
            CloseException ce = (CloseException) cause;
            Callback callback = Callback.NOOP;

            // Force disconnect for protocol breaking status codes
            switch (ce.getStatusCode())
            {
                case WebSocketConstants.PROTOCOL:
                case WebSocketConstants.BAD_DATA:
                case WebSocketConstants.BAD_PAYLOAD:
                case WebSocketConstants.MESSAGE_TOO_LARGE:
                case WebSocketConstants.POLICY_VIOLATION:
                case WebSocketConstants.SERVER_ERROR:
                {
                    callback = Callback.NOOP;
                }
            }

            close(ce.getStatusCode(), ce.getMessage(), callback);
        }
        else if (cause instanceof WebSocketTimeoutException)
        {
            close(WebSocketConstants.SHUTDOWN, cause.getMessage(), Callback.NOOP);
        }
        else
        {
            LOG.warn("Unhandled Error (closing connection)", cause);

            // Exception on end-user WS-Endpoint.
            // Fast-fail & close connection with reason.
            int statusCode = WebSocketConstants.SERVER_ERROR;
            if (getPolicy().getBehavior() == WebSocketBehavior.CLIENT)
            {
                statusCode = WebSocketConstants.POLICY_VIOLATION;
            }
            close(statusCode, cause.getMessage(), Callback.NOOP);
        }
    }

    /**
     * Open/Activate the session.
     */
    public void onOpen()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("onOpen() {}", this);

        try
        {
            // Upgrade success
            state.onConnected();

            if (LOG.isDebugEnabled())
                LOG.debug("ConnectionState: Transition to CONNECTED");

            try
            {
                // Open connection and handler
                state.onOpen();
                handler.onOpen(this);
                if (LOG.isDebugEnabled())
                    LOG.debug("ConnectionState: Transition to OPEN");
            }
            catch (Throwable t)
            {
                LOG.warn("Error during OPEN", t);
                processError(new CloseException(WebSocketConstants.SERVER_ERROR, t));
            }

            // TODO what if we are going to start without read interest?  (eg reactive stream???)
            connection.fillInterested();
        }
        catch (Throwable t)
        {
            processError(t); // Handle error
        }
    }

    public WebSocketConnection getConnection()
    {
        return this.connection;
    }

    public Executor getExecutor()
    {
        return this.connection.getExecutor();
    }

    public ByteBufferPool getBufferPool()
    {
        return this.connection.getBufferPool();
    }

    @Override
    public void onReceiveFrame(Frame frame, Callback callback)
    {
        extensionStack.onReceiveFrame(frame, callback);
    }

    @Override
    public void sendFrame(Frame frame, Callback callback, BatchMode batchMode) 
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("sendFrame({}, {}, {})", frame, callback, batchMode);
        }

        if (frame.getOpCode() == OpCode.CLOSE)
            close(new CloseStatus(frame.getPayload()),callback, batchMode);
        else
            extensionStack.sendFrame(frame,callback,batchMode);
    }


    @Override
    public void flushBatch(Callback callback)
    {
        extensionStack.sendFrame(FrameFlusher.FLUSH_FRAME,callback,BatchMode.OFF);
    }
    
    @Override
    public void abort()
    {
        connection.getEndPoint().close();
    }
    
    private class IncomingState extends OpCode.Sequence implements IncomingFrames
    {
        @Override
        public void onReceiveFrame(Frame frame, Callback callback)
        {
            try
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("receiveFrame({}, {}) - connectionState={}, handler={}",
                              frame, callback, state, handler);

                String failure = check(frame.getOpCode(),frame.isFin());
                if (failure!=null)
                    callback.failed(new ProtocolException(failure)); //TODO protocol error?
                else if (state.isInOpen())
                {   
                    // Handle inbound close
                    if (frame.getOpCode() == OpCode.CLOSE)
                    {
                        CloseStatus closeStatus = new CloseStatus(frame.getPayload());
                        if (state.onCloseIn(closeStatus))
                        {
                            handler.onReceiveFrame(frame, callback); // handler should know about received frame
                            handler.onClosed(state.getCloseStatus());
                            connection.close();
                            return;
                        }

                        callback = new Callback.Nested(callback)
                        {
                            @Override
                            public void completed()
                            {
                                // was a close sent by the handler?
                                if (state.isOutOpen())
                                {
                                    // No!
                                    if (LOG.isDebugEnabled())
                                        LOG.debug("ConnectionState: sending close response {}",closeStatus);

                                    close(closeStatus.getCode(), closeStatus.getReason(), Callback.NOOP);
                                    return;
                                }
                            }
                        };
                    }
                    
                    // Handle the frame
                    handler.onReceiveFrame(frame, callback);
                }
                else
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Discarding post EOF frame - {}", frame);
                    callback.failed(new EofException());
                }
            }
            catch (Throwable t)
            {
                callback.failed(t);
            }
        }
    }

    private class OutgoingState extends OpCode.Sequence implements OutgoingFrames
    {
        @Override
        public void sendFrame(Frame frame, Callback callback, BatchMode batchMode)
        {
            String failure = check(frame.getOpCode(),frame.isFin());
            if (failure!=null)
                callback.failed(new ProtocolException(failure));
            else
                connection.sendFrame(frame,callback,batchMode);
        }
    }    
    
    @Override
    public String dump()
    {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        ContainerLifeCycle.dumpObject(out,this);
        ContainerLifeCycle.dump(out,indent,Arrays.asList(subprotocol,policy,extensionStack,handler));
    }


    @Override
    public List<ExtensionConfig> getExtensionConfig()
    {
        return extensionStack.getNegotiatedExtensions();
    }


    @Override
    public WebSocketBehavior getBehavior()
    {
        return policy.getBehavior();
    }
    
    @Override
    public void removeAttribute(String name)
    {
        attributes.removeAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object attribute)
    {
        attributes.setAttribute(name,attribute);
    }

    @Override
    public Object getAttribute(String name)
    {
        return attributes.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return attributes.getAttributeNames();
    }

    @Override
    public void clearAttributes()
    {
        attributes.clearAttributes();
    }
    
}
