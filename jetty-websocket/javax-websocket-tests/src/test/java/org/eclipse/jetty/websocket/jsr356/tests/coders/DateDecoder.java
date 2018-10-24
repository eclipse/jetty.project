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

package org.eclipse.jetty.websocket.jsr356.tests.coders;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Decode Date
 */
public class DateDecoder implements Decoder.Text<Date>
{
    private TimeZone GMT = TimeZone.getTimeZone("GMT");

    @Override
    public Date decode(String s) throws DecodeException
    {
        try
        {
            SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
            format.setTimeZone(GMT);
            return format.parse(s);
        }
        catch (ParseException e)
        {
            throw new DecodeException(s, e.getMessage(), e);
        }
    }

    @Override
    public void destroy()
    {
        CoderEventTracking.getInstance().addEvent(this, "destroy()");
    }

    @Override
    public void init(EndpointConfig config)
    {
        CoderEventTracking.getInstance().addEvent(this, "init(EndpointConfig)");
    }

    @Override
    public boolean willDecode(String s)
    {
        CoderEventTracking.getInstance().addEvent(this, "willDecode(String)");
        return true;
    }
}
