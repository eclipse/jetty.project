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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.ByteBufferAssert;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.hamcrest.Matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public interface Fuzzer extends AutoCloseable
{
    ByteBuffer asNetworkBuffer(List<Frame> frames);

    /**
     * For some Fuzzers implementations, this triggers a send EOF.
     * TODO: should probably be called shutdownOutput()
     */
    void eof();

    /**
     * Assert that the provided expected WebSocketFrames are what was received
     * from the remote.
     *
     * @param frames the expected frames
     * @throws InterruptedException
     */
    void expect(List<Frame> frames) throws InterruptedException;

    BlockingQueue<Frame> getOutputFrames();

    /**
     * Send raw bytes
     *
     * @param buffer the buffer
     */
    void send(ByteBuffer buffer) throws IOException;

    /**
     * Send some of the raw bytes
     *
     * @param buffer the buffer
     * @param length the number of bytes to send from buffer
     */
    void send(ByteBuffer buffer, int length) throws IOException;

    /**
     * Generate a single ByteBuffer representing the entire
     * list of generated frames, and send it as a single
     * buffer
     *
     * @param frames the list of frames to send
     */
    void sendBulk(List<Frame> frames) throws IOException;

    /**
     * Generate a ByteBuffer for each frame, and send each as
     * unique buffer containing each frame.
     *
     * @param frames the list of frames to send
     */
    void sendFrames(List<Frame> frames) throws IOException;

    /**
     * Generate a ByteBuffer for each frame, and send each as
     * unique buffer containing each frame.
     *
     * @param frames the list of frames to send
     */
    void sendFrames(Frame... frames) throws IOException;

    /**
     * Generate a single ByteBuffer representing the entire list
     * of generated frames, and send segments of {@code segmentSize}
     * to remote as individual buffers.
     *
     * @param frames the list of frames to send
     * @param segmentSize the size of each segment to send
     */
    void sendSegmented(List<Frame> frames, int segmentSize) throws IOException;

    abstract class Adapter
    {
        protected final Logger LOG;

        public Adapter()
        {
            LOG = Log.getLogger(this.getClass());
        }

        @SuppressWarnings("Duplicates")
        public void assertExpected(BlockingQueue<Frame> framesQueue, List<Frame> expect) throws InterruptedException
        {
            int expectedCount = expect.size();

            String prefix;
            for (int i = 0; i < expectedCount; i++)
            {
                prefix = "Frame[" + i + "]";

                Frame expected = expect.get(i);
                if (LOG.isDebugEnabled())
                {
                    LOG.debug("assertExpected() - {} poll", prefix);
                }

                Frame actual = framesQueue.poll(3, TimeUnit.SECONDS);
                assertThat(prefix + ".poll", actual, notNullValue());

                if(LOG.isDebugEnabled())
                {
                    if (actual.getOpCode() == OpCode.CLOSE)
                        LOG.debug("{} CloseFrame: {}", prefix, new CloseStatus(actual.getPayload()));
                    else
                        LOG.debug("{} {}", prefix, actual);
                }

                assertThat(prefix + ".opcode", OpCode.name(actual.getOpCode()), Matchers.is(OpCode.name(expected.getOpCode())));
                prefix += "(op=" + actual.getOpCode() + "," + (actual.isFin() ? "" : "!") + "fin)";
                if (expected.getOpCode() == OpCode.CLOSE)
                {
                    CloseStatus expectedClose = new CloseStatus(expected.getPayload());
                    CloseStatus actualClose = new CloseStatus(actual.getPayload());
                    assertThat(prefix + ".code", actualClose.getCode(), Matchers.is(expectedClose.getCode()));
                }
                else if (expected.hasPayload())
                {
                    if (expected.getOpCode() == OpCode.TEXT)
                    {
                        String expectedText = expected.getPayloadAsUTF8();
                        String actualText = actual.getPayloadAsUTF8();
                        assertThat(prefix + ".text-payload", actualText, is(expectedText));
                    }
                    else
                    {
                        assertThat(prefix + ".payloadLength", actual.getPayloadLength(), Matchers.is(expected.getPayloadLength()));
                        ByteBufferAssert.assertEquals(prefix + ".payload", expected.getPayload(), actual.getPayload());
                    }
                }
                else
                {
                    assertThat(prefix + ".payloadLength", actual.getPayloadLength(), Matchers.is(0));
                }
            }
        }
    }
}
