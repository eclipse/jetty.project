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

package org.eclipse.jetty.websocket.tests.jsr356;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.FrameCallback;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.function.EndpointFunctions;
import org.eclipse.jetty.websocket.jsr356.JsrSession;

public class DummyJsrEndpointFunctions extends AbstractLifeCycle implements EndpointFunctions<JsrSession>
{
    private static final Logger LOG = Log.getLogger(DummyJsrEndpointFunctions.class);
    
    @Override
    public Logger getLog()
    {
        return LOG;
    }
    
    @Override
    public JsrSession getSession()
    {
        return null;
    }
    
    @Override
    public void onOpen(JsrSession session)
    {
    
    }
    
    @Override
    public void onClose(CloseInfo close)
    {
    
    }
    
    @Override
    public void onFrame(Frame frame)
    {
    
    }
    
    @Override
    public void onError(Throwable cause)
    {
    
    }
    
    @Override
    public void onText(Frame frame, FrameCallback callback)
    {
    
    }
    
    @Override
    public void onBinary(Frame frame, FrameCallback callback)
    {
    
    }
    
    @Override
    public void onContinuation(Frame frame, FrameCallback callback)
    {
    
    }
    
    @Override
    public void onPing(ByteBuffer payload)
    {
    
    }
    
    @Override
    public void onPong(ByteBuffer payload)
    {
    
    }
}
