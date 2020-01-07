//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
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

import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.Executor;

import org.eclipse.jetty.websocket.core.Frame;

public class ReaderMessageSink extends DispatchedMessageSink<Reader, Void>
{
    public ReaderMessageSink(Executor executor, MethodHandle methodHandle)
    {
        super(executor, methodHandle);
    }

    @Override
    public MessageReader newSink(Frame frame)
    {
        return new MessageReader(new MessageInputStream());
    }
}
