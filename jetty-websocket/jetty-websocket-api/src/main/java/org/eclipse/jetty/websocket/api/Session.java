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

package org.eclipse.jetty.websocket.api;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;

import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.listeners.WebSocketListener;
import org.eclipse.jetty.websocket.common.HandshakeRequest;
import org.eclipse.jetty.websocket.common.HandshakeResponse;
import org.eclipse.jetty.websocket.common.io.SuspendToken;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.WebSocketPolicy;

/**
 * Session represents an active link of communications with a Remote WebSocket Endpoint.
 */
public interface Session extends Closeable
{
    /**
     * Request a close of the current conversation with a normal status code and no reason phrase.
     * <p>
     * This will enqueue a graceful close to the remote endpoint.
     * 
     * @see #close(CloseStatus)
     * @see #close(int, String)
     * @see #abort()
     */
    @Override
    void close();

    /**
     * Request Close the current conversation, giving a reason for the closure. Note the websocket spec defines the acceptable uses of status codes and reason
     * phrases.
     * <p>
     * This will enqueue a graceful close to the remote endpoint.
     * 
     * @param closeStatus
     *            the reason for the closure
     * 
     * @see #close()
     * @see #close(int, String)
     * @see #abort()
     */
    void close(CloseStatus closeStatus);

    /**
     * Send a websocket Close frame, with status code.
     * <p>
     * This will enqueue a graceful close to the remote endpoint.
     * 
     * @param statusCode
     *            the status code
     * @param reason
     *            the (optional) reason. (can be null for no reason)
     * @see StatusCode
     * 
     * @see #close()
     * @see #close(CloseStatus)
     * @see #abort()
     */
    void close(int statusCode, String reason);

    /**
     * Send a websocket Close frame, with status code.
     * <p>
     * This will enqueue a graceful close to the remote endpoint.
     *
     * @param statusCode
     *            the status code
     * @param reason
     *            the (optional) reason. (can be null for no reason)
     * @see StatusCode
     *
     * @see #close()
     * @see #close(CloseStatus)
     * @see #abort()
     */
    void close(StatusCode statusCode, String reason);

    /**
     * Issue a harsh abort of the underlying connection.
     * <p>
     * This will terminate the connection, without sending a websocket close frame.
     * <p>
     * Once called, any read/write activity on the websocket from this point will be indeterminate.
     * <p>
     * Once the underlying connection has been determined to be closed, the various onClose() events (either
     * {@link WebSocketListener#onWebSocketClose(int, String)} or {@link OnWebSocketClose}) will be called on your
     * websocket.
     * 
     * @throws IOException
     *             if unable to abort
     */
    void abort() throws IOException;

    /**
     * Return the number of milliseconds before this conversation will be closed by the container if it is inactive, ie no messages are either sent or received
     * in that time.
     * 
     * @return the timeout in milliseconds.
     */
    long getIdleTimeout();

    /**
     * Get the address of the local side.
     * <p>
     *     Do not assume that this will return a {@link InetSocketAddress} in all cases.
     *     Use of various proxies, and even UnixSockets can result a SocketAddress being returned
     *     without supporting {@link InetSocketAddress}
     * </p>
     *
     * @return the local side address
     * @since 10.0
     */
    SocketAddress getLocalSocketAddress();

    /**
     * Get the collection of open Sessions being tracked by the websocket container.
     * <p>
     *     The list of sessions can include different WebSocket endpoints from the
     *     endpoint this Session belongs to.
     *     If you need to identify similar endpoints, use the information contained within the
     *     {@link #getHandshakeRequest()} object.
     * </p>
     * @return the list of open Sessions.
     */
    Collection<Session> getOpenSessions();

    /**
     * Access the (now read-only) {@link WebSocketPolicy} in use for this connection.
     * 
     * @return the policy in use
     */
    WebSocketPolicy getPolicy();

    /**
     * Returns the version of the websocket protocol currently being used. This is taken as the value of the Sec-WebSocket-Version header used in the opening
     * handshake. i.e. "13".
     * 
     * @return the protocol version
     */
    String getProtocolVersion();

    /**
     * Return a reference to the RemoteEndpoint object representing the other end of this conversation.
     * 
     * @return the remote endpoint
     */
    RemoteEndpoint getRemote();

    /**
     * Get the address of the remote side.
     * <p>
     *     Do not assume that this will return a {@link InetSocketAddress} in all cases.
     *     Use of various proxies, and even UnixSockets can result a SocketAddress being returned
     *     without supporting {@link InetSocketAddress}
     * </p>
     *
     * @return the remote side address
     * @since 10.0
     */
    SocketAddress getRemoteSocketAddress();

    /**
     * Get the HandshakeRequest used to create this session
     * 
     * @return the HandshakeRequest used to create this session
     */
    HandshakeRequest getHandshakeRequest();

    /**
     * Get the HandshakeResponse used to create this session
     * 
     * @return the HandshakeResponse used to create this session
     */
    HandshakeResponse getHandshakeResponse();

    /**
     * Return true if and only if the underlying socket is onOpen.
     * 
     * @return whether the session is onOpen
     */
    boolean isOpen();

    /**
     * Return true if and only if the underlying socket is using a secure transport.
     * 
     * @return whether its using a secure transport
     */
    boolean isSecure();

    /**
     * Set the number of milliseconds before this conversation will be closed by the container if it is inactive, ie no messages are either sent or received.
     * 
     * @param ms
     *            the number of milliseconds.
     */
    void setIdleTimeout(long ms);

    /**
     * Suspend the incoming read events on the connection.
     * 
     * @return the suspend token suitable for resuming the reading of data on the connection.
     */
    SuspendToken suspend();
}
