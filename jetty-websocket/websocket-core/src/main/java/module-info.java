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

import org.eclipse.jetty.websocket.core.Extension;
import org.eclipse.jetty.websocket.core.internal.FragmentExtension;
import org.eclipse.jetty.websocket.core.internal.IdentityExtension;
import org.eclipse.jetty.websocket.core.internal.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.core.internal.ValidationExtension;

module org.eclipse.jetty.websocket.core
{
    exports org.eclipse.jetty.websocket.core;
    exports org.eclipse.jetty.websocket.core.client;
    exports org.eclipse.jetty.websocket.core.server;
    exports org.eclipse.jetty.websocket.core.internal to org.eclipse.jetty.util;

    requires jetty.servlet.api;
    requires transitive org.eclipse.jetty.client;
    requires transitive org.eclipse.jetty.server;

    // Only required if using XmlHttpClientProvider.
    requires static org.eclipse.jetty.xml;

    uses Extension;

    provides Extension with
        FragmentExtension,
        IdentityExtension,
        PerMessageDeflateExtension,
        ValidationExtension;
}
