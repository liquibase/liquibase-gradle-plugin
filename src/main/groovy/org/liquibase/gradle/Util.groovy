package org.liquibase.gradle

import liquibase.command.CommandArgumentDefinition
import liquibase.command.CommandDefinition

import java.lang.reflect.Field

/**
 * Utility class to hold our helper methods.
 *
 * @author Steven C. Saliman
 */
class Util {
    /**
     * Compare a given semver to a target semver and return whether the given semver is at least the
     * version of the target.
     *
     * @param givenSemver the version of Liquibase found in the classpath
     * @param targetSemver the target version to use as a comparison.
     * @return @{code true} if the given version is greater than or equal to the target semver.
     */
    static boolean versionAtLeast(String givenSemver, String targetSemver) {
        List<String> givenVersions = givenSemver.tokenize('.')
        List<String> targetVersions = targetSemver.tokenize('.')

        int commonIndices = Math.min(givenVersions.size(), targetVersions.size())

        for (int i = 0; i < commonIndices; ++i) {
            int givenNum = givenVersions[i].toInteger()
            int targetNum = targetVersions[i].toInteger()

            if (givenNum != targetNum) {
                return givenNum > targetNum
            }
        }

        // If we got this far then all the common indices are identical, so whichever version is
        // longer must be more recent
        return givenVersions.size() >= targetVersions.size()
    }

    /**
     * Get the command arguments for a Liquibase command
     * @param liquibaseCommand the Liquibase CommandDefinition whose arguments we need.
     * @return an array of supported arguments.
     */
    static Set<String> argumentsForCommand(CommandDefinition liquibaseCommand) {
        // Build a set of all the arguments (and argument aliases) supported by the given command.
        Set<String> supportedCommandArguments = new HashSet<>()
        liquibaseCommand.getArguments().each { argName, a ->
            supportedCommandArguments.add(a.name as String)
            // Starting with Liquibase 4.16, command arguments can have aliases
            Field supportsAliases = CommandArgumentDefinition.getDeclaredFields().find { it.name == 'aliases' }
            if (supportsAliases) {
                Collection<String> aliases = a.aliases ?: []
                supportedCommandArguments.addAll(aliases)
            }
        }
        return supportedCommandArguments

    }

}
