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

package org.eclipse.jetty.server.ssl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SocketCustomizationListener;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SslConnectionFactoryTest
{
    private Server _server;
    private ServerConnector _connector;
    private int _port;

    @BeforeEach
    public void before() throws Exception
    {
        String keystorePath = "src/test/resources/keystore";
        File keystoreFile = new File(keystorePath);
        if (!keystoreFile.exists())
            throw new FileNotFoundException(keystoreFile.getAbsolutePath());

        _server = new Server();

        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(8443);
        http_config.setOutputBufferSize(32768);
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());


        SslContextFactory sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(keystoreFile.getAbsolutePath());
        sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");

        ServerConnector https = _connector = new ServerConnector(_server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        https.setPort(0);
        https.setIdleTimeout(30000);

        _server.addConnector(https);

        _server.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                response.setStatus(200);
                response.getWriter().write("url=" + request.getRequestURI() + "\nhost=" + request.getServerName());
                response.flushBuffer();
            }
        });

        _server.start();
        _port = https.getLocalPort();
    }

    @AfterEach
    public void after() throws Exception
    {
        _server.stop();
        _server = null;
    }

    @Test
    public void testConnect() throws Exception
    {
        String response = getResponse("127.0.0.1", null);
        assertThat(response, Matchers.containsString("host=127.0.0.1"));
    }

    @Test
    public void testSNIConnect() throws Exception
    {
        String response = getResponse("localhost", "localhost", "jetty.eclipse.org");
        assertThat(response, Matchers.containsString("host=localhost"));
    }

    @Test
    public void testBadHandshake() throws Exception
    {
        try (Socket socket = new Socket("127.0.0.1", _port); OutputStream out = socket.getOutputStream())
        {
            out.write("Rubbish".getBytes());
            out.flush();

            socket.setSoTimeout(1000);
            // Expect TLS message type == 21: Alert
            assertThat(socket.getInputStream().read(), Matchers.equalTo(21));
        }
    }

    @Test
    public void testSocketCustomization() throws Exception
    {
        final Queue<String> history = new LinkedBlockingQueue<>();

        _connector.addBean(new SocketCustomizationListener()
        {
            @Override
            protected void customize(Socket socket, Class<? extends Connection> connection, boolean ssl)
            {
                history.add("customize connector " + connection + "," + ssl);
            }
        });

        _connector.getBean(SslConnectionFactory.class).addBean(new SocketCustomizationListener()
        {
            @Override
            protected void customize(Socket socket, Class<? extends Connection> connection, boolean ssl)
            {
                history.add("customize ssl " + connection + "," + ssl);
            }
        });

        _connector.getBean(HttpConnectionFactory.class).addBean(new SocketCustomizationListener()
        {
            @Override
            protected void customize(Socket socket, Class<? extends Connection> connection, boolean ssl)
            {
                history.add("customize http " + connection + "," + ssl);
            }
        });

        String response = getResponse("127.0.0.1", null);
        assertThat(response, Matchers.containsString("host=127.0.0.1"));

        assertEquals("customize connector class org.eclipse.jetty.io.ssl.SslConnection,false", history.poll());
        assertEquals("customize ssl class org.eclipse.jetty.io.ssl.SslConnection,false", history.poll());
        assertEquals("customize connector class org.eclipse.jetty.server.HttpConnection,true", history.poll());
        assertEquals("customize http class org.eclipse.jetty.server.HttpConnection,true", history.poll());
        assertEquals(0, history.size());
    }

    @Test
    public void testServerWithoutHttpConnectionFactory() throws Exception
    {
        _server.stop();
        assertNotNull(_connector.removeConnectionFactory(HttpVersion.HTTP_1_1.asString()));
        assertThrows(IllegalStateException.class, ()-> _server.start());
    }

    private String getResponse(String host, String cn) throws Exception
    {
        String response = getResponse(host, host, cn);
        assertThat(response, Matchers.startsWith("HTTP/1.1 200 OK"));
        assertThat(response, Matchers.containsString("url=/ctx/path"));
        return response;
    }

    private String getResponse(String sniHost, String reqHost, String cn) throws Exception
    {
        SslContextFactory clientContextFactory = new SslContextFactory.Client(true);
        clientContextFactory.start();
        SSLSocketFactory factory = clientContextFactory.getSslContext().getSocketFactory();

        SSLSocket sslSocket = (SSLSocket)factory.createSocket("127.0.0.1", _port);

        if (cn != null)
        {
            SNIHostName serverName = new SNIHostName(sniHost);
            List<SNIServerName> serverNames = new ArrayList<>();
            serverNames.add(serverName);

            SSLParameters params = sslSocket.getSSLParameters();
            params.setServerNames(serverNames);
            sslSocket.setSSLParameters(params);
        }
        sslSocket.startHandshake();

        if (cn != null)
        {
            X509Certificate cert = ((X509Certificate)sslSocket.getSession().getPeerCertificates()[0]);
            assertThat(cert.getSubjectX500Principal().getName("CANONICAL"), Matchers.startsWith("cn=" + cn));
        }

        sslSocket.getOutputStream().write(("GET /ctx/path HTTP/1.0\r\nHost: " + reqHost + ":" + _port + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        String response = IO.toString(sslSocket.getInputStream());

        sslSocket.close();
        clientContextFactory.stop();
        return response;
    }
}
