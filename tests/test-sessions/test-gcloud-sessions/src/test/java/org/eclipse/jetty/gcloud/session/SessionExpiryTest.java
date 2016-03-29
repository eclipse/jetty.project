//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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


package org.eclipse.jetty.gcloud.session;

import org.eclipse.jetty.server.session.AbstractSessionExpiryTest;
import org.eclipse.jetty.server.session.AbstractTestServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;


/**
 * SessionExpiryTest
 *
 *
 */
public class SessionExpiryTest extends AbstractSessionExpiryTest
{

    static GCloudSessionTestSupport _testSupport;
    
    @BeforeClass
    public static void setup () throws Exception
    {
        _testSupport = new GCloudSessionTestSupport();
        _testSupport.setUp();
    }
    
    @AfterClass
    public static void teardown () throws Exception
    {
        _testSupport.tearDown();
    }
    
    
    /** 
     * @see org.eclipse.jetty.server.session.AbstractSessionExpiryTest#createServer(int, int, int)
     */
    @Override
    public AbstractTestServer createServer(int port, int max, int scavenge, int idlePassivationPeriod)
    {
        return  new GCloudTestServer(port, max, scavenge, idlePassivationPeriod, _testSupport.getConfiguration());
    }

    @Test
    @Override
    public void testSessionNotExpired() throws Exception
    {
        super.testSessionNotExpired();
        _testSupport.deleteSessions();
    }

    /** 
     * @see org.eclipse.jetty.server.session.AbstractSessionExpiryTest#testSessionExpiry()
     */
    @Test
    @Override
    public void testSessionExpiry() throws Exception
    {
        super.testSessionExpiry();
        try{_testSupport.assertSessions(0);}catch(Exception e){ Assert.fail(e.getMessage());}
    }

    @Override
    public void verifySessionCreated(TestHttpSessionListener listener, String sessionId)
    {
        super.verifySessionCreated(listener, sessionId);
        try {_testSupport.assertSessions(1);}catch(Exception e){ Assert.fail(e.getMessage());}
    }

    @Override
    public void verifySessionDestroyed(TestHttpSessionListener listener, String sessionId)
    {
        super.verifySessionDestroyed(listener, sessionId);
        try{_testSupport.assertSessions(0);}catch(Exception e){ Assert.fail(e.getMessage());}
    }

    
    
}
