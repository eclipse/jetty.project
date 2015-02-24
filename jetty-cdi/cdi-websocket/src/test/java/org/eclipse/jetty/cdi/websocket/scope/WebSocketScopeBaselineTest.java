//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.cdi.websocket.scope;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Set;

import javax.enterprise.inject.spi.Bean;

import org.eclipse.jetty.cdi.core.AnyLiteral;
import org.eclipse.jetty.cdi.core.ScopedInstance;
import org.eclipse.jetty.cdi.core.logging.Logging;
import org.eclipse.jetty.cdi.websocket.WebSocketScopeContext;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class WebSocketScopeBaselineTest
{
    private static Weld weld;
    private static WeldContainer container;

    @BeforeClass
    public static void startWeld()
    {
        Logging.config();
        weld = new Weld();
        container = weld.initialize();
    }

    @AfterClass
    public static void stopWeld()
    {
        weld.shutdown();
    }

    @Test
    public void testScopeBehavior() throws Exception
    {
        ScopedInstance<WebSocketScopeContext> wsScopeBean = newInstance(WebSocketScopeContext.class);
        WebSocketScopeContext wsScope = wsScopeBean.instance;

        wsScope.create();
        Meal meal1;
        try
        {
            wsScope.begin();
            ScopedInstance<Meal> meal1Bean = newInstance(Meal.class);
            meal1 = meal1Bean.instance;
            ScopedInstance<Meal> meal2Bean = newInstance(Meal.class);
            Meal meal2 = meal2Bean.instance;
            assertThat("Meals are not the same",meal1,not(sameInstance(meal2)));

            assertThat("Meal 1 Entree Constructed",meal1.getEntree().isConstructed(),is(true));
            assertThat("Meal 1 Side Constructed",meal1.getSide().isConstructed(),is(true));

            assertThat("Meal parts not the same",meal1.getEntree(),not(sameInstance(meal1.getSide())));

            assertThat("Meal entrees are the same",meal1.getEntree(),sameInstance(meal2.getEntree()));
            assertThat("Meal sides are the same",meal1.getSide(),sameInstance(meal2.getSide()));

            meal1Bean.destroy();
            meal2Bean.destroy();
        }
        finally
        {
            wsScope.end();
        }

        assertThat("Meal 1 entree destroyed",meal1.getEntree().isDestroyed(),is(false));
        assertThat("Meal 1 side destroyed",meal1.getSide().isDestroyed(),is(false));
        wsScope.destroy();

        assertThat("Meal 1 entree destroyed",meal1.getEntree().isDestroyed(),is(true));
        assertThat("Meal 1 side destroyed",meal1.getSide().isDestroyed(),is(true));
        wsScopeBean.destroy();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> ScopedInstance<T> newInstance(Class<T> clazz) throws Exception
    {
        ScopedInstance sbean = new ScopedInstance();
        Set<Bean<?>> beans = container.getBeanManager().getBeans(clazz,AnyLiteral.INSTANCE);
        if (beans.size() > 0)
        {
            sbean.bean = beans.iterator().next();
            sbean.creationalContext = container.getBeanManager().createCreationalContext(sbean.bean);
            sbean.instance = container.getBeanManager().getReference(sbean.bean,clazz,sbean.creationalContext);
            return sbean;
        }
        else
        {
            throw new Exception(String.format("Can't find class %s",clazz));
        }
    }
}
