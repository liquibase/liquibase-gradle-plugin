package org.liquibase.gradle

import org.gradle.api.Project

class ProjectInfo {
    List<Activity> activities
    String runList
    List<String> jvmArgs
    Map<String, Object> liquibaseProperties
    File buildDir
    Object logger

    ProjectInfo(List<Activity> activities, String runList, List<String> jvmArgs, Map<String, Object> liquibaseProperties, File buildDir, Object logger) {
        this.activities = activities
        this.runList = runList
        this.jvmArgs = jvmArgs
        this.liquibaseProperties = liquibaseProperties
        this.buildDir = buildDir
        this.logger = logger
    }

    static ProjectInfo fromProject(Project project) {
        def buildDir = project.buildDir
        def logger = project.logger
        def liquibaseProperties = [:]
        project.properties.findAll { key, value ->
            if (!key.startsWith("liquibase")) return false
            if (value != null && LiquibaseTask.class.isAssignableFrom(value.class)) return false
            return true
        }.each { key, value ->
            liquibaseProperties[key] = value
        }
        def activities = project.liquibase.activities.toList()
        def runList = project.liquibase.runList
        def jvmArgs = project.liquibase.jvmArgs
        return new ProjectInfo(activities, runList, jvmArgs,
                liquibaseProperties as Map<String, Object>, buildDir, logger)
    }
}
