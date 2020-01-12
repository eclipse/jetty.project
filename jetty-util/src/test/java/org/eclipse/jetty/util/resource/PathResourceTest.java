//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class PathResourceTest
{
    @Test
    public void testNonDefaultFileSystem_GetInputStream() throws URISyntaxException, IOException
    {
        Path exampleJar = MavenTestingUtils.getTestResourcePathFile("example.jar");

        URI uri = new URI("jar", exampleJar.toUri().toASCIIString(), null);

        Map<String, Object> env = new HashMap<>();
        env.put("multi-release", "runtime");

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env))
        {
            Path manifestPath = zipfs.getPath("/META-INF/MANIFEST.MF");
            assertThat(manifestPath, is(not(nullValue())));

            PathResource resource = new PathResource(manifestPath);

            try (InputStream inputStream = resource.getInputStream())
            {
                assertThat("InputStream", inputStream, is(not(nullValue())));
            }
        }
    }

    @Test
    public void testNonDefaultFileSystem_GetReadableByteChannel() throws URISyntaxException, IOException
    {
        Path exampleJar = MavenTestingUtils.getTestResourcePathFile("example.jar");

        URI uri = new URI("jar", exampleJar.toUri().toASCIIString(), null);

        Map<String, Object> env = new HashMap<>();
        env.put("multi-release", "runtime");

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env))
        {
            Path manifestPath = zipfs.getPath("/META-INF/MANIFEST.MF");
            assertThat(manifestPath, is(not(nullValue())));

            PathResource resource = new PathResource(manifestPath);

            try (ReadableByteChannel channel = resource.getReadableByteChannel())
            {
                assertThat("ReadableByteChannel", channel, is(not(nullValue())));
            }
        }
    }

    @Test
    public void testNonDefaultFileSystem_GetFile() throws URISyntaxException, IOException
    {
        Path exampleJar = MavenTestingUtils.getTestResourcePathFile("example.jar");

        URI uri = new URI("jar", exampleJar.toUri().toASCIIString(), null);

        Map<String, Object> env = new HashMap<>();
        env.put("multi-release", "runtime");

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env))
        {
            Path manifestPath = zipfs.getPath("/META-INF/MANIFEST.MF");
            assertThat(manifestPath, is(not(nullValue())));

            PathResource resource = new PathResource(manifestPath);
            File file = resource.getFile();
            assertThat("File should be null for non-default FileSystem", file, is(nullValue()));
        }
    }

    @Test
    public void testNonDefaultFileSystem_WriteTo() throws URISyntaxException, IOException
    {
        Path exampleJar = MavenTestingUtils.getTestResourcePathFile("example.jar");

        URI uri = new URI("jar", exampleJar.toUri().toASCIIString(), null);

        Map<String, Object> env = new HashMap<>();
        env.put("multi-release", "runtime");

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env))
        {
            Path manifestPath = zipfs.getPath("/META-INF/MANIFEST.MF");
            assertThat(manifestPath, is(not(nullValue())));

            PathResource resource = new PathResource(manifestPath);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                resource.writeTo(out, 2, 10);
                String actual = new String(out.toByteArray(), UTF_8);
                String expected = "nifest-Ver";
                assertThat("writeTo(out, 2, 10)", actual, is(expected));
            }
        }
    }

    @Test
    public void testDefaultFileSystem_GetFile() throws Exception
    {
        Path exampleJar = MavenTestingUtils.getTestResourcePathFile("example.jar");
        PathResource resource = new PathResource(exampleJar);

        File file = resource.getFile();
        assertThat("File for default FileSystem", file, is(exampleJar.toFile()));
    }
}
