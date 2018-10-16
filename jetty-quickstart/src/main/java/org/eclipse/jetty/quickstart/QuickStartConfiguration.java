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

package org.eclipse.jetty.quickstart;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.annotations.AnnotationDecorator;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.StandardDescriptorProcessor;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * QuickStartConfiguration
 * <p> 
 * Re-inflate a deployable webapp from a saved effective-web.xml
 * which combines all pre-parsed web xml descriptors and annotations.
 */
public class QuickStartConfiguration extends AbstractConfiguration
{
    private static final Logger LOG = Log.getLogger(QuickStartConfiguration.class);

    public static final Set<Class<? extends Configuration>> __replacedConfigurations = new HashSet<>();
    public static final String ORIGIN_ATTRIBUTE = "org.eclipse.jetty.quickstart.ORIGIN_ATTRIBUTE";
    public static final String GENERATE_ORIGIN = "org.eclipse.jetty.quickstart.GENERATE_ORIGIN";
    public static final String QUICKSTART_WEB_XML = "org.eclipse.jetty.quickstart.QUICKSTART_WEB_XML";

    static
    { 
        __replacedConfigurations.add(org.eclipse.jetty.webapp.WebXmlConfiguration.class);
        __replacedConfigurations.add(org.eclipse.jetty.webapp.MetaInfConfiguration.class); 
        __replacedConfigurations.add(org.eclipse.jetty.webapp.FragmentConfiguration.class);
        __replacedConfigurations.add(org.eclipse.jetty.annotations.AnnotationConfiguration.class);
    };

    public enum Mode
    {
        DISABLED,  // No Quick start
        GENERATE,  // Generate quickstart-web.xml and then stop
        AUTO,      // use or generate depending on the existance of quickstart-web.xml
        QUICKSTART // Use quickstart-web.xml
    };

    private Mode _mode=Mode.AUTO;
    private boolean _quickStart;

    public QuickStartConfiguration()
    {
        super(true);
        addDependencies(WebInfConfiguration.class);
        addDependents(WebXmlConfiguration.class);
    }

    public void setMode(Mode mode)
    {
        _mode=mode;
    }
    
    public Mode getMode()
    {
        return _mode;
    }

    /**
     * @see org.eclipse.jetty.webapp.AbstractConfiguration#preConfigure(org.eclipse.jetty.webapp.WebAppContext)
     */
    @Override
    public void preConfigure(WebAppContext context) throws Exception
    {
        //check that webapp is suitable for quick start - it is not a packed war
        String war = context.getWar();
        if (war == null || war.length()<=0 || !context.getBaseResource().isDirectory())
            throw new IllegalStateException ("Bad Quickstart location");  
       
        //look for quickstart-web.xml in WEB-INF of webapp
        Resource quickStartWebXml = getQuickStartWebXml(context);
        LOG.debug("quickStartWebXml={} exists={}",quickStartWebXml,quickStartWebXml.exists());
 
        _quickStart=false;
        switch(_mode)
        {
            case DISABLED:
                super.preConfigure(context);
                break;
                
            case GENERATE:
            {
                super.preConfigure(context);
                QuickStartGeneratorConfiguration generator = new QuickStartGeneratorConfiguration(true);
                configure(generator, context);
                context.addConfiguration(generator);
                break;
            }
                
            case AUTO:
            {
                if (quickStartWebXml.exists())
                    quickStart(context, quickStartWebXml);
                else
                {
                    super.preConfigure(context);
                    QuickStartGeneratorConfiguration generator = new QuickStartGeneratorConfiguration(false);
                    configure(generator, context);
                    context.addConfiguration(generator);
                }
                break;
            }
                
            case QUICKSTART:
                if (quickStartWebXml.exists())
                    quickStart(context,quickStartWebXml);
                else
                    throw new IllegalStateException ("No "+quickStartWebXml);
                break;
        }
    }

    protected void configure(QuickStartGeneratorConfiguration generator, WebAppContext context) throws IOException
    {
        Object attr;
        attr = context.getAttribute(GENERATE_ORIGIN);
        if (attr!=null)
            generator.setGenerateOrigin(Boolean.valueOf(attr.toString()));
        attr = context.getAttribute(ORIGIN_ATTRIBUTE);
        if (attr!=null)
            generator.setOriginAttribute(attr.toString());
        attr = context.getAttribute(QUICKSTART_WEB_XML);
        if (attr instanceof Resource)
            generator.setQuickStartWebXml((Resource)attr);
        else if (attr!=null)
            generator.setQuickStartWebXml(Resource.newResource(attr.toString()));
    }

    protected void quickStart(WebAppContext context, Resource quickStartWebXml)
            throws Exception
    {
        _quickStart=true;
        context.setConfigurations(context.getWebAppConfigurations().stream()
                .filter(c->!__replacedConfigurations.contains(c.replaces())&&!__replacedConfigurations.contains(c.getClass()))
                .collect(Collectors.toList()).toArray(new Configuration[]{}));
        context.getMetaData().setWebXml(quickStartWebXml);
        context.getServletContext().setEffectiveMajorVersion(context.getMetaData().getWebXml().getMajorVersion());
        context.getServletContext().setEffectiveMinorVersion(context.getMetaData().getWebXml().getMinorVersion());
    }
    
    
    /**
     * @see org.eclipse.jetty.webapp.AbstractConfiguration#configure(org.eclipse.jetty.webapp.WebAppContext)
     */
    @Override
    public void configure(WebAppContext context) throws Exception
    {
        if (!_quickStart)
            super.configure(context);
        else
        {
            //add the processor to handle normal web.xml content
            context.getMetaData().addDescriptorProcessor(new StandardDescriptorProcessor());

            //add a processor to handle extended web.xml format
            context.getMetaData().addDescriptorProcessor(new QuickStartDescriptorProcessor());

            //add a decorator that will find introspectable annotations
            context.getObjectFactory().addDecorator(new AnnotationDecorator(context)); //this must be the last Decorator because they are run in reverse order!

            //add a context bean that will run ServletContainerInitializers as the context starts
            ServletContainerInitializersStarter starter = (ServletContainerInitializersStarter)context.getAttribute(AnnotationConfiguration.CONTAINER_INITIALIZER_STARTER);
            if (starter != null)
                throw new IllegalStateException("ServletContainerInitializersStarter already exists");
            starter = new ServletContainerInitializersStarter(context);
            context.setAttribute(AnnotationConfiguration.CONTAINER_INITIALIZER_STARTER, starter);
            context.addBean(starter, true);       

            LOG.debug("configured {}",this);
        }
    }

    /**
     * Get the quickstart-web.xml file as a Resource.
     * 
     * @param context the web app context
     * @return the Resource for the quickstart-web.xml
     * @throws Exception if unable to find the quickstart xml
     */
    public Resource getQuickStartWebXml (WebAppContext context) throws Exception
    {
        Resource webInf = context.getWebInf();
        if (webInf == null || !webInf.exists())
            throw new IllegalStateException("No WEB-INF");
        LOG.debug("webinf={}",webInf);
  
        Resource quickStartWebXml = webInf.addPath("quickstart-web.xml");
        return quickStartWebXml;
    }
    
}
