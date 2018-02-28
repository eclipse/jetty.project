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

package org.eclipse.jetty.util.thread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.DumpableCollection;

/**
 * A {@link org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool} wrapper around {@link ThreadPoolExecutor}.
 */
@ManagedObject("A thread pool")
public class ExecutorSizedThreadPool extends ContainerLifeCycle implements ThreadPool.SizedThreadPool, TryExecutor
{
    private final ThreadPoolExecutor _executor;
    private final ThreadPoolBudget _budget;
    private final ThreadGroup _group;
    private String _name = "etp" + hashCode();
    private int _minThreads;
    private int _reservedThreads = -1;
    private TryExecutor _tryExecutor = TryExecutor.NO_TRY;
    private int _priority = Thread.NORM_PRIORITY;
    private boolean _daemon;
    private boolean _detailedDump;

    public ExecutorSizedThreadPool()
    {
        this(200, 8);
    }

    public ExecutorSizedThreadPool(int maxThreads)
    {
        this(maxThreads, Math.min(8, maxThreads));
    }
    
    public ExecutorSizedThreadPool(int maxThreads, int minThreads)
    {
        this(new ThreadPoolExecutor(maxThreads, maxThreads, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>()), minThreads, -1, null);
    }

    public ExecutorSizedThreadPool(ThreadPoolExecutor executor)
    {
        this(executor, -1);
    }

    public ExecutorSizedThreadPool(ThreadPoolExecutor executor, int reservedThreads)
    {
        this(executor, reservedThreads, null);
    }

    public ExecutorSizedThreadPool(ThreadPoolExecutor executor, int reservedThreads, ThreadGroup group)
    {
        this(executor, Math.min(Runtime.getRuntime().availableProcessors(), executor.getCorePoolSize()), reservedThreads, group);
    }

    private ExecutorSizedThreadPool(ThreadPoolExecutor executor, int minThreads, int reservedThreads, ThreadGroup group)
    {
        int maxThreads = executor.getMaximumPoolSize();
        if (maxThreads < minThreads)
        {
            executor.shutdownNow();
            throw new IllegalArgumentException("max threads (" + maxThreads + ") cannot be less than min threads (" + minThreads + ")");
        }
        _executor = executor;
        _executor.setThreadFactory(this::newThread);
        _budget = new ThreadPoolBudget(this);
        _group = group;
        _minThreads = minThreads;
        _reservedThreads = reservedThreads;
    }

    /**
     * @return the name of the this thread pool
     */
    @ManagedAttribute("name of this thread pool")
    public String getName()
    {
        return _name;
    }

    /**
     * @param name the name of this thread pool, used to name threads
     */
    public void setName(String name)
    {
        if (isRunning())
            throw new IllegalStateException(getState());
        _name = name;
    }

    @Override
    @ManagedAttribute("minimum number of threads in the pool")
    public int getMinThreads()
    {
        return _minThreads;
    }

    @Override
    public void setMinThreads(int threads)
    {
        _minThreads = threads;
    }

    @Override
    @ManagedAttribute("maximum number of threads in the pool")
    public int getMaxThreads()
    {
        return _executor.getMaximumPoolSize();
    }

    @Override
    public void setMaxThreads(int threads)
    {
        _executor.setCorePoolSize(threads);
        _executor.setMaximumPoolSize(threads);
    }

