//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.start;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Access for all modules declared, as well as what is enabled.
 */
public class Modules implements Iterable<Module>
{
    private final List<Module> _modules = new ArrayList<>();
    private final Map<String,Module> _names = new HashMap<>();
    private final Map<String,Set<Module>> _provided = new HashMap<>();
    private final BaseHome _baseHome;
    private final StartArgs _args;

    public Modules(BaseHome basehome, StartArgs args)
    {
        this._baseHome = basehome;
        this._args = args;
        
        String java_version = System.getProperty("java.version");
        if (java_version!=null)
        {
            args.setProperty("java.version",java_version,"<internal>",false);
        }        
    }

    public void dump()
    {
        List<String> ordered = _modules.stream().map(m->{return m.getName();}).collect(Collectors.toList());
        Collections.sort(ordered);
        ordered.stream().map(n->{return get(n);}).forEach(module->
        {
            String status = "[ ]";
            if (module.isTransitive())
            {
                status = "[t]";
            }
            else if (module.isEnabled())
            {
                status = "[x]";
            }

            System.out.printf("%n %s Module: %s%n",status,module.getName());
            if (module.getProvides().size()>1)
            {
                System.out.printf("   Provides: %s%n",module.getProvides());
            }
            for (String description : module.getDescription())
            {
                System.out.printf("           : %s%n",description);
            }
            for (String parent : module.getDepends())
            {
                System.out.printf("     Depend: %s%n",parent);
            }
            for (String optional : module.getOptional())
            {
                System.out.printf("   Optional: %s%n",optional);
            }
            for (String lib : module.getLibs())
            {
                System.out.printf("        LIB: %s%n",lib);
            }
            for (String xml : module.getXmls())
            {
                System.out.printf("        XML: %s%n",xml);
            }
            for (String jvm : module.getJvmArgs())
            {
                System.out.printf("        JVM: %s%n",jvm);
            }
            if (module.isEnabled())
            {
                for (String selection : module.getEnableSources())
                {
                    System.out.printf("    Enabled: %s%n",selection);
                }
            }
        });
    }

    public void dumpEnabled()
    {
        int i=0;
        for (Module module:getEnabled())
        {
            String name=module.getName();
            String index=(i++)+")";
            for (String s:module.getEnableSources())
            {
                System.out.printf("  %4s %-15s %s%n",index,name,s);
                index="";
                name="";
            }
        }
    }

    public void registerAll() throws IOException
    {
        for (Path path : _baseHome.getPaths("modules/*.mod"))
        {
            registerModule(path);
        }
    }

