package org.liquibase.gradle


import liquibase.configuration.ConfigurationDefinition
import liquibase.configuration.LiquibaseConfiguration
import liquibase.Scope

/**
 * This class builds the Liquibase argument array for liquibase 4.24+.  Starting with Liquibase 4.4,
 * Liquibase no longer silently ignores Command Arguments that are not supported by the command, so
 * we need to be particular about how we put together the array.
 * <p>
 * This class puts together arguments in the following order:
 * <ol>
 * <li>Global Arguments, such as --classpath or --log-level.</li>
 * <li>The Command itself.</li>
 * <li>Command Arguments --username or --changelog-file.</li>
 * <li>Changelog parameters, which are supplied as "-D" arguments.  This only happens if we are also
 * sending the changelog-file parameter.</li>
 * <p>
 * There is a relationship in this class between Liquibase arguments and properties.  Properties are
 * the Gradle property representation of a Liquibase argument.  For example, if Liquibase has an
 * argument named "changelogFile", users can define "-PLiquibaseChangelogFile" to pass an argument
 * at runtime.
 * <p>
 * This class has 2 main parts.  The first part configures the argument builder when the plugin is
 * applied, and the second uses that configuration at execution time to build the correct argument
 * list.  It is important do do all the configuration at apply time because the Liquibase classes
 * used at execution time will come from a different jar, and Liquibase API calls will return the
 * wrong things.  This is because Gradle uses the buildscript classpath at apply time, and the
 * liquibaseRuntime classpath at execution time.   Oh, the fun we have with classpath issues. *
 *
 * @author Steven C. Saliman
 */
class ArgumentBuilder {
    // All known Liquibase global arguments.
    static Set<String> allGlobalArguments
    // All Known Liquibase global arguments, as they can be passed in as properties.
    static Set<String> allGlobalProperties
    // All known Liquibase command arguments.
    static Set<String> allCommandArguments = new HashSet<>()
    // All known Liquibase command arguments, as they can be passed in as properties.
    static Set<String> allCommandProperties = new HashSet<>()

    /**
     * Initialize the global argument and global properties sets.  This needs to be done at apply
     * time so that we get arguments from the same classpath that was used to create the tasks.
     * <p>
     * This method asks Liquibase for all the supported global arguments.  Each one is added to the
     * argument array as-is, and to the property set after capitalizing it and adding "liquibase"
     * to the front.
     */
    def initializeGlobalArguments() {
        allGlobalArguments = new HashSet<>()
        allGlobalProperties = new HashSet<>()
        // This is also how LiquibaseCommandLine.addGlobalArgs() gets global args.
        SortedSet<ConfigurationDefinition<?>> globalConfigurations = Scope
                .getCurrentScope()
                .getSingleton(LiquibaseConfiguration.class)
                .getRegisteredDefinitions(false)
        globalConfigurations.each { opt ->
            // fix it and add it.
            def fixedArg = fixGlobalArgument(opt.getKey())
            allGlobalArguments += fixedArg
            allGlobalProperties += "liquibase" + fixedArg.capitalize()
            opt.getAliasKeys().each {
                def fixedAlias = fixGlobalArgument(it)
                allGlobalArguments += fixedAlias
                allGlobalProperties += "liquibase" + fixedAlias.capitalize()
            }
        }
    }

    /**
     * Add a set of command arguments to our collection of known commands arguments.  The plugin
     * adds them for one command at a time as it creates tasks.  The Argument Builder will then use
     * this list to figure out what arguments are command arguments vs. global arguments.
     *
     * @param commandArguments the arguments to add.
     */
    def addCommandArguments(commandArguments) {
        // Add this command's supported arguments to the set of overall command arguments.
        commandArguments.each { argName ->
            // We'll deal with changelogParameters in a special way later.
            if ( argName == "changelogParameters" ) {
                return
            }
            allCommandArguments += argName
            allCommandProperties += "liquibase" + argName.capitalize()
        }
    }

