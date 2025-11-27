package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * This class has information about our project, that we can access in a configuration-cache safe
 * way, such as the build directory, desired run list, etc.
 * <p>
 * This class is similar to {@link LiquibaseInfo), but not quite the same.  ProjectInfo contains
 * information about <b>what</b> to run, and LiquibaseInfo stores information about <b>how</b> to
 * run it.
 * <p>
 * For compatibiity with the Gradle configuration cache, this class should be accessed through a
 * Gradle {@code Property}, which itself should be set with a closure in a task's {@code configure}
 * method like this:
 * <pre>
 *     projectInfo.set(project.provider { new ProjectInfo(project) })
 * </pre>
 *
 * @author Nouran-11
 */
class ProjectInfo {
    /** The project's logger, needed by ArgumentBuilder */
    Logger logger

    /** The directory where the project is being built */
    File buildDir

    /** The project properties that start with "liquibase" */
    Map<String, Object> liquibaseProperties

    /** The list of known activities in the project */
    List<Activity> activities

    /** The activities the user wants to run this time */
    String runList

    /**
     * Create a ProjectInfo instance from a project.
     * @param project the project to use as a source of data
     * @return a configured ProjectInfo instance
     */
    ProjectInfo(Project project) {
        this.logger = project.logger
        this.buildDir = project.buildDir
        this.activities = project.liquibase.activities.toList()
        this.runList = project.liquibase.runList
        this.liquibaseProperties = [:]
        project.properties.findAll { key, value ->
            if (!key.startsWith("liquibase")) return false
            if (value != null && LiquibaseTask.class.isAssignableFrom(value.class)) return false
            return true
        }.each { key, value ->
            this.liquibaseProperties[key] = value
        }
    }
}
