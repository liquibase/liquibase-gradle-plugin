package org.liquibase.gradle

import liquibase.Scope
import liquibase.configuration.ConfigurationDefinition
import liquibase.configuration.LiquibaseConfiguration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Gradle Build Service that builds Liquibase CLI arguments in a configuration-cache friendly way.
 * This service captures Gradle properties and the build directory at configuration time via
 * service parameters and exposes a method to build argument lists for tasks at execution time.
 */
abstract class ArgumentBuilderService implements BuildService<ArgumentBuilderService.Params>,
        AutoCloseable {

    interface Params extends BuildServiceParameters {
        DirectoryProperty getBuildDir()
        MapProperty<String, String> getProperties()
        SetProperty<String> getAllCommandArguments() // union of all task command args
    }

    private static final Logger LOGGER = Logging.getLogger(ArgumentBuilderService)

    // String constants to avoid duplicate literals
    private static final String LIQUIBASE_PREFIX = 'liquibase'
    private static final String LIQUIBASE_PREFIX_DOT = LIQUIBASE_PREFIX + '.'
    private static final String PROP_CHANGELOG_PARAMETERS = LIQUIBASE_PREFIX + 'ChangelogParameters'
    private static final String ARG_INTEGRATION_NAME = 'integrationName'
    private static final String ARG_CHANGELOG_FILE = 'changelogFile'
    private static final String ARG_CHANGELOG_PARAMETERS = 'changelogParameters'
    private static final String ARG_OUTPUT_DIRECTORY = '--output-directory'
    private static final String FALLBACK_ARG_SEARCH_PATH = 'searchPath'
    private static final String CMD_DBDOC = 'dbDoc'
    private static final String CMD_DROP_ALL_KEBAB = 'drop-all'

    // All known Liquibase global arguments.
    protected Set<String> allGlobalArguments
    // All Known Liquibase global arguments, as they can be passed in as properties.
    protected Set<String> allGlobalProperties

    // Lazily initialize global argument metadata from Liquibase using the buildscript classpath.
    private void ensureGlobalArgsInitialized() {
        if (this.@allGlobalArguments != null && this.@allGlobalProperties != null) {
            return
        }
        this.@allGlobalArguments = new HashSet<>()
        this.@allGlobalProperties = new HashSet<>()
        SortedSet<ConfigurationDefinition<?>> globalConfigurations = Scope
                .getCurrentScope()
                .getSingleton(LiquibaseConfiguration.class)
                .getRegisteredDefinitions(false)
        globalConfigurations.each { ConfigurationDefinition<?> opt ->
            String fixedArg = ArgumentBuilderService.fixGlobalArgument(opt.getKey())
            this.@allGlobalArguments += fixedArg
            this.@allGlobalProperties += LIQUIBASE_PREFIX + fixedArg.capitalize()
            opt.getAliasKeys().each { String it ->
                String fixedAlias = ArgumentBuilderService.fixGlobalArgument(it)
                this.@allGlobalArguments += fixedAlias
                this.@allGlobalProperties += LIQUIBASE_PREFIX + fixedAlias.capitalize()
            }
        }
    }

    /**
     * Build arguments, in the right order, to pass to Liquibase.
     *
     * @param activity the activity being run
     * @param commandName command name
     * @param supportedCommandArguments arguments supported by this specific command
     */
     List<String> buildLiquibaseArgs(
             Activity activity,
             String commandName,
             Set<String> supportedCommandArguments
     ) {
        ensureGlobalArgsInitialized()

        List<String> liquibaseArgs = []
        List<String> globalArgs = []
        List<String> commandArguments = []
        boolean sendingChangelog = false
        String kebabCommand = ArgumentBuilderService.toKebab(commandName)

        if ( this.allGlobalArguments.contains(ARG_INTEGRATION_NAME) ) {
            LOGGER.debug("liquibase-plugin:    Adding --integration-name parameter because \
Liquibase supports it")
            globalArgs += ArgumentBuilderService.argumentString(ARG_INTEGRATION_NAME, 'gradle')
        }

        // Some Liquibase globals are critical even if not exposed by LiquibaseConfiguration
         // in older versions
        Set<String> fallbackGlobalArgs = [FALLBACK_ARG_SEARCH_PATH] as Set<String>

        Map<String, String> resolvedArgs = activity.arguments?.getOrElse([:]) as Map<String, String>
        createArgumentMap(resolvedArgs).each { entry ->
            String argumentName = entry.key
            String entryValue = entry.value
            String kebabArgName = ArgumentBuilderService.toKebab(argumentName)
            if ( this.allGlobalArguments.contains(argumentName)
                    || this.allGlobalArguments.contains(kebabArgName)
                    || fallbackGlobalArgs.contains(argumentName)
            ) {
                globalArgs += ArgumentBuilderService.argumentString(argumentName, entryValue)
            } else if ( supportedCommandArguments.contains(argumentName)
                    || supportedCommandArguments.contains(kebabArgName)
            ) {
                if ( argumentName == ARG_CHANGELOG_FILE ) {
                    if ( kebabCommand == CMD_DROP_ALL_KEBAB ) {
                        return
                    }
                    sendingChangelog = true
                }
                commandArguments += ArgumentBuilderService.argumentString(argumentName, entryValue)
            } else {
                LOGGER.debug("skipping the ${argumentName} command argument because it is \
not supported by the ${commandName} command")
            }
        }

        if ( commandName == CMD_DBDOC && !commandArguments.any {
            it.startsWith(ARG_OUTPUT_DIRECTORY)
        } ) {
            commandArguments +=
                    "${ARG_OUTPUT_DIRECTORY}=${parameters.buildDir.get().asFile}/database/docs"
        }

        liquibaseArgs = globalArgs + kebabCommand + commandArguments

        if ( sendingChangelog ) {
            Map<String, String> changelogParamMap = createChangelogParamMap(activity)
            changelogParamMap.each { k, v -> liquibaseArgs += "-D${k}=${v}" }
        }

        LOGGER.debug("liquibase-plugin: Final CLI args: ${liquibaseArgs}")
        return liquibaseArgs
    }

    static String fixGlobalArgument(String arg) {
        // Normalize Liquibase definition keys to camelCase expected by our DSL/property mapping.
        // Examples:
        //  - liquibase.search-path -> searchPath
        //  - liquibase.log-level   -> logLevel
        //  - liquibase.some.key    -> someKey
        String cleaned = (arg - LIQUIBASE_PREFIX_DOT)
        // dots to camel
        cleaned = cleaned.replaceAll(/(\.)([A-Za-z0-9])/, { m -> m[2].toUpperCase() })
        // hyphens to camel
        cleaned = cleaned.replaceAll(/(-)([A-Za-z0-9])/, { m -> m[2].toUpperCase() })
        return cleaned
    }

    private Map<String, String> createArgumentMap(Map<String, String> arguments) {
        Map<String, String> argumentMap = [:]
        arguments.each { entry ->
            if ( entry.key != ARG_CHANGELOG_PARAMETERS ) {
                LOGGER.trace(
                        "liquibase-plugin:    Setting ${entry.key}=${entry.value} from activities")
                argumentMap.put(entry.key, entry.value)
            }
        }

        Set<String> allCmdProps = parameters.allCommandArguments.get().collect { String arg ->
            LIQUIBASE_PREFIX + arg.capitalize() } as Set<String>

        parameters.properties.get().findAll { prop ->
            if ( !this.allGlobalProperties.contains(prop.key) && !allCmdProps.contains(prop.key) ) {
                return false
            }
            return true
        }.each { prop ->
            String argName = (prop.key - LIQUIBASE_PREFIX).uncapitalize()
            LOGGER.trace(
                    "liquibase-plugin:    Setting ${argName}=${prop.value} from the command line")
            argumentMap.put(argName, prop.value)
        }

        return argumentMap.sort()
    }

    private Map<String, String> createChangelogParamMap(Activity activity) {
        Map<String, String> changelogParameters = [:]

        Map<String, String> resolvedParams = activity.changelogParameters.get()
        resolvedParams.each { entry ->
            LOGGER.trace("liquibase-plugin:    Adding activity changelogParameter \
${entry.key}=${entry.value}")
            changelogParameters.put(entry.key as String, entry.value)
        }

        if ( !parameters.properties.get().containsKey(PROP_CHANGELOG_PARAMETERS) ) {
            return changelogParameters
        }
        parameters.properties.get().get(PROP_CHANGELOG_PARAMETERS)
                .toString().split(',').each { String pair ->
            def (String key, String value) = pair.split('=')
            LOGGER.trace("liquibase-plugin:    Adding property changelogParameter ${key}=${value}")
            changelogParameters.put(key, value)
        }
        return changelogParameters
    }

    private static String argumentString(String argumentName, String argumentValue) {
        String option = ArgumentBuilderService.toKebab(argumentName)
        return argumentValue ? "--${option}=${argumentValue}" : "--${option}"
    }

    private static String toKebab(String str) {
        return str.replaceAll(/([A-Z])/, { m -> "-" + m[1].toLowerCase() })
    }

    @Override
    void close() throws Exception {
        // nothing to close
    }
}
