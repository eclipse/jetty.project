//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.http2.parser;

/**
 * Controls rate of events via {@link #onEvent(Object)}.
 */
public interface RateControl
{
    public static final RateControl NO_RATE_CONTROL = event -> true;

    /**
     * <p>Applications should call this method when they want to signal an
     * event that is subject to rate control.</p>
     * <p>Implementations should return true if the event does not exceed
     * the desired rate, or false to signal that the event exceeded the
     * desired rate.</p>
     *
     * @param event the event subject to rate control.
     * @return true IFF the rate is within limits
     */
    public boolean onEvent(Object event);
}
