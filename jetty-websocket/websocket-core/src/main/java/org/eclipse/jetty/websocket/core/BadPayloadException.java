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

package org.eclipse.jetty.websocket.core;

/**
 * Exception to terminate the connection because it has received data within a frame payload that was not consistent with the requirements of that frame
 * payload. (eg: not UTF-8 in a text frame, or a unexpected data seen by an extension)
 *
 * @see <a href="https://tools.ietf.org/html/rfc6455#section-7.4.1">RFC6455 : Section 7.4.1</a>
 */
@SuppressWarnings("serial")
public class BadPayloadException extends CloseException
{
    public BadPayloadException(String message)
    {
        super(CloseStatus.BAD_PAYLOAD, message);
    }

    public BadPayloadException(String message, Throwable t)
    {
        super(CloseStatus.BAD_PAYLOAD, message, t);
    }

    public BadPayloadException(Throwable t)
    {
        super(CloseStatus.BAD_PAYLOAD, t);
    }
}
