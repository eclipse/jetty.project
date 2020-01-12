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

package org.eclipse.jetty.util.component;

import java.io.FileWriter;
import java.io.Writer;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * A LifeCycle Listener that writes state changes to a file.
 * <p>This can be used with the jetty.sh script to wait for successful startup.
 */
public class FileNoticeLifeCycleListener implements LifeCycle.Listener
{
    private static final Logger LOG = Log.getLogger(FileNoticeLifeCycleListener.class);

    private final String _filename;

    public FileNoticeLifeCycleListener(String filename)
    {
        _filename = filename;
    }

    private void writeState(String action, LifeCycle lifecycle)
    {
        try (Writer out = new FileWriter(_filename, true))
        {
            out.append(action).append(" ").append(lifecycle.toString()).append("\n");
        }
        catch (Exception e)
        {
            LOG.warn(e);
        }
    }

    @Override
    public void lifeCycleStarting(LifeCycle event)
    {
        writeState("STARTING", event);
    }

    @Override
    public void lifeCycleStarted(LifeCycle event)
    {
        writeState("STARTED", event);
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause)
    {
        writeState("FAILED", event);
    }

    @Override
    public void lifeCycleStopping(LifeCycle event)
    {
        writeState("STOPPING", event);
    }

    @Override
    public void lifeCycleStopped(LifeCycle event)
    {
        writeState("STOPPED", event);
    }
}
