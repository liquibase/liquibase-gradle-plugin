package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class LiquibasePluginTest {
    Project project

    @Before
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
        assertTrue("Project is missing plugin", project.plugins.hasPlugin(LiquibasePlugin))
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('tag')
        assertNotNull("Project is missing tag task", task)
        assertTrue("tag task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("tag task should be enabled", task.enabled)
        assertEquals("tag task has the wrong command", "tag", task.liquibaseCommand.get().command)
        // and the update task does not.
        task = project.tasks.findByName('update')
        assertNotNull("Project is missing update task", task)
        assertTrue("update task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("update task should be enabled", task.enabled)
        assertEquals("update task has the wrong command", "update", task.liquibaseCommand.get().command)
    }

    /**
     * Apply the plugin by name and make sure it creates tasks.  We don't go nuts here, just look
     * for a task that takes an argument, and one that doesn't
     */
    @Test
    void applyPluginByName() {
        project.apply plugin: 'org.liquibase.gradle'
        assertTrue("Project is missing plugin", project.plugins.hasPlugin(LiquibasePlugin))
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('tag')
        assertNotNull("Project is missing tag task", task)
        assertTrue("tag task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("tag task should be enabled", task.enabled)
        assertEquals("tag task has the wrong command", "tag", task.liquibaseCommand.get().command)
        // and the update task does not.
        task = project.tasks.findByName('update')
        assertNotNull("Project is missing update task", task)
        assertTrue("update task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("update task should be enabled", task.enabled)
        assertEquals("update task has the wrong command", "update", task.liquibaseCommand.get().command)
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
        assertTrue("Project is missing plugin", project.plugins.hasPlugin(LiquibasePlugin))
        project.repositories.configure { mavenCentral() }
        project.dependencies.add(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION, "org.liquibase:liquibase-core:4.4.0")
        // the tag task takes an arg...
        def task = project.tasks.findByName('liquibaseTag')
        assertNotNull("Project is missing tag task", task)
        assertTrue("tag task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("tag task should be enabled", task.enabled)
        assertEquals("tag task has the wrong command", "tag", task.liquibaseCommand.get().command)
        // and the update task does not.
        task = project.tasks.findByName('liquibaseUpdate')
        assertNotNull("Project is missing update task", task)
        assertTrue("update task is the wrong type", task instanceof LiquibaseTask)
        assertTrue("update task should be enabled", task.enabled)
        assertEquals("update task has the wrong command", "update", task.liquibaseCommand.get().command)

        // Make sure the standard tasks didn't get created, since we created them with different
        // names.
        task = project.tasks.findByName('tag')
        assertNull("We shouldn't have a tag task", task)
        task = project.tasks.findByName('update')
        assertNull("We shouldn't have an update task", task)
    }

    /**
     * Confirm that the plugin will set the correct main class name when we put Liquibase 4.4+ in
     * the class path.
     */
    @Test
    void checkVersionDetectionLiquibase44Plus() {
        def task = configureForVersion("4.4.0")
        assertEquals("The plugin set the wrong main class name for Liquibase 4.4+",
                "liquibase.integration.commandline.LiquibaseCommandLine", task.mainClass.get())
    }

    /**
     * Confirm that the plugin will set the correct main class name when we put a pre-Liquibase 4.4+
     * version in the class path.
     */
    @Test
    void checkVersionDetectionPreLiquibase44() {
        def task = configureForVersion("4.3.0")
        assertEquals("The plugin set the wrong main class name for Liquibase <4.4",
                "liquibase.integration.commandline.Main", task.mainClass.get())
    }

    /**
     * Confirm that, when users set their own main class name, he plugin will use it, regardless of
     * the version of Liquibase in the class path.
     */
    @Test
    void checkVersionDetectionCustomMainClass() {
        def task = configureForVersion("4.3.0") {
            (it.extensions.liquibase as LiquibaseExtension).mainClassName = "com.example.CustomMain"
        }
        assertEquals("The plugin failed to set the user's specified main class",
                "com.example.CustomMain", task.mainClass.get())
    }

    /**
     * Confirm that we get an exception when there is no version of Liquibase in the class path.
     */
    @Test
    void checkVersionDetectionMissingLiquibaseDependency() {
        def task = configureForVersion(null)
        try {
            task.mainClass.get()
            fail("Failed to throw an exception when Liquibase is not in the class path")
        } catch (Exception e) {
            // The provider looking for Liquibase will throw an
            // AbstractProperty$PropertyQueryException that should be wrapping the
            // LiquibaseConfigurationException that the plugin throws.  Does it?
            assertTrue("Wrong Exception when Liquibase is not in the class path",
                    e.getCause() instanceof LiquibaseConfigurationException)
        }
    }

    /**
     * Helper method to add a specific version of Liquibase to the class path, and perform
     * additional configuration on the project, and return a LiquibaseTask that can be checked for
     * a main class name.
     * @param version the version to include.  If {@code null}, no version of Liquibase will be
     *         added.
     * @param closure additional pro configuration to perform before returning the task.
     * @return a task that tests can use to verify configuration.
     */
    private LiquibaseTask configureForVersion(String version, Closure closure = {}) {
        project.apply plugin: 'org.liquibase.gradle'
        assertTrue("Project is missing plugin", project.plugins.hasPlugin(LiquibasePlugin))
        if ( version != null ) {
            project.configurations.getByName(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION) {
                dependencies.add(
                        project.dependencies.create("org.liquibase:liquibase-core:$version")
                )
            }
            project.repositories.mavenCentral()
        }
        closure(project)

        LiquibaseTask task = project.tasks.findByName('update')
        assertNotNull("Project is missing update task", task)
        task.configure {}
        return task
    }
}
