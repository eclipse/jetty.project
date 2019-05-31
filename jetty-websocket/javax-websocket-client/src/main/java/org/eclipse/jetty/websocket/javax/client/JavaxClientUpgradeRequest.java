//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.javax.client;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import javax.websocket.Session;

import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketFrameHandler;
import org.eclipse.jetty.websocket.javax.common.UpgradeRequest;

public class JavaxClientUpgradeRequest extends ClientUpgradeRequest
{
    private final JavaxWebSocketClientContainer containerContext;
    private final Object websocketPojo;
    private final CompletableFuture<Session> futureSession;

    public JavaxClientUpgradeRequest(JavaxWebSocketClientContainer clientContainer, WebSocketCoreClient coreClient, URI requestURI, Object websocketPojo)
    {
        super(coreClient, requestURI);
        this.containerContext = clientContainer;
        this.websocketPojo = websocketPojo;
        this.futureSession = new CompletableFuture<>();
    }

    @Override
    protected void handleException(Throwable failure)
    {
        super.handleException(failure);
        futureSession.completeExceptionally(failure);
    }

    @Override
    public FrameHandler getFrameHandler(WebSocketCoreClient coreClient)
    {
        UpgradeRequest upgradeRequest = new DelegatedJavaxClientUpgradeRequest(this);
        JavaxWebSocketFrameHandler frameHandler = containerContext.newFrameHandler(websocketPojo, upgradeRequest, futureSession);
        return frameHandler;
    }

    public CompletableFuture<Session> getFutureSession()
    {
        return futureSession;
    }
}