    /**
     * Build arguments, in the right order, to pass to Liquibase.  Note that all the argument sets
     * must have already been initialized.  We can't ask Liquibase for anything because we'll be
     * using a different classpath at execution time.
     *
     * @param activity the activity being run, which contains global and command parameters.
     * @param commandName the name of the liquibase command being run.
     * @param supportedCommandArguments the command arguments supported by the command being run.
     * @param liquibaseInfo the liquibase information containing logger, build directory, and liquibase properties.
     * @return the argument string to pass to liquibase when we invoke it.
     */
    def buildLiquibaseArgs(Activity activity, commandName, supportedCommandArguments, LiquibaseInfo liquibaseInfo) {
        // This is what we'll ultimately return.
        def liquibaseArgs = []

        // Different parts of our liquibaseArgs before we string 'em all together.
        def globalArgs = []
        def commandArguments = []
        def sendingChangelog = false

        if ( allGlobalArguments.contains("integrationName") ) {
            liquibaseInfo.logger.debug("liquibase-plugin:    Adding --integration-name parameter because Liquibase supports it")
            globalArgs += argumentString("integrationName", "gradle")
        }

        // Create a merged map of activity arguments and arguments given as Gradle properties, then
        // process each of the arguments from the map, figuring out what kind of argument each one
        // is and responding accordingly.
        createArgumentMap(activity.arguments, liquibaseInfo).each {
            def argumentName = it.key
            if ( allGlobalArguments.contains(argumentName) ) {
                // We're dealing with global arg.
                globalArgs += argumentString(argumentName, it.value)
            } else if ( supportedCommandArguments.contains(argumentName) ) {
                // We have a command argument, and it is supported by this command.
                // Liquibase 4.4+ has a bug with the way it handles CLI defined changelog
                // parameters with the drop-all command.  It fails to send them to the changelog
                // parser, causing the parse to fail when changelogs use parameters.
                // https://github.com/liquibase/liquibase/issues/3380  As a workaround, we won't
                // add the argument if it is the changeLogFile arg, and we're running the
                // drop-all command.
                if ( argumentName == 'changelogFile' ) {
                    if ( commandName == 'drop-all' ) {
                        return
                    }
                    // Still here?  It's changelogFile, but not drop-all.  Note that we will be
                    // sending a Changelog
                    sendingChangelog = true
                }
                commandArguments += argumentString(argumentName, it.value)
            } else {
                // If nothing matched above, then we had a command argument that was not supported
                // by the command being run.
                liquibaseInfo.logger.debug("skipping the ${argumentName} command argument because it is not supported by the ${commandName} command")
            }
        }

        // If we're processing the db-doc command, and we don't have an output directory in our
        // command arguments, add it here.  The db-doc command is the only one that has a default
        // value.
        if ( commandName == "dbDoc" && !commandArguments.any {it.startsWith("--output-directory") } ) {
            commandArguments += "--output-directory=${liquibaseInfo.buildDir}/database/docs"
        }

        // Now build our final argument array in the following order:
        // global args, command, command args, changelog parameters (-D args)
        liquibaseArgs = globalArgs + toKebab(commandName) + commandArguments

        // If we're sending a changelog, we need to also send change log parameters.  Unfortunately,
        // due to a bug in liquibase itself (https://liquibase.jira.com/browse/CORE-2519), we need
        // to put the -D arguments after the command.  If we put them before the command, they are
        // ignored
        if ( sendingChangelog ) {
            def changelogParamMap = createChangelogParamMap(activity, liquibaseInfo)
            changelogParamMap.each { liquibaseArgs += "-D${it.key}=${it.value}" }

        }

        return liquibaseArgs
    }

    /**
     * Little helper method to "fix" a global argument.  Many of the argument names, as Liquibase
     * gives them to us, start with "liquibase.".  We want to remove that prefix.  We also want to
     * remove dots and change what follows a dot to be capitalized.  For example, "sql.showSql"
     * will become "sqlShowSql".
     *
     * @param arg the argument to fix.
     * @return the fixed arg.
     */
    private fixGlobalArgument(arg) {
        return (arg - "liquibase.").replaceAll("(\\.)([A-Za-z0-9])", { Object[] it -> it[2].toUpperCase() })
    }

