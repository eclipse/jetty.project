//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.servlets;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlets.DoSFilter.RateTracker;
import org.eclipse.jetty.servlets.mocks.MockFilterConfig;
import org.eclipse.jetty.servlets.mocks.MockHttpServletRequest;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DoSFilterTest extends AbstractDoSFilterTest
{
    @Before
    public void setUp() throws Exception
    {
        startServer(DoSFilter.class);
    }
    
    
    @Test
    public void testRemotePortLoadIdCreation() throws ServletException {
        final ServletRequest request = newMockHttpServletRequest("127.0.0.1", 12345);
        DoSFilter doSFilter = new DoSFilter();
        doSFilter.init(new MockFilterConfig());
        doSFilter.setRemotePort(true);
        
        try {
            RateTracker tracker = doSFilter.getRateTracker(request);
            Assert.assertEquals("127.0.0.1:12345", tracker.getId());
        } finally {
            doSFilter.stopScheduler();
        }
    }
    
    
    

    @Test
    public void testRateIsRateExceeded() throws InterruptedException
    {
        DoSFilter doSFilter = new DoSFilter();
        doSFilter.setName("foo");
        boolean exceeded = hitRateTracker(doSFilter,0);
        Assert.assertTrue("Last hit should have exceeded",exceeded);

        int sleep = 250;
        exceeded = hitRateTracker(doSFilter,sleep);
        Assert.assertFalse("Should not exceed as we sleep 300s for each hit and thus do less than 4 hits/s",exceeded);
    }

    @Test
    public void testWhitelist() throws Exception
    {
        DoSFilter filter = new DoSFilter();
        filter.setName("foo");
        filter.setWhitelist("192.168.0.1/32,10.0.0.0/8,4d8:0:a:1234:ABc:1F:b18:17,4d8:0:a:1234:ABc:1F:0:0/96");
        Assert.assertTrue(filter.checkWhitelist("192.168.0.1"));
        Assert.assertFalse(filter.checkWhitelist("192.168.0.2"));
        Assert.assertFalse(filter.checkWhitelist("11.12.13.14"));
        Assert.assertTrue(filter.checkWhitelist("10.11.12.13"));
        Assert.assertTrue(filter.checkWhitelist("10.0.0.0"));
        Assert.assertFalse(filter.checkWhitelist("0.0.0.0"));
        Assert.assertTrue(filter.checkWhitelist("4d8:0:a:1234:ABc:1F:b18:17"));
        Assert.assertTrue(filter.checkWhitelist("4d8:0:a:1234:ABc:1F:b18:0"));
        Assert.assertFalse(filter.checkWhitelist("4d8:0:a:1234:ABc:1D:0:0"));
    }

    @Test
    public void testUnresponsiveServer() throws Exception
    {
        String last="GET /ctx/timeout/?sleep="+2*_requestMaxTime+" HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
        String responses = doRequests("",0,0,0,last);
        Assert.assertThat(responses, Matchers.containsString(" 503 "));
    }

    private boolean hitRateTracker(DoSFilter doSFilter, int sleep) throws InterruptedException
    {
        boolean exceeded = false;
        ServletContext context = new ContextHandler.StaticContext();
        RateTracker rateTracker = new RateTracker(context, doSFilter.getName(), "test2",0,4);

        for (int i = 0; i < 5; i++)
        {
            Thread.sleep(sleep);
            if (rateTracker.isRateExceeded(System.currentTimeMillis()))
                exceeded = true;
        }
        return exceeded;
    }
    
    
    
    private HttpServletRequest newMockHttpServletRequest(final String remoteAddr,
            final int remotePort) {
        return new MockHttpServletRequest() {
            @Override
            public String getRemoteAddr() {
                return remoteAddr;
            }

            @Override
            public int getRemotePort() {
                return remotePort;
            }
        }; 
    }
}
