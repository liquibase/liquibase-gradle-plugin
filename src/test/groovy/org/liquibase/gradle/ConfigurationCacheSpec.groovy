package org.liquibase.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

class ConfigurationCacheSpec {
    @TempDir
    public Path testProjectDir

    @Test
    void pluginShouldBeCompatibleWithConfigurationCache() {
        def projectDir = testProjectDir.toFile()
        def changelogFile = new File(projectDir, "changelog.yml")
        def buildFile = new File(projectDir, "build.gradle")
        buildFile.text = """
            // Under TestKit, we do not need a buildscript block
            // as recommended here https://github.com/liquibase/liquibase-gradle-plugin/blob/master/doc/usage.md#2-setting-up-the-classpath
            // buildscript { ... }

            plugins {
                // Under TestKit, we do not need a version
                id 'org.liquibase.gradle'
            } 
  
            repositories {
               mavenCentral()
            }
            
            dependencies {
                liquibaseRuntime 'org.liquibase:liquibase-core:4.33.0'
                liquibaseRuntime 'info.picocli:picocli:4.7.5'
                liquibaseRuntime 'com.h2database:h2:2.2.224'
            }
            
            liquibase {
                activities {
                    main {
                        changelogFile 'changelog.yml'
                        url 'jdbc:h2:mem:testdb'
                        username 'sa'
                        password '1234'
                        searchPath "${projectDir}"
                    }
                }
                runList = 'main'
            }
        """

        changelogFile.text = """
           databaseChangeLog:
             - changeSet:
                 id: 1
                 author: your.name
                 changes:
                   - createTable:
                       tableName: person
                       columns:
                         - column:
                             name: id
                             type: int
                             autoIncrement: true
                             constraints:
                               primaryKey: true
                               nullable: false
       """

        def testPluginClasspath = loadTestClasspath()

        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath(testPluginClasspath)
                .withArguments("update", "--configuration-cache","-s")
                .forwardOutput()
                .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(result.output.contains("Configuration cache entry stored."))
    }

    /**
     * We generate a custom test resource file containing the test classpath
     * during the plugin build.
     *
     * @return the classpath we loaded from build/resources/test/test-classpath.properties
     */
    protected List<File> loadTestClasspath() {
        def props = new Properties()
        def testClasspathFile = "test-classpath.properties"
        InputStream testClasspathContents = Thread.currentThread().contextClassLoader.getResourceAsStream(testClasspathFile)
        testClasspathContents.withCloseable {
            props.load(it)
        }
        def testClasspathProperty = "test-classpath"
        return props.get(testClasspathProperty).split(":").collect { new File(it) }
    }
}
