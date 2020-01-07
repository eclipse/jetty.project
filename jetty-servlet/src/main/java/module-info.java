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

module org.eclipse.jetty.servlet
{
    exports org.eclipse.jetty.servlet;
    exports org.eclipse.jetty.servlet.jmx to org.eclipse.jetty.jmx;
    exports org.eclipse.jetty.servlet.listener;

    requires transitive org.eclipse.jetty.security;

    // Only required if using StatisticsServlet.
    requires static java.management;
    // Only required if using IntrospectorCleaner.
    requires static java.desktop;
    // Only required if using JMX.
    requires static org.eclipse.jetty.jmx;
}
