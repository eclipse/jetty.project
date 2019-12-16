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

package org.eclipse.jetty.client;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>ClientConnectionFactory for the
 * <a href="http://www.haproxy.org/download/2.1/doc/proxy-protocol.txt">PROXY protocol</a>.</p>
 * <p>Use the {@link V1} or {@link V2} versions of this class to specify what version of the
 * PROXY protocol you want to use.</p>
 */
public abstract class ProxyProtocolClientConnectionFactory implements ClientConnectionFactory
{
    public static class V1 extends ProxyProtocolClientConnectionFactory
    {
        public V1(ClientConnectionFactory factory)
        {
            super(factory);
        }

        @Override
        protected ProxyProtocolConnection newProxyProtocolConnection(EndPoint endPoint, Map<String, Object> context)
        {
            HttpDestination destination = (HttpDestination)context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
            Executor executor = destination.getHttpClient().getExecutor();
            Tag tag = (Tag)destination.getOrigin().getTag();
            if (tag == null)
            {
                InetSocketAddress local = endPoint.getLocalAddress();
                InetSocketAddress remote = endPoint.getRemoteAddress();
                boolean ipv4 = local.getAddress() instanceof Inet4Address;
                tag = new Tag(ipv4 ? "TCP4" : "TCP6", local.getAddress().getHostAddress(), local.getPort(), remote.getAddress().getHostAddress(), remote.getPort());
            }
            return new ProxyProtocolConnectionV1(endPoint, executor, getClientConnectionFactory(), context, tag);
        }

        public static class Tag implements ClientConnectionFactory.Decorator
        {
            /**
             * The PROXY V1 Tag typically used to "ping" the server.
             */
            public static final Tag UNKNOWN = new Tag("UNKNOWN", null, 0, null, 0);

            private final String family;
            private final String srcIP;
            private final int srcPort;
            private final String dstIP;
            private final int dstPort;

            public Tag()
            {
                this(null, 0);
            }

            public Tag(String srcIP, int srcPort)
            {
                this(null, srcIP, srcPort, null, 0);
            }

            public Tag(String family, String srcIP, int srcPort, String dstIP, int dstPort)
            {
                this.family = family;
                this.srcIP = srcIP;
                this.srcPort = srcPort;
                this.dstIP = dstIP;
                this.dstPort = dstPort;
            }

            public String getFamily()
            {
                return family;
            }

            public String getSourceAddress()
            {
                return srcIP;
            }

            public int getSourcePort()
            {
                return srcPort;
            }

            public String getDestinationAddress()
            {
                return dstIP;
            }

            public int getDestinationPort()
            {
                return dstPort;
            }

            @Override
            public ClientConnectionFactory apply(ClientConnectionFactory factory)
            {
                return new V1(factory);
            }

            @Override
            public boolean equals(Object obj)
            {
                if (this == obj)
                    return true;
                if (obj == null || getClass() != obj.getClass())
                    return false;
                Tag that = (Tag)obj;
                return Objects.equals(family, that.family) &&
                    Objects.equals(srcIP, that.srcIP) &&
                    srcPort == that.srcPort &&
                    Objects.equals(dstIP, that.dstIP) &&
                    dstPort == that.dstPort;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(family, srcIP, srcPort, dstIP, dstPort);
            }
        }
    }

    public static class V2 extends ProxyProtocolClientConnectionFactory
    {
        public V2(ClientConnectionFactory factory)
        {
            super(factory);
        }

        @Override
        protected ProxyProtocolConnection newProxyProtocolConnection(EndPoint endPoint, Map<String, Object> context)
        {
            HttpDestination destination = (HttpDestination)context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
            Executor executor = destination.getHttpClient().getExecutor();
            Tag tag = (Tag)destination.getOrigin().getTag();
            if (tag == null)
            {
                InetSocketAddress local = endPoint.getLocalAddress();
                InetSocketAddress remote = endPoint.getRemoteAddress();
                boolean ipv4 = local.getAddress() instanceof Inet4Address;
                tag = new Tag(Tag.Command.PROXY, ipv4 ? Tag.Family.INET4 : Tag.Family.INET6, Tag.Protocol.STREAM, local.getAddress().getHostAddress(), local.getPort(), remote.getAddress().getHostAddress(), remote.getPort());
            }
            return new ProxyProtocolConnectionV2(endPoint, executor, getClientConnectionFactory(), context, tag);
        }

        public static class Tag implements ClientConnectionFactory.Decorator
        {
            /**
             * The PROXY V2 Tag typically used to "ping" the server.
             */
            public static final Tag LOCAL = new Tag(Command.LOCAL, Family.UNSPEC, Protocol.UNSPEC, null, 0, null, 0);

            private Command command;
            private Family family;
            private Protocol protocol;
            private String srcIP;
            private int srcPort;
            private String dstIP;
            private int dstPort;
            private Map<Integer, byte[]> vectors;

            public Tag()
            {
                this(null, 0);
            }

            public Tag(String srcIP, int srcPort)
            {
                this(Command.PROXY, null, Protocol.STREAM, srcIP, srcPort, null, 0);
            }

