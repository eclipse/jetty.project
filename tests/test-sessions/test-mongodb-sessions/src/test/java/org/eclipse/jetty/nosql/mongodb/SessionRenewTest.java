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

package org.eclipse.jetty.nosql.mongodb;

import org.eclipse.jetty.server.session.AbstractSessionRenewTest;
import org.eclipse.jetty.server.session.AbstractTestServer;
import org.eclipse.jetty.webapp.WebAppContext;

import static org.junit.Assert.assertNotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class SessionRenewTest extends AbstractSessionRenewTest
{

    
    @BeforeClass
    public static void beforeClass() throws Exception
    {
        MongoTestServer.dropCollection();
        MongoTestServer.createCollection();
    }

    @AfterClass
    public static void afterClass() throws Exception
    {
        MongoTestServer.dropCollection();
    }
    
    @Override
    public AbstractTestServer createServer(int port, int max, int scavenge, int idlePassivate)
    {
        return new MongoTestServer(port, max, scavenge,  idlePassivate);
    }

    @Test
    public void testSessionRenewal() throws Exception
    {
        super.testSessionRenewal();
    }

    /** 
     * @see org.eclipse.jetty.server.session.AbstractSessionRenewTest#verifyChange(java.lang.String, java.lang.String)
     */
    @Override
    public boolean verifyChange(WebAppContext context, String oldSessionId, String newSessionId)
    {
        try
        {
            DBCollection sessions = MongoTestServer.getCollection();
            assertNotNull(sessions);
            DBObject sessionDocument = sessions.findOne(new BasicDBObject("id", oldSessionId));
            if (sessionDocument != null)
                return false;
            
            sessionDocument = sessions.findOne(new BasicDBObject("id", newSessionId));
            return (sessionDocument != null);
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
