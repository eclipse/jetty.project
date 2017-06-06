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

package org.eclipse.jetty.websocket.tests;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.PongFrame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test various invalid frame situations
 */
@RunWith(value = Parameterized.class)
public class GeneratorFrameFlagsTest
{
    private static ByteBufferPool bufferPool = new MappedByteBufferPool();
    
    @Parameters
    public static Collection<WebSocketFrame[]> data()
    {
        List<WebSocketFrame[]> data = new ArrayList<>();
        data.add(new WebSocketFrame[]{new PingFrame().setFin(false)});
        data.add(new WebSocketFrame[]{new PingFrame().setRsv1(true)});
        data.add(new WebSocketFrame[]{new PingFrame().setRsv2(true)});
        data.add(new WebSocketFrame[]{new PingFrame().setRsv3(true)});
        data.add(new WebSocketFrame[]{new PongFrame().setFin(false)});
        data.add(new WebSocketFrame[]{new PingFrame().setRsv1(true)});
        data.add(new WebSocketFrame[]{new PongFrame().setRsv2(true)});
        data.add(new WebSocketFrame[]{new PongFrame().setRsv3(true)});
        data.add(new WebSocketFrame[]{new CloseInfo().asFrame().setFin(false)});
        data.add(new WebSocketFrame[]{new CloseInfo().asFrame().setRsv1(true)});
        data.add(new WebSocketFrame[]{new CloseInfo().asFrame().setRsv2(true)});
        data.add(new WebSocketFrame[]{new CloseInfo().asFrame().setRsv3(true)});
        return data;
    }
    
    private WebSocketFrame invalidFrame;
    
    public GeneratorFrameFlagsTest(WebSocketFrame invalidFrame)
    {
        this.invalidFrame = invalidFrame;
    }
    
    @Test(expected = ProtocolException.class)
    public void testGenerateInvalidControlFrame()
    {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        new Generator(WebSocketPolicy.newServerPolicy(), bufferPool).generateWholeFrame(invalidFrame, buffer);
    }
}
