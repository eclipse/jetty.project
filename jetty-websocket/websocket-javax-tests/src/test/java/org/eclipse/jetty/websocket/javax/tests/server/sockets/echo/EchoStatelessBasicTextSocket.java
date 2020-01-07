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

package org.eclipse.jetty.websocket.javax.tests.server.sockets.echo;

import java.io.IOException;
import javax.websocket.CloseReason;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/echo/text/basic/stateless")
public class EchoStatelessBasicTextSocket
{
    @OnMessage
    public void onText(Session session, String msg)
    {
        try
        {
            session.getBasicRemote().sendText(msg);
        }
        catch (IOException esend)
        {
            esend.printStackTrace(System.err);
            try
            {
                session.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(4001), "Unable to echo msg"));
            }
            catch (IOException eclose)
            {
                eclose.printStackTrace();
            }
        }
    }
}
