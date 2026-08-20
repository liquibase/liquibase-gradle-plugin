package org.liquibase.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Functional tests that make sure applying and configuring the plugin doesn't cause Gradle to emit
 * deprecation warnings.  Deprecated Gradle APIs used by the plugin show up in the builds of every
 * project that uses the plugin, so we run against a Gradle version that is new enough to report
 * everything that is scheduled for removal in Gradle 10.
 */
class DeprecationWarningTest {
    /**
     * The Gradle version used to look for deprecation warnings.  This is pinned so that a new
     * Gradle release can't fail the build with a deprecation the plugin doesn't know about yet.
     */
    static final String GRADLE_VERSION = "9.4.1"

    @TempDir
    Path testProjectDir

    /**
     * Apply the plugin, configure a typical {@code activities} block, and run a task that only
     * needs the configuration phase.  Expect no deprecation warnings at all.
     */
    @Test
    void applyingPluginProducesNoDeprecationWarnings() {
        def projectDir = testProjectDir.toFile()
        new File(projectDir, "settings.gradle").text = """
            rootProject.name = 'deprecation-test'
        """
        new File(projectDir, "build.gradle").text = """
            plugins {
                id 'org.liquibase.gradle'
            }

            liquibase {
                activities {
                    main {
                        changelogFile 'changelog.xml'
                        url 'jdbc:h2:mem:testdb'
                        username 'sa'
                        password '1234'
                    }
                }
                runList = 'main'
            }
        """

        BuildResult result = GradleRunner.create()
                .withGradleVersion(GRADLE_VERSION)
                .withProjectDir(projectDir)
                .withPluginClasspath(loadTestClasspath())
                .withArguments("tasks", "--warning-mode", "all")
                .forwardOutput()
                .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), "The build should have succeeded")
        assertFalse(result.output.contains("Project.container"),
                "Applying the plugin should not use the deprecated Project.container method")
        assertFalse(result.output.contains("Deprecated Gradle features were used in this build"),
                "Applying the plugin should not use any deprecated Gradle features")
    }

    /**
     * We generate a custom test resource file containing the test classpath during the plugin
     * build.
     *
     * @return the classpath we loaded from build/resources/test/test-classpath.properties
     */
    protected static List<File> loadTestClasspath() {
        def props = new Properties()
        def testClasspathFile = "test-classpath.properties"
        InputStream testClasspathContents = Thread.currentThread().contextClassLoader.getResourceAsStream(testClasspathFile)
        testClasspathContents.withCloseable {
            props.load(it)
        }
        def testClasspathProperty = "test-classpath"
        return props.get(testClasspathProperty).split(File.pathSeparator).collect { new File(it) }
    }
}