            public Tag(Command command, Family family, Protocol protocol, String srcIP, int srcPort, String dstIP, int dstPort)
            {
                this.command = command;
                this.family = family;
                this.protocol = protocol;
                this.srcIP = srcIP;
                this.srcPort = srcPort;
                this.dstIP = dstIP;
                this.dstPort = dstPort;
            }

            public void put(int type, byte[] data)
            {
                if (type < 0 || type > 255)
                    throw new IllegalArgumentException("Invalid type: " + type);
                if (data != null && data.length > 65535)
                    throw new IllegalArgumentException("Invalid data length: " + data.length);
                if (vectors == null)
                    vectors = new HashMap<>();
                vectors.put(type, data);
            }

            public Command getCommand()
            {
                return command;
            }

            public Family getFamily()
            {
                return family;
            }

            public Protocol getProtocol()
            {
                return protocol;
            }

            public String getSourceAddress()
            {
                return srcIP;
            }

            public int getSourcePort()
            {
                return srcPort;
            }

            public String getDestinationAddress()
            {
                return dstIP;
            }

            public int getDestinationPort()
            {
                return dstPort;
            }

            public Map<Integer, byte[]> getVectors()
            {
                return vectors != null ? vectors : Collections.emptyMap();
            }

            @Override
            public ClientConnectionFactory apply(ClientConnectionFactory factory)
            {
                return new V2(factory);
            }

            @Override
            public boolean equals(Object obj)
            {
                if (this == obj)
                    return true;
                if (obj == null || getClass() != obj.getClass())
                    return false;
                Tag that = (Tag)obj;
                return command == that.command &&
                    family == that.family &&
                    protocol == that.protocol &&
                    Objects.equals(srcIP, that.srcIP) &&
                    srcPort == that.srcPort &&
                    Objects.equals(dstIP, that.dstIP) &&
                    dstPort == that.dstPort;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(command, family, protocol, srcIP, srcPort, dstIP, dstPort);
            }

            public enum Command
            {
                LOCAL, PROXY
            }

            public enum Family
            {
                UNSPEC, INET4, INET6, UNIX
            }

            public enum Protocol
            {
                UNSPEC, STREAM, DGRAM
            }
        }
    }

    private final ClientConnectionFactory factory;

    private ProxyProtocolClientConnectionFactory(ClientConnectionFactory factory)
    {
        this.factory = factory;
    }

    public ClientConnectionFactory getClientConnectionFactory()
    {
        return factory;
    }

    @Override
    public org.eclipse.jetty.io.Connection newConnection(EndPoint endPoint, Map<String, Object> context)
    {
        ProxyProtocolConnection connection = newProxyProtocolConnection(endPoint, context);
        return customize(connection, context);
    }

    protected abstract ProxyProtocolConnection newProxyProtocolConnection(EndPoint endPoint, Map<String, Object> context);

    private abstract static class ProxyProtocolConnection extends AbstractConnection implements Callback
    {
        protected static final Logger LOG = Log.getLogger(ProxyProtocolConnection.class);

        private final ClientConnectionFactory factory;
        private final Map<String, Object> context;

        private ProxyProtocolConnection(EndPoint endPoint, Executor executor, ClientConnectionFactory factory, Map<String, Object> context)
        {
            super(endPoint, executor);
            this.factory = factory;
            this.context = context;
        }

        @Override
        public void onOpen()
        {
            super.onOpen();
            writePROXYBytes(getEndPoint(), this);
        }

        protected abstract void writePROXYBytes(EndPoint endPoint, Callback callback);

        @Override
        public void succeeded()
        {
            try
            {
                EndPoint endPoint = getEndPoint();
                Connection connection = factory.newConnection(endPoint, context);
                if (LOG.isDebugEnabled())
                    LOG.debug("Written PROXY line, upgrading to {}", connection);
                endPoint.upgrade(connection);
            }
            catch (Throwable x)
            {
                failed(x);
            }
        }

        @Override
        public void failed(Throwable x)
        {
            close();
            Promise<?> promise = (Promise<?>)context.get(HttpClientTransport.HTTP_CONNECTION_PROMISE_CONTEXT_KEY);
            promise.failed(x);
        }

        @Override
        public InvocationType getInvocationType()
        {
            return InvocationType.NON_BLOCKING;
        }

        @Override
        public void onFillable()
        {
        }
    }

    private static class ProxyProtocolConnectionV1 extends ProxyProtocolConnection
    {
        private final V1.Tag tag;

        public ProxyProtocolConnectionV1(EndPoint endPoint, Executor executor, ClientConnectionFactory factory, Map<String, Object> context, V1.Tag tag)
        {
            super(endPoint, executor, factory, context);
            this.tag = tag;
        }

