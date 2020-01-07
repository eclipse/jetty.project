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

import org.eclipse.jetty.webapp.Configuration;

module org.eclipse.jetty.webapp
{
    exports org.eclipse.jetty.webapp;

    requires transitive java.instrument;
    requires transitive org.eclipse.jetty.servlet;
    requires transitive org.eclipse.jetty.xml;

    uses Configuration;

    provides Configuration with
        org.eclipse.jetty.webapp.FragmentConfiguration,
        org.eclipse.jetty.webapp.JaasConfiguration,
        org.eclipse.jetty.webapp.JettyWebXmlConfiguration,
        org.eclipse.jetty.webapp.JmxConfiguration,
        org.eclipse.jetty.webapp.JndiConfiguration,
        org.eclipse.jetty.webapp.JspConfiguration,
        org.eclipse.jetty.webapp.MetaInfConfiguration,
        org.eclipse.jetty.webapp.ServletsConfiguration,
        org.eclipse.jetty.webapp.WebAppConfiguration,
        org.eclipse.jetty.webapp.WebInfConfiguration,
        org.eclipse.jetty.webapp.WebXmlConfiguration;
}