    /**
     * Helper method to create an argument map that combines the activity's arguments and the
     * arguments passed in via supported {@code liquibase} properties from the Gradle command line.
     * <p>
     * The output of this method is a map of argument names and their values.  The Gradle properties
     * will be processed after the activity arguments so that they override whatever was in the
     * activity.
     * <p>
     * When this method processes Gradle properties, it filters out properties Liquibase doesn't
     * recognize.  It does this silently because not all properties that start with "liquibase" is
     * meant to be an argument.  For example, "liquibaseVersion" is a common property to define the
     * version Gradle should use in the build, but it is not meant to be passed on to Liquibase
     * itself.
     *
     * @param arguments the arguments from the activity
     * @param liquibaseInfo the liquibase information, from which we'll get the extra arguments.
     * @return a map of argument names and their values.
     */
    private createArgumentMap(arguments, LiquibaseInfo liquibaseInfo) {
        def argumentMap = [:]
        // Start with the activity's arguments
        arguments.each {
            // We'll handle changelog parameters later.
            if ( it.key != "changelogParameters" ) {
                liquibaseInfo.logger.trace("liquibase-plugin:    Setting ${it.key}=${it.value} from activities")
                argumentMap.put(it.key, it.value)
            }
        }

        // Now go through all of the Gradle properties that start with "liquibase" and use them
        // to override/add to the arguments, ignoring the ones Liquibase won't recognize.
        liquibaseInfo.liquibaseProperties.findAll { key, value ->
            if ( !allGlobalProperties.contains(key) && !allCommandProperties.contains(key) ) {
                return false
            }

            // Tasks are also properties, and there is a liquibaseTag task that we want to ignore.
            if ( value != null && LiquibaseTask.class.isAssignableFrom(value.class) ) {
                return false
            }
            return true
        }.each { key, value ->
            def argName = key - "liquibase"
            argName = argName.uncapitalize()
            liquibaseInfo.logger.trace("liquibase-plugin:    Setting ${argName}=${value} from the command line")
            argumentMap.put(argName, value)
        }

        // Return the sorted map.  Unit tests need to have a predictable argument order, and
        // Liquibase doesn't care about order, just what is before and after the command.
        return argumentMap.sort()
    }

    /**
     * Helper method to create a Changelog Parameter map that combines the activity's changelog
     * parameters and the parameters passed in via the {@code liquibaseChangelogParameters}
     * property from the Gradle command line.
     * <p>
     * The output of this method is a map of parameter names and their values.  The Gradle
     * properties will be processed after the activity arguments so that they override whatever was
     * in the activity.
     *
     * @param arguments the arguments from the activity
     * @param liquibaseInfo the liquibase information, from which we'll get the extra arguments.
     * @return a map of parameter names and their values.
     */
    private createChangelogParamMap(activity, LiquibaseInfo liquibaseInfo) {
        def changelogParameters = [:]

        // Start by adding parameters from the activity
        activity.changelogParameters.each {
            liquibaseInfo.logger.trace("liquibase-plugin:    Adding activity changelogParameter ${it.key}=${it.value}")
            changelogParameters.put(it.key, it.value)
        }

        // Override/add to the map with liquibaseInfo properties
        if ( !liquibaseInfo.liquibaseProperties.containsKey("liquibaseChangelogParameters") ) {
            return changelogParameters
        }
        liquibaseInfo.liquibaseProperties.get("liquibaseChangelogParameters").split(",").each {
            def (key, value) = it.split("=")
            liquibaseInfo.logger.trace("liquibase-plugin:    Adding property changelogParameter ${key}=${value}")
            changelogParameters.put(key, value)
        }
        return changelogParameters
    }

    /**
     * Determine the correct argument string to send to Liquibase.  The argument name will be
     * converted to kebab-case, and we'll add the value if we have one.  If we don't we'll assume
     * we are dealing with a boolean argument.
     *
     * @param argumentName the name of the argument to process
     * @param the value of the argument to process.  If this is null, this method assumes we're
     *         dealing with a boolean argument.
     * @return the argument string to send to Liquibase.
     */
    private argumentString(argumentName, argumentValue) {
        // convert to kebab case.
        def option = toKebab(argumentName)

        // return the right argument string.  If we don't have a value, assume a boolean argument.
        return argumentValue ? "--${option}=${argumentValue}" : "--${option}"
    }

    /**
     * Helper method to convert a string to kebab-case.
     * @param str the string to convert
     * @return the converted string.
     */
    private toKebab(str) {
        return str.replaceAll("([A-Z])", { Object[] it -> "-" + it[1].toLowerCase() })
    }

}
