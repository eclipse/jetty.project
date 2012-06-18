/*
 * Copyright (c) 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.jetty.spdy.proxy;

import org.eclipse.jetty.spdy.ServerSPDYAsyncConnectionFactory;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.http.AbstractHTTPSPDYServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class HTTPSPDYProxyConnector extends AbstractHTTPSPDYServerConnector
{
    public HTTPSPDYProxyConnector(SPDYProxyEngine proxyEngine)
    {
        this(proxyEngine, null);
    }

    public HTTPSPDYProxyConnector(SPDYProxyEngine proxyEngine, SslContextFactory sslContextFactory)
    {
        super(proxyEngine, sslContextFactory);
        clearAsyncConnectionFactories();

        putAsyncConnectionFactory("spdy/3", new ServerSPDYAsyncConnectionFactory(SPDY.V3, getByteBufferPool(), getExecutor(), getScheduler(), proxyEngine));
        putAsyncConnectionFactory("spdy/2", new ServerSPDYAsyncConnectionFactory(SPDY.V2, getByteBufferPool(), getExecutor(), getScheduler(), proxyEngine));
        putAsyncConnectionFactory("http/1.1", new ProxyHTTPAsyncConnectionFactory(this, SPDY.V3, proxyEngine));
        setDefaultAsyncConnectionFactory(getAsyncConnectionFactory("http/1.1"));
    }
}
