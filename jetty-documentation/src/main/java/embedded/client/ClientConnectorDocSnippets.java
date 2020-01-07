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

package embedded.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import static java.lang.System.Logger.Level.INFO;

public class ClientConnectorDocSnippets
{
    public void simplest() throws Exception
    {
        // tag::simplest[]
        ClientConnector clientConnector = new ClientConnector();
        clientConnector.start();
        // end::simplest[]
    }

    public void typical() throws Exception
    {
        // tag::typical[]
        // Create and configure the SslContextFactory.
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.addExcludeProtocols("TLSv1", "TLSv1.1");

        // Create and configure the thread pool.
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("client");

        // Create and configure the ClientConnector.
        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslContextFactory);
        clientConnector.setExecutor(threadPool);
        clientConnector.start();
        // end::typical[]
    }

    public void advanced() throws Exception
    {
        // tag::advanced[]
        class CustomClientConnector extends ClientConnector
        {
            @Override
            protected SelectorManager newSelectorManager()
            {
                return new ClientSelectorManager(getExecutor(), getScheduler(), getSelectors())
                {
                    @Override
                    protected void endPointOpened(EndPoint endpoint)
                    {
                        System.getLogger("endpoint").log(INFO, "opened %s", endpoint);
                    }

                    @Override
                    protected void endPointClosed(EndPoint endpoint)
                    {
                        System.getLogger("endpoint").log(INFO, "closed %s", endpoint);
                    }
                };
            }
        }

        // Create and configure the thread pool.
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("client");

        // Create and configure the scheduler.
        Scheduler scheduler = new ScheduledExecutorScheduler("scheduler-client", false);

        // Create and configure the custom ClientConnector.
        CustomClientConnector clientConnector = new CustomClientConnector();
        clientConnector.setExecutor(threadPool);
        clientConnector.setScheduler(scheduler);
        clientConnector.start();
        // end::advanced[]
    }

    public void connect() throws Exception
    {
        class CustomHTTPConnection extends AbstractConnection
        {
            public CustomHTTPConnection(EndPoint endPoint, Executor executor)
            {
                super(endPoint, executor);
            }

            @Override
            public void onOpen()
            {
                super.onOpen();
            }

            @Override
            public void onFillable()
            {
            }
        }

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.start();

        String host = "wikipedia.org";
        int port = 80;
        SocketAddress address = new InetSocketAddress(host, port);

        ClientConnectionFactory connectionFactory = (endPoint, context) ->
        {
            System.getLogger("connection").log(INFO, "Creating connection for {0}", endPoint);
            return new CustomHTTPConnection(endPoint, clientConnector.getExecutor());
        };
        Map<String, Object> context = new HashMap<>();
        context.put(ClientConnector.CLIENT_CONNECTION_FACTORY_CONTEXT_KEY, connectionFactory);
        clientConnector.connect(address, context);
    }

    public static void main(String[] args) throws Exception
    {
        new ClientConnectorDocSnippets().connect();
    }
}
