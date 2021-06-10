//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.io;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class AdapterMemoryPool implements MemoryPool<RetainableByteBuffer>
{
    private final ByteBufferPool byteBufferPool;
    private final Consumer<ByteBuffer> releaser;

    public AdapterMemoryPool(ByteBufferPool byteBufferPool)
    {
        this.byteBufferPool = byteBufferPool;
        this.releaser = byteBufferPool::release;
    }

    @Override
    public RetainableByteBuffer acquire(int size, boolean direct)
    {
        ByteBuffer byteBuffer = byteBufferPool.acquire(size, direct);
        return new RetainableByteBuffer(byteBuffer, releaser);
    }

    @Override
    public void release(RetainableByteBuffer buffer)
    {
        buffer.release();
    }
}
