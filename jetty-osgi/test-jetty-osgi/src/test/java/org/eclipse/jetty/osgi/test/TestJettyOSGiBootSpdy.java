//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.osgi.test;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * SPDY setup.
 */
@RunWith(PaxExam.class)
//@Ignore
public class TestJettyOSGiBootSpdy
{
    private static final String LOG_LEVEL = "WARN";


    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] config()
    {
        ArrayList<Option> options = new ArrayList<Option>();
        options.addAll(TestJettyOSGiBootWithJsp.configureJettyHomeAndPort(true,"jetty-spdy.xml"));
        options.addAll(TestJettyOSGiBootCore.coreJettyDependencies());
        options.addAll(spdyJettyDependencies());
        options.add(CoreOptions.junitBundles());
        options.addAll(TestJettyOSGiBootCore.httpServiceJetty());
        options.addAll(Arrays.asList(options(systemProperty("pax.exam.logging").value("none"))));
        options.addAll(Arrays.asList(options(systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"))));
        options.addAll(Arrays.asList(options(systemProperty("org.eclipse.jetty.LEVEL").value("INFO"))));
        options.addAll(Arrays.asList(options(systemProperty("org.eclipse.jetty.osgi.LEVEL").value("DEBUG"))));
        options.addAll(Arrays.asList(options(systemProperty("org.eclipse.jetty.util.component.LEVEL").value("DEBUG"))));
        options.addAll(Arrays.asList(options(systemProperty("org.eclipse.jetty.server.LEVEL").value("DEBUG"))));
        options.addAll(Arrays.asList(options(systemProperty("org.eclipse.jetty.xml.LEVEL").value("INFO"))));
        // options.addAll(Arrays.asList(options(systemProperty("osgi.console").value("6666"))));
        // options.addAll(Arrays.asList(options(systemProperty("osgi.console.enable.builtin").value("true"))));
        return options.toArray(new Option[options.size()]);
    }

    public static List<Option> spdyJettyDependencies()
    {
        List<Option> res = new ArrayList<Option>();
        res.add(CoreOptions.systemProperty("jetty.port").value(String.valueOf(TestJettyOSGiBootCore.DEFAULT_HTTP_PORT)));
        res.add(CoreOptions.systemProperty("ssl.port").value(String.valueOf(TestJettyOSGiBootCore.DEFAULT_SSL_PORT)));

        String alpnBoot = System.getProperty("mortbay-alpn-boot");
        if (alpnBoot == null) { throw new IllegalStateException("Define path to alpn boot jar as system property -Dmortbay-alpn-boot"); }
        File checkALPNBoot = new File(alpnBoot);
        if (!checkALPNBoot.exists()) { throw new IllegalStateException("Unable to find the alpn boot jar here: " + alpnBoot); }

        res.add(CoreOptions.vmOptions("-Xbootclasspath/p:" + alpnBoot));

        res.add(mavenBundle().groupId("org.eclipse.jetty.osgi").artifactId("jetty-osgi-alpn").versionAsInProject().noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-server").versionAsInProject().start());

        res.add(mavenBundle().groupId("org.eclipse.jetty.spdy").artifactId("spdy-core").versionAsInProject().noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty.spdy").artifactId("spdy-client").versionAsInProject().start());
        res.add(mavenBundle().groupId("org.eclipse.jetty.spdy").artifactId("spdy-server").versionAsInProject().noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty.spdy").artifactId("spdy-http-common").versionAsInProject().noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty.spdy").artifactId("spdy-http-server").versionAsInProject().noStart());
        return res;
    }

    @Test
    public void checkALPNBootOnBootstrapClasspath() throws Exception
    {
        Class<?> alpn = Thread.currentThread().getContextClassLoader().loadClass("org.eclipse.jetty.alpn.ALPN");
        Assert.assertNotNull(alpn);
        Assert.assertNull(alpn.getClassLoader());
    }

    @Ignore
    @Test
    public void assertAllBundlesActiveOrResolved()
    {
        // TestOSGiUtil.debugBundles(bundleContext);
        TestOSGiUtil.assertAllBundlesActiveOrResolved(bundleContext);
    }

    @Test
    public void testSpdyOnHttpService() throws Exception
    {
        // TestOSGiUtil.debugBundles(bundleContext);
        // Thread.sleep(2000000000);
        TestOSGiUtil.testHttpServiceGreetings(bundleContext, "https", TestJettyOSGiBootCore.DEFAULT_SSL_PORT);
    }

}
