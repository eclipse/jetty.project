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

package org.eclipse.jetty.util.preventers;

import java.security.Security;

/**
 * SecurityProviderLeakPreventer
 *
 * Some security providers, such as sun.security.pkcs11.SunPKCS11 start a deamon thread,
 * which will use the thread context classloader. Load them here to ensure the classloader
 * is not a webapp classloader.
 *
 * Inspired by Tomcat JreMemoryLeakPrevention
 */
public class SecurityProviderLeakPreventer extends AbstractLeakPreventer
{

    /**
     * @see org.eclipse.jetty.util.preventers.AbstractLeakPreventer#prevent(java.lang.ClassLoader)
     */
    @Override
    public void prevent(ClassLoader loader)
    {
        Security.getProviders();
    }
}
