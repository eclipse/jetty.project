//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.unixdomain.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V1;
import static org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V2;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class UnixDomainTest
{
    private ConnectionFactory[] factories = new ConnectionFactory[]{new HttpConnectionFactory()};
    private Server server;
    private Path unixDomainPath;

    private void start(Handler handler) throws Exception
    {
        server = new Server();
        UnixDomainServerConnector connector = new UnixDomainServerConnector(server, factories);
        unixDomainPath = Files.createTempFile(Path.of("/tmp"), "unixdomain_", ".sock");
        Files.delete(unixDomainPath);
        connector.setUnixDomainPath(unixDomainPath);
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();
    }

    @AfterEach
    public void dispose()
    {
        LifeCycle.stop(server);
    }

    @Test
    public void testHTTPOverUnixDomain() throws Exception
    {
        String uri = "http://localhost:1234/path";
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request jettyRequest, HttpServletRequest request, HttpServletResponse response)
            {
                jettyRequest.setHandled(true);

                // Verify the URI is preserved.
                assertEquals(uri, request.getRequestURL().toString());

                EndPoint endPoint = jettyRequest.getHttpChannel().getEndPoint();

                // Verify the SocketAddresses.
                SocketAddress local = endPoint.getLocalSocketAddress();
                assertThat(local, Matchers.instanceOf(UnixDomainSocketAddress.class));
                SocketAddress remote = endPoint.getRemoteSocketAddress();
                assertThat(remote, Matchers.instanceOf(UnixDomainSocketAddress.class));

                // Verify that other address methods don't throw.
                local = assertDoesNotThrow(endPoint::getLocalAddress);
                assertNull(local);
                remote = assertDoesNotThrow(endPoint::getRemoteAddress);
                assertNull(remote);

                assertDoesNotThrow(endPoint::toString);
            }
        });

        // Java 16 way of implementing the SocketChannelWithAddress.Factory.
        // See tests below for a Java 11+ version.
        ClientConnector clientConnector = new ClientConnector((address, context) ->
        {
            SocketChannel socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
            UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(unixDomainPath);
            return new ClientConnector.SocketChannelWithAddress(socketChannel, socketAddress);
        });

        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        httpClient.start();
        try
        {
            ContentResponse response = httpClient.newRequest(uri)
                .timeout(5, TimeUnit.SECONDS)
                .send();

            assertEquals(HttpStatus.OK_200, response.getStatus());
        }
        finally
        {
            httpClient.stop();
        }
    }

    @Test
    public void testHTTPOverUnixDomainWithHTTPProxy() throws Exception
    {
        int fakeProxyPort = 4567;
        int fakeServerPort = 5678;
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request jettyRequest, HttpServletRequest request, HttpServletResponse response)
            {
                jettyRequest.setHandled(true);
                // Proxied requests must have an absolute URI.
                HttpURI uri = jettyRequest.getMetaData().getURI();
                assertNotNull(uri.getScheme());
                assertEquals(fakeServerPort, uri.getPort());
            }
        });

        ClientConnector clientConnector = new ClientConnector((address, context) ->
        {
            if (address instanceof InetSocketAddress inet && inet.getPort() == fakeProxyPort)
            {
                SocketChannel socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
                UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(unixDomainPath);
                return new ClientConnector.SocketChannelWithAddress(socketChannel, socketAddress);
            }
            throw new IOException("request was not proxied");
        });

        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", fakeProxyPort));
        httpClient.start();
        try
        {
            ContentResponse response = httpClient.newRequest("localhost", fakeServerPort)
                .timeout(5, TimeUnit.SECONDS)
                .send();

            assertEquals(HttpStatus.OK_200, response.getStatus());
        }
        finally
        {
            httpClient.stop();
        }
    }

    @Test
    public void testHTTPOverUnixDomainWithProxyProtocol() throws Exception
    {
        String srcAddr = "/proxySrcAddr";
        String dstAddr = "/proxyDstAddr";
        factories = new ConnectionFactory[]{new ProxyConnectionFactory(), new HttpConnectionFactory()};
        start(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request jettyRequest, HttpServletRequest request, HttpServletResponse response)
            {
                jettyRequest.setHandled(true);
                EndPoint endPoint = jettyRequest.getHttpChannel().getEndPoint();
                assertThat(endPoint, Matchers.instanceOf(ProxyConnectionFactory.ProxyEndPoint.class));
                assertThat(endPoint.getLocalSocketAddress(), Matchers.instanceOf(UnixDomainSocketAddress.class));
                assertThat(endPoint.getRemoteSocketAddress(), Matchers.instanceOf(UnixDomainSocketAddress.class));
                if ("/v1".equals(target))
                {
                    // As PROXYv1 does not support UNIX, the wrapped EndPoint data is used.
                    assertThat(((UnixDomainSocketAddress)endPoint.getLocalSocketAddress()).getPath(), Matchers.equalTo(unixDomainPath));
                }
                else if ("/v2".equals(target))
                {
                    assertThat(((UnixDomainSocketAddress)endPoint.getLocalSocketAddress()).getPath().toString(), Matchers.equalTo(dstAddr));
                    assertThat(((UnixDomainSocketAddress)endPoint.getRemoteSocketAddress()).getPath().toString(), Matchers.equalTo(srcAddr));
                }
                else
                {
                    fail("Invalid PROXY protocol version " + target);
                }
            }
        });

        // Java 11+ portable way to implement SocketChannelWithAddress.Factory.
        ClientConnector clientConnector = new ClientConnector(ClientConnector.SocketChannelWithAddress.Factory.forUnixDomain(unixDomainPath));

        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        httpClient.start();
        try
        {
            // Try PROXYv1 with the PROXY information retrieved from the EndPoint.
            // PROXYv1 does not support the UNIX family.
            ContentResponse response1 = httpClient.newRequest("localhost", 0)
                .path("/v1")
                .tag(new V1.Tag())
                .timeout(5, TimeUnit.SECONDS)
                .send();

            assertEquals(HttpStatus.OK_200, response1.getStatus());

            // Try PROXYv2 with explicit PROXY information.
            var tag = new V2.Tag(V2.Tag.Command.PROXY, V2.Tag.Family.UNIX, V2.Tag.Protocol.STREAM, srcAddr, 0, dstAddr, 0, null);
            ContentResponse response2 = httpClient.newRequest("localhost", 0)
                .path("/v2")
                .tag(tag)
                .timeout(5, TimeUnit.SECONDS)
                .send();

            assertEquals(HttpStatus.OK_200, response2.getStatus());
        }
        finally
        {
            httpClient.stop();
        }
    }
}
