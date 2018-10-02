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

package org.eclipse.jetty.websocket.tests.client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;

public class ClientWriteThread extends Thread
{
    private static final Logger LOG = Log.getLogger(ClientWriteThread.class);
    private final Session session;
    private int slowness = -1;
    private int messageCount = 100;
    private String message = "Hello";

    public ClientWriteThread(Session session)
    {
        this.session = session;
    }

    public String getMessage()
    {
        return message;
    }

    public int getMessageCount()
    {
        return messageCount;
    }

    public int getSlowness()
    {
        return slowness;
    }

    @Override
    public void run()
    {
        final AtomicInteger m = new AtomicInteger();

        try
        {
            LOG.debug("Writing {} messages to connection {}",messageCount);
            LOG.debug("Artificial Slowness {} ms",slowness);
            RemoteEndpoint remote = session.getRemote();
            while (m.get() < messageCount)
            {
                remote.sendString(message + "/" + m.get() + "/");

                m.incrementAndGet();

                if (slowness > 0)
                {
                    TimeUnit.MILLISECONDS.sleep(slowness);
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn(e);
        }
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public void setMessageCount(int messageCount)
    {
        this.messageCount = messageCount;
    }

    public void setSlowness(int slowness)
    {
        this.slowness = slowness;
    }
}
