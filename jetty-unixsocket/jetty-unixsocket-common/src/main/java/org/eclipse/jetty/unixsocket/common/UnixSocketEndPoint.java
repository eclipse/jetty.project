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

package org.eclipse.jetty.unixsocket.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

import jnr.unixsocket.UnixSocketChannel;
import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Scheduler;

public class UnixSocketEndPoint extends ChannelEndPoint
{
    private static final Logger LOG = Log.getLogger(UnixSocketEndPoint.class);

    private final UnixSocketChannel _channel;

    public UnixSocketEndPoint(UnixSocketChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler)
    {
        super(channel, selector, key, scheduler);
        _channel = channel;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return null;
    }

    @Override
    protected void doShutdownOutput()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("oshut {}", this);
        try
        {
            _channel.shutdownOutput();
            super.doShutdownOutput();
        }
        catch (IOException e)
        {
            LOG.debug(e);
        }
    }
}
