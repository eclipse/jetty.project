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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.management.RuntimeErrorException;

import org.eclipse.jetty.start.builders.StartDirBuilder;
import org.eclipse.jetty.start.builders.StartIniBuilder;
import org.eclipse.jetty.start.fileinits.MavenLocalRepoFileInitializer;
import org.eclipse.jetty.start.fileinits.TestFileInitializer;
import org.eclipse.jetty.start.fileinits.UriFileInitializer;

/**
 * Build a start configuration in <code>${jetty.base}</code>, including
 * ini files, directories, and libs. Also handles License management.
 */
public class BaseBuilder
{
    public static interface Config
    {
        /**
         * Add a module to the start environment in <code>${jetty.base}</code>
         *
         * @param module
         *            the module to add
         * @return true if module was added, false if module was not added
         *         (because that module already exists)
         * @throws IOException if unable to add the module
         */
        public boolean addModule(Module module) throws IOException;
    }

    private static final String EXITING_LICENSE_NOT_ACKNOWLEDGED = "Exiting: license not acknowledged!";

    private final BaseHome baseHome;
    private final List<FileInitializer> fileInitializers;
    private final StartArgs startArgs;

    public BaseBuilder(BaseHome baseHome, StartArgs args)
    {
        this.baseHome = baseHome;
        this.startArgs = args;
        this.fileInitializers = new ArrayList<>();

        // Establish FileInitializers
        if (args.isTestingModeEnabled())
        {
            // No downloads performed
            fileInitializers.add(new TestFileInitializer());
        }
        else if (args.isDownload())
        {
            // Downloads are allowed to be performed
            // Setup Maven Local Repo
            Path localRepoDir = args.getMavenLocalRepoDir();
            if (localRepoDir != null)
            {
                // Use provided local repo directory
                fileInitializers.add(new MavenLocalRepoFileInitializer(baseHome,localRepoDir));
            }
            else
            {
                // No no local repo directory (direct downloads)
                fileInitializers.add(new MavenLocalRepoFileInitializer(baseHome));
            }

            // Normal URL downloads
            fileInitializers.add(new UriFileInitializer(baseHome));
        }
    }


