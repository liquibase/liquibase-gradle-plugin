package org.liquibase.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.liquibase.gradle.support.GradleLiquibaseMatrixProvider

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

/**
 * Functional tests of {@Link LiquibasePlugin} against multiple Gradle and Liquibase versions.
 */
@ExtendWith(GradleLiquibaseMatrixProvider)
class FunctionalCompatibilityTest {

    @TempDir
    Path testProjectDir

    @TestTemplate
    void executesUpdateAgainstH2FileDbGroovyDsl(String gradleVersion, String liquibaseVersion) {
        List<String> pluginCpEntries = computePluginClasspathEntries()
        copyChangelogFixture()

        String searchPath = testProjectDir.toAbsolutePath().toString()
        String testClasspathFiles = toGroovyFilesList(pluginCpEntries)

        // Use unique DB per Liquibase version to avoid cross-run state
        String h2Url = h2UrlFor("dbfile-${liquibaseVersion.replace('.', '_')}")

        // Write settings.gradle and build.gradle for this invocation
        writeGroovyBuildFiles(
                'it-project',
                liquibaseVersion,
                testClasspathFiles,
                searchPath,
                h2Url,
                true
        )

        // First run: update should apply the changeSet
        BuildResult first = createRunner(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['update', '--stacktrace', '--info'])
                .forwardOutput()
                .build()
        assertNotNull(first.task(':update'))
        assertEquals(TaskOutcome.SUCCESS, first.task(':update')?.outcome,
                "update should succeed on first run (Liquibase ${liquibaseVersion})")

        // Second run: update again should find nothing to do
        BuildResult second = createRunner(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['update', '--stacktrace', '--info'])
                .forwardOutput()
                .build()
        assertEquals(TaskOutcome.SUCCESS, second.task(':update')?.outcome,
                "update should succeed on second run (Liquibase ${liquibaseVersion})")
        assertTrue(second.output.toLowerCase().contains('no changes to deploy')
                || second.output.toLowerCase().contains('up to date')
                || second.output.toLowerCase().contains('nothing to do'),
                'Expected second update to indicate no changes to deploy')
    }

    @TestTemplate
    void executesUpdateAgainstH2FileDbKotlinDsl(String gradleVersion, String liquibaseVersion) {
        List<String> pluginCpEntries = computePluginClasspathEntries()
        copyChangelogFixture()

        String searchPath = testProjectDir.toAbsolutePath().toString()
        String testClasspathKtsFiles = toKtsFilesList(pluginCpEntries)

        // Use unique DB per Liquibase version to avoid cross-run state
        String h2Url = h2UrlFor("dbfile-kts-${liquibaseVersion.replace('.', '_')}")

        // Write settings.gradle.kts and build.gradle.kts for this invocation
        writeKotlinBuildFiles(
                'it-project',
                liquibaseVersion,
                testClasspathKtsFiles,
                searchPath,
                h2Url
        )

        // First run: update should apply the changeSet
        BuildResult first = createRunner(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['update', '--stacktrace', '--info'])
                .forwardOutput()
                .build()
        assertNotNull(first.task(':update'))
        assertEquals(TaskOutcome.SUCCESS, first.task(':update')?.outcome,
                "update should succeed on first run (Liquibase ${liquibaseVersion}) [Kotlin DSL]")

        // Second run: update again should find nothing to do
        BuildResult second = createRunner(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['update', '--stacktrace', '--info'])
                .forwardOutput()
                .build()
        assertEquals(TaskOutcome.SUCCESS, second.task(':update')?.outcome,
                "update should succeed on second run (Liquibase ${liquibaseVersion}) [Kotlin DSL]")
        assertTrue(second.output.toLowerCase().contains('no changes to deploy')
                || second.output.toLowerCase().contains('up to date')
                || second.output.toLowerCase().contains('nothing to do'),
                'Expected second update to indicate no changes to deploy [Kotlin DSL]')
    }

    /**
     * Verify that executing an update without any liquibaseRuntime dependency fails early with a
     * helpful error. This test uses the current Gradle version only (no matrix).
     */
    @Test
    void checkVersionDetectionMissingLiquibaseDependency() {
        List<String> pluginCpEntries = computePluginClasspathEntries()
        copyChangelogFixture()

        String searchPath = testProjectDir.toAbsolutePath().toString()
        String testClasspathFiles = toGroovyFilesList(pluginCpEntries)
        String h2Url = h2UrlFor('dbfile-missing')

        // Write settings.gradle and build.gradle for this invocation (no liquibaseRuntime deps)
        writeGroovyBuildFiles(
                'it-project-missing-liquibase',
                '4.27.0',
                testClasspathFiles,
                searchPath,
                h2Url,
                false
        )

        BuildResult result = createRunner(null)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['update', '--stacktrace', '--info'])
                .forwardOutput()
                .buildAndFail()

