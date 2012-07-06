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

package org.eclipse.jetty.spdy.http;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class HTTPSPDYServerConnector extends AbstractHTTPSPDYServerConnector
{
    public HTTPSPDYServerConnector()
    {
        this(null, Collections.<Short, PushStrategy>emptyMap());
    }

    public HTTPSPDYServerConnector(Map<Short, PushStrategy> pushStrategies)
    {
        this(null, pushStrategies);
    }

    public HTTPSPDYServerConnector(SslContextFactory sslContextFactory)
    {
        this(sslContextFactory, Collections.<Short, PushStrategy>emptyMap());
    }

    public HTTPSPDYServerConnector(SslContextFactory sslContextFactory, Map<Short, PushStrategy> pushStrategies)
    {
        // We pass a null ServerSessionFrameListener because for
        // HTTP over SPDY we need one that references the endPoint
        super(null, sslContextFactory);
        clearAsyncConnectionFactories();
        PushStrategy.None pushStrategy = new PushStrategy.None();
        // The "spdy/3" protocol handles HTTP over SPDY
        putAsyncConnectionFactory(SPDY.V3, pushStrategy);
        // The "spdy/2" protocol handles HTTP over SPDY
        putAsyncConnectionFactory(SPDY.V2, pushStrategy);
        // Immediately overwrite the defaults if PushStrategies have been provided. The LinkedHashMap will preserve the right order of the protocols
        addPushStrategies(pushStrategies);
        // The "http/1.1" protocol handles browsers that support NPN but not SPDY
        putAsyncConnectionFactory("http/1.1", new ServerHTTPAsyncConnectionFactory(this));
        // The default connection factory handles plain HTTP on non-SSL or non-NPN connections
        setDefaultAsyncConnectionFactory(getAsyncConnectionFactory("http/1.1"));
    }

    private void putAsyncConnectionFactory(short version, PushStrategy pushStrategy)
    {
        String protocol = version == SPDY.V2 ? "spdy/2" : "spdy/3";
        putAsyncConnectionFactory(protocol, new ServerHTTPSPDYAsyncConnectionFactory(version, getByteBufferPool(), getExecutor(), getScheduler(), this, pushStrategy));
    }

    public void addPushStrategies(Map<Short, PushStrategy> pushStrategies)
    {
        for (Short version : pushStrategies.keySet())
        {
            putAsyncConnectionFactory(version, pushStrategies.get(version));
        }
    }
}