    /**
     * Build out the Base directory (if needed)
     * 
     * @return true if base directory was changed, false if left unchanged.
     * @throws IOException if unable to build
     */
    public boolean build() throws IOException
    {
        Modules modules = startArgs.getAllModules();

        // Select all the added modules to determine which ones are newly enabled
        Set<String> enabled = new HashSet<>();
        Set<String> startDModules = new HashSet<>();
        Set<String> startModules = new HashSet<>();
        if (!startArgs.getAddToStartdIni().isEmpty() || !startArgs.getAddToStartIni().isEmpty())
        {
            if (startArgs.isAddToStartdFirst())
            {
                for (String name:startArgs.getAddToStartdIni())
                    startDModules.addAll(modules.select(name,"--add-to-startd"));
                for (String name:startArgs.getAddToStartIni())
                    startModules.addAll(modules.select(name,"--add-to-start"));
            }
            else
            {
                for (String name:startArgs.getAddToStartIni())
                    startModules.addAll(modules.select(name,"--add-to-start"));
                for (String name:startArgs.getAddToStartdIni())
                    startDModules.addAll(modules.select(name,"--add-to-startd"));
            }
            enabled.addAll(startDModules);
            enabled.addAll(startModules);
        }

        if (StartLog.isDebugEnabled())
            StartLog.debug("startD=%s start=%s",startDModules,startModules);
        
        // Check the licenses
        if (startArgs.isLicenseCheckRequired())
        {
            Licensing licensing = new Licensing();
            for (String name : enabled)
                licensing.addModule(modules.get(name));
            
            if (licensing.hasLicenses())
            {
                if (startArgs.isApproveAllLicenses())
                {
                    StartLog.info("All Licenses Approved via Command Line Option");
                }
                else if (!licensing.acknowledgeLicenses())
                {
                    StartLog.warn(EXITING_LICENSE_NOT_ACKNOWLEDGED);
                    System.exit(1);
                }
            }
        }

        // generate the files
        List<FileArg> files = new ArrayList<FileArg>();
        AtomicReference<BaseBuilder.Config> builder = new AtomicReference<>();
        AtomicBoolean modified = new AtomicBoolean();
        Consumer<Module> do_build_add = module ->
        {
            try
            {
                if (module.isSkipFilesValidation())
                {
                    StartLog.debug("Skipping [files] validation on %s",module.getName());
                } 
                else 
                {
                    if (builder.get().addModule(module))
                        modified.set(true);
                    for (String file : module.getFiles())
                        files.add(new FileArg(module,startArgs.getProperties().expand(file)));
                }
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        };
        
        if (!startDModules.isEmpty())
        {
            builder.set(new StartDirBuilder(this));
            startDModules.stream().map(n->modules.get(n)).forEach(do_build_add);
        }

        if (!startModules.isEmpty())
        {
            builder.set(new StartIniBuilder(this));
            startModules.stream().map(n->modules.get(n)).forEach(do_build_add);
        }

        files.addAll(startArgs.getFiles());
        if (!files.isEmpty() && processFileResources(files))
            modified.set(Boolean.TRUE);
        
        return modified.get();
    }
    
        
    public BaseHome getBaseHome()
    {
        return baseHome;
    }

    public StartArgs getStartArgs()
    {
        return startArgs;
    }

    /**
     * Process a specific file resource
     * 
     * @param arg
     *            the fileArg to work with
     * @param file
     *            the resolved file reference to work with
     * @return true if change was made as a result of the file, false if no change made.
     * @throws IOException
     *             if there was an issue in processing this file
     */
    private boolean processFileResource(FileArg arg, Path file) throws IOException
    {
        if (startArgs.isDownload() && (arg.uri != null))
        {
            // now on copy/download paths (be safe above all else)
            if (!file.startsWith(baseHome.getBasePath()))
            {
                throw new IOException("For security reasons, Jetty start is unable to process maven file resource not in ${jetty.base} - " + file);
            }
            
            // make the directories in ${jetty.base} that we need
            boolean modified = FS.ensureDirectoryExists(file.getParent());
            
            URI uri = URI.create(arg.uri);

            // Process via initializers
            for (FileInitializer finit : fileInitializers)
            {
                if (finit.init(uri,file,arg.location))
                {
                    // Completed successfully
                    return true;
                }
            }

            return false;
        }
        else
        {
            // Process directly
            boolean isDir = arg.location.endsWith("/");

            if (FS.exists(file))
            {
                // Validate existence
                if (isDir)
                {
                    if (!Files.isDirectory(file))
                    {
                        throw new IOException("Invalid: path should be a directory (but isn't): " + file);
                    }
                    if (!FS.canReadDirectory(file))
                    {
                        throw new IOException("Unable to read directory: " + file);
                    }
                }
                else
                {
                    if (!FS.canReadFile(file))
                    {
                        throw new IOException("Unable to read file: " + file);
                    }
                }

                return false;
            }

            if (isDir)
            {
                // Create directory
                StartLog.log("MKDIR",baseHome.toShortForm(file));
                return FS.ensureDirectoryExists(file);
            }
            else
            {
                // Warn on missing file (this has to be resolved manually by user)
                String shortRef = baseHome.toShortForm(file);
                if (startArgs.isTestingModeEnabled())
                {
                    StartLog.log("TESTING MODE","Skipping required file check on: %s",shortRef);
                    return false;
                }

                StartLog.warn("Missing Required File: %s",baseHome.toShortForm(file));
                startArgs.setRun(false);
                if (arg.uri != null)
                {
                    StartLog.warn("  Can be downloaded From: %s",arg.uri);
                    StartLog.warn("  Run start.jar --create-files to download");
                }

                return false;
            }
        }
    }

    /**
     * Process the {@link FileArg} for startup, assume that all licenses have
     * been acknowledged at this stage.
     *
     * @param files
     *            the list of {@link FileArg}s to process
     * @return true if base directory modified, false if left untouched
     */
    private boolean processFileResources(List<FileArg> files) throws IOException
    {
        if ((files == null) || (files.isEmpty()))
        {
            return false;
        }

        boolean dirty = false;

        List<String> failures = new ArrayList<String>();

        for (FileArg arg : files)
        {
            Path file = baseHome.getBasePath(arg.location);
            try
            {
                boolean processed = processFileResource(arg,file);
                dirty |= processed;
            }
            catch (Throwable t)
            {
                StartLog.warn(t);
                failures.add(String.format("[%s] %s - %s",t.getClass().getSimpleName(),t.getMessage(),file.toAbsolutePath().toString()));
            }
        }

        if (!failures.isEmpty())
        {
            StringBuilder err = new StringBuilder();
            err.append("Failed to process all file resources.");
            for (String failure : failures)
            {
                err.append(System.lineSeparator()).append(" - ").append(failure);
            }
            StartLog.warn(err.toString());

            throw new RuntimeException(err.toString());
        }

        return dirty;
    }
}
