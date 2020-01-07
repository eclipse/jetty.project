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

package org.eclipse.jetty.websocket.javax.server.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.websocket.Extension;
import javax.websocket.Extension.Parameter;
import javax.websocket.server.ServerEndpointConfig;

import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.ExtensionConfig;
import org.eclipse.jetty.websocket.core.WebSocketExtensionRegistry;
import org.eclipse.jetty.websocket.javax.common.ConfiguredEndpoint;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketExtension;
import org.eclipse.jetty.websocket.javax.common.ServerEndpointConfigWrapper;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

public class JavaxWebSocketCreator implements WebSocketCreator
{
    public static final String PROP_REMOTE_ADDRESS = "javax.websocket.endpoint.remoteAddress";
    public static final String PROP_LOCAL_ADDRESS = "javax.websocket.endpoint.localAddress";
    public static final String PROP_LOCALES = "javax.websocket.upgrade.locales";
    private static final Logger LOG = Log.getLogger(JavaxWebSocketCreator.class);
    private final JavaxWebSocketContainer containerScope;
    private final ServerEndpointConfig baseConfig;
    private final WebSocketExtensionRegistry extensionRegistry;

    public JavaxWebSocketCreator(JavaxWebSocketContainer containerScope, ServerEndpointConfig config, WebSocketExtensionRegistry extensionRegistry)
    {
        this.containerScope = containerScope;
        this.baseConfig = config;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
    {
        final JsrHandshakeRequest jsrHandshakeRequest = new JsrHandshakeRequest(req);
        final JsrHandshakeResponse jsrHandshakeResponse = new JsrHandshakeResponse(resp);

        // Establish a copy of the config, so that the UserProperties are unique
        // per upgrade request.
        ServerEndpointConfig config = new ServerEndpointConfigWrapper(baseConfig)
        {
            Map<String, Object> userProperties = new HashMap<>(baseConfig.getUserProperties());

            @Override
            public Map<String, Object> getUserProperties()
            {
                return userProperties;
            }
        };

        // Bug 444617 - Expose localAddress and remoteAddress for jsr modify handshake to use
        // This is being implemented as an optional set of userProperties so that
        // it is not JSR api breaking.  A few users on #jetty and a few from cometd
        // have asked for access to this information.
        Map<String, Object> userProperties = config.getUserProperties();
        userProperties.put(PROP_LOCAL_ADDRESS, req.getLocalSocketAddress());
        userProperties.put(PROP_REMOTE_ADDRESS, req.getRemoteSocketAddress());
        userProperties.put(PROP_LOCALES, Collections.list(req.getLocales()));

        // Get Configurator from config object (not guaranteed to be unique per endpoint upgrade)
        ServerEndpointConfig.Configurator configurator = config.getConfigurator();

        // [JSR] Step 1: check origin
        if (!configurator.checkOrigin(req.getOrigin()))
        {
            try
            {
                resp.sendForbidden("Origin mismatch");
            }
            catch (IOException e)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Unable to send error response", e);
            }
            return null;
        }

        // [JSR] Step 2: deal with sub protocols
        List<String> supported = config.getSubprotocols();
        List<String> requested = req.getSubProtocols();
        String subprotocol = configurator.getNegotiatedSubprotocol(supported, requested);
        if (StringUtil.isNotBlank(subprotocol))
        {
            resp.setAcceptedSubProtocol(subprotocol);
        }

        // [JSR] Step 3: deal with extensions
        List<Extension> installedExtensions = new ArrayList<>();
        for (String extName : extensionRegistry.getAvailableExtensions().keySet())
        {
            installedExtensions.add(new JavaxWebSocketExtension(extName));
        }
        List<Extension> requestedExts = new ArrayList<>();
        for (ExtensionConfig reqCfg : req.getExtensions())
        {
            requestedExts.add(new JavaxWebSocketExtension(reqCfg));
        }
        List<Extension> usedExtensions = configurator.getNegotiatedExtensions(installedExtensions, requestedExts);
        List<ExtensionConfig> configs = new ArrayList<>();
        if (usedExtensions != null)
        {
            for (Extension used : usedExtensions)
            {
                ExtensionConfig ecfg = new ExtensionConfig(used.getName());
                for (Parameter param : used.getParameters())
                {
                    ecfg.setParameter(param.getName(), param.getValue());
                }
                configs.add(ecfg);
            }
        }
        resp.setExtensions(configs);

        // [JSR] Step 4: build out new ServerEndpointConfig
        PathSpec pathSpec = jsrHandshakeRequest.getRequestPathSpec();
        if (pathSpec instanceof UriTemplatePathSpec)
        {
            // We have a PathParam path spec
            UriTemplatePathSpec wspathSpec = (UriTemplatePathSpec)pathSpec;
            String requestPath = req.getRequestPath();
            // Wrap the config with the path spec information
            config = new PathParamServerEndpointConfig(config, wspathSpec, requestPath);
        }

        // [JSR] Step 5: Call modifyHandshake
        configurator.modifyHandshake(config, jsrHandshakeRequest, jsrHandshakeResponse);
        // Set modified headers Map back into response properly
        jsrHandshakeResponse.setHeaders(jsrHandshakeResponse.getHeaders());

        try
        {
            // [JSR] Step 6: create endpoint class
            Class<?> endpointClass = config.getEndpointClass();
            Object endpoint = config.getConfigurator().getEndpointInstance(endpointClass);
            // Do not decorate here (let the Connection and Session start first)
            // This will allow CDI to see Session for injection into Endpoint classes.
            return new ConfiguredEndpoint(endpoint, config);
        }
        catch (InstantiationException e)
        {
            LOG.warn("Unable to create websocket: " + config.getEndpointClass().getName(), e);
            return null;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        JavaxWebSocketCreator that = (JavaxWebSocketCreator)o;

        return baseConfig != null ? baseConfig.equals(that.baseConfig) : that.baseConfig == null;
    }

    @Override
    public int hashCode()
    {
        int result = (baseConfig != null ? baseConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return String.format("JsrCreator[%s%s]", (baseConfig instanceof AnnotatedServerEndpointConfig ? "@" : ""), baseConfig.getEndpointClass().getName());
    }
}
