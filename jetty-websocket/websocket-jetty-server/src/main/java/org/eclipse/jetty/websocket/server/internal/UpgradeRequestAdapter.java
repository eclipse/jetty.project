//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.server.internal;

import java.net.HttpCookie;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.common.JettyExtensionConfig;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

public class UpgradeRequestAdapter implements UpgradeRequest
{
    private final ServletUpgradeRequest servletRequest;

    public UpgradeRequestAdapter(ServletUpgradeRequest servletRequest)
    {
        this.servletRequest = servletRequest;
    }

    @Override
    public void addExtensions(ExtensionConfig... configs)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void addExtensions(String... configs)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public List<HttpCookie> getCookies()
    {
        return this.servletRequest.getCookies();
    }

    @Override
    public List<ExtensionConfig> getExtensions()
    {
        return this.servletRequest.getExtensions().stream()
            .map((ext) -> new JettyExtensionConfig(ext.getName(), ext.getParameters()))
            .collect(Collectors.toList());
    }

    @Override
    public String getHeader(String name)
    {
        return this.servletRequest.getHeader(name);
    }

    @Override
    public int getHeaderInt(String name)
    {
        return this.servletRequest.getHeaderInt(name);
    }

    @Override
    public Map<String, List<String>> getHeaders()
    {
        return this.servletRequest.getHeadersMap();
    }

    @Override
    public List<String> getHeaders(String name)
    {
        return this.servletRequest.getHeaders(name);
    }

    @Override
    public String getHost()
    {
        return this.servletRequest.getHost();
    }

    @Override
    public String getHttpVersion()
    {
        return this.servletRequest.getHttpVersion();
    }

    @Override
    public String getMethod()
    {
        return this.servletRequest.getMethod();
    }

    @Override
    public String getOrigin()
    {
        return this.servletRequest.getOrigin();
    }

    @Override
    public Map<String, List<String>> getParameterMap()
    {
        return this.servletRequest.getParameterMap();
    }

    @Override
    public String getProtocolVersion()
    {
        return this.servletRequest.getProtocolVersion();
    }

    @Override
    public String getQueryString()
    {
        return this.servletRequest.getQueryString();
    }

    @Override
    public URI getRequestURI()
    {
        return this.servletRequest.getRequestURI();
    }

    @Override
    public Object getSession()
    {
        return this.servletRequest.getSession();
    }

    @Override
    public List<String> getSubProtocols()
    {
        return this.servletRequest.getSubProtocols();
    }

    @Override
    public Principal getUserPrincipal()
    {
        return this.servletRequest.getUserPrincipal();
    }

    @Override
    public boolean hasSubProtocol(String test)
    {
        return this.servletRequest.hasSubProtocol(test);
    }

    @Override
    public boolean isSecure()
    {
        return this.servletRequest.isSecure();
    }

    @Override
    public void setCookies(List<HttpCookie> cookies)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setExtensions(List<ExtensionConfig> configs)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setHeader(String name, List<String> values)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setHeader(String name, String value)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setHeaders(Map<String, List<String>> headers)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setSession(Object session)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setSubProtocols(List<String> protocols)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }

    @Override
    public void setSubProtocols(String... protocols)
    {
        throw new UnsupportedOperationException("Not supported from Servlet API");
    }
}
