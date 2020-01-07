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

package org.eclipse.jetty.websocket.javax.common;

import java.util.List;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;

public class ServerEndpointConfigWrapper extends EndpointConfigWrapper implements ServerEndpointConfig
{
    private ServerEndpointConfig _endpointConfig;

    public ServerEndpointConfigWrapper()
    {
    }

    public ServerEndpointConfigWrapper(ServerEndpointConfig endpointConfig)
    {
        init(endpointConfig);
    }

    public void init(ServerEndpointConfig endpointConfig)
    {
        _endpointConfig = endpointConfig;
        super.init(endpointConfig);
    }

    @Override
    public Class<?> getEndpointClass()
    {
        return _endpointConfig.getEndpointClass();
    }

    @Override
    public String getPath()
    {
        return _endpointConfig.getPath();
    }

    @Override
    public List<String> getSubprotocols()
    {
        return _endpointConfig.getSubprotocols();
    }

    @Override
    public List<Extension> getExtensions()
    {
        return _endpointConfig.getExtensions();
    }

    @Override
    public Configurator getConfigurator()
    {
        return _endpointConfig.getConfigurator();
    }
}
