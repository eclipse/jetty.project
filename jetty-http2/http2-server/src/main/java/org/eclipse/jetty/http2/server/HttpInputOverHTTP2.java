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

package org.eclipse.jetty.http2.server;

import org.eclipse.jetty.server.AbstractLockedHttpInput;
import org.eclipse.jetty.server.HttpChannelState;

public class HttpInputOverHTTP2 extends AbstractLockedHttpInput
{
    public HttpInputOverHTTP2(HttpChannelState state)
    {
        super(state);
    }

    @Override
    protected void produceRawContent()
    {
        ((HttpChannelOverHTTP2)_channelState.getHttpChannel()).fetchContent();
    }

    @Override
    protected void failRawContent(Throwable failure)
    {
        ((HttpChannelOverHTTP2)_channelState.getHttpChannel()).getStream().fail(failure);
    }
}