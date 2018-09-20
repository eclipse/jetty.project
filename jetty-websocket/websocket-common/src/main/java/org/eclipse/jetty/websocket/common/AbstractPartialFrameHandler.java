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

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.ProtocolException;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;

public abstract class AbstractPartialFrameHandler extends AbstractFrameTypeHandler
{
    protected byte dataType = -1;

    @Override
    public final /* prevent override */ void onReceiveFrame(Frame frame, Callback callback)
    {
        super.onReceiveFrame(frame, callback);
        if (frame.isFin() && !frame.isControlFrame())
            dataType = -1;
    }

    public void onText(Frame frame, Callback callback)
    {
        dataType = OpCode.TEXT;
    }

    @Override
    public void onBinary(Frame frame, Callback callback)
    {
        dataType = OpCode.BINARY;
    }

    @Override
    public final /* prevent override */ void onContinuation(Frame frame, Callback callback)
    {
        switch (dataType)
        {
            case OpCode.TEXT:
                onText(frame, callback);
                break;
            case OpCode.BINARY:
                onBinary(frame, callback);
                break;
            default:
                throw new ProtocolException("Unable to process continuation during dataType " + dataType);
        }
    }
}
