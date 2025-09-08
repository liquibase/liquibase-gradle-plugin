/*
 * Copyright 2011-2025 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 */

package org.liquibase.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

import static org.liquibase.gradle.Util.versionAtLeast

/**
 * Gradle task that calls Liquibase to run a command.
 * <p>
 * Liquibase tasks are JavaExec tasks to try to minimize the liquibase dependencies that are in the
 * Gradle build script's classpath.  Also , we can't use the the Liquibase CommandScope API directly
 * due to bugs.
 *
 * @author Stven C. Saliman
 */
class LiquibaseTask extends DefaultTask {

    /** The Liquibase command to run */
    @Input
    final Property<String> commandName

    /** The supported arguments of the command */
    @Input
    final SetProperty<String> commandArguments

    /** Activities for configuration-cache friendly execution. */
    @Input 
    final MapProperty<String, ActivitySpec> activitySpecMap

    /** Main class to run Liquibase CLI. Mirrors JavaExec's mainClass property for compatibility. */
    @Input
    Provider<String> mainClass

    /** Build Service Provider that will build the arguments to send to Liquibase. */
    @ServiceReference
    Provider<ArgumentBuilderService> argumentBuilderService

    /** a {@code Provider} that can provide a value for the liquibase version. */
    private Provider<String> liquibaseVersionProvider

    /** Provider for runList from the extension, captured during configuration. */
    private Provider<String> runListProvider

    /** Provider for jvmArgs from the extension, captured during configuration. */
    private Provider<List<String>> jvmArgsProvider

    /** Provider for the liquibaseRuntime configuration's classpath, captured during configuration. */
    private Provider<FileCollection> liquibaseRuntimeConfigurationProvider

    /** Provider for a custom main class name from the extension, captured during configuration. */
    private Provider<String> customMainClassProvider

    /** Exec operations to launch Liquibase without accessing Project at execution time. */
    private final ExecOperations execOperations

    /** ProviderFactory for building Providers without accessing Project at execution time. */
    private final ProviderFactory providerFactory

    @Inject
    LiquibaseTask(
            ObjectFactory objects,
            ExecOperations execOperations,
            ProviderFactory providerFactory
    ) {
        this.execOperations = execOperations
        this.providerFactory = providerFactory
        this.mainClass = objects.property(String)
        this.commandName = objects.property(String)
        this.commandArguments = objects.setProperty(String)
        this.activitySpecMap = objects.mapProperty(String, ActivitySpec)

        // Initialize Providers at configuration time and set mainClass convention
        // so it always has a value
        this.liquibaseVersionProvider = createLiquibaseVersionProvider()
        LiquibaseExtension ext = project.extensions.getByType(LiquibaseExtension)
        this.customMainClassProvider = ext.mainClassName
        this.runListProvider = ext.runList
        this.jvmArgsProvider = ext.jvmArgs
        this.liquibaseRuntimeConfigurationProvider = project.configurations.named(
            LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION
        ).map { it.incoming.files }
        this.mainClass.set(
            createMainClassProvider(this.liquibaseVersionProvider, this.customMainClassProvider)
        )

        // Convert NamedDomainObjectContainer<Activity> to configuration-cache-friendly properties
        convertActivitiesToSpecs(ext.activities)
    }

    /**
     * Convert NamedDomainObjectContainer<Activity> to configuration-cache-friendly properties
     * at configuration time to avoid accessing Gradle model objects during execution.
     */
    private void convertActivitiesToSpecs(NamedDomainObjectContainer<Activity> activities) {
        activities.each { activity ->
            ActivitySpec spec = new ActivitySpec(activity)
            this.activitySpecMap.put(activity.name, spec)
        }
    }

    /**
     * Do the work of this task.
     */
    @TaskAction
    void runTask() {
        String runList = runListProvider.getOrNull()

        if (activitySpecMap.get().isEmpty()) {
            throw new LiquibaseConfigurationException(
                    "No activities defined.  Did you forget to add a 'liquibase' block \
to your build.gradle file?")
        }
        if ( runList != null && runList.trim().size() > 0 ) {
            runList.split(',').each { activityName ->
                activityName = activityName.trim()
                ActivitySpec activitySpec = activitySpecMap.get().get(activityName)
                if ( activitySpec == null ) {
                    throw new LiquibaseConfigurationException(
                            "No activity named '${activityName}' is defined the \
liquibase configuration")
                }
                runLiquibase(activitySpec)
            }
        } else {
            activitySpecMap.get().values().each { activitySpec ->
                runLiquibase(activitySpec)
            }
        }
    }

