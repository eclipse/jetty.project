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

package org.eclipse.jetty.maven.plugin;

import java.io.File;
import java.io.IOException;

import org.eclipse.jetty.util.resource.Resource;

/**
 * Overlay
 * 
 * An Overlay represents overlay information derived from the
 * maven-war-plugin.
 */
public class Overlay
{
    private OverlayConfig _config;
    private Resource _resource;

    public Overlay(OverlayConfig config, Resource resource)
    {
        _config = config;
        _resource = resource;
    }

    public Overlay(OverlayConfig config)
    {
        _config = config;
    }

    public void setResource(Resource r)
    {
        _resource = r;
    }

    public Resource getResource()
    {
        return _resource;
    }

    public OverlayConfig getConfig()
    {
        return _config;
    }

    @Override
    public String toString()
    {
        StringBuilder strbuff = new StringBuilder();
        if (_resource != null)
            strbuff.append(_resource);
        if (_config != null)
        {
            strbuff.append(" [");
            strbuff.append(_config);
            strbuff.append("]");
        }
        return strbuff.toString();
    }
    
    /**
     * Unpack the overlay into the given directory. Only
     * unpack if the directory does not exist, or the overlay
     * has been modified since the dir was created.
     * 
     * @param dir the directory into which to unpack the overlay
     * @throws IOException 
     */
    public void unpackTo(File dir) throws IOException
    {
        if (dir == null)
            throw new IllegalStateException("No overly unpack directory");
        //only unpack if the overlay is newer
        if (!dir.exists() || (getResource().lastModified() > dir.lastModified()))
            getResource().copyTo(dir);
    }
}
