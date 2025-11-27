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
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

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
    def commandName

    /** The supported arguments of the command */
    @Input
    def commandArguments

    /** The argument builder that will build the arguments to send to Liquibase. */
    @Internal
    ArgumentBuilder argumentBuilder

    /** The {@link LiquibaseInfo} property we can use at execution time to get information about this run */
    @Internal
    final Property<LiquibaseInfo> liquibaseInfo

    /** The {@link ProjectInfo} property we can use at execution time to get information about the project */
    @Internal
    final Property<ProjectInfo> projectInfo

    /** The classpath to use during execution */
    @Classpath
    FileCollection classPath

    /** Exec operations to launch Liquibase without accessing Project at execution time. */
    @Inject
    private ExecOperations execOperations

    /** ProviderFactory for building Providers without accessing Project at execution time. */
    private ProviderFactory providerFactory

    /** a {@code Provider} that can provide a value for the liquibase version. */
    private Provider<String> iquibaseVersionProvider

    /**
     * Initializing constructor.  Sets up our liquibaseInfo and projectInfo in a way that is
     * compatible with the Gradle Configuration Cache
     */
    @Inject
    LiquibaseTask(ExecOperations execOperations, ProviderFactory providerFactory) {
        this.execOperations = execOperations
        this.providerFactory = providerFactory
        liquibaseInfo = project.objects.property(LiquibaseInfo)
        projectInfo = project.objects.property(ProjectInfo)
        classPath = project.configurations.getByName(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION)
    }

    /**
     * Set up the project info and liquibase info properties during task configuration.  Each will
     * be set up with provider closures that make the actual objects at execution time, but in a
     * configuration-cache safe way.
     *
     * @param closure
     * @return
     */
    @Override
    Task configure(Closure closure) {
        def configProject = project
        projectInfo.set(project.provider {
            new ProjectInfo(configProject)
        })
        liquibaseInfo.set(project.provider {
            new LiquibaseInfo(configProject)
        })
        return super.configure(closure)
    }

    /**
     * Do the work of this task by building the liquibase argument list and calling liquibase in a
     * new VM.
     */
    @TaskAction
    void exec() {
        // Start by figuring out what to run in this execution
        def projectInfo = this.projectInfo.get()
        def activities = projectInfo.activities
        def runList = projectInfo.runList

        if ( activities == null || activities.size() == 0 ) {
            throw new LiquibaseConfigurationException("No activities defined.  Did you forget to add a 'liquibase' block to your build.gradle file?")
        }

        if ( runList != null && runList.trim().size() > 0 ) {
            runList.split(',').each { activityName ->
                activityName = activityName.trim()
                def activity = activities.find { it.name == activityName }
                if ( activity == null ) {
                    throw new LiquibaseConfigurationException("No activity named '${activityName}' is defined the liquibase configuration")
                }
                runLiquibase(activity)
            }
        } else
            activities.each { activity ->
                runLiquibase(activity)
            }
    }

    /**
     * Build the proper command line and call Liquibase.
     *
     * @param activity the activity holding the Liquibase particulars.
     */
    def runLiquibase(activity) {
        def projectInfo = this.projectInfo.get()
        def liquibaseInfo = this.liquibaseInfo.get()
        def args = argumentBuilder.buildLiquibaseArgs(activity, commandName, commandArguments, projectInfo)

        if ( classPath == null || classPath.isEmpty() ) {
            throw new LiquibaseConfigurationException("No liquibaseRuntime dependencies were defined.  You must at least add Liquibase itself as a liquibaseRuntime dependency.")
        }
        println "liquibase-plugin: Running the '${activity.name}' activity..."
        logger.debug("liquibase-plugin: The ${liquibaseInfo.mainClass} class will be used to run Liquibase")
        logger.debug("liquibase-plugin: Liquibase will be run with the following jvmArgs: ${liquibaseInfo.jvmArgs}")
        logger.debug("liquibase-plugin: Running 'liquibase ${args.join(" ")}'")
        execOperations.javaexec { spec ->
            spec.classpath = liquibaseInfo.classPath
            spec.mainClass.set(liquibaseInfo.mainClass)
            spec.jvmArgs(liquibaseInfo.jvmArgs)
            spec.systemProperties(System.properties) // "inherit" these from the Gradle JVM
            spec.args(args)
        }

    }
}
