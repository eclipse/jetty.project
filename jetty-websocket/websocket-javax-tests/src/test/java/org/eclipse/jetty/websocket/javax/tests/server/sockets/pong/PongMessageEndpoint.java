//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests.server.sockets.pong;

import java.nio.charset.StandardCharsets;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.eclipse.jetty.util.BufferUtil;

public class PongMessageEndpoint extends Endpoint implements MessageHandler.Whole<PongMessage>
{
    private String path = "?";
    private Session session;

    @Override
    public void onOpen(Session session, EndpointConfig config)
    {
        this.session = session;
        this.session.addMessageHandler(this);
        this.path = (String)config.getUserProperties().get("path");
    }

    @Override
    public void onMessage(PongMessage pong)
    {
        byte[] buf = BufferUtil.toArray(pong.getApplicationData());
        String message = new String(buf, StandardCharsets.UTF_8);
        this.session.getAsyncRemote().sendText("PongMessageEndpoint.onMessage(PongMessage):[" + path + "]:" + message);
    }
}
