//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URIUtil Tests.
 */
@SuppressWarnings("SpellCheckingInspection")
public class URIUtilTest
{
    public static Stream<Arguments> encodePathSource()
    {
        return Stream.of(
            Arguments.of("/foo%23+;,:=/b a r/?info ", "/foo%2523+%3B,:=/b%20a%20r/%3Finfo%20"),
            Arguments.of("/context/'list'/\"me\"/;<script>window.alert('xss');</script>",
                "/context/%27list%27/%22me%22/%3B%3Cscript%3Ewindow.alert(%27xss%27)%3B%3C/script%3E"),
            Arguments.of("test\u00f6?\u00f6:\u00df", "test%C3%B6%3F%C3%B6:%C3%9F"),
            Arguments.of("test?\u00f6?\u00f6:\u00df", "test%3F%C3%B6%3F%C3%B6:%C3%9F")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("encodePathSource")
    public void testEncodePath(String rawPath, String expectedEncoded)
    {
        // test basic encode/decode
        StringBuilder buf = new StringBuilder();
        buf.setLength(0);
        URIUtil.encodePath(buf, rawPath);
        assertEquals(expectedEncoded, buf.toString());
    }

    @Test
    public void testEncodeString()
    {
        StringBuilder buf = new StringBuilder();
        buf.setLength(0);
        URIUtil.encodeString(buf, "foo%23;,:=b a r", ";,= ");
        assertEquals("foo%2523%3b%2c:%3db%20a%20r", buf.toString());
    }

    public static Stream<Arguments> decodePathSource()
    {
        List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of("/foo/bar", "/foo/bar"));

        arguments.add(Arguments.of("/f%20o/b%20r", "/f o/b r"));
        arguments.add(Arguments.of("fää%2523%3b%2c:%3db%20a%20r%3D", "f\u00e4\u00e4%23;,:=b a r="));
        arguments.add(Arguments.of("f%d8%a9%d8%a9%2523%3b%2c:%3db%20a%20r", "f\u0629\u0629%23;,:=b a r"));

        // path parameters should be ignored
        arguments.add(Arguments.of("/foo;ignore/bar;ignore", "/foo/bar"));
        arguments.add(Arguments.of("/f\u00e4\u00e4;ignore/bar;ignore", "/fää/bar"));
        arguments.add(Arguments.of("/f%d8%a9%d8%a9%2523;ignore/bar;ignore", "/f\u0629\u0629%23/bar"));
        arguments.add(Arguments.of("foo%2523%3b%2c:%3db%20a%20r;rubbish", "foo%23;,:=b a r"));

        // Test for null character (real world ugly test case)
        byte[] oddBytes = {'/', 0x00, '/'};
        String odd = new String(oddBytes, StandardCharsets.ISO_8859_1);
        assertEquals(odd, URIUtil.decodePath("/%00/"));
        arguments.add(Arguments.of("/%00/", odd));

        return arguments.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("decodePathSource")
    public void testDecodePath(String encodedPath, String expectedPath)
    {
        String path = URIUtil.decodePath(encodedPath);
        assertEquals(expectedPath, path);
    }

    @Test
    public void testDecodePathSubstring()
    {
        String path = URIUtil.decodePath("xx/foo/barxx", 2, 8);
        assertEquals("/foo/bar", path);

        path = URIUtil.decodePath("xxx/foo/bar%2523%3b%2c:%3db%20a%20r%3Dxxx;rubbish", 3, 35);
        assertEquals("/foo/bar%23;,:=b a r=", path);
    }

    public static Stream<Arguments> addEncodedPathsSource()
    {
        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, "", ""),
            Arguments.of(null, "bbb", "bbb"),
            Arguments.of(null, "/", "/"),
            Arguments.of(null, "/bbb", "/bbb"),

            Arguments.of("", null, ""),
            Arguments.of("", "", ""),
            Arguments.of("", "bbb", "bbb"),
            Arguments.of("", "/", "/"),
            Arguments.of("", "/bbb", "/bbb"),

            Arguments.of("aaa", null, "aaa"),
            Arguments.of("aaa", "", "aaa"),
            Arguments.of("aaa", "bbb", "aaa/bbb"),
            Arguments.of("aaa", "/", "aaa/"),
            Arguments.of("aaa", "/bbb", "aaa/bbb"),

            Arguments.of("/", null, "/"),
            Arguments.of("/", "", "/"),
            Arguments.of("/", "bbb", "/bbb"),
            Arguments.of("/", "/", "/"),
            Arguments.of("/", "/bbb", "/bbb"),

            Arguments.of("aaa/", null, "aaa/"),
            Arguments.of("aaa/", "", "aaa/"),
            Arguments.of("aaa/", "bbb", "aaa/bbb"),
            Arguments.of("aaa/", "/", "aaa/"),
            Arguments.of("aaa/", "/bbb", "aaa/bbb"),

            Arguments.of(";JS", null, ";JS"),
            Arguments.of(";JS", "", ";JS"),
            Arguments.of(";JS", "bbb", "bbb;JS"),
            Arguments.of(";JS", "/", "/;JS"),
            Arguments.of(";JS", "/bbb", "/bbb;JS"),

            Arguments.of("aaa;JS", null, "aaa;JS"),
            Arguments.of("aaa;JS", "", "aaa;JS"),
            Arguments.of("aaa;JS", "bbb", "aaa/bbb;JS"),
            Arguments.of("aaa;JS", "/", "aaa/;JS"),
            Arguments.of("aaa;JS", "/bbb", "aaa/bbb;JS"),

            Arguments.of("aaa/;JS", null, "aaa/;JS"),
            Arguments.of("aaa/;JS", "", "aaa/;JS"),
            Arguments.of("aaa/;JS", "bbb", "aaa/bbb;JS"),
            Arguments.of("aaa/;JS", "/", "aaa/;JS"),
            Arguments.of("aaa/;JS", "/bbb", "aaa/bbb;JS"),

            Arguments.of("?A=1", null, "?A=1"),
            Arguments.of("?A=1", "", "?A=1"),
            Arguments.of("?A=1", "bbb", "bbb?A=1"),
            Arguments.of("?A=1", "/", "/?A=1"),
            Arguments.of("?A=1", "/bbb", "/bbb?A=1"),

            Arguments.of("aaa?A=1", null, "aaa?A=1"),
            Arguments.of("aaa?A=1", "", "aaa?A=1"),
            Arguments.of("aaa?A=1", "bbb", "aaa/bbb?A=1"),
            Arguments.of("aaa?A=1", "/", "aaa/?A=1"),
            Arguments.of("aaa?A=1", "/bbb", "aaa/bbb?A=1"),

            Arguments.of("aaa/?A=1", null, "aaa/?A=1"),
            Arguments.of("aaa/?A=1", "", "aaa/?A=1"),
            Arguments.of("aaa/?A=1", "bbb", "aaa/bbb?A=1"),
            Arguments.of("aaa/?A=1", "/", "aaa/?A=1"),
            Arguments.of("aaa/?A=1", "/bbb", "aaa/bbb?A=1"),

            Arguments.of(";JS?A=1", null, ";JS?A=1"),
            Arguments.of(";JS?A=1", "", ";JS?A=1"),
            Arguments.of(";JS?A=1", "bbb", "bbb;JS?A=1"),
            Arguments.of(";JS?A=1", "/", "/;JS?A=1"),
            Arguments.of(";JS?A=1", "/bbb", "/bbb;JS?A=1"),

            Arguments.of("aaa;JS?A=1", null, "aaa;JS?A=1"),
            Arguments.of("aaa;JS?A=1", "", "aaa;JS?A=1"),
            Arguments.of("aaa;JS?A=1", "bbb", "aaa/bbb;JS?A=1"),
            Arguments.of("aaa;JS?A=1", "/", "aaa/;JS?A=1"),
            Arguments.of("aaa;JS?A=1", "/bbb", "aaa/bbb;JS?A=1"),

            Arguments.of("aaa/;JS?A=1", null, "aaa/;JS?A=1"),
            Arguments.of("aaa/;JS?A=1", "", "aaa/;JS?A=1"),
            Arguments.of("aaa/;JS?A=1", "bbb", "aaa/bbb;JS?A=1"),
            Arguments.of("aaa/;JS?A=1", "/", "aaa/;JS?A=1"),
            Arguments.of("aaa/;JS?A=1", "/bbb", "aaa/bbb;JS?A=1")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}+{1}")
    @MethodSource("addEncodedPathsSource")
    public void testAddEncodedPaths(String path1, String path2, String expected)
    {
        String actual = URIUtil.addEncodedPaths(path1, path2);
        assertEquals(expected, actual, String.format("%s+%s", path1, path2));
    }

    public static Stream<Arguments> addDecodedPathsSource()
    {
        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, "", ""),
            Arguments.of(null, "bbb", "bbb"),
            Arguments.of(null, "/", "/"),
            Arguments.of(null, "/bbb", "/bbb"),

            Arguments.of("", null, ""),
            Arguments.of("", "", ""),
            Arguments.of("", "bbb", "bbb"),
            Arguments.of("", "/", "/"),
            Arguments.of("", "/bbb", "/bbb"),

            Arguments.of("aaa", null, "aaa"),
            Arguments.of("aaa", "", "aaa"),
            Arguments.of("aaa", "bbb", "aaa/bbb"),
            Arguments.of("aaa", "/", "aaa/"),
            Arguments.of("aaa", "/bbb", "aaa/bbb"),

            Arguments.of("/", null, "/"),
            Arguments.of("/", "", "/"),
            Arguments.of("/", "bbb", "/bbb"),
            Arguments.of("/", "/", "/"),
            Arguments.of("/", "/bbb", "/bbb"),

            Arguments.of("aaa/", null, "aaa/"),
            Arguments.of("aaa/", "", "aaa/"),
            Arguments.of("aaa/", "bbb", "aaa/bbb"),
            Arguments.of("aaa/", "/", "aaa/"),
            Arguments.of("aaa/", "/bbb", "aaa/bbb"),

            Arguments.of(";JS", null, ";JS"),
            Arguments.of(";JS", "", ";JS"),
            Arguments.of(";JS", "bbb", ";JS/bbb"),
            Arguments.of(";JS", "/", ";JS/"),
            Arguments.of(";JS", "/bbb", ";JS/bbb"),

            Arguments.of("aaa;JS", null, "aaa;JS"),
            Arguments.of("aaa;JS", "", "aaa;JS"),
            Arguments.of("aaa;JS", "bbb", "aaa;JS/bbb"),
            Arguments.of("aaa;JS", "/", "aaa;JS/"),
            Arguments.of("aaa;JS", "/bbb", "aaa;JS/bbb"),

            Arguments.of("aaa/;JS", null, "aaa/;JS"),
            Arguments.of("aaa/;JS", "", "aaa/;JS"),
            Arguments.of("aaa/;JS", "bbb", "aaa/;JS/bbb"),
            Arguments.of("aaa/;JS", "/", "aaa/;JS/"),
            Arguments.of("aaa/;JS", "/bbb", "aaa/;JS/bbb"),

            Arguments.of("?A=1", null, "?A=1"),
            Arguments.of("?A=1", "", "?A=1"),
            Arguments.of("?A=1", "bbb", "?A=1/bbb"),
            Arguments.of("?A=1", "/", "?A=1/"),
            Arguments.of("?A=1", "/bbb", "?A=1/bbb"),

            Arguments.of("aaa?A=1", null, "aaa?A=1"),
            Arguments.of("aaa?A=1", "", "aaa?A=1"),
            Arguments.of("aaa?A=1", "bbb", "aaa?A=1/bbb"),
            Arguments.of("aaa?A=1", "/", "aaa?A=1/"),
            Arguments.of("aaa?A=1", "/bbb", "aaa?A=1/bbb"),

            Arguments.of("aaa/?A=1", null, "aaa/?A=1"),
            Arguments.of("aaa/?A=1", "", "aaa/?A=1"),
            Arguments.of("aaa/?A=1", "bbb", "aaa/?A=1/bbb"),
            Arguments.of("aaa/?A=1", "/", "aaa/?A=1/"),
            Arguments.of("aaa/?A=1", "/bbb", "aaa/?A=1/bbb")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}+{1}")
    @MethodSource("addDecodedPathsSource")
    public void testAddDecodedPaths(String path1, String path2, String expected)
    {
        String actual = URIUtil.addPaths(path1, path2);
        assertEquals(expected, actual, String.format("%s+%s", path1, path2));
    }

