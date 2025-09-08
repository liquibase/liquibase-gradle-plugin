package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class LiquibaseTaskTest {
    Project project

    @BeforeEach
    void setup() {
        project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        project.apply plugin: 'org.liquibase.gradle'
        // Put a liquibase-core dep in liquibaseRuntime so tasks can be configured without
        // throwing on version lookup
        project.configurations.getByName(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION) {
            dependencies.add(
                project.dependencies.create("org.liquibase:liquibase-core:4.24.0")
            )
            dependencies.add(
                project.dependencies.create("info.picocli:picocli:4.7.7")
            )
        }
    }

    @Test
    void execHonorsRunListFilteringAndDoesNotExecuteJavaExec() {
        // given: activities and a runList specifying a subset
        LiquibaseExtension ext = project.extensions.getByType(LiquibaseExtension)
        ext.activities.create('a')
        ext.activities.create('b')
        ext.activities.create('c')
        ext.runList.set('a, c')
        ext.jvmArgs.set(['-Xms128m', '-Xmx256m'])

        LiquibaseTask task = project.tasks.findByName('update') as LiquibaseTask
        assertNotNull(task)

        // Intercept runLiquibase to capture which activities are executed and
        // avoid launching JavaExec
        List<String> invoked = []
        task.metaClass.runLiquibase = { ActivitySpec activity ->
            invoked << activity.name
        }

        // when: exec is called
        task.runTask()

        // then: only the runList activities are invoked, in order
        assertEquals(['a', 'c'], invoked)
    }

    @Test
    void execRunsAllActivitiesWhenRunListIsNotSet() {
        // given
        LiquibaseExtension ext = project.extensions.getByType(LiquibaseExtension)
        ext.activities.create('a')
        ext.activities.create('b')
        ext.activities.create('c')
        ext.runList.set('')
        ext.jvmArgs.set([])

        LiquibaseTask task = project.tasks.findByName('update') as LiquibaseTask

        List<String> invoked = []
        task.metaClass.runLiquibase = { ActivitySpec activity ->
            invoked << activity.name
        }

        // when
        task.runTask()

        // then
        assertEquals(['a','b','c'], invoked)
    }
}