    private Module registerModule(Path file)
    {
        if (!FS.canReadFile(file))
        {
            throw new IllegalStateException("Cannot read file: " + file);
        }
        String shortName = _baseHome.toShortForm(file);
        try
        {
            StartLog.debug("Registering Module: %s",shortName);
            Module module = new Module(_baseHome,file);
            _modules.add(module);
            _names.put(module.getName(),module);
            module.getProvides().forEach(n->{
                _provided.computeIfAbsent(n,k->new HashSet<Module>()).add(module);
            });
            
            return module;
        }
        catch (Error|RuntimeException t)
        {
            throw t;
        }
        catch (Throwable t)
        {
            throw new IllegalStateException("Unable to register module: " + shortName,t);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();
        str.append("Modules[");
        str.append("count=").append(_modules.size());
        str.append(",<");
        final AtomicBoolean delim = new AtomicBoolean(false);
        _modules.forEach(m->
        {
            if (delim.get())
                str.append(',');
            str.append(m.getName());
            delim.set(true);
        });
        str.append(">");
        str.append("]");
        return str.toString();
    }

    public void sort()
    {
        TopologicalSort<Module> sort = new TopologicalSort<>();
        for (Module module: _modules)
        {
            Consumer<String> add = name ->
            {
                Module dependency = _names.get(name);
                if (dependency!=null)
                    sort.addDependency(module,dependency);
                
                Set<Module> provided = _provided.get(name);
                if (provided!=null)
                    provided.forEach(p->sort.addDependency(module,p));
            };
            module.getDepends().forEach(add);
            module.getOptional().forEach(add);
        }
        
        sort.sort(_modules);
    }

    public List<Module> getEnabled()
    {
        return _modules.stream().filter(m->{return m.isEnabled();}).collect(Collectors.toList());
    }

    /** Enable a module
     * @param name The name of the module to enable
     * @param enabledFrom The source the module was enabled from
     * @return The set of modules newly enabled
     */
    public Set<String> enable(String name, String enabledFrom)
    {
        Module module = get(name);
        if (module==null)
            throw new UsageException(UsageException.ERR_UNKNOWN,"Unknown module='%s'",name);

        Set<String> enabled = new HashSet<>();
        enable(enabled,module,enabledFrom,false);
        return enabled;
    }

    private void enable(Set<String> newlyEnabled, Module module, String enabledFrom, boolean transitive)
    {
        StartLog.debug("enable %s from %s transitive=%b",module,enabledFrom,transitive);
        
        // Check that this is not already provided by another module!
        for (String name:module.getProvides())
        {
            Set<Module> providers = _provided.get(name);
            if (providers!=null)
            {
                providers.forEach(p-> 
                { 
                    if (p!=module && p.isEnabled())
                    {
                        // If the already enabled module is transitive and this enable is not
                        if (p.isTransitive() && !transitive)
                        {
                            p.clearTransitiveEnable();
                            if (p.hasDefaultConfig())
                            {
                                p.getDefaultConfig().forEach(a->
                                {
                                    _args.removeProperty(a,p.getName());
                                });
                            }
                        }
                        else
                            throw new UsageException("%s provides %s, which is already provided by %s enabled in %s",module.getName(),name,p.getName(),p.getEnableSources());
                    }
                });
            }   
        }
      
        // Enable the  module
        if (module.enable(enabledFrom,transitive))
        {
            StartLog.debug("enabled %s",module.getName());
            newlyEnabled.add(module.getName());
            
            // Expand module properties
            module.expandProperties(_args.getProperties());
            
            // Apply default configuration
            if (module.hasDefaultConfig())
            {
                for(String line:module.getDefaultConfig())
                    _args.parse(line,module.getName(),false);
                for (Module m:_modules)
                    m.expandProperties(_args.getProperties());
            }
        }
        else if (module.isTransitive() && module.hasIniTemplate())
            newlyEnabled.add(module.getName());
        
        
        // Process module dependencies (always processed as may be dynamic)
        for(String dependsOn:module.getDepends())
        {
            // Look for modules that provide that dependency
            Set<Module> providers = _provided.get(dependsOn);
            StartLog.debug("%s depends on %s provided by ",module,dependsOn,providers);
            
            // If there are no known providers of the module
            if ((providers==null||providers.isEmpty()))
            {
                // look for a dynamic module
                if (dependsOn.contains("/"))
                {
                    Path file = _baseHome.getPath("modules/" + dependsOn + ".mod");
                    registerModule(file).expandProperties(_args.getProperties());
                    sort();
                    providers = _provided.get(dependsOn);
                    if (providers==null || providers.isEmpty())
                        throw new UsageException("Module %s does not provide %s",_baseHome.toShortForm(file),dependsOn);

                    enable(newlyEnabled,providers.stream().findFirst().get(),"dynamic dependency of "+module.getName(),true);
                    continue;
                }
                throw new UsageException("No module found to provide %s for %s",dependsOn,module);
            }
            
            // If a provider is already enabled, then add a transitive enable
            long enabled=providers.stream().filter(Module::isEnabled).count();
            if (enabled>0)
                providers.stream().filter(m->m.isEnabled()&&m!=module).forEach(m->enable(newlyEnabled,m,"transitive provider of "+dependsOn+" for "+module.getName(),true));
            else
            {
                // Is there an obvious default?
                Optional<Module> dftProvider = providers.stream().filter(m->m.getName().equals(dependsOn)).findFirst();
                if (dftProvider.isPresent())
                    enable(newlyEnabled,dftProvider.get(),"default provider of "+dependsOn+" for "+module.getName(),true);
                else if (StartLog.isDebugEnabled())
                    StartLog.debug("Module %s requires %s from one of %s",module,dependsOn,providers);
            }
        }
    }
    
    public Module get(String name)
    {
        return _names.get(name);
    }

    @Override
    public Iterator<Module> iterator()
    {
        return _modules.iterator();
    }

    public Stream<Module> stream()
    {
        return _modules.stream();
    }

    public void checkEnabledModules()
    {
        StringBuilder unsatisfied=new StringBuilder();
        _modules.stream().filter(Module::isEnabled).forEach(m->
        {
            // Check dependencies
            m.getDepends().forEach(d->
            {
                Set<Module> providers =_provided.get(d);
                if (providers.stream().filter(Module::isEnabled).count()==0)
                { 
                    if (unsatisfied.length()>0)
                        unsatisfied.append(',');
                    unsatisfied.append(m.getName());
                    StartLog.warn("Module %s requires %s from one of %s%n",m.getName(),d,providers);
                }
            });
        });
        
        if (unsatisfied.length()>0)
            throw new UsageException(-1,"Unsatisfied module dependencies: "+unsatisfied);
    }
    
}
