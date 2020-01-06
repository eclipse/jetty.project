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

package org.eclipse.jetty.jndi;

import java.util.Iterator;
import javax.naming.Binding;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * NameEnumeration
 */
public class NameEnumeration implements NamingEnumeration<NameClassPair>
{
    Iterator<Binding> _delegate;

    public NameEnumeration(Iterator<Binding> e)
    {
        _delegate = e;
    }

    @Override
    public void close()
        throws NamingException
    {
    }

    @Override
    public boolean hasMore()
        throws NamingException
    {
        return _delegate.hasNext();
    }

    @Override
    public NameClassPair next()
        throws NamingException
    {
        Binding b = _delegate.next();
        return new NameClassPair(b.getName(), b.getClassName(), true);
    }

    @Override
    public boolean hasMoreElements()
    {
        return _delegate.hasNext();
    }

    @Override
    public NameClassPair nextElement()
    {
        Binding b = _delegate.next();
        return new NameClassPair(b.getName(), b.getClassName(), true);
    }
}
