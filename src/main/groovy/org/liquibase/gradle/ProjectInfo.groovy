package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * This class is a configuration-cache safe holder for project info the extension needs, such as
 * the logger, build directory, etc.
 *
 * @author Nouran-11
 */
class ProjectInfo {
    /** The logger to use */
    Logger logger

    /** The directory where the project is being built */
    File buildDir

    /** The project properties that apply to liquibase */
    Map<String, Object> liquibaseProperties

    /**
     * Create a ProjectInfo instance from a project.
     * @param project the project to use as a source of data
     * @return a configured ProjectInfo instance
     */
    static ProjectInfo fromProject(Project project) {
        // Let's save some time later, and store just the project properties that start with
        // "liquibase".
        def liquibaseProperties = [:]
        project.properties.findAll { key, value ->
            if (!key.startsWith("liquibase")) return false
            if (value != null && LiquibaseTask.class.isAssignableFrom(value.class)) return false
            return true
        }.each { key, value ->
            liquibaseProperties[key] = value
        }

        return new ProjectInfo(project.logger, project.buildDir, liquibaseProperties)
    }

    /**
     * Populating constructor
     * @param logger the logger to use
     * @param buildDir the buildDir to use
     * @param liquibaseProperties the liquibase related properties to use
     */
    ProjectInfo(Logger logger, File buildDir, Map<String, Object> liquibaseProperties) {
        this.logger = logger
        this.buildDir = buildDir
        this.liquibaseProperties = liquibaseProperties
    }
}
