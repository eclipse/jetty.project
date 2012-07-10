/*
 * Copyright (c) 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.jetty.spdy.api;

/**
 * <p>Helper class that holds useful SPDY constants.</p>
 */
public class SPDY
{
    /**
     * <p>Constant that indicates the version 2 of the SPDY protocol</p>
     */
    public static final short V2 = 2;

    /**
     * <p>Constant that indicates the version 3 of the SPDY protocol</p>
     */
    public static final short V3 = 3;

    public static final short getVersion(String protocol)
    {
        if ("spdy/2".equals(protocol))
            return V2;
        else if ("spdy/3".equals(protocol))
            return V3;
        throw new IllegalArgumentException("Procotol: " + protocol + " is not a known SPDY protocol");
    }

    private SPDY()
    {
    }
}
