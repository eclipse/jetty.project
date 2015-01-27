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

package org.eclipse.jetty.start.builders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.jetty.start.BaseBuilder;
import org.eclipse.jetty.start.BaseHome;
import org.eclipse.jetty.start.FS;
import org.eclipse.jetty.start.Module;
import org.eclipse.jetty.start.StartLog;
import org.eclipse.jetty.start.graph.OnlyTransitivePredicate;

/**
 * Management of the <code>${jetty.base}/start.d/</code> based configuration.
 * <p>
 * Implementation of the <code>--add-to-startd=[name]</code> command line behavior
 */
public class StartDirBuilder implements BaseBuilder.Config
{
    private final BaseHome baseHome;
    private final Path startDir;
    
    public StartDirBuilder(BaseBuilder baseBuilder) throws IOException
    {
        this.baseHome = baseBuilder.getBaseHome();
        this.startDir = baseHome.getBasePath("start.d");
        FS.ensureDirectoryExists(startDir);
    }
    
    @Override
    public boolean addModule(Module module) throws IOException
    {
        if (module.isVirtual())
        {
            // skip, no need to reference
            StartLog.info("%-15s skipping (virtual module)",module.getName());
            return false;
        }
        
        String mode = "";
        if (module.matches(OnlyTransitivePredicate.INSTANCE))
        {
            mode = "(transitively) ";
        }
        
        // Create start.d/{name}.ini
        Path ini = startDir.resolve(module.getName() + ".ini");
        StartLog.info("%-15s initialised %sin %s",module.getName(),mode,baseHome.toShortForm(ini));
        
        try (BufferedWriter writer = Files.newBufferedWriter(ini,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING))
        {
            writeModuleSection(writer,module);
        }

        return true;
    }
    
    protected void writeModuleSection(BufferedWriter writer, Module module)
    {
        PrintWriter out = new PrintWriter(writer);

        out.println("# --------------------------------------- ");
        out.println("# Module: " + module.getName());

        out.println("--module=" + module.getName());

        for (String line : module.getDefaultConfig())
        {
            out.println(line);
        }

        out.println();
        out.flush();
    }
}
