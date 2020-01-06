//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty. Ltd and others
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

package org.eclipse.jetty.servlet.jmx;

import org.eclipse.jetty.jmx.ObjectMBean;
import org.eclipse.jetty.servlet.Holder;

public class HolderMBean extends ObjectMBean
{
    public HolderMBean(Object managedObject)
    {
        super(managedObject);
    }

    @Override
    public String getObjectNameBasis()
    {
        if (_managed != null && _managed instanceof Holder)
        {
            Holder holder = (Holder)_managed;
            String name = holder.getName();
            if (name != null)
                return name;
        }
        return super.getObjectNameBasis();
    }
}
