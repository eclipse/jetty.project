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

package org.eclipse.jetty.websocket.jsr356.tests.server;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.jsr356.tests.Fuzzer;
import org.eclipse.jetty.websocket.jsr356.tests.WSServer;
import org.eclipse.jetty.websocket.jsr356.tests.server.sockets.echo.BasicEchoSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Testing the use of an alternate {@link org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter}
 * defined in the WEB-INF/web.xml
 */
@ExtendWith(WorkDirExtension.class)
public class AltFilterTest
{
    public WorkDir testdir;

    @Test
    public void testEcho() throws Exception
    {
        WSServer wsb = new WSServer(testdir.getPath(),"app");
        wsb.copyWebInf("alt-filter-web.xml");
        // the endpoint (extends javax.websocket.Endpoint)
        wsb.copyClass(BasicEchoSocket.class);

        try
        {
            wsb.start();
            
            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);
            
            FilterHolder filterWebXml = webapp.getServletHandler().getFilter("wsuf-test");
            assertThat("Filter[wsuf-test]", filterWebXml, notNullValue());
            
            FilterHolder filterSCI = webapp.getServletHandler().getFilter("Jetty_WebSocketUpgradeFilter");
            assertThat("Filter[Jetty_WebSocketUpgradeFilter]", filterSCI, nullValue());

            List<Frame> send = new ArrayList<>();
            send.add(new Frame(OpCode.TEXT).setPayload("Hello Echo"));
            send.add(CloseStatus.toFrame(CloseStatus.NORMAL));
    
            List<Frame> expect = new ArrayList<>();
            expect.add(new Frame(OpCode.TEXT).setPayload("Hello Echo"));
            expect.add(CloseStatus.toFrame(CloseStatus.NORMAL));
            
            try(Fuzzer session = wsb.newNetworkFuzzer("/app/echo;jsession=xyz"))
            {
                session.sendFrames(send);
                session.expect(expect);
            }
        }
        finally
        {
            wsb.stop();
        }
    }
}
