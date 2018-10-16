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


package org.eclipse.jetty.maven.plugin;

import java.io.File;
import java.util.Iterator;

import org.eclipse.jetty.quickstart.QuickStartConfiguration;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * MavenQuickStartConfiguration
 *
 *
 */
public class MavenQuickStartConfiguration extends QuickStartConfiguration
{
    private static final Logger LOG = Log.getLogger(QuickStartConfiguration.class);
    
    private Resource _quickStartWebXml; //the descriptor to use for starting/generating quickstart

    public void setQuickStartWebXml(Resource quickStartWebXml)
    {
        _quickStartWebXml = quickStartWebXml;
    }
    
    
    @Override
    public Resource getQuickStartWebXml(WebAppContext context) throws Exception
    {
        if (_quickStartWebXml == null)
            return super.getQuickStartWebXml(context);
        
        return _quickStartWebXml;
    }

    @Override
    public void preConfigure(WebAppContext context) throws Exception
    {        
        //check that webapp is suitable for quick start 
        if (context.getBaseResource() == null)
            throw new IllegalStateException ("No location for webapp");  

        
        //look for quickstart-web.xml in WEB-INF of webapp
        Resource quickStartWebXml = getQuickStartWebXml(context);
        if (LOG.isDebugEnabled()) LOG.debug("quickStartWebXml={}",quickStartWebXml);
        super.preConfigure(context);
    }

    @Override
    public void configure(WebAppContext context) throws Exception
    {
       JettyWebAppContext jwac = (JettyWebAppContext)context;
        
        //put the classes dir and all dependencies into the classpath
        if (jwac.getClassPathFiles() != null)
        {
            if (LOG.isDebugEnabled()) LOG.debug("Setting up classpath ...");
            for(File classPathFile:jwac.getClassPathFiles())
                ((WebAppClassLoader)context.getClassLoader()).addClassPath(classPathFile.getCanonicalPath());
        }
        
        //Set up the quickstart environment for the context
        super.configure(context);       
    }
    
    @Override
    public void deconfigure(WebAppContext context) throws Exception
    {
        //if we're not persisting the temp dir, get rid of any overlays
        if (!context.isPersistTempDirectory())
        {
            Resource originalBases = (Resource)context.getAttribute("org.eclipse.jetty.resources.originalBases");
            String originalBaseStr = originalBases.toString();

            //Iterate over all of the resource bases and ignore any that were original bases, just
            //deleting the overlays
            Resource res = context.getBaseResource();
            if (res instanceof ResourceCollection)
            {
                for (Resource r:((ResourceCollection)res).getResources())
                {
                    if (originalBaseStr.contains(r.toString()))
                        continue;
                    IO.delete(r.getFile());
                }
            }
        }
        super.deconfigure(context);
    }
    
}
