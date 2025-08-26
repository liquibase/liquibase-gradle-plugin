package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logger

class LiquibaseInfo {
    Logger logger
    File buildDir
    Map<String, Object> liquibaseProperties
    
    LiquibaseInfo(Logger logger, File buildDir, Map<String, Object> liquibaseProperties) {
        this.logger = logger
        this.buildDir = buildDir
        this.liquibaseProperties = liquibaseProperties
    }
    
    static LiquibaseInfo fromProject(Project project) {
        def liquibaseProperties = [:]
        project.properties.findAll { key, value ->
            if (!key.startsWith("liquibase")) return false
            if (value != null && LiquibaseTask.class.isAssignableFrom(value.class)) return false
            return true
        }.each { key, value ->
            liquibaseProperties[key] = value
        }
        
        return new LiquibaseInfo(project.logger, project.buildDir, liquibaseProperties)
    }
}
