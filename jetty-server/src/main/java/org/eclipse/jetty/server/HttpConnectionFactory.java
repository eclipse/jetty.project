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

package org.eclipse.jetty.server;

import java.util.Objects;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.annotation.Name;

/**
 * A Connection Factory for HTTP Connections.
 * <p>Accepts connections either directly or via SSL and/or ALPN chained connection factories.  The accepted
 * {@link HttpConnection}s are configured by a {@link HttpConfiguration} instance that is either created by
 * default or passed in to the constructor.
 */
public class HttpConnectionFactory extends AbstractConnectionFactory implements HttpConfiguration.ConnectionFactory
{
    private final HttpConfiguration _config;
    private boolean _recordHttpComplianceViolations;
    private boolean _useInputDirectByteBuffers;
    private boolean _useOutputDirectByteBuffers;

    public HttpConnectionFactory()
    {
        this(new HttpConfiguration());
    }

    public HttpConnectionFactory(@Name("config") HttpConfiguration config)
    {
        super(HttpVersion.HTTP_1_1.asString());
        _config = Objects.requireNonNull(config);
        addBean(_config);
        setUseInputDirectByteBuffers(_config.isUseInputDirectByteBuffers());
        setUseOutputDirectByteBuffers(_config.isUseOutputDirectByteBuffers());
    }

    @Override
    public HttpConfiguration getHttpConfiguration()
    {
        return _config;
    }

    public boolean isRecordHttpComplianceViolations()
    {
        return _recordHttpComplianceViolations;
    }

    public void setRecordHttpComplianceViolations(boolean recordHttpComplianceViolations)
    {
        this._recordHttpComplianceViolations = recordHttpComplianceViolations;
    }

    public boolean isUseInputDirectByteBuffers()
    {
        return _useInputDirectByteBuffers;
    }

    public void setUseInputDirectByteBuffers(boolean useInputDirectByteBuffers)
    {
        _useInputDirectByteBuffers = useInputDirectByteBuffers;
    }

    public boolean isUseOutputDirectByteBuffers()
    {
        return _useOutputDirectByteBuffers;
    }

    public void setUseOutputDirectByteBuffers(boolean useOutputDirectByteBuffers)
    {
        _useOutputDirectByteBuffers = useOutputDirectByteBuffers;
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        HttpConnection connection = new HttpConnection(_config, connector, endPoint, isRecordHttpComplianceViolations());
        connection.setUseInputDirectByteBuffers(isUseInputDirectByteBuffers());
        connection.setUseOutputDirectByteBuffers(isUseOutputDirectByteBuffers());
        return configure(connection, connector, endPoint);
    }
}
