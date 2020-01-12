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

package org.eclipse.jetty.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Display an optional Warning Message if the {jetty.home} and {jetty.base} are the same directory.
 * <p>
 * This is to warn about not recommended approach to setting up the Jetty Distribution.
 */
public class HomeBaseWarning
{
    private static final Logger LOG = Log.getLogger(HomeBaseWarning.class);

    public HomeBaseWarning()
    {
        boolean showWarn = false;

        String home = System.getProperty("jetty.home");
        String base = System.getProperty("jetty.base");

        if (StringUtil.isBlank(base))
        {
            // no base defined? then we are likely running
            // via direct command line.
            return;
        }

        Path homePath = new File(home).toPath();
        Path basePath = new File(base).toPath();

        try
        {
            showWarn = Files.isSameFile(homePath, basePath);
        }
        catch (IOException e)
        {
            LOG.ignore(e);
            // Can't definitively determine this state
            return;
        }

        if (showWarn)
        {
            StringBuilder warn = new StringBuilder();
            warn.append("This instance of Jetty is not running from a separate {jetty.base} directory");
            warn.append(", this is not recommended.  See documentation at http://www.eclipse.org/jetty/documentation/current/startup.html");
            LOG.warn("{}", warn.toString());
        }
    }
}
