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

package org.eclipse.jetty.util.thread;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * ShutdownThread is a shutdown hook thread implemented as
 * singleton that maintains a list of lifecycle instances
 * that are registered with it and provides ability to stop
 * these lifecycles upon shutdown of the Java Virtual Machine
 */
public class ShutdownThread extends Thread
{
    private static final Logger LOG = Log.getLogger(ShutdownThread.class);
    private static final ShutdownThread _thread = new ShutdownThread();

    private boolean _hooked;
    private final List<LifeCycle> _lifeCycles = new CopyOnWriteArrayList<LifeCycle>();

    /**
     * Default constructor for the singleton
     *
     * Registers the instance as shutdown hook with the Java Runtime
     */
    private ShutdownThread()
    {
    }

    private synchronized void hook()
    {
        try
        {
            if (!_hooked)
                Runtime.getRuntime().addShutdownHook(this);
            _hooked = true;
        }
        catch (Exception e)
        {
            LOG.ignore(e);
            LOG.info("shutdown already commenced");
        }
    }

    private synchronized void unhook()
    {
        try
        {
            _hooked = false;
            Runtime.getRuntime().removeShutdownHook(this);
        }
        catch (Exception e)
        {
            LOG.ignore(e);
            LOG.debug("shutdown already commenced");
        }
    }

    /**
     * Returns the instance of the singleton
     *
     * @return the singleton instance of the {@link ShutdownThread}
     */
    public static ShutdownThread getInstance()
    {
        return _thread;
    }

    public static synchronized void register(LifeCycle... lifeCycles)
    {
        _thread._lifeCycles.addAll(Arrays.asList(lifeCycles));
        if (_thread._lifeCycles.size() > 0)
            _thread.hook();
    }

    public static synchronized void register(int index, LifeCycle... lifeCycles)
    {
        _thread._lifeCycles.addAll(index, Arrays.asList(lifeCycles));
        if (_thread._lifeCycles.size() > 0)
            _thread.hook();
    }

    public static synchronized void deregister(LifeCycle lifeCycle)
    {
        _thread._lifeCycles.remove(lifeCycle);
        if (_thread._lifeCycles.size() == 0)
            _thread.unhook();
    }

    public static synchronized boolean isRegistered(LifeCycle lifeCycle)
    {
        return _thread._lifeCycles.contains(lifeCycle);
    }

    @Override
    public void run()
    {
        for (LifeCycle lifeCycle : _thread._lifeCycles)
        {
            try
            {
                if (lifeCycle.isStarted())
                {
                    lifeCycle.stop();
                    LOG.debug("Stopped {}", lifeCycle);
                }

                if (lifeCycle instanceof Destroyable)
                {
                    ((Destroyable)lifeCycle).destroy();
                    LOG.debug("Destroyed {}", lifeCycle);
                }
            }
            catch (Exception ex)
            {
                LOG.debug(ex);
            }
        }
    }
}
