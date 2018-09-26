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

package org.eclipse.jetty.websocket.tests.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.servlet.ServletContext;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.Decorator;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.listeners.WebSocketAdapter;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.tests.Fuzzer;
import org.eclipse.jetty.websocket.tests.SimpleServletServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Test the {@link Decorator} features of the WebSocketServer
 */
public class DecoratorsTest
{
    private static final Logger LOG = Log.getLogger(DecoratorsTest.class);
    
    public static class DecoratorsSocket extends WebSocketAdapter
    {
        private final DecoratedObjectFactory objFactory;
        
        public DecoratorsSocket(DecoratedObjectFactory objFactory)
        {
            this.objFactory = objFactory;
        }
        
        @Override
        public void onWebSocketText(String message)
        {
            StringWriter str = new StringWriter();
            PrintWriter out = new PrintWriter(str);
            
            if (objFactory != null)
            {
                out.printf("Object is a DecoratedObjectFactory%n");
                List<Decorator> decorators = objFactory.getDecorators();
                out.printf("Decorators.size = [%d]%n",decorators.size());
                for (Decorator decorator : decorators)
                {
                    out.printf(" decorator[] = %s%n",decorator.getClass().getName());
                }
            }
            else
            {
                out.printf("DecoratedObjectFactory is NULL%n");
            }
            
            LOG.debug(out.toString());

            getRemote().sendStringByFuture(str.toString());
        }
    }

    private static class DecoratorsCreator implements WebSocketCreator
    {
        @Override
        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
        {
            ServletContext servletContext = req.getHttpServletRequest().getServletContext();
            DecoratedObjectFactory objFactory = (DecoratedObjectFactory)servletContext.getAttribute(DecoratedObjectFactory.ATTR);
            return new DecoratorsSocket(objFactory);
        }
    }

    public static class DecoratorsRequestServlet extends WebSocketServlet
    {
        private static final long serialVersionUID = 1L;
        private final WebSocketCreator creator;

        public DecoratorsRequestServlet(WebSocketCreator creator)
        {
            this.creator = creator;
        }

        @Override
        public void configure(WebSocketServletFactory factory)
        {
            factory.setCreator(this.creator);
        }
    }
    
    private static class DummyUtilDecorator implements org.eclipse.jetty.util.Decorator
    {
        @Override
        public <T> T decorate(T o)
        {
            return o;
        }

        @Override
        public void destroy(Object o)
        {
        }
    }
    
    @SuppressWarnings("deprecation")
    private static class DummyLegacyDecorator implements org.eclipse.jetty.servlet.ServletContextHandler.Decorator
    {
        @Override
        public <T> T decorate(T o)
        {
            return o;
        }
        
        @Override
        public void destroy(Object o)
        {
        }
    }
    
    private interface Case
    {
        void customize(ServletContextHandler context);
    }
    
    @SuppressWarnings("deprecation")
    public static Stream<Arguments> data()
    {
        return Stream.of(
            Arguments.of( "Legacy Usage", (Case) (context) -> {
                        context.getObjectFactory().clear();
                        // Add decorator in the legacy way
                        context.addDecorator(new DummyLegacyDecorator());
                    },
                    DummyLegacyDecorator.class
            ),

            Arguments.of( "Recommended Usage", (Case) (context) -> {
                        // Add decorator in the new util way
                        context.getObjectFactory().clear();
                        context.getObjectFactory().addDecorator(new DummyUtilDecorator());
                    },
                    DummyUtilDecorator.class
            )
        );
    }

    private SimpleServletServer server;

    public void setupServer(String testId, Case testcase) throws Exception
    {
        LOG.debug("Testing {}", testId);
        server = new SimpleServletServer(new DecoratorsRequestServlet(new DecoratorsCreator()))
        {
            @Override
            protected void configureServletContextHandler(ServletContextHandler context)
            {
                super.configureServletContextHandler(context);
                testcase.customize(context);
            }
        };
        server.start();
    }

    @AfterEach
    public void stopServer() throws Exception
    {
        server.stop();
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testAccessRequestCookies(String testId, Case testcase, Class<?> expectedDecoratorClass) throws Exception
    {
        setupServer(testId, testcase);

        try (Fuzzer session = server.newNetworkFuzzer("/"))
        {
            session.sendFrames(
                    new Frame(OpCode.TEXT).setPayload("info"),
                    CloseStatus.toFrame(StatusCode.NORMAL.getCode())
            );
        
            BlockingQueue<Frame> framesQueue = session.getOutputFrames();
        
            Frame frame = framesQueue.poll(1, TimeUnit.SECONDS);
            assertThat("Frame.opCode", frame.getOpCode(), is(OpCode.TEXT));
        
            String payload = frame.getPayloadAsUTF8();
            assertThat("Text - DecoratedObjectFactory", payload, containsString("Object is a DecoratedObjectFactory"));
            assertThat("Text - decorators.size", payload, containsString("Decorators.size = [1]"));
            assertThat("Text - decorator type", payload, containsString("decorator[] = " + expectedDecoratorClass.getName()));
        }
    }
}
