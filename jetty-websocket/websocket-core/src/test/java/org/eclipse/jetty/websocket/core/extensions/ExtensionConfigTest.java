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

package org.eclipse.jetty.websocket.core.extensions;

import org.eclipse.jetty.websocket.core.ExtensionConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ExtensionConfigTest
{
    private void assertConfig(ExtensionConfig cfg, String expectedName, Map<String, String> expectedParams)
    {
        String prefix = "ExtensionConfig";
        assertThat(prefix + ".Name",cfg.getName(),is(expectedName));

        prefix += ".getParameters()";
        Map<String, String> actualParams = cfg.getParameters();
        assertThat(prefix,actualParams,notNullValue());
        assertThat(prefix + ".size",actualParams.size(),is(expectedParams.size()));

        for (String expectedKey : expectedParams.keySet())
        {
            assertThat(prefix + ".containsKey(" + expectedKey + ")",actualParams.containsKey(expectedKey),is(true));

            String expectedValue = expectedParams.get(expectedKey);
            String actualValue = actualParams.get(expectedKey);

            assertThat(prefix + ".containsKey(" + expectedKey + ")",actualValue,is(expectedValue));
        }
    }

    @Test
    public void testParseMuxExample()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("mux; max-channels=4; flow-control");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("max-channels","4");
        expectedParams.put("flow-control",null);
        assertConfig(cfg,"mux",expectedParams);
    }

    @Test
    public void testParsePerMessageCompressExample1()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("permessage-compress; method=foo");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("method","foo");
        assertConfig(cfg,"permessage-compress",expectedParams);
    }

    @Test
    public void testParsePerMessageCompressExample2()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("permessage-compress; method=\"foo; x=10\"");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("method","foo; x=10");
        assertConfig(cfg,"permessage-compress",expectedParams);
    }

    @Test
    public void testParsePerMessageCompressExample3()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("permessage-compress; method=\"foo, bar\"");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("method","foo, bar");
        assertConfig(cfg,"permessage-compress",expectedParams);
    }

    @Test
    public void testParsePerMessageCompressExample4()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("permessage-compress; method=\"foo; use_x, foo\"");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("method","foo; use_x, foo");
        assertConfig(cfg,"permessage-compress",expectedParams);
    }

    @Test
    public void testParsePerMessageCompressExample5()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("permessage-compress; method=\"foo; x=\\\"Hello World\\\", bar\"");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("method","foo; x=\"Hello World\", bar");
        assertConfig(cfg,"permessage-compress",expectedParams);
    }

    @Test
    public void testParseSimple_BasicParameters()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("bar; baz=2");
        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("baz","2");
        assertConfig(cfg,"bar",expectedParams);
    }

    @Test
    public void testParseSimple_NoParameters()
    {
        ExtensionConfig cfg = ExtensionConfig.parse("foo");
        Map<String, String> expectedParams = new HashMap<>();
        assertConfig(cfg,"foo",expectedParams);
    }

    @Test
    public void testParseList_Simple()
    {
        String rawHeaders[] = new String[] {
                "permessage-compress; client_max_window_bits",
                "capture; output=\"wscapture.log\"",
                "identity"
        };

        List<ExtensionConfig> configs = ExtensionConfig.parseList(rawHeaders);
        assertThat("Configs", configs.size(), is(3));
        assertThat("Configs[0]", configs.get(0).getName(), is("permessage-compress"));
        assertThat("Configs[1]", configs.get(1).getName(), is("capture"));
        assertThat("Configs[2]", configs.get(2).getName(), is("identity"));
    }

    /**
     * Parse a list of headers from a client that isn't following the RFC spec properly,
     * where they include multiple extensions in 1 header.
     */
    @Test
    public void testParseList_Unsplit()
    {
        String rawHeaders[] = new String[] {
                "permessage-compress; client_max_window_bits, identity",
                "capture; output=\"wscapture.log\""
        };

        List<ExtensionConfig> configs = ExtensionConfig.parseList(rawHeaders);
        assertThat("Configs", configs.size(), is(3));
        assertThat("Configs[0]", configs.get(0).getName(), is("permessage-compress"));
        assertThat("Configs[1]", configs.get(1).getName(), is("identity"));
        assertThat("Configs[2]", configs.get(2).getName(), is("capture"));
    }
}
