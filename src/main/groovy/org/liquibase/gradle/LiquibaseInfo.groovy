package org.liquibase.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection

import static org.liquibase.gradle.Util.versionAtLeast

/**
 * This class has information we'll need to run Liquibase, including the name of the main class,
 * the desired JVM arguments, etc.
 * <p>
 * This class is similar to {@link ProjectInfo), but not quite the same.  ProjectInfo contains
 * information about <b>what</b> to run, and LiquibaseInfo stores information about <b>how</b> to
 * run it.
 * <p>
 * For compatibiity with the Gradle configuration cache, this class should be accessed through a
 * Gradle {@code Property}, which itself should be set with a closure in a task's {@code configure}
 * method like this:
 * <pre>
 *     liquibaseInfo.set(project.provider { new LiquibaseInfo(project) })
 * </pre>
 *
 * @author Nouran-11
 */
class LiquibaseInfo {

    /** The classpath to use when we run Liquibase */
    FileCollection classPath

    /** the version of liquibase we're going to run */
    String liquibaseVersion

    /** The name of the Liquibase "main" class that we'll call */
    String mainClass

    /** The JVM arguments to use when we call liquibase. */
    List<String> jvmArgs

    LiquibaseInfo(Project project) {
        // Get the liquibaseRuntime classpath that was configured in the project.
        this.classPath = project.configurations.getByName(LiquibasePlugin.LIQUIBASE_RUNTIME_CONFIGURATION)
        // Based on that classpath, find the version of Liquibase in use.
        this.liquibaseVersion = findLiquibaseVersion(classPath, project.logger)
        // get the name of the liquibase main class for that version, unless overridden.
        def liquibaseExtension = project.extensions.findByType(LiquibaseExtension.class)
        this.mainClass = chooseMainClass(liquibaseExtension, liquibaseVersion, project.logger)
        // get the JVM args from the liquibase extension.
        this.jvmArgs = liquibaseExtension.jvmArgs

    }

    /**
     * Helper method to find the version of liquibase present in the liquibaseRuntime classpath
     * @param classPath the classpath to check
     * @param logger a logger that can be used to show information during the run
     * @return the version of liquibase found.
     */
    def findLiquibaseVersion(classPath, logger) {
        def coreDeps = classPath.resolvedConfiguration.resolvedArtifacts.findAll { artifact ->
            artifact.moduleVersion.id.name == 'liquibase-core'
        }

        if ( coreDeps.size() < 1 ) {
            throw new LiquibaseConfigurationException("Liquibase-core was not found in the liquibaseRuntime configuration!")
        }
        if ( coreDeps.size() > 1 ) {
            logger.warn("liquibase-plugin: More than one version of the liquibase-core dependency was found in the liquibaseRuntime configuration!")
        }

        String foundVersion = coreDeps.last()?.moduleVersion?.id?.version
        logger.debug("liquibase-plugin: Found version ${foundVersion} of liquibase-core.")
        return foundVersion
    }

    /**
     * Helper method to determine the name of the liquibase "Main" class.  This is the class that
     * this plugin calls to do the liquibase work.  This class is based on the liquibase version,
     * unless the user specified a class manually in the liquibase block of build.gradle
     * @param liquibaseExtension the liquibase extension, possibly holding the user supplied main
     *         class to use.
     * @param liquibaseVersion the version of liquibase we're using.
     * @param logger a logger that can be used to show information during the run
     * @return the name of the main class we should use.
     */
    def chooseMainClass(liquibaseExtension, liquibaseVersion, logger) {
        // If we have a custom class name, it doesn't matter what version of Liquibase we have,
        // just use the given class name.
        def mainClass = liquibaseExtension.mainClassName
        if ( mainClass ) {
            logger.debug("liquibase-plugin: The extension's mainClassName was set, skipping version detection.")
            return mainClass as String
        }

        if ( versionAtLeast(liquibaseVersion, '4.4') ) {
            logger.debug("liquibase-plugin: Using the 4.4+ command line parser.")
            return "liquibase.integration.commandline.LiquibaseCommandLine"
        } else {
            throw new LiquibaseConfigurationException("The Liquibase gradle plugin doesn't support this version of Liquibase!")
        }

    }
}
