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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TrackingFrameHandler implements FrameHandler
{
    private final Logger LOG;

    public CountDownLatch openLatch = new CountDownLatch(1);
    public CountDownLatch closeLatch = new CountDownLatch(1);
    public CountDownLatch errorLatch = new CountDownLatch(1);
    public AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
    public AtomicReference<Throwable> closeStack = new AtomicReference<>();
    public AtomicReference<Throwable> error = new AtomicReference<>();
    public BlockingQueue<Frame> framesQueue = new LinkedBlockingDeque<>();

    public TrackingFrameHandler(String id)
    {
        LOG = Log.getLogger(this.getClass().getName() + "." + id);
        LOG.debug("init");
    }

    public void assertCloseStatus(String prefix, int expectedCloseStatusCode, Matcher<? super String> reasonMatcher) throws InterruptedException
    {
        CloseStatus close = closeStatus.get();
        assertThat(prefix + " close info", close, Matchers.notNullValue());
        assertThat(prefix + " received close code", close.getCode(), Matchers.is(expectedCloseStatusCode));
        assertThat(prefix + " received close reason", close.getReason(), reasonMatcher);
    }

    public void assertErrorEvent(String prefix, Matcher<Throwable> throwableMatcher, Matcher<? super String> messageMatcher)
    {
        assertThat(prefix + " error event type", error.get(), throwableMatcher);
        assertThat(prefix + " error event message", error.get().getMessage(), messageMatcher);
    }

    public void awaitClosedEvent(String prefix) throws InterruptedException
    {
        assertTrue(closeLatch.await(Defaults.CLOSE_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS),prefix + " onClosed event should have occurred");
    }

    public void awaitOpenEvent(String prefix) throws InterruptedException
    {
        assertTrue(openLatch.await(Defaults.OPEN_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS),prefix + " onOpen event should have occurred");
    }

    public void awaitErrorEvent(String prefix) throws InterruptedException
    {
        assertTrue(errorLatch.await(Defaults.CLOSE_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS),prefix + " onError event should have occurred");
    }

    @Override
    public void onOpen(CoreSession coreSession) throws Exception
    {
        openLatch.countDown();
    }

    @Override
    public void onReceiveFrame(Frame frame, Callback callback)
    {
        framesQueue.offer(Frame.copy(frame));
        callback.succeeded();
    }

    @Override
    public void onClosed(CloseStatus close)
    {
        if(LOG.isDebugEnabled())
        {
            LOG.debug("onClosed({})", close);
        }

        if (closeStatus.compareAndSet(null, close) == false)
        {
            // Notify of websocket-core regression
            LOG.warn("onClose should only happen once - Original Close: " + closeStatus.get(), closeStack.get());
            LOG.warn("onClose should only happen once - Extra/Excess Close: " + close, new Throwable("extra(excess) close"));
            fail("onClose should only happen once!");
        }
        closeStack.compareAndSet(null, new Throwable("original"));
        closeLatch.countDown();
    }

    @Override
    public void onError(Throwable cause) throws Exception
    {
        if(LOG.isDebugEnabled())
        {
            LOG.debug("onError()", cause);
        }
        assertThat("Error must have value", cause, notNullValue());
        if (error.compareAndSet(null, cause) == false)
        {
            // Notify of websocket-core regression
            LOG.warn("onError should only happen once - Original Cause", error.get());
            LOG.warn("onError should only happen once - Extra/Excess Cause", cause);
            fail("onError should only happen once!");
        }
        this.errorLatch.countDown();
    }
}
