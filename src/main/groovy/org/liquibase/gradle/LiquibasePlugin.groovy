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

import liquibase.Scope
import liquibase.command.CommandArgumentDefinition
import liquibase.command.CommandDefinition
import liquibase.command.CommandFactory
import liquibase.configuration.ConfigurationDefinition
import liquibase.configuration.LiquibaseConfiguration
import org.gradle.api.Project
import org.gradle.api.Plugin

class LiquibasePlugin implements Plugin<Project> {

    public static final String LIQUIBASE_RUNTIME_CONFIGURATION = "liquibaseRuntime"

    void apply(Project project) {
        applyExtension(project)
        applyConfiguration(project)
        applyTasks(project)
    }


    void applyExtension(Project project) {
        def activityClosure = { name ->
            new Activity(name)
        }
        doApplyExtension(project, activityClosure)
    }

    protected static void doApplyExtension(Project project, Closure<Activity> activityClosure) {
        def activities = project.container(Activity, activityClosure)
        project.configure(project) {
            extensions.create("liquibase", LiquibaseExtension, activities)
        }
    }

    void applyConfiguration(Project project) {
        project.configure(project) {
            configurations.maybeCreate(LIQUIBASE_RUNTIME_CONFIGURATION)
        }
    }

    /**
     * Create all of the liquibase tasks and add them to the project.  If the liquibaseTaskPrefix
     * is set, add that prefix to the task names.
     * @param project the project to enhance
     */
    void applyTasks(Project project) {
        // Make an argument builder for tasks to share, and initialize the global arguments while
        // we are still in the apply phase.
        ArgumentBuilder builder = new ArgumentBuilder()
        builder.initializeGlobalArguments()

        // Get the commands from the CommandFactory that are not internal, not hidden, and not the
        // init command.
        Set<CommandDefinition> commands = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommands(false)
        def supportedCommands = commands.findAll { !it.hidden && !it.name.contains("init") }
        supportedCommands.each {  command ->
            // Build a list of all the arguments (and argument aliases) supported by the given command.
            def supportedCommandArguments = Util.argumentsForCommand(command)
            // Let the builder know about the command so it can process arguments later
            builder.addCommandArguments(supportedCommandArguments)

            // If the command has a nested command, append it to the task name.
            def taskName = command.name[0]
            if ( command.name.size() > 1 ) {
                taskName += command.name[1].capitalize()
            }

            // Fix the task name if we have a task prefix.
            if ( project.hasProperty('liquibaseTaskPrefix') ) {
                taskName = project.liquibaseTaskPrefix + taskName.capitalize()
            }
            project.tasks.register(taskName, LiquibaseTask) {
                group = 'Liquibase'
                description = command.shortDescription
                commandName = command.name[0]
                commandArguments = supportedCommandArguments
                argumentBuilder = builder
            }
        }
    }
}
