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

package org.eclipse.jetty.websocket.tests.client;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.tests.Defaults;
import org.eclipse.jetty.websocket.tests.TrackingEndpoint;
import org.eclipse.jetty.websocket.tests.UntrustedWSServer;
import org.eclipse.jetty.websocket.tests.UntrustedWSSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class CookieTest
{
    private static final Logger LOG = Log.getLogger(CookieTest.class);
    
    private UntrustedWSServer server;
    private WebSocketClient client;
    
    @BeforeEach
    public void startClient() throws Exception
    {
        client = new WebSocketClient();
        client.start();
    }
    
    @BeforeEach
    public void startServer() throws Exception
    {
        server = new UntrustedWSServer();
        server.start();
    }
    
    @AfterEach
    public void stopClient() throws Exception
    {
        client.stop();
    }
    
    @AfterEach
    public void stopServer() throws Exception
    {
        server.stop();
    }
    
    @Test
    public void testViaCookieManager(TestInfo testInfo) throws Exception
    {
        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo);
        URI wsUri = server.getUntrustedWsUri(this.getClass(), testInfo);
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerOnOpenFuture(wsUri, serverSessionFut);
        
        // Setup client
        CookieManager cookieMgr = new CookieManager();
        client.setCookieStore(cookieMgr.getCookieStore());
        HttpCookie cookie = new HttpCookie("hello", "world");
        cookie.setPath("/");
        cookie.setVersion(0);
        cookie.setMaxAge(100000);
        cookieMgr.getCookieStore().add(server.getWsUri(), cookie);
        
        cookie = new HttpCookie("foo", "bar is the word");
        cookie.setPath("/");
        cookie.setMaxAge(100000);
        cookieMgr.getCookieStore().add(server.getWsUri(), cookie);
    
        // Client connects
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri);
        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        // client confirms upgrade and receipt of frame
        List<String> serverCookies = serverSession.getUntrustedEndpoint().openUpgradeRequest.getHeaders("Cookie");
        
        assertThat("Cookies seen at server side", serverCookies, hasItem(containsString("hello=world")));
        assertThat("Cookies seen at server side", serverCookies, hasItem(containsString("foo=bar is the word")));
    }
    
    @Test
    public void testViaServletUpgradeRequest(TestInfo testInfo) throws Exception
    {
        TrackingEndpoint clientSocket = new TrackingEndpoint(testInfo);
        URI wsUri = server.getUntrustedWsUri(this.getClass(), testInfo);
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerOnOpenFuture(wsUri, serverSessionFut);
        
        // Setup client
        HttpCookie cookie = new HttpCookie("hello", "world");
        cookie.setPath("/");
        cookie.setMaxAge(100000);
        
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setCookies(Collections.singletonList(cookie));
        
        // Client connects
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsUri, request);
        Session clientSession = clientConnectFuture.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(Defaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        // client confirms upgrade and receipt of frame
        List<String> serverCookies = serverSession.getUntrustedEndpoint().openUpgradeRequest.getHeaders("Cookie");
        
        assertThat("Cookies seen at server side", serverCookies, hasItem(containsString("hello=world")));
    }
}