    public static Stream<Arguments> compactPathSource()
    {
        return Stream.of(
            Arguments.of("/foo/bar", "/foo/bar"),
            Arguments.of("/foo/bar?a=b//c", "/foo/bar?a=b//c"),

            Arguments.of("//foo//bar", "/foo/bar"),
            Arguments.of("//foo//bar?a=b//c", "/foo/bar?a=b//c"),

            Arguments.of("/foo///bar", "/foo/bar"),
            Arguments.of("/foo///bar?a=b//c", "/foo/bar?a=b//c")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("compactPathSource")
    public void testCompactPath(String path, String expected)
    {
        String actual = URIUtil.compactPath(path);
        assertEquals(expected, actual);
    }

    public static Stream<Arguments> parentPathSource()
    {
        return Stream.of(
            Arguments.of("/aaa/bbb/", "/aaa/"),
            Arguments.of("/aaa/bbb", "/aaa/"),
            Arguments.of("/aaa/", "/"),
            Arguments.of("/aaa", "/"),
            Arguments.of("/", null),
            Arguments.of(null, null)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parentPathSource")
    public void testParentPath(String path, String expectedPath)
    {
        String actual = URIUtil.parentPath(path);
        assertEquals(expectedPath, actual, String.format("parent %s", path));
    }

    public static Stream<Arguments> equalsIgnoreEncodingStringTrueSource()
    {
        return Stream.of(
            Arguments.of("http://example.com/foo/bar", "http://example.com/foo/bar"),
            Arguments.of("/barry's", "/barry%27s"),
            Arguments.of("/barry%27s", "/barry's"),
            Arguments.of("/barry%27s", "/barry%27s"),
            Arguments.of("/b rry's", "/b%20rry%27s"),
            Arguments.of("/b rry%27s", "/b%20rry's"),
            Arguments.of("/b rry%27s", "/b%20rry%27s"),

            Arguments.of("/foo%2fbar", "/foo%2fbar"),
            Arguments.of("/foo%2fbar", "/foo%2Fbar")
        );
    }

    @ParameterizedTest
    @MethodSource("equalsIgnoreEncodingStringTrueSource")
    public void testEqualsIgnoreEncodingStringTrue(String uriA, String uriB)
    {
        assertTrue(URIUtil.equalsIgnoreEncodings(uriA, uriB));
    }

    public static Stream<Arguments> equalsIgnoreEncodingStringFalseSource()
    {
        return Stream.of(
            Arguments.of("ABC", "abc"),
            Arguments.of("/barry's", "/barry%26s"),

            Arguments.of("/foo/bar", "/foo%2fbar"),
            Arguments.of("/foo2fbar", "/foo/bar")
        );
    }

    @ParameterizedTest
    @MethodSource("equalsIgnoreEncodingStringFalseSource")
    public void testEqualsIgnoreEncodingStringFalse(String uriA, String uriB)
    {
        assertFalse(URIUtil.equalsIgnoreEncodings(uriA, uriB));
    }

    public static Stream<Arguments> equalsIgnoreEncodingURITrueSource()
    {
        return Stream.of(
            Arguments.of(
                URI.create("jar:file:/path/to/main.jar!/META-INF/versions/"),
                URI.create("jar:file:/path/to/main.jar!/META-INF/%76ersions/")
            ),
            Arguments.of(
                URI.create("JAR:FILE:/path/to/main.jar!/META-INF/versions/"),
                URI.create("jar:file:/path/to/main.jar!/META-INF/versions/")
            )
        );
    }

    @ParameterizedTest
    @MethodSource("equalsIgnoreEncodingURITrueSource")
    public void testEqualsIgnoreEncodingURITrue(URI uriA, URI uriB)
    {
        assertTrue(URIUtil.equalsIgnoreEncodings(uriA, uriB));
    }

    public static Stream<Arguments> getJarSourceStringSource()
    {
        return Stream.of(
            Arguments.of("file:///tmp/", "file:///tmp/"),
            Arguments.of("jar:file:///tmp/foo.jar", "file:///tmp/foo.jar"),
            Arguments.of("jar:file:///tmp/foo.jar!/some/path", "file:///tmp/foo.jar")
        );
    }

    @ParameterizedTest
    @MethodSource("getJarSourceStringSource")
    public void testJarSourceString(String uri, String expectedJarUri) throws Exception
    {
        assertThat(URIUtil.getJarSource(uri), is(expectedJarUri));
    }

    public static Stream<Arguments> getJarSourceURISource()
    {
        return Stream.of(
            Arguments.of(URI.create("file:///tmp/"), URI.create("file:///tmp/")),
            Arguments.of(URI.create("jar:file:///tmp/foo.jar"), URI.create("file:///tmp/foo.jar")),
            Arguments.of(URI.create("jar:file:///tmp/foo.jar!/some/path"), URI.create("file:///tmp/foo.jar"))
        );
    }

    @ParameterizedTest
    @MethodSource("getJarSourceURISource")
    public void testJarSourceURI(URI uri, URI expectedJarUri) throws Exception
    {
        assertThat(URIUtil.getJarSource(uri), is(expectedJarUri));
    }

    public static Stream<Arguments> encodeSpacesSource()
    {
        return Stream.of(
            // null
            Arguments.of(null, null),

            // no spaces
            Arguments.of("abc", "abc"),

            // match
            Arguments.of("a c", "a%20c"),
            Arguments.of("   ", "%20%20%20"),
            Arguments.of("a%20space", "a%20space")
        );
    }

    @ParameterizedTest
    @MethodSource("encodeSpacesSource")
    public void testEncodeSpaces(String raw, String expected)
    {
        assertThat(URIUtil.encodeSpaces(raw), is(expected));
    }

    public static Stream<Arguments> encodeSpecific()
    {
        return Stream.of(
            // [raw, chars, expected]

            // null input
            Arguments.of(null, null, null),

            // null chars
            Arguments.of("abc", null, "abc"),

            // empty chars
            Arguments.of("abc", "", "abc"),

            // no matches
            Arguments.of("abc", ".;", "abc"),
            Arguments.of("xyz", ".;", "xyz"),
            Arguments.of(":::", ".;", ":::"),

            // matches
            Arguments.of("a c", " ", "a%20c"),
            Arguments.of("name=value", "=", "name%3Dvalue"),
            Arguments.of("This has fewer then 10% hits.", ".%", "This has fewer then 10%25 hits%2E"),

            // partially encoded already
            Arguments.of("a%20name=value%20pair", "=", "a%20name%3Dvalue%20pair"),
            Arguments.of("a%20name=value%20pair", "=%", "a%2520name%3Dvalue%2520pair")
        );
    }

    @ParameterizedTest
    @MethodSource(value = "encodeSpecific")
    public void testEncodeSpecific(String raw, String chars, String expected)
    {
        assertThat(URIUtil.encodeSpecific(raw, chars), is(expected));
    }

    public static Stream<Arguments> decodeSpecific()
    {
        return Stream.of(
            // [raw, chars, expected]

            // null input
            Arguments.of(null, null, null),

            // null chars
            Arguments.of("abc", null, "abc"),

            // empty chars
            Arguments.of("abc", "", "abc"),

            // no matches
            Arguments.of("abc", ".;", "abc"),
            Arguments.of("xyz", ".;", "xyz"),
            Arguments.of(":::", ".;", ":::"),

            // matches
            Arguments.of("a%20c", " ", "a c"),
            Arguments.of("name%3Dvalue", "=", "name=value"),
            Arguments.of("This has fewer then 10%25 hits%2E", ".%", "This has fewer then 10% hits."),

            // partially decode
            Arguments.of("a%20name%3Dvalue%20pair", "=", "a%20name=value%20pair"),
            Arguments.of("a%2520name%3Dvalue%2520pair", "=%", "a%20name=value%20pair")
        );
    }

    @ParameterizedTest
    @MethodSource(value = "decodeSpecific")
    public void testDecodeSpecific(String raw, String chars, String expected)
    {
        assertThat(URIUtil.decodeSpecific(raw, chars), is(expected));
    }
}
