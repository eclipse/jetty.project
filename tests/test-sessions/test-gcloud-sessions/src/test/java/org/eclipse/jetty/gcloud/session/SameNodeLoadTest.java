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

import org.eclipse.jetty.server.session.AbstractSameNodeLoadTest;
import org.eclipse.jetty.server.session.AbstractTestServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * SameNodeLoadTest
 *
 *
 */
public class SameNodeLoadTest extends AbstractSameNodeLoadTest
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
     * @see org.eclipse.jetty.server.session.AbstractSameNodeLoadTest#createServer(int, int, int, int)
     */
    @Override
    public AbstractTestServer createServer(int port, int max, int scavenge, int idlePassivationPeriod)
    {
        return new GCloudTestServer(port, max, scavenge, idlePassivationPeriod, _testSupport.getConfiguration());
    }

    @Test
    @Override
    public void testLoad() throws Exception
    {
        super.testLoad();
    }

    
}
