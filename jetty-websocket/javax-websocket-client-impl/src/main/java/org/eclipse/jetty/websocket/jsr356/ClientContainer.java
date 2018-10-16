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

package org.eclipse.jetty.websocket.jsr356;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.eclipse.jetty.websocket.api.InvalidWebSocketException;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.ExtensionFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.io.UpgradeListener;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.common.function.EndpointFunctions;
import org.eclipse.jetty.websocket.common.scopes.DelegatedContainerScope;
import org.eclipse.jetty.websocket.common.scopes.SimpleContainerScope;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;
import org.eclipse.jetty.websocket.jsr356.client.AnnotatedClientEndpointConfig;
import org.eclipse.jetty.websocket.jsr356.client.EmptyClientEndpointConfig;
import org.eclipse.jetty.websocket.jsr356.decoders.AvailableDecoders;
import org.eclipse.jetty.websocket.jsr356.encoders.AvailableEncoders;
import org.eclipse.jetty.websocket.jsr356.function.JsrEndpointFunctions;

/**
 * Container for Client use of the javax.websocket API.
 * <p>
 * This should be specific to a JVM if run in a standalone mode. or specific to a WebAppContext if running on the Jetty server.
 */
@ManagedObject("JSR356 Client Container")
public class ClientContainer extends ContainerLifeCycle implements WebSocketContainer, WebSocketContainerScope
{
    private static final Logger LOG = Log.getLogger(ClientContainer.class);

    /** The delegated Container Scope */
    private final WebSocketContainerScope scopeDelegate;
    /** The jetty websocket client in use for this container */
    private final WebSocketClient client;
    private final boolean internalClient;
    
    /**
     * @deprecated use {@link #ClientContainer(WebSocketContainerScope)}
     */
    @Deprecated
    public ClientContainer()
    {
        // This constructor is used with Standalone JSR Client usage.
        this(new SimpleContainerScope(WebSocketPolicy.newClientPolicy()));
        client.setDaemon(true);
    }
    
    /**
     * This is the entry point for ServerContainer, via ServletContext.getAttribute(ServerContainer.class.getName())
     *
     * @param scope the scope of the ServerContainer
     */
    public ClientContainer(final WebSocketContainerScope scope)
    {
        this(scope, null);
    }
    
    /**
     * This is the entry point for ServerContainer, via ServletContext.getAttribute(ServerContainer.class.getName())
     *
     * @param scope the scope of the ServerContainer
     * @param httpClient the HttpClient instance to use
     */
    protected ClientContainer(final WebSocketContainerScope scope, final HttpClient httpClient)
    {
        String jsr356TrustAll = System.getProperty("org.eclipse.jetty.websocket.jsr356.ssl-trust-all");
        
        WebSocketContainerScope clientScope;
        if (scope.getPolicy().getBehavior() == WebSocketBehavior.CLIENT)
        {
            clientScope = scope;
        }
        else
        {
            // We need to wrap the scope for the CLIENT Policy behaviors
            clientScope = new DelegatedContainerScope(WebSocketPolicy.newClientPolicy(), scope);
        }
    
        this.scopeDelegate = clientScope;
        this.client = new WebSocketClient(scopeDelegate);
        this.client.setSessionFactory(new JsrSessionFactory(this));
        this.internalClient = true;

        if(jsr356TrustAll != null)
        {
            boolean trustAll = Boolean.parseBoolean(jsr356TrustAll);
            client.getSslContextFactory().setTrustAll(trustAll);
        }
        
        ShutdownThread.register(this);
    }
    
    /**
     * Build a ClientContainer with a specific WebSocketClient in mind.
     *
     * @param client the WebSocketClient to use.
     */
    public ClientContainer(WebSocketClient client)
    {
        this.scopeDelegate = client;
        this.client = client;
        this.client.setSessionFactory(new JsrSessionFactory(this));
        this.internalClient = false;
    }

