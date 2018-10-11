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

package org.eclipse.jetty.websocket.common;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.common.message.MessageWriter;
import org.eclipse.jetty.websocket.core.TestableLeakTrackingBufferPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MessageWriterTest
{
    private static final Logger LOG = Log.getLogger(MessageWriterTest.class);
    private static final int OUTPUT_BUFFER_SIZE = 4096;

    public TestableLeakTrackingBufferPool bufferPool = new TestableLeakTrackingBufferPool("Test");

    @AfterEach
    public void afterEach()
    {
        bufferPool.assertNoLeaks();
    }

    private OutgoingMessageCapture remoteSocket;

    @BeforeEach
    public void setupSession()
    {
        remoteSocket = new OutgoingMessageCapture();
    }
    
    @Test
    public void testMultipleWrites() throws Exception
    {
        try (MessageWriter stream = new MessageWriter(remoteSocket, OUTPUT_BUFFER_SIZE))
        {
            stream.write("Hello");
            stream.write(" ");
            stream.write("World");
        }

        assertThat("Socket.messageQueue.size",remoteSocket.textMessages.size(),is(1));
        String msg = remoteSocket.textMessages.poll();
        assertThat("Message",msg,is("Hello World"));
    }
    
    @Test
    public void testSingleWrite() throws Exception
    {
        try (MessageWriter stream = new MessageWriter(remoteSocket, OUTPUT_BUFFER_SIZE))
        {
            stream.append("Hello World");
        }

        assertThat("Socket.messageQueue.size",remoteSocket.textMessages.size(),is(1));
        String msg = remoteSocket.textMessages.poll();
        assertThat("Message",msg,is("Hello World"));
    }
    
    @Test
    public void testWriteLarge_RequiringMultipleBuffers() throws Exception
    {
        int size = (int)(OUTPUT_BUFFER_SIZE * 2.5);
        char buf[] = new char[size];
        if (LOG.isDebugEnabled())
            LOG.debug("Buffer size: {}",size);
        Arrays.fill(buf,'x');
        buf[size - 1] = 'o'; // mark last entry for debugging

        try (MessageWriter stream = new MessageWriter(remoteSocket, OUTPUT_BUFFER_SIZE))
        {
            stream.write(buf);
        }

        assertThat("Socket.messageQueue.size",remoteSocket.textMessages.size(),is(1));
        String msg = remoteSocket.textMessages.poll();
        String expected = new String(buf);
        assertThat("Message",msg,is(expected));
    }
}
