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

package org.eclipse.jetty.websocket.tests;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.tests.examples.MyAdvancedEchoServlet;
import org.eclipse.jetty.websocket.tests.examples.MyAuthedServlet;
import org.eclipse.jetty.websocket.tests.examples.MyEchoServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketServletExamplesTest
{
    private Server _server;
    private ServletContextHandler _context;

    @BeforeEach
    public void setup() throws Exception
    {
        _server = new Server();
        ServerConnector connector = new ServerConnector(_server);
        connector.setPort(8080);
        _server.addConnector(connector);

        _context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        _context.setContextPath("/");
        _context.setSecurityHandler(getSecurityHandler("user", "password", "testRealm"));
        _server.setHandler(_context);

        _context.addServlet(MyEchoServlet.class, "/echo");
        _context.addServlet(MyAdvancedEchoServlet.class, "/advancedEcho");
        _context.addServlet(MyAuthedServlet.class, "/authed");

        JettyWebSocketServletContainerInitializer.configureContext(_context);
        _server.start();
    }

    @AfterEach
    public void stop() throws Exception
    {
        _server.stop();
    }

    private static SecurityHandler getSecurityHandler(String username, String password, String realm) {

        HashLoginService loginService = new HashLoginService();
        UserStore userStore = new UserStore();
        userStore.addUser(username, Credential.getCredential(password), new String[] {"websocket"});
        loginService.setUserStore(userStore);
        loginService.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[]{"**"});

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/authed/*");
        mapping.setConstraint(constraint);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.addConstraintMapping(mapping);
        security.setAuthenticator(new BasicAuthenticator());
        security.setLoginService(loginService);

        return security;
    }

    @Test
    public void testEchoServlet() throws Exception
    {
        WebSocketClient client = new WebSocketClient();
        client.start();

        URI uri = URI.create("ws://localhost:8080/echo");
        EventSocket socket = new EventSocket();
        CompletableFuture<Session> connect = client.connect(socket, uri);
        try (Session session = connect.get(5, TimeUnit.SECONDS))
        {
            String message = "hello world";
            session.getRemote().sendString(message);

            String response = socket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat(response, is(message));
        }

        assertTrue(socket.closeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testAdvancedEchoServlet() throws Exception
    {
        WebSocketClient client = new WebSocketClient();
        client.start();

        URI uri = URI.create("ws://localhost:8080/advancedEcho");
        EventSocket socket = new EventSocket();

        UpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("text");
        CompletableFuture<Session> connect = client.connect(socket, uri, upgradeRequest);
        try (Session session = connect.get(5, TimeUnit.SECONDS))
        {
            String message = "hello world";
            session.getRemote().sendString(message);

            String response = socket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat(response, is(message));
        }

        assertTrue(socket.closeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testAuthedServlet() throws Exception
    {
        WebSocketClient client = new WebSocketClient();
        client.start();
        AuthenticationStore authenticationStore = client.getHttpClient().getAuthenticationStore();

        URI uri = URI.create("ws://localhost:8080/authed");

        BasicAuthentication basicAuthentication = new BasicAuthentication(uri, "testRealm", "user", "password");
        authenticationStore.addAuthentication(basicAuthentication);

        EventSocket socket = new EventSocket();
        CompletableFuture<Session> connect = client.connect(socket, uri);
        try (Session session = connect.get(5, TimeUnit.SECONDS))
        {
            String message = "hello world";
            session.getRemote().sendString(message);

            String response = socket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat(response, is(message));
        }

        assertTrue(socket.closeLatch.await(10, TimeUnit.SECONDS));
    }
}
