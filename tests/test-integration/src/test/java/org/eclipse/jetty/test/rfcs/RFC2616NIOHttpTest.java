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

package org.eclipse.jetty.test.rfcs;

import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.test.support.XmlBasedJettyServer;
import org.eclipse.jetty.test.support.rawhttp.HttpSocket;
import org.eclipse.jetty.test.support.rawhttp.HttpSocketImpl;
import org.junit.jupiter.api.BeforeAll;

/**
 * Perform the RFC2616 tests against a server running with the Jetty NIO Connector and listening on standard HTTP.
 */
public class RFC2616NIOHttpTest extends RFC2616BaseTest
{
    @BeforeAll
    public static void setupServer() throws Exception
    {
        XmlBasedJettyServer server = new XmlBasedJettyServer();
        server.setScheme(HttpScheme.HTTP.asString());
        server.addXmlConfiguration("RFC2616Base.xml");
        server.addXmlConfiguration("RFC2616_Redirects.xml");
        server.addXmlConfiguration("RFC2616_Filters.xml");
        server.addXmlConfiguration("NIOHttp.xml");
        setUpServer(server, RFC2616NIOHttpTest.class);
    }

    @Override
    public HttpSocket getHttpClientSocket()
    {
        return new HttpSocketImpl();
    }
}
