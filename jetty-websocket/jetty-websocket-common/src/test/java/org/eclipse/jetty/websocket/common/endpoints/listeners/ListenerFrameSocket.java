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

package org.eclipse.jetty.websocket.common.endpoints.listeners;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.listeners.WebSocketFrameListener;
import org.eclipse.jetty.websocket.common.test.EventQueue;
import org.eclipse.jetty.websocket.common.util.TextUtil;
import org.eclipse.jetty.websocket.core.frames.Frame;

public class ListenerFrameSocket implements WebSocketFrameListener
{
    public EventQueue events = new EventQueue();
    
    @Override
    public void onWebSocketClose(int statusCode, String reason)
    {
        events.add("onWebSocketClose(%s, %s)", StatusCode.asName(statusCode), TextUtil.quote(reason));
    }
    
    @Override
    public void onWebSocketConnect(Session session)
    {
        events.add("onWebSocketConnect(%s)", session);
    }
    
    @Override
    public void onWebSocketError(Throwable cause)
    {
        events.add("onWebSocketError((%s) %s)", cause.getClass().getSimpleName(), TextUtil.quote(cause.getMessage()));
    }
    
    @Override
    public void onWebSocketFrame(Frame frame)
    {
        events.add("onWebSocketFrame(%s)", frame.toString());
    }
}
