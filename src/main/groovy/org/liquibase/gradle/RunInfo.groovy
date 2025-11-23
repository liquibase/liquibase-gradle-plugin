package org.liquibase.gradle

import org.gradle.api.Project

/**
 * This class wraps information about the desired run, including the runlist, known activities, and
 * desired JVM arguments.  This wrapper allows us to configure the plugin in a way that is
 * compatible with the Gradle Configuration Cache
 *
 * @author Nouran-11
 */
class RunInfo {
    /** The list of known activities in the project */
    List<Activity> activities

    /** The activities the user wants to run this time */
    String runList

    /** The JVM arguments to use during this run. */
    List<String> jvmArgs

    /**
     * Factory method to create an instance of RunInfo from a project.
     * @param project the project from which to get the run info
     * @return a configured RunInfo object
     */
    static RunInfo fromProject(Project project) {
        def activities = project.liquibase.activities.toList()
        def runList = project.liquibase.runList
        def jvmArgs = project.liquibase.jvmArgs
        return new RunInfo(activities, runList, jvmArgs)
    }

    /**
     * Initializing constructor
     * @param activities the known activities in the project
     * @param runList the activities that are being executed in this run
     * @param jvmArgs the JVM arguments that will be used in this run
     */
    RunInfo(List<Activity> activities, String runList, List<String> jvmArgs) {
        this.activities = activities
        this.runList = runList
        this.jvmArgs = jvmArgs
    }
}