    public EndpointFunctions newJsrEndpointFunction(Object endpoint,
                                                    WebSocketPolicy sessionPolicy,
                                                    AvailableEncoders availableEncoders,
                                                    AvailableDecoders availableDecoders,
                                                    Map<String, String> pathParameters,
                                                    EndpointConfig config)
    {
        return new JsrEndpointFunctions(endpoint,
                sessionPolicy,
                getExecutor(),
                availableEncoders,
                availableDecoders,
                pathParameters,
                config);
    }

    private Session connect(ConfiguredEndpoint instance, URI path) throws IOException
    {
        synchronized (this.client)
        {
            if (this.internalClient && !this.client.isStarted())
            {
                try
                {
                    this.client.start();
                    addManaged(this.client);
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Unable to start Client", e);
                }
            }
        }
        
        Objects.requireNonNull(instance,"EndpointInstance cannot be null");
        Objects.requireNonNull(path,"Path cannot be null");

        ClientEndpointConfig config = (ClientEndpointConfig)instance.getConfig();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        UpgradeListener upgradeListener = null;
        
        for (Extension ext : config.getExtensions())
        {
            req.addExtensions(new JsrExtensionConfig(ext));
        }

        if (config.getPreferredSubprotocols().size() > 0)
        {
            req.setSubProtocols(config.getPreferredSubprotocols());
        }

        if (config.getConfigurator() != null)
        {
            upgradeListener = new JsrUpgradeListener(config.getConfigurator());
        }

        Future<org.eclipse.jetty.websocket.api.Session> futSess = client.connect(instance, path, req, upgradeListener);
        try
        {
            return (JsrSession) futSess.get();
        }
        catch (InterruptedException e)
        {
            throw new IOException("Connect failure", e);
        }
        catch (ExecutionException e)
        {
            // Unwrap Actual Cause
            Throwable cause = e.getCause();

            if (cause instanceof IOException)
            {
                // Just rethrow
                throw (IOException) cause;
            }
            else
            {
                throw new IOException("Connect failure", cause);
            }
        }
    }

    @Override
    public Session connectToServer(final Class<? extends Endpoint> endpointClass, final ClientEndpointConfig config, URI path) throws DeploymentException, IOException
    {
        ClientEndpointConfig clientEndpointConfig = config;
        if (clientEndpointConfig == null)
        {
            clientEndpointConfig = new EmptyClientEndpointConfig();
        }
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpointClass, clientEndpointConfig);
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(final Class<?> annotatedEndpointClass, final URI path) throws DeploymentException, IOException
    {
        ConfiguredEndpoint instance = newConfiguredEndpoint(annotatedEndpointClass, new EmptyClientEndpointConfig());
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(final Endpoint endpoint, final ClientEndpointConfig config, final URI path) throws DeploymentException, IOException
    {
        ClientEndpointConfig clientEndpointConfig = config;
        if (clientEndpointConfig == null)
        {
            clientEndpointConfig = new EmptyClientEndpointConfig();
        }
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpoint, clientEndpointConfig);
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(Object endpoint, URI path) throws DeploymentException, IOException
    {
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpoint, new EmptyClientEndpointConfig());
        return connect(instance, path);
    }

    @Override
    protected void doStop() throws Exception
    {
        ShutdownThread.deregister(this);
        super.doStop();
    }

    @Override
    public ByteBufferPool getBufferPool()
    {
        return scopeDelegate.getBufferPool();
    }

    public WebSocketClient getClient()
    {
        return client;
    }

