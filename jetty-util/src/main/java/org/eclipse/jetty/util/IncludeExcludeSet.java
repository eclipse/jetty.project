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

package org.eclipse.jetty.util;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/** Utility class to maintain a set of inclusions and exclusions.
 * <p>Maintains a set of included and excluded elements.  The method {@link #test(Object)}
 * will return true IFF the passed object is not in the excluded set AND ( either the 
 * included set is empty OR the object is in the included set) 
 * <p>The type of the underlying {@link Set} used may be passed into the 
 * constructor, so special sets like Servlet PathMap may be used.
 * <p>
 * @param <T> The type of element of the set (often a pattern)
 * @param <P> The type of the instance passed to the predicate 
 */
public class IncludeExcludeSet<T,P> implements Predicate<P>
{
    private final Set<T> _includes;
    private final Predicate<P> _includePredicate;
    private final Set<T> _excludes;
    private final Predicate<P> _excludePredicate;
    
    private static class SetContainsPredicate<T> implements Predicate<T>
    {
        private final Set<T> set;
        
        public SetContainsPredicate(Set<T> set)
        {
            this.set = set;
        }
        
        @Override
        public boolean test(T item)
        {
            return set.contains(item);
        }
    }
    
    /**
     * Default constructor over {@link HashSet}
     */
    public IncludeExcludeSet()
    {
        this(HashSet.class);
    }
    
    /**
     * Construct an IncludeExclude.
     * @param setClass The type of {@link Set} to using internally to hold patterns. Two instances will be created.
     * one for include patterns and one for exclude patters.  If the class is also a {@link Predicate},
     * then it is also used as the item test for the set, otherwise a {@link SetContainsPredicate} instance
     * is created.
     * @param <SET> The type of a set to use as the backing store
     */
    public <SET extends Set<T>> IncludeExcludeSet(Class<SET> setClass)
    {
        try
        {
            _includes = setClass.getDeclaredConstructor().newInstance();
            _excludes = setClass.getDeclaredConstructor().newInstance();
            
            if(_includes instanceof Predicate) 
            {
                _includePredicate = (Predicate<P>)_includes;
            } 
            else 
            {
                _includePredicate = new SetContainsPredicate(_includes);
            }
            
            if(_excludes instanceof Predicate) 
            {
                _excludePredicate = (Predicate<P>)_excludes;
            } 
            else 
            {
                _excludePredicate = new SetContainsPredicate(_excludes);
            }
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Construct an IncludeExclude
     * 
     * @param includeSet the Set of items that represent the included space 
     * @param includePredicate the Predicate for included item testing (null for simple {@link Set#contains(Object)} test)
     * @param excludeSet the Set of items that represent the excluded space
     * @param excludePredicate the Predicate for excluded item testing (null for simple {@link Set#contains(Object)} test)
     * @param <SET> The type of a set to use as the backing store
     */
    public <SET extends Set<T>> IncludeExcludeSet(Set<T> includeSet, Predicate<P> includePredicate, Set<T> excludeSet, Predicate<P> excludePredicate)
    {
        Objects.requireNonNull(includeSet,"Include Set");
        Objects.requireNonNull(includePredicate,"Include Predicate");
        Objects.requireNonNull(excludeSet,"Exclude Set");
        Objects.requireNonNull(excludePredicate,"Exclude Predicate");
        
        _includes = includeSet;
        _includePredicate = includePredicate;
        _excludes = excludeSet;
        _excludePredicate = excludePredicate;
    }
    
    public void include(T element)
    {
        _includes.add(element);
    }
    
    public void include(T... element)
    {
        for (T e: element)
            _includes.add(e);
    }

    public void exclude(T element)
    {
        _excludes.add(element);
    }
    
    public void exclude(T... element)
    {
        for (T e: element)
            _excludes.add(e);
    }
    
    @Deprecated
    public boolean matches(P t)
    {
        return test(t);
    }
    
    @Override
    public boolean test(P t)
    {
        if (!_includes.isEmpty() && !_includePredicate.test(t))
            return false;
        return !_excludePredicate.test(t);
    }
    
    /**
     * Test Included and not Excluded
     * @param t The item to test
     * @return Boolean.TRUE if t is included, Boolean.FALSE if t is excluded and null if neither
     */
    public Boolean isIncludedAndNotExcluded(P t)
    {
        if (_excludePredicate.test(t))
            return Boolean.FALSE;
        if (_includePredicate.test(t))
            return Boolean.TRUE;
        
        return null;
    }
    
    public boolean hasIncludes()
    {
        return !_includes.isEmpty();
    }
    
    public int size()
    {
        return _includes.size()+_excludes.size();
    }
    
    public Set<T> getIncluded()
    {
        return _includes;
    }
    
    public Set<T> getExcluded()
    {
        return _excludes;
    }

    public void clear()
    {
        _includes.clear();
        _excludes.clear();
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{i=%s,ip=%s,e=%s,ep=%s}",this.getClass().getSimpleName(),hashCode(),_includes,_includePredicate,_excludes,_excludePredicate);
    }

    public boolean isEmpty()
    {
        return _includes.isEmpty() && _excludes.isEmpty();
    }
}
