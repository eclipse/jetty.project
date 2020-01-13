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

package examples;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.websocket.ClientEndpointConfig;

/**
 * Provide a means to set the `Origin` header for outgoing WebSocket upgrade requests
 */
public class OriginServerConfigurator extends ClientEndpointConfig.Configurator
{
    private final String originServer;

    public OriginServerConfigurator(String originServer)
    {
        this.originServer = originServer;
    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers)
    {
        headers.put("Origin", Collections.singletonList(originServer));
        super.beforeRequest(headers);
    }
}