    @Override
    public long getDefaultAsyncSendTimeout()
    {
        return client.getAsyncWriteTimeout();
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize()
    {
        return client.getMaxBinaryMessageBufferSize();
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout()
    {
        return client.getMaxIdleTimeout();
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize()
    {
        return client.getMaxTextMessageBufferSize();
    }

    @Override
    public Executor getExecutor()
    {
        return scopeDelegate.getExecutor();
    }

    @Override
    public Set<Extension> getInstalledExtensions()
    {
        Set<Extension> ret = new HashSet<>();
        ExtensionFactory extensions = client.getExtensionFactory();

        for (String name : extensions.getExtensionNames())
        {
            ret.add(new JsrExtension(name));
        }

        return ret;
    }

    @Override
    public DecoratedObjectFactory getObjectFactory()
    {
        return scopeDelegate.getObjectFactory();
    }

    /**
     * Used in {@link Session#getOpenSessions()}
     *
     * @return the set of open sessions
     */
    public Set<Session> getOpenSessions()
    {
        return new HashSet<>(getBeans(Session.class));
    }

    @Override
    public WebSocketPolicy getPolicy()
    {
        return scopeDelegate.getPolicy();
    }

    @Override
    public SslContextFactory getSslContextFactory()
    {
        return scopeDelegate.getSslContextFactory();
    }

    private ConfiguredEndpoint newConfiguredEndpoint(Class<?> endpointClass, EndpointConfig config)
    {
        try
        {
            return newConfiguredEndpoint(endpointClass.getDeclaredConstructor().newInstance(), config);
        }
        catch (Exception e)
        {
            throw new InvalidWebSocketException("Unable to instantiate websocket: " + endpointClass.getClass(), e);
        }
    }

    public ConfiguredEndpoint newConfiguredEndpoint(Object endpoint, EndpointConfig providedConfig) throws DeploymentException
    {
        EndpointConfig config = providedConfig;

        if (config == null)
        {
            config = newEmptyConfig(endpoint);
        }

        config = readAnnotatedConfig(endpoint, config);

        return new ConfiguredEndpoint(endpoint, config);
    }

    protected EndpointConfig newEmptyConfig(Object endpoint)
    {
        return new EmptyClientEndpointConfig();
    }

    protected EndpointConfig readAnnotatedConfig(Object endpoint, EndpointConfig config) throws DeploymentException
    {
        ClientEndpoint anno = endpoint.getClass().getAnnotation(ClientEndpoint.class);
        if (anno != null)
        {
            // Overwrite Config from Annotation
            // TODO: should we merge with provided config?
            return new AnnotatedClientEndpointConfig(anno);
        }
        return config;
    }

    @Override
    public void addSessionListener(WebSocketSession.Listener listener)
    {
        this.scopeDelegate.addSessionListener(listener);
    }

    @Override
    public boolean removeSessionListener(WebSocketSession.Listener listener)
    {
        return this.scopeDelegate.removeSessionListener(listener);
    }

    @Override
    public void onSessionClosed(WebSocketSession session)
    {
        if (session instanceof Session)
        {
            removeBean(session);
        }
        else
        {
            LOG.warn("JSR356 Implementation should not be mixed with native implementation: Expected {} to implement {}", session.getClass().getName(),
                    Session.class.getName());
        }
    }

    @Override
    public void onSessionOpened(WebSocketSession session)
    {
        if (session instanceof Session)
        {
            addManaged(session);
        }
        else
        {
            LOG.warn("JSR356 Implementation should not be mixed with Jetty native websocket implementation: Expected {} to implement {}", session.getClass().getName(),
                    Session.class.getName());
        }
    }

    @Override
    public void setAsyncSendTimeout(long ms)
    {
        client.setAsyncWriteTimeout(ms);
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max)
    {
        // overall message limit (used in non-streaming)
        client.getPolicy().setMaxBinaryMessageSize(max);
        // incoming streaming buffer size
        client.setMaxBinaryMessageBufferSize(max);
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long ms)
    {
        client.setMaxIdleTimeout(ms);
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int max)
    {
        // overall message limit (used in non-streaming)
        client.getPolicy().setMaxTextMessageSize(max);
        // incoming streaming buffer size
        client.setMaxTextMessageBufferSize(max);
    }
}
