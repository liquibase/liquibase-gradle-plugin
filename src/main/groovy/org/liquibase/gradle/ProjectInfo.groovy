package org.liquibase.gradle

import org.gradle.api.Project

class ProjectInfo {
    List<Activity> activities
    String runList
    List<String> jvmArgs

    ProjectInfo(List<Activity> activities, String runList, List<String> jvmArgs) {
        this.activities = activities
        this.runList = runList
        this.jvmArgs = jvmArgs
    }

    static ProjectInfo fromProject(Project project) {
        def activities = project.liquibase.activities.toList()
        def runList = project.liquibase.runList
        def jvmArgs = project.liquibase.jvmArgs
        return new ProjectInfo(activities, runList, jvmArgs)
    }
}
