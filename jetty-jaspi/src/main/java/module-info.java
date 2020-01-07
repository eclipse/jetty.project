//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
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

import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.jaspi.JaspiAuthenticatorFactory;

module org.eclipse.jetty.security.jaspi
{
    exports org.eclipse.jetty.security.jaspi;
    exports org.eclipse.jetty.security.jaspi.callback;
    exports org.eclipse.jetty.security.jaspi.modules;

    requires javax.security.auth.message;
    requires jetty.servlet.api;
    requires transitive org.eclipse.jetty.security;

    provides Authenticator.Factory with JaspiAuthenticatorFactory;
}
