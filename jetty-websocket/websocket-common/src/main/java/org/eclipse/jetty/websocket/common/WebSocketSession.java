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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Utf8Appendable.NotUtf8Exception;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.FrameCallback;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.SuspendToken;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.WebSocketTimeoutException;
import org.eclipse.jetty.websocket.api.extensions.ExtensionFactory;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.IncomingFrames;
import org.eclipse.jetty.websocket.api.extensions.OutgoingFrames;
import org.eclipse.jetty.websocket.common.frames.CloseFrame;
import org.eclipse.jetty.websocket.common.function.CommonEndpointFunctions;
import org.eclipse.jetty.websocket.common.function.EndpointFunctions;
import org.eclipse.jetty.websocket.common.io.AbstractWebSocketConnection;
import org.eclipse.jetty.websocket.common.message.MessageSink;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;
import org.eclipse.jetty.websocket.common.scopes.WebSocketSessionScope;

@ManagedObject("A Jetty WebSocket Session")
public class WebSocketSession extends ContainerLifeCycle implements Session, RemoteEndpointFactory,
        WebSocketSessionScope, IncomingFrames, LogicalConnection.Listener, Connection.Listener
{
    private static final FrameCallback EMPTY = new FrameCallback.Adapter();

    private final Logger LOG;

    private final WebSocketContainerScope containerScope;
    private final WebSocketPolicy policy;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final URI requestURI;
    private final LogicalConnection connection;
    private final Executor executor;
    private final AtomicConnectionState connectionState = new AtomicConnectionState();
    private final AtomicBoolean closeSent = new AtomicBoolean(false);
    private final AtomicBoolean closeNotified = new AtomicBoolean(false);

    /* The websocket endpoint object itself.
     * Not declared final, as it can be decorated by other libraries (like CDI)
     */
    private Object endpoint;

    // Callbacks

    // Endpoint Functions and MessageSinks
    protected EndpointFunctions endpointFunctions;
    private MessageSink activeMessageSink;

    private ClassLoader classLoader;
    private ExtensionFactory extensionFactory;
    private BatchMode batchmode = BatchMode.AUTO;
    private RemoteEndpointFactory remoteEndpointFactory;
    private String protocolVersion;
    private Map<String, String[]> parameterMap = new HashMap<>();
    private RemoteEndpoint remote;
    private OutgoingFrames outgoingHandler;
    private UpgradeRequest upgradeRequest;
    private UpgradeResponse upgradeResponse;
    private CompletableFuture<Session> openFuture;
    private AtomicReference<Throwable> pendingError = new AtomicReference<>();

    public WebSocketSession(WebSocketContainerScope containerScope, URI requestURI, Object endpoint, LogicalConnection connection)
    {
        Objects.requireNonNull(containerScope, "Container Scope cannot be null");
        Objects.requireNonNull(requestURI, "Request URI cannot be null");

        LOG = Log.getLogger(WebSocketSession.class.getName() + "." + connection.getPolicy().getBehavior().name());

        this.classLoader = Thread.currentThread().getContextClassLoader();
        this.containerScope = containerScope;
        this.requestURI = requestURI;
        this.endpoint = endpoint;
        this.connection = connection;
        this.executor = connection.getExecutor();
        this.outgoingHandler = connection;
        this.policy = connection.getPolicy();

        this.connection.setSession(this);

        addBean(this.connection);
        addBean(endpoint);
    }

    public EndpointFunctions newEndpointFunctions(Object endpoint)
    {
        return new CommonEndpointFunctions(endpoint, getPolicy(), this.executor);
    }

    public void connect()
    {
        connectionState.onConnecting();
    }

    /**
     * Aborts the active session abruptly.
     * @param statusCode the status code
     * @param reason the raw reason code
     */
    public void abort(int statusCode, String reason)
    {
        close(new CloseInfo(statusCode, reason), new DisconnectCallback());
    }

    @Override
    public void close()
    {
        /* This is assumed to always be a NORMAL closure, no reason phrase */
        close(new CloseInfo(StatusCode.NORMAL), null);
    }

    @Override
    public void close(CloseStatus closeStatus)
    {
        close(new CloseInfo(closeStatus.getCode(),closeStatus.getPhrase()), null);
    }

    @Override
    public void close(int statusCode, String reason)
    {
        close(new CloseInfo(statusCode, reason), null);
    }

    /**
     * CLOSE Primary Entry Point.
     *
     * <ul>
     *   <li>atomically enqueue CLOSE frame + flip flag to reject more frames</li>
     *   <li>setup CLOSE frame callback: must close flusher</li>
     * </ul>
     *
     * @param closeInfo the close details
     */
    private void close(CloseInfo closeInfo, FrameCallback callback)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("close({})", closeInfo);

        if (closed.compareAndSet(false, true))
        {
            CloseFrame frame = closeInfo.asFrame();
            connection.outgoingFrame(frame, new OnCloseLocalCallback(callback, connection, closeInfo), BatchMode.OFF);
        }
    }

    /**
     * Harsh disconnect
     */
    @Override
    public void disconnect()
    {
        connection.disconnect();
    }

    public void dispatch(Runnable runnable)
    {
        executor.execute(runnable);
    }

    @Override
    protected void doStart() throws Exception
    {
        if (LOG.isDebugEnabled())
            LOG.debug("starting - {}", this);

        Iterator<RemoteEndpointFactory> iter = ServiceLoader.load(RemoteEndpointFactory.class).iterator();
        if (iter.hasNext())
            remoteEndpointFactory = iter.next();

        if (remoteEndpointFactory == null)
            remoteEndpointFactory = this;

        if (LOG.isDebugEnabled())
            LOG.debug("Using RemoteEndpointFactory: {}", remoteEndpointFactory);

        // Start WebSocketSession before decorating the endpoint (CDI requirement)
        super.doStart();

        // Decorate endpoint only after WebSocketSession has been started (CDI requirement)
        if(this.endpoint instanceof ManagedEndpoint)
        {
            ManagedEndpoint managedEndpoint = (ManagedEndpoint) this.endpoint;
            Object rawEndpoint = managedEndpoint.getRawEndpoint();
            rawEndpoint = this.containerScope.getObjectFactory().decorate(rawEndpoint);
            managedEndpoint.setRawEndpoint(rawEndpoint);
        }
        else
        {
            this.endpoint = this.containerScope.getObjectFactory().decorate(this.endpoint);
        }

        this.endpointFunctions = newEndpointFunctions(this.endpoint);
        addManaged(this.endpointFunctions);

        connection.setMaxIdleTimeout(this.policy.getIdleTimeout());

        Throwable fastFail;
        synchronized (pendingError)
        {
            fastFail = pendingError.get();
        }
        if (fastFail != null)
            onError(fastFail);
    }

    @Override
    protected void doStop() throws Exception
    {
        if (LOG.isDebugEnabled())
            LOG.debug("stopping - {}", this);

        try
        {
            CloseInfo closeInfo = new CloseInfo(StatusCode.SHUTDOWN, "Shutdown");
            close(closeInfo, new FrameCallback.Adapter()
            {
                @Override
                public void succeed()
                {
                    endpointFunctions.onClose(closeInfo);
                }
            });

        }
        catch (Throwable ignore)
        {
            LOG.ignore(ignore);
        }
        super.doStop();
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        dumpThis(out);
        out.append(indent).append(" +- endpoint : ").append(endpoint.getClass().getName()).append('@').append(Integer.toHexString(endpoint.hashCode()));
        out.append(indent).append(" +- outgoingHandler : ");
        if (outgoingHandler instanceof Dumpable)
        {
            ((Dumpable) outgoingHandler).dump(out, indent + "    ");
        }
        else
        {
            out.append(outgoingHandler.toString()).append(System.lineSeparator());
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        WebSocketSession other = (WebSocketSession) obj;
        if (connection == null)
        {
            if (other.connection != null)
            {
                return false;
            }
        }
        else if (!connection.equals(other.connection))
        {
            return false;
        }
        return true;
    }

    public ByteBufferPool getBufferPool()
    {
        return this.connection.getBufferPool();
    }

    public ClassLoader getClassLoader()
    {
        return this.getClass().getClassLoader();
    }

    public LogicalConnection getConnection()
    {
        return connection;
    }

    public AtomicConnectionState getConnectionState()
    {
        return connectionState;
    }

    @Override
    public WebSocketContainerScope getContainerScope()
    {
        return this.containerScope;
    }

    public Object getEndpoint()
    {
        Object ret = endpoint;
        while (ret instanceof ManagedEndpoint)
        {
            ret = ((ManagedEndpoint) ret).getRawEndpoint();
        }
        return ret;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public ExtensionFactory getExtensionFactory()
    {
        return extensionFactory;
    }

    /**
     * The idle timeout in milliseconds
     */
    @Override
    public long getIdleTimeout()
    {
        return connection.getMaxIdleTimeout();
    }

    private Throwable getInvokedCause(Throwable t)
    {
        if (t instanceof FunctionCallException)
        {
            Throwable cause = ((FunctionCallException) t).getInvokedCause();
            if (cause != null)
                return cause;
        }

        return t;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return connection.getLocalAddress();
    }

    @ManagedAttribute(readonly = true)
    public OutgoingFrames getOutgoingHandler()
    {
        return outgoingHandler;
    }

    @Override
    public WebSocketPolicy getPolicy()
    {
        return this.policy;
    }

    @Override
    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    @Override
    public RemoteEndpoint getRemote()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{}.getRemote()", this.getClass().getSimpleName());

        AtomicConnectionState.State state = connectionState.get();

        if ((state == AtomicConnectionState.State.OPEN) || (state == AtomicConnectionState.State.CONNECTED))
        {
            return remote;
        }

        String err = String.format("RemoteEndpoint unavailable, current state [%s], expecting [%s or %s]",
                state.name(), AtomicConnectionState.State.OPEN.name(), AtomicConnectionState.State.CONNECTED.name());

        throw new WebSocketException(err);
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return connection.getRemoteAddress();
    }

    public URI getRequestURI()
    {
        return requestURI;
    }

    @Override
    public UpgradeRequest getUpgradeRequest()
    {
        return this.upgradeRequest;
    }

    @Override
    public UpgradeResponse getUpgradeResponse()
    {
        return this.upgradeResponse;
    }

    @Override
    public WebSocketSession getWebSocketSession()
    {
        return this;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((connection == null) ? 0 : connection.hashCode());
        return result;
    }

    /**
     * Incoming Raw Frames from Parser (after ExtensionStack)
     */
    @Override
    public void incomingFrame(Frame frame, FrameCallback callback)
    {
        try (ThreadClassLoaderScope scope = new ThreadClassLoaderScope(classLoader))
        {
            if (LOG.isDebugEnabled())
            {
                LOG.debug("incomingFrame({}, {}) - this.state={}, connectionState={}, endpointFunctions={}",
                        frame, callback, getState(), connectionState.get(), endpointFunctions);
            }
            if (connectionState.get() != AtomicConnectionState.State.CLOSED)
            {
                // For endpoints that want to see raw frames.
                // These are immutable.
                endpointFunctions.onFrame(frame);

                byte opcode = frame.getOpCode();
                switch (opcode)
                {
                    case OpCode.CLOSE:
                    {

                        if (connectionState.onClosing())
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("ConnectionState: Transition to CLOSING");
                            CloseFrame closeframe = (CloseFrame) frame;
                            CloseInfo closeInfo = new CloseInfo(closeframe, true);
                            notifyClose(closeInfo);
                            close(closeInfo, new DisconnectCallback());
                        }
                        else if (connectionState.onClosed())
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("ConnectionState: Transition to CLOSED");
                            CloseFrame closeframe = (CloseFrame) frame;
                            CloseInfo closeInfo = new CloseInfo(closeframe, true);
                            notifyClose(closeInfo);
                            connection.disconnect();
                        }
                        else
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("ConnectionState: {} - Close Frame Received", connectionState);
                        }

                        // let fill/parse continue
                        callback.succeed();

                        return;
                    }
                    case OpCode.PING:
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("PING: {}", BufferUtil.toDetailString(frame.getPayload()));

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

                        endpointFunctions.onPing(frame.getPayload());
                        callback.succeed();

                        try
                        {
                            getRemote().sendPong(pongBuf);
                        }
                        catch (Throwable t)
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("Unable to send pong", t);
                        }
                        break;
                    }
                    case OpCode.PONG:
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("PONG: {}", BufferUtil.toDetailString(frame.getPayload()));

                        endpointFunctions.onPong(frame.getPayload());
                        callback.succeed();
                        break;
                    }
                    case OpCode.BINARY:
                    {
                        endpointFunctions.onBinary(frame, callback);
                        return;
                    }
                    case OpCode.TEXT:
                    {
                        endpointFunctions.onText(frame, callback);
                        return;
                    }
                    case OpCode.CONTINUATION:
                    {
                        endpointFunctions.onContinuation(frame, callback);
                        if (activeMessageSink != null)
                            activeMessageSink.accept(frame, callback);

                        return;
                    }
                    default:
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("Unhandled OpCode: {}", opcode);
                    }
                }
            }
            else
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Discarding post EOF frame - {}", frame);
            }
        }
        catch (Throwable t)
        {
            callback.fail(t);
        }

        // Unset active MessageSink if this was a fin frame
        if (frame.getType().isData() && frame.isFin() && activeMessageSink != null)
            activeMessageSink = null;
    }

    @Override
    public boolean isOpen()
    {
        return !closed.get() && (this.connectionState.get() == AtomicConnectionState.State.OPEN);
    }

    @Override
    public boolean isSecure()
    {
        if (upgradeRequest == null)
        {
            throw new IllegalStateException("No valid UpgradeRequest yet");
        }

        URI requestURI = upgradeRequest.getRequestURI();

        return "wss".equalsIgnoreCase(requestURI.getScheme());
    }

    public void notifyClose(CloseInfo closeInfo)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("notifyClose({}) closeNotified={} [{}]", closeInfo, closeNotified.get(), getState());
        }

        // only notify once
        if (closeNotified.compareAndSet(false, true))
        {
            endpointFunctions.onClose(closeInfo);
        }
    }

    /**
     * Error Event.
     * <p>
     * Can be seen from Session and Connection.
     * </p>
     *
     * @param t the raw cause
     */
    @Override
    public void onError(Throwable t)
    {
        synchronized (pendingError)
        {
            if (!endpointFunctions.isStarted())
            {
                // this is a *really* fast fail, before the Session has even started.
                pendingError.compareAndSet(null, t);
                return;
            }
        }

        Throwable cause = getInvokedCause(t);

        if (openFuture != null && !openFuture.isDone())
            openFuture.completeExceptionally(cause);

        // Forward Errors to User WebSocket Object
        endpointFunctions.onError(cause);

        if (cause instanceof NotUtf8Exception)
        {
            close(new CloseInfo(StatusCode.BAD_PAYLOAD, cause.getMessage()), new DisconnectCallback());
        }
        else if (cause instanceof SocketTimeoutException)
        {
            // A path often seen in Windows
            close(new CloseInfo(StatusCode.SHUTDOWN, cause.getMessage()), new DisconnectCallback());
        }
        else if (cause instanceof IOException)
        {
            close(new CloseInfo(StatusCode.PROTOCOL, cause.getMessage()), new DisconnectCallback());
        }
        else if (cause instanceof SocketException)
        {
            // A path unique to Unix
            close(new CloseInfo(StatusCode.SHUTDOWN, cause.getMessage()), new DisconnectCallback());
        }
        else if (cause instanceof CloseException)
        {
            CloseException ce = (CloseException) cause;
            FrameCallback callback = EMPTY;

            // Force disconnect for protocol breaking status codes
            switch (ce.getStatusCode())
            {
                case StatusCode.PROTOCOL:
                case StatusCode.BAD_DATA:
                case StatusCode.BAD_PAYLOAD:
                case StatusCode.MESSAGE_TOO_LARGE:
                case StatusCode.POLICY_VIOLATION:
                case StatusCode.SERVER_ERROR:
                {
                    callback = new DisconnectCallback();
                }
            }

            close(new CloseInfo(ce.getStatusCode(), ce.getMessage()), callback);
        }
        else if (cause instanceof WebSocketTimeoutException)
        {
            close(new CloseInfo(StatusCode.SHUTDOWN, cause.getMessage()), new DisconnectCallback());
        }
        else
        {
            LOG.warn("Unhandled Error (closing connection)", cause);

            // Exception on end-user WS-Endpoint.
            // Fast-fail & close connection with reason.
            int statusCode = StatusCode.SERVER_ERROR;
            if (getPolicy().getBehavior() == WebSocketBehavior.CLIENT)
            {
                statusCode = StatusCode.POLICY_VIOLATION;
            }
            close(statusCode, cause.getMessage());
        }
    }

    /**
     * Connection Disconnect Event.
     * <p>
     *     Represents the low level Jetty Connection close/disconnect.
     * </p>
     *
     * @param connection the connection
     */
    @Override
    public void onClosed(Connection connection)
    {
        try
        {
            if (LOG.isDebugEnabled())
                LOG.debug("{}.onSessionClosed()", containerScope.getClass().getSimpleName());
            remote = null;
            containerScope.onSessionClosed(this);
        }
        catch (Throwable t)
        {
            LOG.ignore(t);
        }
    }

    /**
     * Connection Open Event
     * <p>
     *     Represents the low level Jetty Connection open/connect.
     * </p>
     *
     * @param connection the connection
     */
    @Override
    public void onOpened(Connection connection)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{}.onOpened()", this.getClass().getSimpleName());
        connectionState.onConnecting();
        open();
    }

    @Override
    public WebSocketRemoteEndpoint newRemoteEndpoint(LogicalConnection connection, OutgoingFrames outgoingFrames, BatchMode batchMode)
    {
        return new WebSocketRemoteEndpoint(this, outgoingHandler, getBatchMode());
    }

    /**
     * Open/Activate the session
     */
    public void open()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{}.open()", this.getClass().getSimpleName());

        if (remote != null)
        {
            // already opened
            return;
        }

        try (ThreadClassLoaderScope scope = new ThreadClassLoaderScope(classLoader))
        {
            // Upgrade success
            if (connectionState.onConnected())
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("ConnectionState: Transition to CONNECTED");

                // Connect remote
                remote = remoteEndpointFactory.newRemoteEndpoint(connection, outgoingHandler, getBatchMode());
                if (LOG.isDebugEnabled())
                    LOG.debug("{}.open() remote={}", this.getClass().getSimpleName(), remote);

                try
                {
                    // Open WebSocket
                    endpointFunctions.onOpen(this);

                    // Open connection
                    if (connectionState.onOpen())
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("ConnectionState: Transition to OPEN");

                        // notify session listeners
                        try
                        {
                            if (LOG.isDebugEnabled())
                                LOG.debug("{}.onSessionOpened()", containerScope.getClass().getSimpleName());
                            containerScope.onSessionOpened(this);
                        }
                        catch (Throwable t)
                        {
                            LOG.ignore(t);
                        }

                        if (LOG.isDebugEnabled())
                        {
                            LOG.debug("open -> {}", dump());
                        }

                        if (openFuture != null)
                        {
                            openFuture.complete(this);
                        }
                    }
                }
                catch (Throwable t)
                {
                    endpointFunctions.getLog().warn("Error during OPEN", t);
                    onError(new CloseException(StatusCode.SERVER_ERROR, t));
                }
                /* Perform fillInterested outside of onConnected / onOpen.
                 *
                 * This is to allow for 2 specific scenarios.
                 *
                 * 1) Fast Close
                 *    When an end users WSEndpoint.onOpen() calls
                 *    the Session.close() method.
                 *    This is a state transition of CONNECTING -> CONNECTED -> CLOSING
                 * 2) Fast Fail
                 *    When an end users WSEndpoint.onOpen() throws an Exception.
                 */
                connection.fillInterested();
            }
            else
            {
                IllegalStateException ise = new IllegalStateException("Unexpected state [" + connectionState.get() + "] when attempting to transition to CONNECTED");
                if (openFuture != null)
                {
                    openFuture.completeExceptionally(ise);
                }
                else
                {
                    throw ise;
                }
            }
        }
        catch (Throwable t)
        {
            LOG.warn(t);
            onError(t);
        }
    }

    public void setExtensionFactory(ExtensionFactory extensionFactory)
    {
        this.extensionFactory = extensionFactory;
    }

    public void setFuture(CompletableFuture<Session> fut)
    {
        this.openFuture = fut;
    }

    /**
     * Set the timeout in milliseconds
     */
    @Override
    public void setIdleTimeout(long ms)
    {
        connection.setMaxIdleTimeout(ms);
    }

    public void setOutgoingHandler(OutgoingFrames outgoing)
    {
        this.outgoingHandler = outgoing;
    }

    public void setUpgradeRequest(UpgradeRequest request)
    {
        this.upgradeRequest = request;
        this.protocolVersion = request.getProtocolVersion();
        this.parameterMap.clear();
        if (request.getParameterMap() != null)
        {
            for (Map.Entry<String, List<String>> entry : request.getParameterMap().entrySet())
            {
                List<String> values = entry.getValue();
                if (values != null)
                {
                    this.parameterMap.put(entry.getKey(), values.toArray(new String[values.size()]));
                }
                else
                {
                    this.parameterMap.put(entry.getKey(), new String[0]);
                }
            }
        }
    }

    public void setUpgradeResponse(UpgradeResponse response)
    {
        this.upgradeResponse = response;
    }

    @Override
    public SuspendToken suspend()
    {
        // TODO: limit ability to suspend to only when websocket calls application ?

        return connection.suspend();
    }

    /**
     * @return the default (initial) value for the batching mode.
     */
    public BatchMode getBatchMode()
    {
        return this.batchmode;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getPolicy().getBehavior());
        Object endp = endpoint;
        // unwrap
        while (endp instanceof ManagedEndpoint)
        {
            endp = ((ManagedEndpoint) endp).getRawEndpoint();
        }
        sb.append(',').append(endp.getClass().getName());
        sb.append(',').append(getConnection().getClass().getSimpleName());
        if (getConnection() instanceof AbstractWebSocketConnection)
        {
            if (isOpen() && remote != null)
            {
                sb.append(',').append(getRemoteAddress());
                if (getPolicy().getBehavior() == WebSocketBehavior.SERVER)
                {
                    sb.append(',').append(getRequestURI());
                    sb.append(',').append(getLocalAddress());
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public interface Listener
    {
        @SuppressWarnings("unused")
        default void onCreated(WebSocketSession session) { }

        void onOpened(WebSocketSession session);

        void onClosed(WebSocketSession session);
    }

    public static class OnCloseLocalCallback implements FrameCallback
    {
        private final FrameCallback wrapped;
        private final LogicalConnection connection;
        private final CloseInfo close;

        public OnCloseLocalCallback(FrameCallback callback, LogicalConnection connection, CloseInfo close)
        {
            this.wrapped = callback;
            this.connection = connection;
            this.close = close;
        }

        @Override
        public void succeed()
        {
            try
            {
                if (wrapped != null)
                {
                    wrapped.succeed();
                }
            }
            finally
            {
                connection.onLocalClose(close);
            }
        }

        @Override
        public void fail(Throwable cause)
        {
            try
            {
                if (wrapped != null)
                {
                    wrapped.fail(cause);
                }
            }
            finally
            {
                connection.onLocalClose(close);
            }
        }
    }

    public class DisconnectCallback extends CompletionCallback
    {
        @Override
        public void complete()
        {
            connectionState.onClosed();
            disconnect();
        }
    }
}