        @Override
        protected void writePROXYBytes(EndPoint endPoint, Callback callback)
        {
            try
            {
                InetSocketAddress localAddress = endPoint.getLocalAddress();
                InetSocketAddress remoteAddress = endPoint.getRemoteAddress();
                String family = tag.getFamily();
                String srcIP = tag.getSourceAddress();
                int srcPort = tag.getSourcePort();
                String dstIP = tag.getDestinationAddress();
                int dstPort = tag.getDestinationPort();
                if (family == null)
                    family = localAddress.getAddress() instanceof Inet4Address ? "TCP4" : "TCP6";
                family = family.toUpperCase(Locale.ENGLISH);
                boolean unknown = family.equals("UNKNOWN");
                StringBuilder builder = new StringBuilder(64);
                builder.append("PROXY ").append(family);
                if (!unknown)
                {
                    if (srcIP == null)
                        srcIP = localAddress.getAddress().getHostAddress();
                    builder.append(" ").append(srcIP);
                    if (dstIP == null)
                        dstIP = remoteAddress.getAddress().getHostAddress();
                    builder.append(" ").append(dstIP);
                    if (srcPort <= 0)
                        srcPort = localAddress.getPort();
                    builder.append(" ").append(srcPort);
                    if (dstPort <= 0)
                        dstPort = remoteAddress.getPort();
                    builder.append(" ").append(dstPort);
                }
                builder.append("\r\n");
                String line = builder.toString();
                if (LOG.isDebugEnabled())
                    LOG.debug("Writing PROXY bytes: {}", line.trim());
                ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.US_ASCII));
                endPoint.write(callback, buffer);
            }
            catch (Throwable x)
            {
                callback.failed(x);
            }
        }
    }

    private static class ProxyProtocolConnectionV2 extends ProxyProtocolConnection
    {
        private static final byte[] MAGIC = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};

        private final V2.Tag tag;

        public ProxyProtocolConnectionV2(EndPoint endPoint, Executor executor, ClientConnectionFactory factory, Map<String, Object> context, V2.Tag tag)
        {
            super(endPoint, executor, factory, context);
            this.tag = tag;
        }

        @Override
        protected void writePROXYBytes(EndPoint endPoint, Callback callback)
        {
            try
            {
                int capacity = MAGIC.length;
                capacity += 1; // version and command
                capacity += 1; // family and protocol
                capacity += 2; // length
                capacity += 216; // max address length
                Map<Integer, byte[]> vectors = tag.getVectors();
                int vectorsLength = vectors.values().stream()
                    .mapToInt(data -> 1 + 2 + data.length)
                    .sum();
                capacity += vectorsLength;
                ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
                buffer.put(MAGIC);
                V2.Tag.Command command = tag.getCommand();
                int versionAndCommand = (2 << 4) | (command.ordinal() & 0x0F);
                buffer.put((byte)versionAndCommand);
                V2.Tag.Family family = tag.getFamily();
                String srcAddr = tag.getSourceAddress();
                if (srcAddr == null)
                    srcAddr = endPoint.getLocalAddress().getAddress().getHostAddress();
                int srcPort = tag.getSourcePort();
                if (srcPort <= 0)
                    srcPort = endPoint.getLocalAddress().getPort();
                if (family == null)
                    family = InetAddress.getByName(srcAddr) instanceof Inet4Address ? V2.Tag.Family.INET4 : V2.Tag.Family.INET6;
                V2.Tag.Protocol protocol = tag.getProtocol();
                if (protocol == null)
                    protocol = V2.Tag.Protocol.STREAM;
                int familyAndProtocol = (family.ordinal() << 4) | protocol.ordinal();
                buffer.put((byte)familyAndProtocol);
                int length = 0;
                switch (family)
                {
                    case UNSPEC:
                        break;
                    case INET4:
                        length = 12;
                        break;
                    case INET6:
                        length = 36;
                        break;
                    case UNIX:
                        length = 216;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                length += vectorsLength;
                buffer.putShort((short)length);
                String dstAddr = tag.getDestinationAddress();
                if (dstAddr == null)
                    dstAddr = endPoint.getRemoteAddress().getAddress().getHostAddress();
                int dstPort = tag.getDestinationPort();
                if (dstPort <= 0)
                    dstPort = endPoint.getRemoteAddress().getPort();
                switch (family)
                {
                    case UNSPEC:
                        break;
                    case INET4:
                    case INET6:
                        buffer.put(InetAddress.getByName(srcAddr).getAddress());
                        buffer.put(InetAddress.getByName(dstAddr).getAddress());
                        buffer.putShort((short)srcPort);
                        buffer.putShort((short)dstPort);
                        break;
                    case UNIX:
                        int position = buffer.position();
                        buffer.put(srcAddr.getBytes(StandardCharsets.US_ASCII));
                        buffer.position(position + 108);
                        buffer.put(dstAddr.getBytes(StandardCharsets.US_ASCII));
                        break;
                    default:
                        throw new IllegalStateException();
                }
                for (Map.Entry<Integer, byte[]> entry : vectors.entrySet())
                {
                    buffer.put(entry.getKey().byteValue());
                    byte[] data = entry.getValue();
                    buffer.putShort((short)data.length);
                    buffer.put(data);
                }
                buffer.flip();
                endPoint.write(callback, buffer);
            }
            catch (Throwable x)
            {
                callback.failed(x);
            }
        }
    }
}