    /**
     * @return the maximum thread idle time in ms.
     * @see #setIdleTimeout(int)
     */
    @ManagedAttribute("maximum time a thread may be idle in ms")
    public int getIdleTimeout()
    {
        return (int)_executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Sets the maximum thread idle time in ms.</p>
     * <p>Threads that are idle for longer than this
     * period may be stopped.</p>
     *
     * @param idleTimeout the maximum thread idle time in ms.
     * @see #getIdleTimeout()
     */
    public void setIdleTimeout(int idleTimeout)
    {
        _executor.setKeepAliveTime(idleTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * @return number of reserved threads or -1 to indicate that the number is heuristically determined
     * @see #setReservedThreads(int)
     */
    @ManagedAttribute("the number of reserved threads in the pool")
    public int getReservedThreads()
    {
        if (isStarted())
            return getBean(ReservedThreadExecutor.class).getCapacity();
        return _reservedThreads;
    }

    /**
     * Sets the number of reserved threads.
     *
     * @param reservedThreads number of reserved threads or -1 to determine the number heuristically
     * @see #getReservedThreads()
     */
    public void setReservedThreads(int reservedThreads)
    {
        if (isRunning())
            throw new IllegalStateException(getState());
        _reservedThreads = reservedThreads;
    }

    public void setThreadsPriority(int priority)
    {
        _priority = priority;
    }

    public int getThreadsPriority()
    {
        return _priority;
    }

    /**
     * @return whether this thread pool uses daemon threads
     * @see #setDaemon(boolean)
     */
    @ManagedAttribute("whether this thread pool uses daemon threads")
    public boolean isDaemon()
    {
        return _daemon;
    }

    /**
     * @param daemon whether this thread pool uses daemon threads
     * @see Thread#setDaemon(boolean)
     */
    public void setDaemon(boolean daemon)
    {
        _daemon = daemon;
    }

    @ManagedAttribute("reports additional details in the dump")
    public boolean isDetailedDump()
    {
        return _detailedDump;
    }

    public void setDetailedDump(boolean detailedDump)
    {
        _detailedDump = detailedDump;
    }

    @Override
    @ManagedAttribute("number of threads in the pool")
    public int getThreads()
    {
        return _executor.getPoolSize();
    }

    @Override
    @ManagedAttribute("number of idle threads in the pool")
    public int getIdleThreads()
    {
        return _executor.getPoolSize() - _executor.getActiveCount();
    }

    @Override
    public void execute(Runnable command)
    {
        _executor.execute(command);
    }

    @Override
    public boolean tryExecute(Runnable task)
    {
        TryExecutor tryExecutor = _tryExecutor;
        return tryExecutor != null && tryExecutor.tryExecute(task);
    }

    @Override
    @ManagedAttribute(value = "thread pool is low on threads", readonly = true)
    public boolean isLowOnThreads()
    {
        return getThreads() == getMaxThreads() && _executor.getQueue().size() >= getIdleThreads();
    }

    @Override
    protected void doStart() throws Exception
    {
        if (_executor.isShutdown())
            throw new IllegalStateException("This thread pool is not restartable");
        for (int i = 0; i < _minThreads; ++i)
            _executor.prestartCoreThread();

        _tryExecutor = new ReservedThreadExecutor(this, _reservedThreads);
        addBean(_tryExecutor);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception
    {
        super.doStop();
        removeBean(_tryExecutor);
        _tryExecutor = TryExecutor.NO_TRY;
        _executor.shutdownNow();
        _budget.reset();
    }

    @Override
    public void join() throws InterruptedException
    {
        _executor.awaitTermination(getStopTimeout(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ThreadPoolBudget getThreadPoolBudget()
    {
        return _budget;
    }

    protected Thread newThread(Runnable job)
    {
        Thread thread = new Thread(_group, job);
        thread.setDaemon(isDaemon());
        thread.setPriority(getThreadsPriority());
        thread.setName(getName() + "-" + thread.getId());
        return thread;
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        String prefix = getName() + "-";
        List<Dumpable> threads = Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> entry.getKey().getName().startsWith(prefix))
                .map(entry ->
                {
                    Thread thread = entry.getKey();
                    StackTraceElement[] frames = entry.getValue();
                    String knownMethod = null;
                    for (StackTraceElement frame : frames)
                    {
                        if ("getTask".equals(frame.getMethodName()) && frame.getClassName().endsWith("ThreadPoolExecutor"))
                        {
                            knownMethod = "IDLE ";
                            break;
                        }
                        else if ("reservedWait".equals(frame.getMethodName()) && frame.getClassName().endsWith("ReservedThread"))
                        {
                            knownMethod = "RESERVED ";
                            break;
                        }
                        else if ("select".equals(frame.getMethodName()) && frame.getClassName().endsWith("SelectorProducer"))
                        {
                            knownMethod = "SELECTING ";
                            break;
                        }
                        else if ("accept".equals(frame.getMethodName()) && frame.getClassName().contains("ServerConnector"))
                        {
                            knownMethod = "ACCEPTING ";
                            break;
                        }
                    }
                    String known = knownMethod == null ? "" : knownMethod;
                    return new Dumpable()
                    {
                        @Override
                        public void dump(Appendable out, String indent) throws IOException
                        {
                            out.append(String.valueOf(thread.getId()))
                                    .append(" ")
                                    .append(thread.getName())
                                    .append(" p=").append(String.valueOf(thread.getPriority()))
                                    .append(" ")
                                    .append(known)
                                    .append(thread.getState().toString());
                            if (isDetailedDump())
                            {
                                out.append(System.lineSeparator());
                                if (known.isEmpty())
                                    ContainerLifeCycle.dump(out, indent, Arrays.asList(frames));
                            }
                            else
                            {
                                out.append(" @ ").append(frames.length > 0 ? String.valueOf(frames[0]) : "<no_stack_frames>")
                                        .append(System.lineSeparator());
                            }
                        }

                        @Override
                        public String dump()
                        {
                            return null;
                        }
                    };
                })
                .collect(Collectors.toList());

        List<Runnable> jobs = Collections.emptyList();
        if (isDetailedDump())
            jobs = new ArrayList<>(_executor.getQueue());
        dumpBeans(out, indent, threads, Collections.singletonList(new DumpableCollection("jobs - size=" + jobs.size(), jobs)));
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s]@%x{%s,%d<=%d<=%d,i=%d,q=%d}",
                getClass().getSimpleName(),
                getName(),
                hashCode(),
                getState(),
                getMinThreads(),
                getThreads(),
                getMaxThreads(),
                getIdleThreads(),
                _executor.getQueue().size());
    }
}
