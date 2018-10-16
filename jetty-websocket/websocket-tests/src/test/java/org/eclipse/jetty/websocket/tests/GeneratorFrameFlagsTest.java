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

package org.eclipse.jetty.websocket.tests;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.Generator;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.PongFrame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test various invalid frame situations
 */
public class GeneratorFrameFlagsTest
{
    private static ByteBufferPool bufferPool = new MappedByteBufferPool();
    

    public static Stream<Arguments> badFrames()
    {
        return Stream.of(
                new PingFrame().setFin(false),
                new PingFrame().setRsv1(true),
                new PingFrame().setRsv2(true),
                new PingFrame().setRsv3(true),
                new PongFrame().setFin(false),
                new PingFrame().setRsv1(true),
                new PongFrame().setRsv2(true),
                new PongFrame().setRsv3(true),
                new CloseInfo().asFrame().setFin(false),
                new CloseInfo().asFrame().setRsv1(true),
                new CloseInfo().asFrame().setRsv2(true),
                new CloseInfo().asFrame().setRsv3(true))
                .map(Arguments::of);
    }


    @ParameterizedTest
    @MethodSource("badFrames")
    public void testGenerateInvalidControlFrame(WebSocketFrame invalidFrame)
    {
        assertThrows(ProtocolException.class, () -> {
            ByteBuffer buffer = ByteBuffer.allocate( 100);
            new Generator( WebSocketPolicy.newServerPolicy(), bufferPool).generateWholeFrame( invalidFrame, buffer);
        });
    }
}
