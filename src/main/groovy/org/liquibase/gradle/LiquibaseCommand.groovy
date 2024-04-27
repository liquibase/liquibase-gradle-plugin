package org.liquibase.gradle

/**
 * Base class for representing Liquibase commands.  There will be one class in the
 * {@code liquibase.command} package for each command Liquibase supports.  Most of them is based on
 * a matching {@code CommandStep} in Liquibase's {@code liquibase.command.core} package.
 * 
 * @author Steven C. Saliman
 */

abstract class LiquibaseCommand implements Serializable {
    // These constants represent the known arguments supported by a Liquibase command.  They come
    // from collecting all the CommandArgumentDefinition from CommandSteps in LB.  If you add
    // something here, you must add it to the collection later.  LB uses camelCase in the Java code,
    // which is a nice coincidence since Gradle uses camelCase in its actions.  The two main changes
    // between Liquibase and this list is that we have a "LABELS" constant to represent the alias
    // created in any command that uses "LABEL_FILTER", and the URL constant had to be named
    // something else to avoid a naming conflict with the URL class.
    static final AUTHOR = 'author'
    static final AUTO_UPDATE = 'autoUpdate'
    static final CHANGELOG_FILE = 'changelogFile'
    static final CHANGE_EXEC_LISTENER_CLASS = 'changeExecListenerClass'
    static final CHANGE_EXEC_LISTENER_PROPERTIES_FILE = 'changeExecListenerPropertiesFile'
    static final CHANGESET_AUTHOR = 'changesetAuthor'
    static final CHANGESET_ID = 'changesetId'
    static final CHANGESET_IDENTIFIER = 'changesetIdentifier'
    static final CHANGESET_PATH = 'changesetPath'
    static final CHECK_NAME = 'checkName'
    static final CHECKS_SETTINGS_FILE ='checksSettingsFile'
    static final CONTEXTS = 'contexts'
    static final COUNT = 'count'
    static final DATA_OUTPUT_DIRECTORY = 'dataOutputDirectory'
    static final DATE = "date"
    static final DEFAULT_CATALOG_NAME = 'defaultCatalogName'
    static final DEFAULT_SCHEMA_NAME = 'defaultSchemaName'
    static final DELIMITER = 'delimiter'
    static final DEPLOYMENT_ID = 'deploymentId'
    static final DIFF_TYPES = 'diffTypes'
    static final DRIVER = 'driver'
    static final DRIVER_PROPERTIES_FILE = 'driverPropertiesFile'
    static final EXCLUDE_OBJECTS = 'excludeObjects'
    static final FORCE = 'force'
    static final FORMAT = 'format'
    static final INCLUDE_CATALOG = 'includeCatalog'
    static final INCLUDE_OBJECTS = 'includeObjects'
    static final INCLUDE_SCHEMA = 'includeSchema'
    static final INCLUDE_TABLESPACE = 'includeTablespace'
    static final HUB_CONNECTION_ID = 'hubConnectionId'
    static final HUB_PROJECT_ID = 'hubProjectId'
    static final HUB_PROJECT_NAME = 'hubProjectName'
    static final LABEL_FILTER = 'labelFilter'
    static final LABELS = 'labels' // An alias for label-filter
    static final OUTPUT_DEFAULT_CATALOG = 'output-default-catalog'
    static final OUTPUT_DEFAULT_SCHEMA = 'outputDefaultSchema'
    static final OUTPUT_DIRECTORY = 'outputDirectory'
    static final OVERWRITE_OUTPUT_FILE = 'overwriteOutputFile'
    static final PASSWORD = 'password'
    static final REFERENCE_DEFAULT_CATALOG_NAME = 'referenceDefaultCatalogName'
    static final REFERENCE_DEFAULT_SCHEMA_NAME = 'referenceDefaultSchemaName'
    static final REFERENCE_PASSWORD = 'referencePassword'
    static final REFERENCE_URL = 'referenceUrl'
    static final REFERENCE_USERNAME = 'referenceUsername'
    static final REGISTERED_CHANGELOG_ID = 'registeredChangelogId'
    static final ROLLBACK_SCRIPT = 'rollbackScript'
    static final SCHEMAS = 'schemas'
    static final SNAPSHOT_FORMAT = 'snapshotFormat'
    static final SQL = 'sql'
    static final SQL_FILE = 'sqlFile'
    static final TAG = 'tag'
    static final URL_ARG = 'url' // Just using "URL" was causing a conflict with the URL class.
    static final USERNAME = 'username'
    static final VERBOSE = 'verbose'

