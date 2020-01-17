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