    /**
     * Build the proper command line and call Liquibase.
     *
     * @param activitySpec the activity spec holding the Liquibase particulars.
     */
    void runLiquibase(ActivitySpec activitySpec) {
        List<String> args = argumentBuilderService.get()
                .buildLiquibaseArgs(activitySpec, commandName.get(), commandArguments.get())

        FileCollection classpath = liquibaseRuntimeConfigurationProvider.get()
        if ( classpath == null || classpath.isEmpty() ) {
            throw new LiquibaseConfigurationException(
                    "No liquibaseRuntime dependencies were defined.  You must at least add \
Liquibase itself as a liquibaseRuntime dependency.")
        }
        String mainCls = mainClass
                .getOrElse("liquibase.integration.commandline.LiquibaseCommandLine")
        List<String> jvmArgsList = this.jvmArgsProvider.get()
        logger.quiet("liquibase-plugin: Running the '${activitySpec.name}' activity...")
        logger.debug("liquibase-plugin: The ${mainCls} class will be used to run Liquibase")
        logger.debug(
                "liquibase-plugin: Liquibase will be run with the following jvmArgs: ${jvmArgsList}")
        logger.debug("liquibase-plugin: Running 'liquibase ${args.join(" ")}'")

        // Launch Liquibase using injected ExecOperations without touching Project at execution time
        execOperations.javaexec { spec ->
            spec.classpath = classpath
            spec.mainClass.set(mainCls)
            spec.jvmArgs(jvmArgsList)
            spec.systemProperties(System.properties)
            spec.args(args)
        }
    }

    /**
     * Create a {@code Provider} that can return the the main class to be used when running
     * Liquibase.  Since we can't call setMain directly in Gradle 6.4+, we had to register a
     * listener that watched for changes to the extension's "mainClassName" property.  But if the
     * user didn't set a value, we'll need to set one before we try to run Liquibase so the property
     * listener can set the class name correctly.
     * <p>
     * This method chooses the right default based on the version of liquibase it is given.
     *
     * @param liquibaseVersionProvider a {@code Provider} that can return the version of liquibase
     *         we're using.
     * @return a Provider that can return the correct Liquibase main class to use.
     */
    Provider<String> createMainClassProvider(
            Provider<String> liquibaseVersionProvider,
            Provider<String> customMainClassProvider
    ) {
        // map the LiquibaseVersion to a class name
        return liquibaseVersionProvider.map {
            // If we have a custom class name, it doesn't matter what version of Liquibase we have,
            // just use the given class name.
            Object customMainClass = customMainClassProvider.getOrNull()
            if ( customMainClass ) {
                logger.debug("liquibase-plugin: The extension's mainClassName was set, \
skipping version detection.")
                return customMainClass as String
            }

            if ( versionAtLeast(it, '4.24') ) {
                logger.debug("liquibase-plugin: Using the 4.24+ command line parser.")
                return "liquibase.integration.commandline.LiquibaseCommandLine"
            } else {
                throw new LiquibaseConfigurationException(
                        "The Liquibase gradle plugin doesn't support this version of Liquibase!")
            }
        }
    }

    /**
     * This method creates a {@code Provider} that detects and returns the resolved version of
     * Liquibase in the liquibaseRuntime configuration
     * <p>
     * If for some reason, it finds Liquibase in the classpath more than once, the last one it
     * finds, wins.
     *
     * @param project the Gradle project object from which we can get a classpath.
     * @return a Provider that can return the version of Liquibase found
     * @throws LiquibaseConfigurationException if no version if Liquibase is found at runtime.
     */
    Provider<String> createLiquibaseVersionProvider() {
        // Make a new provider whose value is the resolved artifact set, then call map, which maps
        // the artifacts to a version string, returning that version string as the provider's value.
        return project.configurations.named(
                LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION
        ).map {
            it.resolvedConfiguration.resolvedArtifacts
        }.map { Set<ResolvedArtifact> artifacts ->
            Set<ResolvedArtifact> coreDeps = artifacts.findAll { dep ->
                dep.moduleVersion.id.name == 'liquibase-core'
            }

            if ( coreDeps.size() < 1 ) {
                throw new LiquibaseConfigurationException("Liquibase-core was not found in \
the liquibaseRuntime configuration!")
            }
            if ( coreDeps.size() > 1 ) {
                logger.warn("liquibase-plugin: More than one version of the liquibase-core \
dependency was found in the liquibaseRuntime configuration!")
            }

            String foundVersion = coreDeps.last()?.moduleVersion?.id?.version
            logger.debug("liquibase-plugin: Found version ${foundVersion} of liquibase-core.")
            return foundVersion
        }
    }
}