    /**
     * Supported Command Arguments.  This is the complete list of arguments that could be a Command
     * Argument, and is used to distinguish between Global Arguments and Command Arguments.
     */
    static final COMMAND_ARGUMENTS = [
            AUTO_UPDATE,
            CHANGELOG_FILE,
            CHANGE_EXEC_LISTENER_CLASS,
            CHANGE_EXEC_LISTENER_PROPERTIES_FILE,
            CHANGESET_AUTHOR,
            CHANGESET_ID,
            CHANGESET_IDENTIFIER,
            CHANGESET_PATH,
            CHECK_NAME,
            CHECKS_SETTINGS_FILE,
            CONTEXTS,
            COUNT,
            DATA_OUTPUT_DIRECTORY,
            DATE,
            DEFAULT_CATALOG_NAME,
            DEFAULT_SCHEMA_NAME,
            DELIMITER,
            DEPLOYMENT_ID,
            DIFF_TYPES,
            DRIVER,
            DRIVER_PROPERTIES_FILE,
            EXCLUDE_OBJECTS,
            FORCE,
            FORMAT,
            INCLUDE_CATALOG,
            INCLUDE_OBJECTS,
            INCLUDE_SCHEMA,
            INCLUDE_TABLESPACE,
            HUB_CONNECTION_ID,
            HUB_PROJECT_ID,
            HUB_PROJECT_NAME,
            LABEL_FILTER,
            LABELS,
            OUTPUT_DEFAULT_CATALOG,
            OUTPUT_DEFAULT_SCHEMA,
            OUTPUT_DIRECTORY,
            OVERWRITE_OUTPUT_FILE,
            PASSWORD,
            REFERENCE_DEFAULT_CATALOG_NAME,
            REFERENCE_DEFAULT_SCHEMA_NAME,
            REFERENCE_PASSWORD,
            REFERENCE_URL,
            REFERENCE_USERNAME,
            REGISTERED_CHANGELOG_ID,
            ROLLBACK_SCRIPT,
            SCHEMAS,
            SNAPSHOT_FORMAT,
            SQL,
            SQL_FILE,
            TAG,
            URL_ARG,
            USERNAME,
            VERBOSE
    ]

    /**
     * Command Arguments that need to come after the command.  This list comes from the second
     * argument to the {@code collectArguments} methods of the Liquibase CommandSteps.
     */
    static final POST_COMMAND_ARGUMENTS = [
            AUTHOR,
            DATA_OUTPUT_DIRECTORY,
            EXCLUDE_OBJECTS,
            INCLUDE_OBJECTS,
            SQL,
            SQL_FILE,
            VERBOSE,
    ]

    /**
     * The command to run in Liquibase 4.4+.  These names should be in kebab-case, and are derived
     * from the CommandStep and the Liquibase documentation at
     * https://docs.liquibase.com/commands/home.html
     */
    def command = null

    /**
     * The legacy command in lb 4.3 and below.  For most commands, this is simply the camelCase
     * version of the command, which is also in the CommandStep classes, but in some cases, there
     * will be subtle differences, such as with the mark-changeset-ran command, which in its legacy
     * form is markNextChangeSetRan with a capital "S".
     */
    def legacyCommand = null

    /**
     * Is a value required for this command?  Some commands require a value.  For example, the
     * {@code tag} command requires the tag value. Most, but not all commands that require a value
     * will also have a {@link #valueArgument}.
     */
    def requiresValue = false

    /**
     * For commands that can take a value, this field will be the name of the command argument that
     * holds that value.  The plugin uses this to know how to send the value provided by the
     * {@code liquibaseCommandValue} property at runtime.
     * <p>
     * Value Arguments come from the 3rd argument of the {@code collectArguments} method in each
     * CommandStep in liquibase.
     */
    def valueArgument = null

    /**
     * The Command arguments supported by this command.  While Global arguments are supported by
     * all commands, Command arguments are only supported by some commands, with this field defining
     * the arguments that this command supports.  They are derived from the
     * {@code CommandArgumentDefinition} constants in each Liquibase CommandStep, minus the ones
     * that are marked as hidden in that CommandStep's static initializer.
     */
    def commandArguments = null

    /**
     * The description of the command, which will be used as the description of the task created by
     * the plugin for the command.  Descriptions are based on the {@code adjustCommandDefinition}
     * method from the Liquibase CommandStep.
     */
    def description = null
}
