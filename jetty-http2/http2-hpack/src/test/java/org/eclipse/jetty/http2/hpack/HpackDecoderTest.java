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


package org.eclipse.jetty.http2.hpack;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Iterator;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.hpack.HpackException.StreamException;
import org.eclipse.jetty.util.TypeUtil;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class HpackDecoderTest
{
    @Test
    public void testDecodeD_3() throws Exception
    {
        HpackDecoder decoder = new HpackDecoder(4096,8192);

        // First request
        String encoded="828684410f7777772e6578616d706c652e636f6d";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        MetaData.Request request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        assertFalse(request.iterator().hasNext());

        // Second request
        encoded="828684be58086e6f2d6361636865";
        buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        Iterator<HttpField> iterator=request.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(new HttpField("cache-control","no-cache"),iterator.next());
        assertFalse(iterator.hasNext());

        // Third request
        encoded="828785bf400a637573746f6d2d6b65790c637573746f6d2d76616c7565";
        buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET",request.getMethod());
        assertEquals(HttpScheme.HTTPS.asString(),request.getURI().getScheme());
        assertEquals("/index.html",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        iterator=request.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(new HttpField("custom-key","custom-value"),iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testDecodeD_4() throws Exception
    {
        HpackDecoder decoder = new HpackDecoder(4096,8192);

        // First request
        String encoded="828684418cf1e3c2e5f23a6ba0ab90f4ff";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        MetaData.Request request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        assertFalse(request.iterator().hasNext());

        // Second request
        encoded="828684be5886a8eb10649cbf";
        buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        Iterator<HttpField> iterator=request.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(new HttpField("cache-control","no-cache"),iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testDecodeWithArrayOffset() throws Exception
    {
        String value = "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==";

        HpackDecoder decoder = new HpackDecoder(4096,8192);
        String encoded = "8682418cF1E3C2E5F23a6bA0Ab90F4Ff841f0822426173696320515778685a475270626a70766347567549484e6c633246745a513d3d";
        byte[] bytes = TypeUtil.fromHexString(encoded);
        byte[] array = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, array, 1, bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(array, 1, bytes.length).slice();

        MetaData.Request request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        assertEquals(1,request.getFields().size());
        HttpField field = request.iterator().next();
        assertEquals(HttpHeader.AUTHORIZATION, field.getHeader());
        assertEquals(value, field.getValue());
    }

    @Test
    public void testDecodeHuffmanWithArrayOffset() throws Exception
    {
        HpackDecoder decoder = new HpackDecoder(4096,8192);

        String encoded="8286418cf1e3c2e5f23a6ba0ab90f4ff84";
        byte[] bytes = TypeUtil.fromHexString(encoded);
        byte[] array = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, array, 1, bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(array, 1, bytes.length).slice();

        MetaData.Request request = (MetaData.Request)decoder.decode(buffer);

        assertEquals("GET", request.getMethod());
        assertEquals(HttpScheme.HTTP.asString(),request.getURI().getScheme());
        assertEquals("/",request.getURI().getPath());
        assertEquals("www.example.com",request.getURI().getHost());
        assertFalse(request.iterator().hasNext());
    }
    
    @Test
    public void testNghttpx() throws Exception
    {        
        // Response encoded by nghttpx
        String encoded="886196C361Be940b6a65B6850400B8A00571972e080a62D1Bf5f87497cA589D34d1f9a0f0d0234327690Aa69D29aFcA954D3A5358980Ae112e0f7c880aE152A9A74a6bF3";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        HpackDecoder decoder = new HpackDecoder(4096,8192);
        MetaData.Response response = (MetaData.Response)decoder.decode(buffer);

        assertThat(response.getStatus(),is(200));
        assertThat(response.getFields().size(),is(6));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.DATE,"Fri, 15 Jul 2016 02:36:20 GMT")));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.CONTENT_TYPE,"text/html")));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.CONTENT_ENCODING,"")));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.CONTENT_LENGTH,"42")));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.SERVER,"nghttpx nghttp2/1.12.0")));
        assertTrue(response.getFields().contains(new HttpField(HttpHeader.VIA,"1.1 nghttpx")));
    }
    
    @Test
    public void testResize() throws Exception
    {
        String encoded = "3f6166871e33A13a47497f205f8841E92b043d492d49";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));
        HpackDecoder decoder = new HpackDecoder(4096, 8192);
        MetaData metaData = decoder.decode(buffer);
        assertThat(metaData.getFields().get(HttpHeader.HOST),is("aHostName"));
        assertThat(metaData.getFields().get(HttpHeader.CONTENT_TYPE),is("some/content"));
        assertThat(decoder.getHpackContext().getDynamicTableSize(),is(0));
    }
    
    @Test
    public void testTooBigToIndex() throws Exception
    {
        String encoded = "44FfEc02Df3990A190A0D4Ee5b3d2940Ec98Aa4a62D127D29e273a0aA20dEcAa190a503b262d8a2671D4A2672a927aA874988a2471D05510750c951139EdA2452a3a548cAa1aA90bE4B228342864A9E0D450A5474a92992a1aA513395448E3A0Aa17B96cFe3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f3f14E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F3E7Cf9f3e7cF9F353F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F54f";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        HpackDecoder decoder = new HpackDecoder(128,8192);
        MetaData metaData = decoder.decode(buffer);
        
        assertThat(decoder.getHpackContext().getDynamicTableSize(),is(0));
        assertThat(((MetaData.Request)metaData).getURI().toString(),Matchers.startsWith("This is a very large field"));
    }

    @Test
    public void testUnknownIndex() throws Exception
    {
        String encoded = "BE";
        ByteBuffer buffer = ByteBuffer.wrap(TypeUtil.fromHexString(encoded));

        HpackDecoder decoder = new HpackDecoder(128,8192);
        try
        {
            decoder.decode(buffer);
            Assert.fail();
        }
        catch (HpackException.SessionException e)
        {
            assertThat(e.getMessage(),Matchers.startsWith("Unknown index"));
        }
    
    }
    
    /* 8.1.2.1. Pseudo-Header Fields */
    @Test()
    public void test8_1_2_1_PsuedoHeaderFields() throws Exception
    {
        // 1:Sends a HEADERS frame that contains a unknown pseudo-header field
        MetaDataBuilder mdb = new MetaDataBuilder(4096);
        mdb.emit(new HttpField(":unknown","value"));
        try
        {
            mdb.build();
            Assert.fail();
        }
        catch(StreamException ex)
        {
            Assert.assertThat(ex.getMessage(),Matchers.containsString("Unknown pseudo header"));
        }
        
        // 2: Sends a HEADERS frame that contains the pseudo-header field defined for response
        mdb = new MetaDataBuilder(4096);
        mdb.emit(new HttpField(HttpHeader.C_SCHEME,"http"));
        mdb.emit(new HttpField(HttpHeader.C_METHOD,"GET"));
        mdb.emit(new HttpField(HttpHeader.C_PATH,"/path"));
        mdb.emit(new HttpField(HttpHeader.C_STATUS,"100"));
        try
        {
            mdb.build();
            Assert.fail();
        }
        catch(StreamException ex)
        {
            Assert.assertThat(ex.getMessage(),Matchers.containsString("Request and Response headers"));
        }

        // 3: Sends a HEADERS frame that contains a pseudo-header field as trailers
        
        // 4: Sends a HEADERS frame that contains a pseudo-header field that appears in a header block after a regular header field
        mdb = new MetaDataBuilder(4096);
        mdb.emit(new HttpField(HttpHeader.C_SCHEME,"http"));
        mdb.emit(new HttpField(HttpHeader.C_METHOD,"GET"));
        mdb.emit(new HttpField(HttpHeader.C_PATH,"/path"));
        mdb.emit(new HttpField("Accept","No Compromise"));
        mdb.emit(new HttpField(HttpHeader.C_AUTHORITY,"localhost"));
        try
        {
            mdb.build();
            Assert.fail();
        }
        catch(StreamException ex)
        {
            Assert.assertThat(ex.getMessage(),Matchers.containsString("Pseudo header :authority after fields"));
        }
    }
    
    /*
     * 
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
              ✔ 3: Sends a HEADERS frame that contains a pseudo-header field as trailers
                 
              × 4: Sends a HEADERS frame that contains a pseudo-header field that appears in a header block after a regular header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   
                   */
    
    
                   
                   /*
            8.1.2.2. Connection-Specific Header Fields
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:33, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 1: Sends a HEADERS frame that contains the connection-specific header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:44, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 2: Sends a HEADERS frame that contains the TE header field with any value other than "trailers"
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)

            8.1.2.3. Request Pseudo-Header Fields
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:16, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:23, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:50, flags:0x01, stream_id:1)
                   [recv] RST_STREAM Frame (length:4, flags:0x00, stream_id:1)
                   [recv] Timeout
              × 1: Sends a HEADERS frame with empty ":path" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:50, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:13, flags:0x05, stream_id:1)
                   [recv] Timeout
              × 2: Sends a HEADERS frame that omits ":method" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: Timeout
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:14, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:100, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 3: Sends a HEADERS frame that omits ":scheme" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:14, flags:0x05, stream_id:1)
                   [recv] GOAWAY Frame (length:20, flags:0x00, stream_id:0)
              ✔ 4: Sends a HEADERS frame that omits ":path" pseudo-header field
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:16, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 5: Sends a HEADERS frame with duplicated ":method" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:16, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 6: Sends a HEADERS frame with duplicated ":scheme" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:18, flags:0x05, stream_id:1)
                   [recv] HEADERS Frame (length:79, flags:0x05, stream_id:1)
                   [recv] Timeout
              × 7: Sends a HEADERS frame with duplicated ":method" pseudo-header field
                -> The endpoint MUST respond with a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: HEADERS Frame (length:79, flags:0x05, stream_id:1)

            8.1.2.6. Malformed Requests and Responses
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:18, flags:0x04, stream_id:1)
                   [send] DATA Frame (length:4, flags:0x01, stream_id:1)
                   [recv] HEADERS Frame (length:100, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 1: Sends a HEADERS frame with the "content-length" header field which does not equal the DATA frame payload length
                -> The endpoint MUST treat this as a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
                   [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                   [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                   [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                   [send] HEADERS Frame (length:18, flags:0x04, stream_id:1)
                   [send] DATA Frame (length:4, flags:0x00, stream_id:1)
                   [send] DATA Frame (length:4, flags:0x01, stream_id:1)
                   [recv] HEADERS Frame (length:100, flags:0x04, stream_id:1)
                   [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
                   [recv] Timeout
              × 2: Sends a HEADERS frame with the "content-length" header field which does not equal the sum of the multiple DATA frames payload length
                -> The endpoint MUST treat this as a stream error of type PROTOCOL_ERROR.
                   Expected: GOAWAY Frame (Error Code: PROTOCOL_ERROR)
                             RST_STREAM Frame (Error Code: PROTOCOL_ERROR)
                             Connection closed
                     Actual: DATA Frame (length:687, flags:0x01, stream_id:1)

        8.2. Server Push
               [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
               [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [send] PUSH_PROMISE Frame (length:19, flags:0x04, stream_id:1)
               [recv] GOAWAY Frame (length:20, flags:0x00, stream_id:0)
          ✔ 1: Sends a PUSH_PROMISE frame

    HPACK: Header Compression for HTTP/2
      2. Compression Process Overview
        2.3. Indexing Tables
          2.3.3. Index Address Space
                 [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
                 [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
                 [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                 [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
                 [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
                 [send] HEADERS Frame (length:16, flags:0x05, stream_id:1)
                 [recv] GOAWAY Frame (length:20, flags:0x00, stream_id:0)
                 [recv] Connection closed
            ✔ 1: Sends a header field representation with invalid index

      4. Dynamic Table Management
        4.2. Maximum Table Size
               [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
               [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [send] HEADERS Frame (length:16, flags:0x05, stream_id:1)
               [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
               [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
               [recv] Timeout
          × 1: Sends a dynamic table size update at the end of header block
            -> The endpoint MUST treat this as a decoding error.
               Expected: GOAWAY Frame (Error Code: COMPRESSION_ERROR)
                         Connection closed
                 Actual: DATA Frame (length:687, flags:0x01, stream_id:1)

      5. Primitive Type Representations
        5.2. String Literal Representation
               [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
               [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [send] HEADERS Frame (length:27, flags:0x05, stream_id:1)
               [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
               [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
               [recv] Timeout
          × 1: Sends a Huffman-encoded string literal representation with padding longer than 7 bits
            -> The endpoint MUST treat this as a decoding error.
               Expected: GOAWAY Frame (Error Code: COMPRESSION_ERROR)
                         Connection closed
                 Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
               [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
               [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [send] HEADERS Frame (length:26, flags:0x05, stream_id:1)
               [recv] HEADERS Frame (length:101, flags:0x04, stream_id:1)
               [recv] DATA Frame (length:687, flags:0x01, stream_id:1)
               [recv] Timeout
          × 2: Sends a Huffman-encoded string literal representation padded by zero
            -> The endpoint MUST treat this as a decoding error.
               Expected: GOAWAY Frame (Error Code: COMPRESSION_ERROR)
                         Connection closed
                 Actual: DATA Frame (length:687, flags:0x01, stream_id:1)
               [send] SETTINGS Frame (length:6, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:24, flags:0x00, stream_id:0)
               [send] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [recv] WINDOW_UPDATE Frame (length:4, flags:0x00, stream_id:0)
               [recv] SETTINGS Frame (length:0, flags:0x01, stream_id:0)
               [send] HEADERS Frame (length:28, flags:0x05, stream_id:1)
               [recv] GOAWAY Frame (length:20, flags:0x00, stream_id:0)
               [recv] Connection closed
          ✔ 3: Sends a Huffman-encoded string literal representation containing the EOS symbol

    */
    
}
