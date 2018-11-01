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

package org.eclipse.jetty.websocket.jsr356.tests.server.sockets.pong;

import org.eclipse.jetty.util.BufferUtil;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import java.nio.charset.StandardCharsets;

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
        byte buf[] = BufferUtil.toArray(pong.getApplicationData());
        String message = new String(buf, StandardCharsets.UTF_8);
        this.session.getAsyncRemote().sendText("PongMessageEndpoint.onMessage(PongMessage):[" + path + "]:" + message);
    }
}
