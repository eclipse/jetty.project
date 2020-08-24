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

package org.eclipse.jetty.rewrite.handler;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.ArrayTernaryTrie;
import org.eclipse.jetty.util.Trie;

/**
 * Special handling for MSIE (Microsoft Internet Explorer).
 * <ul>
 *     <li>Disable keep alive for SSL from IE5 or IE6 on Windows 2000</li>
 *     <li>Disable encodings for IE<=6</li>
 * </ul>
 */
public class MsieRule extends MsieSslRule
{
    private static final int IEv5 = '5';
    private static final int IEv6 = '6';
    private static Trie<Boolean> __IE6_BadOS = new ArrayTernaryTrie<>();
    private static HttpField CONNECTION_CLOSE = new HttpField(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);

    {
        __IE6_BadOS.put("NT 5.01", Boolean.TRUE);
        __IE6_BadOS.put("NT 5.0", Boolean.TRUE);
        __IE6_BadOS.put("NT 4.0", Boolean.TRUE);
        __IE6_BadOS.put("98", Boolean.TRUE);
        __IE6_BadOS.put("98; Win 9x 4.90", Boolean.TRUE);
        __IE6_BadOS.put("95", Boolean.TRUE);
        __IE6_BadOS.put("CE", Boolean.TRUE);
    }

    public MsieRule()
    {
        _handling = false;
        _terminating = false;
    }

    @Override
    public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        Request baseRequest = Request.getBaseRequest(request);
        if (baseRequest == null)
            return null;
        String userAgent = baseRequest.getHttpFields().get(HttpHeader.USER_AGENT);

        int msie = userAgent.indexOf("MSIE");
        if (msie >= 0)
        {
            int version = (userAgent.length() - msie > 5) ? userAgent.charAt(msie + 5) : IEv5;

            if (version <= IEv6)
            {
                HttpFields.Mutable fields = HttpFields.build(baseRequest.getHttpFields());

                // Don't gzip responses for IE<=6
                fields.remove(HttpHeader.ACCEPT_ENCODING);

                // IE<=6 can't do persistent SSL
                if (request.isSecure())
                {
                    boolean badOs = false;
                    if (version == IEv6)
                    {
                        int windows = userAgent.indexOf("Windows", msie + 5);
                        if (windows > 0)
                        {
                            int end = userAgent.indexOf(')', windows + 8);
                            badOs = (end < 0 || __IE6_BadOS.get(userAgent, windows + 8, end - windows - 8) != null);
                        }
                    }

                    if (version <= IEv5 || badOs)
                    {
                        fields.remove(HttpHeader.KEEP_ALIVE);
                        fields.ensure(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE.asString());
                        response.setHeader(HttpHeader.CONNECTION.asString(), HttpHeaderValue.CLOSE.asString());
                    }
                }
                baseRequest.setHttpFields(fields);
                return target;
            }
        }
        return null;
    }

}
