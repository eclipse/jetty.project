package org.eclipse.jetty.spdy.http;

import java.util.Set;

import org.eclipse.jetty.spdy.api.Headers;
import org.eclipse.jetty.spdy.api.SPDY;
import org.eclipse.jetty.spdy.api.Session;
import org.eclipse.jetty.spdy.api.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReferrerPushStrategyUnitTest
{
    public static final short VERSION = SPDY.V3;
    public static final String SCHEME = "http";
    public static final String HOST = "localhost";
    public static final String MAIN_URI = "/index.html";
    public static final String METHOD = "GET";

    // class under test
    private ReferrerPushStrategy referrerPushStrategy;

    @Mock
    Stream stream;
    @Mock
    Session session;


    @Before
    public void setup()
    {
        referrerPushStrategy = new ReferrerPushStrategy();
    }

    @Test
    public void testReferrerCallsAfterTimeoutAreNotAddedAsPushResources() throws InterruptedException
    {
        Headers requestHeaders = new Headers();
        requestHeaders.put(HTTPSPDYHeader.SCHEME.name(VERSION), SCHEME);
        requestHeaders.put(HTTPSPDYHeader.HOST.name(VERSION), HOST);
        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), MAIN_URI);
        requestHeaders.put(HTTPSPDYHeader.METHOD.name(VERSION), METHOD);
        int referrerCallTimeout = 1000;
        referrerPushStrategy.setReferrerPushPeriod(referrerCallTimeout);

        when(stream.getSession()).thenReturn(session);
        when(session.getVersion()).thenReturn(VERSION);
        Set<String> pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        assertThat("pushResources is empty", pushResources.size(), is(0));

        String origin = SCHEME + "://" + HOST;
        String referrerUrl = origin + MAIN_URI;

        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), "image.jpg");
        requestHeaders.put("referer", referrerUrl);
        pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        assertThat("pushResources is empty", pushResources.size(), is(0));

        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), "style.css");
        pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        assertThat("pushResources is empty", pushResources.size(), is(0));

        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), MAIN_URI);
        pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        assertThat("pushResources contains two elements image.jpg and style.css", pushResources.size(), is(2));

        // sleep to pretend that the user manually clicked on a linked resource instead the browser requesting subresources immediately
        Thread.sleep(referrerCallTimeout + 1);

        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), "image2.jpg");
        requestHeaders.put("referer", referrerUrl);
        pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        assertThat("pushResources is empty", pushResources.size(), is(0));

        requestHeaders.put(HTTPSPDYHeader.URI.name(VERSION), MAIN_URI);
        pushResources = referrerPushStrategy.apply(stream, requestHeaders, new Headers());
        // as the image2.jpg request has been a link and not a subresource, we expect that pushResources.size() is still 2
        assertThat("pushResources contains two elements image.jpg and style.css", pushResources.size(), is(2));
    }
}