        // We expect an early failure due to missing liquibaseRuntime dependencies
        assertTrue(result.output
                .contains('Liquibase-core was not found in the liquibaseRuntime configuration!'),
                'Expected failure message about missing liquibaseRuntime dependencies')
    }

    private static GradleRunner createRunner(String gradleVersion) {
        def runner = GradleRunner.create()
        if (gradleVersion != null) {
            runner = runner.withGradleVersion(gradleVersion)
        } else {
            runner = runner.withGradleVersion(GradleVersion.current().version)
        }
        return runner
    }

    private void copyChangelogFixture() {
        URL changeLogUrl = FunctionalCompatibilityTest.class.getResource('/changelog.xml')
        assertNotNull(changeLogUrl, 'Test changelog resource not found')
        changeLogUrl.withInputStream { is ->
            Files.copy(is, testProjectDir.resolve('changelog.xml'))
        }
    }

    private static List<String> computePluginClasspathEntries() {
        URL pluginLoc = LiquibasePlugin.protectionDomain.codeSource.location
        File pluginClasses = new File(pluginLoc.toURI())
        File pluginResources = new File(pluginClasses.parentFile.parentFile,
                "resources/${pluginClasses.name}")
        List<String> pluginCpEntries = []
        if (pluginClasses.exists()) pluginCpEntries << pluginClasses.absolutePath
        if (pluginResources.exists()) pluginCpEntries << pluginResources.absolutePath
        return pluginCpEntries
    }

    private static String toGroovyFilesList(List<String> entries) {
        return entries.collect { "'${it.replace('\\\\', '\\\\\\\\')}'" }.join(', ')
    }

    private static String toKtsFilesList(List<String> entries) {
        return entries.collect { "\"${it.replace('\\\\', '\\\\\\\\')}\"" }.join(', ')
    }

    private String h2UrlFor(String dbName) {
        Path dbPath = testProjectDir.resolve("build/h2/${dbName}")
        return "jdbc:h2:file:${dbPath.toAbsolutePath().toString()}"
    }

    private void writeGroovyBuildFiles(String rootName,
                                       String liquibaseVersion,
                                       String testClasspathFiles,
                                       String searchPath,
                                       String h2Url,
                                       boolean includeRuntimeDeps) {
        Files.writeString(testProjectDir.resolve('settings.gradle'),
                "rootProject.name=\"${rootName}\"")
        String depsBlock = includeRuntimeDeps ? """
            dependencies {
                liquibaseRuntime 'org.liquibase:liquibase-core:${liquibaseVersion}'
                liquibaseRuntime 'info.picocli:picocli:4.7.7'
                liquibaseRuntime 'com.h2database:h2:2.2.224'
            }
        """ : """
            // Intentionally no liquibaseRuntime dependencies
        """
        Files.writeString(testProjectDir.resolve('build.gradle'), """
            buildscript {
                repositories { mavenCentral() }
                dependencies {
                    classpath files(${testClasspathFiles})
                    classpath 'org.liquibase:liquibase-core:${liquibaseVersion}'
                }
            }

            apply plugin: org.liquibase.gradle.LiquibasePlugin
            repositories { mavenCentral() }

            ${depsBlock}

            liquibase {
                runList = 'main'
                activities {
                    main {
                        arguments.set([
                            url: '${h2Url}',
                            username: 'sa',
                            password: 'test',
                            changelogFile: 'changelog.xml',
                            searchPath: '${searchPath}'
                        ])
                    }
                }
            }
        """.stripIndent())
    }

    private void writeKotlinBuildFiles(String rootName,
                                       String liquibaseVersion,
                                       String testClasspathKtsFiles,
                                       String searchPath,
                                       String h2Url) {
        Files.writeString(testProjectDir.resolve('settings.gradle.kts'),
                "rootProject.name = \"${rootName}\"")
        Files.writeString(testProjectDir.resolve('build.gradle.kts'), """
            buildscript {
                repositories { mavenCentral() }
                dependencies {
                    classpath(files(${testClasspathKtsFiles}))
                    classpath("org.liquibase:liquibase-core:${liquibaseVersion}")
                }
            }

            @Suppress("UNCHECKED_CAST")
            apply<org.liquibase.gradle.LiquibasePlugin>()
            repositories { mavenCentral() }

            dependencies {
                add("liquibaseRuntime", "org.liquibase:liquibase-core:${liquibaseVersion}")
                add("liquibaseRuntime", "info.picocli:picocli:4.7.7")
                add("liquibaseRuntime", "com.h2database:h2:2.2.224")
            }

            val lb = extensions.getByType(org.liquibase.gradle.LiquibaseExtension::class)
            lb.runList = "main"
            lb.activities.register("main") {
                arguments.put("url", "${h2Url}")
                arguments.put("username", "sa")
                arguments.put("password", "test")
                arguments.put("changelogFile", "changelog.xml")
                arguments.put("searchPath", "${searchPath}")
            }
        """.stripIndent())
    }
}
