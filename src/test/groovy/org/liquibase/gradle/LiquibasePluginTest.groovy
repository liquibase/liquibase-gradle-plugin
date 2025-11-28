package org.liquibase.gradle

import liquibase.Scope
import liquibase.command.CommandDefinition
import liquibase.command.CommandFactory
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

class LiquibasePluginTest {
    Project project

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build()
    }

    /**
     * Typically, a plugin is applied by name, but Gradle supports applying by type.  Prove that it
     * works.  We aren't going to go nuts here, just look for one task that takes an argument, and
     * one that doesn't
     */
    @Test
    void applyPluginByType() {
        project.apply plugin: org.liquibase.gradle.LiquibasePlugin
        assertTrue(project.plugins.hasPlugin(LiquibasePlugin), "Project is missing plugin")
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('tag')
        assertNotNull(task, "Project is missing tag task")
        assertTrue(task instanceof LiquibaseTask, "tag task is the wrong type")
        assertTrue(task.enabled, "tag task should be enabled")
        assertEquals("tag", task.commandName, "tag task has the wrong command")
        // and the update task does not.
        task = project.tasks.findByName('update')
        assertNotNull(task, "Project is missing update task")
        assertTrue(task instanceof LiquibaseTask, "update task is the wrong type")
        assertTrue(task.enabled, "update task should be enabled")
        assertEquals("update", task.commandName, "update task has the wrong command")
    }

    /**
     * Apply the plugin by name and make sure it creates tasks.  We don't go nuts here, just look
     * for a task that takes an argument, and one that doesn't
     */
    @Test
    void applyPluginByName() {
        project.apply plugin: 'org.liquibase.gradle'
        assertTrue(project.plugins.hasPlugin(LiquibasePlugin), "Project is missing plugin")
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('tag')
        assertNotNull(task, "Project is missing tag task")
        assertTrue(task instanceof LiquibaseTask, "tag task is the wrong type")
        assertTrue(task.enabled, "tag task should be enabled")
        assertEquals("tag", task.commandName, "tag task has the wrong command")
        // and the update task does not.
        task = project.tasks.findByName('update')
        assertNotNull(task, "Project is missing update task")
        assertTrue(task instanceof LiquibaseTask, "update task is the wrong type")
        assertTrue(task.enabled, "update task should be enabled")
        assertEquals("update", task.commandName, "update task has the wrong command")
    }

    /**
     * Apply the plugin by name, but this time, specify a value for the liquibaseTaskPrefix and make
     * sure it changes the task names accordingly.  We don't go nuts here, just look for a task that
     * takes an argument, and one that doesn't.  We also make sure that while the task names are
     * changed, the commands they run are not.
     */
    @Test
    void applyPluginByNameWithPrefix() {
        project.ext.liquibaseTaskPrefix = 'liquibase'
        project.apply plugin: 'org.liquibase.gradle'
        assertTrue(project.plugins.hasPlugin(LiquibasePlugin), "Project is missing plugin")
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('liquibaseTag')
        assertNotNull(task, "Project is missing tag task")
        assertTrue(task instanceof LiquibaseTask, "tag task is the wrong type")
        assertTrue(task.enabled, "tag task should be enabled")
        assertEquals("tag", task.commandName, "tag task has the wrong command")
        // and the update task does not.
        task = project.tasks.findByName('liquibaseUpdate')
        assertNotNull(task, "Project is missing update task")
        assertTrue(task instanceof LiquibaseTask, "update task is the wrong type")
        assertTrue(task.enabled, "update task should be enabled")
        assertEquals("update", task.commandName, "update task has the wrong command")

        // Make sure the standard tasks didn't get created, since we created them with different
        // names.
        task = project.tasks.findByName('tag')
        assertNull(task, "We shouldn't have a tag task")
        task = project.tasks.findByName('update')
        assertNull(task, "We shouldn't have an update task")
    }

    @Test
    void cliApi() {
        CommandFactory factory = Scope.getCurrentScope().getSingleton(CommandFactory.class)
        // need to get non-internal, then exclude hidden and "init"
        Set<CommandDefinition> commands = factory.getCommands(false)
        def i = 0;
        commands.findAll { !it.hidden }.each {  command ->
            def taskName = command.name[0]
            if ( command.name.size() > 1 ) {
                taskName += command.name[1].capitalize()
            }
            println "${i}: ${taskName} - ${command.shortDescription}"
            println "${i++}: ${taskName} - ${command.longDescription}\n"

        }
        println("Done")
    }
}
