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

package org.eclipse.jetty.websocket.common.message;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.common.AbstractMessageSink;
import org.eclipse.jetty.websocket.core.Frame;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.Executor;

public class PartialBinaryMessageSink extends AbstractMessageSink
{
    public PartialBinaryMessageSink(Executor executor, MethodHandle methodHandle)
    {
        super(executor, methodHandle);
    }

    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            methodHandle.invoke(frame.getPayload(), frame.isFin());
            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
    }
}
