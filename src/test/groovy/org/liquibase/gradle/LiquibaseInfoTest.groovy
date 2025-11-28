package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Unit tests to make sure we create LiquibaseInfo correctly
 *
 * @author Steven C. Saliman
 */
class LiquibaseInfoTest {
    Project project

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build()
    }

    /**
     * Confirm that the plugin will set the correct main class name when we put Liquibase 4.4+ in
     * the class path.
     */
    @Test
    void checkVersionDetectionLiquibase44Plus() {
        def liquibaseInfo = configureForVersion("4.4.0")
        assertEquals("liquibase.integration.commandline.LiquibaseCommandLine", liquibaseInfo.mainClass,
                "The plugin set the wrong main class name for Liquibase 4.4+")
    }

    /**
     * Confirm that the plugin will throw an exception when we put a pre-Liquibase 4.4+ version in
     * the class path.
     */
    @Test
    void checkVersionDetectionPreLiquibase44() {
        try {
            configureForVersion("4.3.0")
        } catch (Exception e) {
            assertTrue(e instanceof LiquibaseConfigurationException,
                    "Wrong Exception when Liquibase <4.4 is in the class path")
        }
    }

    /**
     * Confirm that, when users set their own main class name, he plugin will use it, regardless of
     * the version of Liquibase in the class path.
     */
    @Test
    void checkVersionDetectionCustomMainClass() {
        def liquibaseInfo = configureForVersion("4.3.0") {
            (it.extensions.liquibase as LiquibaseExtension).mainClassName = "com.example.CustomMain"
        }
        assertEquals("com.example.CustomMain", liquibaseInfo.mainClass,
                "The plugin failed to set the user's specified main class")
    }

    /**
     * Confirm that we get an exception when there is no version of Liquibase in the class path.
     */
    @Test
    void checkVersionDetectionMissingLiquibaseDependency() {
        try {
            configureForVersion(null)
            fail("Failed to throw an exception when Liquibase is not in the class path")
        } catch (Exception e) {
            // The provider looking for Liquibase will throw an
            // AbstractProperty$PropertyQueryException that should be wrapping the
            // LiquibaseConfigurationException that the plugin throws.  Does it?
            assertTrue(e instanceof LiquibaseConfigurationException,
                    "Wrong Exception when Liquibase is not in the class path")
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
    private LiquibaseInfo configureForVersion(String version, Closure closure = {}) {
        project.apply plugin: 'org.liquibase.gradle'
        assertTrue(project.plugins.hasPlugin(LiquibasePlugin), "Project is missing plugin")
        if ( version != null ) {
            project.configurations.getByName(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION) {
                dependencies.add(
                        project.dependencies.create("org.liquibase:liquibase-core:$version")
                )
            }
            project.repositories.mavenCentral()
        }
        closure(project)

//        LiquibaseTask task = project.tasks.findByName('update')
//        assertNotNull("Project is missing update task", task)
//        task.configure {}
//        return task
        return new LiquibaseInfo(project)
    }

}
