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

package org.eclipse.jetty.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java Version Utility class.
 * <p>Parses java versions to extract a consistent set of version parts</p>
 */
public class JavaVersion
{
    /**
     * Context attribute that can be set to target a different version of the jvm than the current runtime.
     * Acceptable values should correspond to those returned by JavaVersion.getPlatform().
     */
    public static final String JAVA_TARGET_PLATFORM = "org.eclipse.jetty.javaTargetPlatform";
    
    /** Regex for Java version numbers */
    private static final String VNUM = "(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*\\.[0-9]+)*)";
    private static final String UPDATE = "(?:(?<UNDERSCORE>_)(?<UPDATE>[0-9]+))?";
    private static final String PRE = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
    private static final String BUILD = "(?:(?<PLUS>\\+)(?<BUILD>[0-9]+))?";
    private static final String OPT = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";

    private static final String VSTR_FORMAT = VNUM + UPDATE + PRE + BUILD + OPT;

    static final Pattern VSTR_PATTERN = Pattern.compile(VSTR_FORMAT);
    
    public static final JavaVersion VERSION = parse(System.getProperty("java.runtime.version",System.getProperty("java.version")));

    public static JavaVersion parse(String v) 
    {
        Matcher m = VSTR_PATTERN.matcher(v);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid version string: '" + v + "'");
        
        // $VNUM is a dot-separated list of integers of arbitrary length
        String[] split = m.group("VNUM").split("\\.");
        int[] version = new int[split.length];
        for (int i = 0; i < split.length; i++)
            version[i] = Integer.parseInt(split[i]);

        if (m.group("UNDERSCORE")!=null)
        {
            return new JavaVersion(
                    v,
                    (version[0]>=9 || version.length==1)?version[0]:version[1],
                    version[0],
                    version.length>1?version[1]:0,
                    version.length>2?version[2]:0,
                    Integer.parseInt(m.group("UPDATE")),
                    suffix(version,m.group("PRE"),m.group("OPT"))
                    );
        }
        
        if (m.group("PLUS")!=null)
        {
            return new JavaVersion(
                    v,
                    (version[0]>=9 || version.length==1)?version[0]:version[1],
                    version[0],
                    version.length>1?version[1]:0,
                    version.length>2?version[2]:0,
                    Integer.parseInt(m.group("BUILD")),
                    suffix(version,m.group("PRE"),m.group("OPT"))
                    );
        }

        return new JavaVersion(
                v,
                (version[0]>=9 || version.length==1)?version[0]:version[1],
                version[0],
                version.length>1?version[1]:0,
                version.length>2?version[2]:0,
                0,
                suffix(version,m.group("PRE"),m.group("OPT"))
                );
        
    }

    private static String suffix(int[] version, String pre, String opt)
    {
        StringBuilder buf = new StringBuilder();
        for (int i=3;i<version.length;i++)
        {
            if (i>3)
                buf.append(".");
            buf.append(version[i]);
        }
               
        if (pre!=null)
        {
            if (buf.length()>0)
                buf.append('-');
            buf.append(pre);
        }
        
        if (opt!=null)
        {
            if (buf.length()>0)
                buf.append('-');
            buf.append(opt);
        }
        
        if (buf.length()==0)
            return null;
        
        return buf.toString();
    }
    
    private final String version;
    private final int platform;
    private final int major;
    private final int minor;
    private final int micro;
    private final int update;
    private final String suffix;

    private JavaVersion(String version, int platform, int major, int minor, int micro, int update, String suffix)
    {
        this.version = version;
        this.platform = platform;
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.update = update;
        this.suffix = suffix;
    }

    /**
     * @return the string from which this JavaVersion was created
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * <p>Returns the Java Platform version, such as {@code 8} for JDK 1.8.0_92 and {@code 9} for JDK 9.2.4.</p>
     *
     * @return the Java Platform version
     */
    public int getPlatform()
    {
        return platform;
    }

    /**
     * <p>Returns the major number version, such as {@code 1} for JDK 1.8.0_92 and {@code 9} for JDK 9.2.4.</p>
     *
     * @return the major number version
     */
    public int getMajor()
    {
        return major;
    }

    /**
     * <p>Returns the minor number version, such as {@code 8} for JDK 1.8.0_92 and {@code 2} for JDK 9.2.4.</p>
     *
     * @return the minor number version
     */
    public int getMinor()
    {
        return minor;
    }

    /**
     * <p>Returns the micro number version (aka security number), such as {@code 0} for JDK 1.8.0_92 and {@code 4} for JDK 9.2.4.</p>
     *
     * @return the micro number version
     */
    public int getMicro()
    {
        return micro;
    }

    /**
     * <p>Returns the update number version, such as {@code 92} for JDK 1.8.0_92 and {@code 0} for JDK 9.2.4.</p>
     *
     * @return the update number version
     */
    public int getUpdate()
    {
        return update;
    }

    /**
     * <p>Returns the remaining string after the version numbers, such as {@code -internal} for
     * JDK 1.8.0_92-internal and {@code -ea} for JDK 9-ea, or {@code +13} for JDK 9.2.4+13.</p>
     *
     * @return the remaining string after the version numbers
     */
    public String getSuffix()
    {
        return suffix;
    }

    public String toString()
    {
        return version;
    }
}
