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

package org.eclipse.jetty.websocket.jsr356.messages;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.jsr356.CompletableFutureCallback;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DecodedTextStreamMessageSinkTest extends AbstractMessageSinkTest
{
    public final static TimeZone GMT = TimeZone.getTimeZone("GMT");

    @Test
    public void testDate_1_Frame() throws Exception
    {
        CompletableFuture<Date> copyFuture = new CompletableFuture<>();
        DecodedDateCopy copy = new DecodedDateCopy(copyFuture);
        MethodHandle copyHandle = getAcceptHandle(copy, Date.class);
        Decoder.TextStream<Date> decoder = new GmtDecoder();
        DecodedTextStreamMessageSink sink = new DecodedTextStreamMessageSink(session, decoder, copyHandle);

        CompletableFutureCallback finCallback = new CompletableFutureCallback();
        sink.accept(new Frame(OpCode.TEXT).setPayload("2018.02.13").setFin(true), finCallback);

        finCallback.get(1, TimeUnit.SECONDS); // wait for callback
        Date decoded = copyFuture.get(1, TimeUnit.SECONDS);
        assertThat("FinCallback.done", finCallback.isDone(), is(true));
        assertThat("Decoded.contents", format(decoded, "MM-dd-yyyy"), is("02-13-2018"));
    }

    @Test
    public void testDate_3_Frames() throws Exception
    {
        CompletableFuture<Date> copyFuture = new CompletableFuture<>();
        DecodedDateCopy copy = new DecodedDateCopy(copyFuture);
        MethodHandle copyHandle = getAcceptHandle(copy, Date.class);
        Decoder.TextStream<Date> decoder = new GmtDecoder();
        DecodedTextStreamMessageSink sink = new DecodedTextStreamMessageSink(session, decoder, copyHandle);

        CompletableFutureCallback callback1 = new CompletableFutureCallback();
        CompletableFutureCallback callback2 = new CompletableFutureCallback();
        CompletableFutureCallback finCallback = new CompletableFutureCallback();

        sink.accept(new Frame(OpCode.TEXT).setPayload("2023").setFin(false), callback1);
        sink.accept(new Frame(OpCode.CONTINUATION).setPayload(".08").setFin(false), callback2);
        sink.accept(new Frame(OpCode.CONTINUATION).setPayload(".22").setFin(true), finCallback);

        finCallback.get(1, TimeUnit.SECONDS); // wait for callback
        Date decoded = copyFuture.get(1, TimeUnit.SECONDS);
        assertThat("Callback1.done", callback1.isDone(), is(true));
        assertThat("Callback2.done", callback2.isDone(), is(true));
        assertThat("finCallback.done", finCallback.isDone(), is(true));

        assertThat("Decoded.contents", format(decoded, "MM-dd-yyyy"), is("08-22-2023"));
    }

    private String format(Date date, String formatPattern)
    {
        SimpleDateFormat format = new SimpleDateFormat(formatPattern);
        format.setTimeZone(GMT);
        return format.format(date);
    }

    public static class DecodedDateCopy implements Consumer<Date>
    {
        private final CompletableFuture<Date> copyFuture;

        public DecodedDateCopy(CompletableFuture<Date> copyFuture)
        {
            this.copyFuture = copyFuture;
        }

        @Override
        public void accept(Date date)
        {
            copyFuture.complete(date);
        }
    }

    @SuppressWarnings("Duplicates")
    public static class GmtDecoder implements Decoder.TextStream<Date>
    {

        @Override
        public Date decode(Reader reader) throws DecodeException
        {
            String content;
            try
            {
                content = IO.toString(reader);
            }
            catch (IOException e)
            {
                throw new DecodeException("", "Unable to read from Reader", e);
            }

            try
            {

                SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
                format.setTimeZone(GMT);
                return format.parse(content);
            }
            catch (ParseException e)
            {
                throw new DecodeException(content, e.getMessage(), e);
            }
        }

        @Override
        public void destroy()
        {
        }

        @Override
        public void init(EndpointConfig config)
        {
        }
    }
}
