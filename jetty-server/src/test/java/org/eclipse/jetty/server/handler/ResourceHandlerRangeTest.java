//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.server.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Unfixed range bug - Issue #107")
public class ResourceHandlerRangeTest
{
    private static Server server;
    private static URI serverUri;

    @BeforeAll
    public static void startServer() throws Exception
    {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        ContextHandlerCollection contexts = new ContextHandlerCollection();

        File dir = MavenTestingUtils.getTargetTestingDir(ResourceHandlerRangeTest.class.getSimpleName());
        FS.ensureEmpty(dir);
        File rangeFile = new File(dir,"range.txt");
        try (FileWriter writer = new FileWriter(rangeFile))
        {
            writer.append("0123456789");
            writer.flush();
        }

        ContextHandler contextHandler = new ContextHandler();
        ResourceHandler contentResourceHandler = new ResourceHandler();
        contextHandler.setBaseResource(Resource.newResource(dir.getAbsolutePath()));
        contextHandler.setHandler(contentResourceHandler);
        contextHandler.setContextPath("/");

        contexts.addHandler(contextHandler);

        server.setHandler(contexts);
        server.start();

        String host = connector.getHost();
        if (host == null)
        {
            host = "localhost";
        }
        int port = connector.getLocalPort();
        serverUri = new URI(String.format("http://%s:%d/",host,port));
    }

    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    @Test
    public void testGetRange() throws Exception
    {
        URI uri = serverUri.resolve("range.txt");

        HttpURLConnection uconn = (HttpURLConnection)uri.toURL().openConnection();
        uconn.setRequestMethod("GET");
        uconn.addRequestProperty("Range","bytes=" + 5 + "-");

        int contentLength = Integer.parseInt(uconn.getHeaderField("Content-Length"));

        String response;
        try (InputStream is = uconn.getInputStream())
        {
            response = IO.toString(is);
        }

        assertThat("Content Length",contentLength,is(5));
        assertThat("Response Content",response,is("56789"));
    }
}
