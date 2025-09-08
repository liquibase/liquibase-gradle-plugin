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
import liquibase.command.CommandDefinition
import liquibase.command.CommandFactory
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class LiquibasePlugin implements Plugin<Project> {

    public static final String LIQUIBASE_RUNTIME_CONFIGURATION = "liquibaseRuntime"

    void apply(Project project) {
        applyExtension(project)
        applyConfiguration(project)
        applyTasks(project)
    }

    void applyExtension(Project project) {
        NamedDomainObjectContainer<Activity> activities =
                project.container(Activity) { String name ->
                    new Activity(name, project.objects)
                }
        project.configure(project) {
            project.extensions.create("liquibase", LiquibaseExtension, activities)
        }
    }

    void applyConfiguration(Project project) {
        project.configure(project) {
            project.configurations.maybeCreate(LIQUIBASE_RUNTIME_CONFIGURATION)
        }
    }

    /**
     * Create all of the liquibase tasks and add them to the project.  If the liquibaseTaskPrefix
     * is set, add that prefix to the task names.
     * @param project the project to enhance
     */
    void applyTasks(Project project) {
        // Get the commands from the CommandFactory that are not internal, not hidden, and not the
        // init command.
        Set<CommandDefinition> commands = Scope.getCurrentScope()
                .getSingleton(CommandFactory.class).getCommands(false)
        Set<CommandDefinition> supportedCommands = commands.findAll {
            !it.hidden && !it.name.contains("init")
        }

        // Precompute argument sets and union across commands
        Map<CommandDefinition, Set<String>> commandArgMap = new LinkedHashMap<>()
        Set<String> unionCommandArgs = new HashSet<>()
        supportedCommands.each { command ->
            Set<String> supportedCommandArguments = Util.argumentsForCommand(command)
            commandArgMap.put(command, supportedCommandArguments)
            unionCommandArgs.addAll(supportedCommandArguments)
        }

        // Prepare a safe, isolatable map of properties: only liquibase* keys with non-null values,
        // stringified
        Map<String, String> safeProps = project.properties
                .findAll { k, v ->
                    (k instanceof String) && ((String) k).startsWith('liquibase') && v != null
                }
                .collectEntries { k, v -> [(k.toString()): v.toString()] }

        // Register a shared build service for argument building
        Provider<ArgumentBuilderService> serviceProvider = project.gradle.sharedServices
                .registerIfAbsent("liquibaseArgumentBuilder", ArgumentBuilderService) {
                    parameters.buildDir.set(project.layout.buildDirectory)
                    parameters.properties.set(safeProps)
                    parameters.allCommandArguments.set(unionCommandArgs)
                }

        // Register tasks and inject the service
        LiquibaseExtension ext = project.extensions.getByType(LiquibaseExtension)
        commandArgMap.each { CommandDefinition command, Set<String> supportedCommandArguments ->
            // If the command has a nested command, append it to the task name.
            String taskName = command.name[0]
            if ( command.name.size() > 1 ) {
                taskName += command.name[1].capitalize()
            }

            // Fix the task name if we have a task prefix.
            if ( project.hasProperty('liquibaseTaskPrefix') ) {
                taskName = project.properties['liquibaseTaskPrefix'] + taskName.capitalize()
            }
            project.tasks.register(taskName, LiquibaseTask, new Action<LiquibaseTask>() {
                @Override
                void execute(LiquibaseTask t) {
                    t.group = 'Liquibase'
                    t.description = command.shortDescription
                    t.commandName.set(command.name[0])
                    t.commandArguments.set(supportedCommandArguments)
                    t.argumentBuilderService = serviceProvider
                }
            })
        }
    }
}
