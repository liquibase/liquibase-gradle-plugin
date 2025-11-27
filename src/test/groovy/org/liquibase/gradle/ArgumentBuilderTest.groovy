package org.liquibase.gradle

import liquibase.Scope
import liquibase.command.CommandDefinition
import liquibase.command.CommandFactory
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.liquibase.gradle.Util.argumentsForCommand

/**
 * Unit tests for the {@link ArgumentBuilder}
 *
 * @author Steven C. Saliman
 */
class ArgumentBuilderTest {
    def activity
    def command
    def actualArgs
    def expectedArgs

    Project project
    ArgumentBuilder argumentBuilder

    @Before
    void setUp() {
        // Set up a command that supports our standard database arguments.  We use status because it
        // supports a boolean argument with an optional value (--verbose)
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("status")

        // Set up an activity that defines some arguments.  Some of them are here to be overridden
        // by the command line later.
        activity = new Activity("main")

        // Add some command arguments.  One of them needs to be unknown to Liquibase, one needs to
        // be a valid command argument that is not supported by our test command, and one needs to
        // be a boolean.
        activity.changelogFile "activityChangelog"  // needed to test proper handling of "-D" args
        activity.username "activityUsername"
        activity.password "activityPassword"  // This one will be overridden.
        activity.verbose()  // boolean used by status
        activity.includeObjects "activityIncludes" // not supported by he Status command

        // Add some global arguments.  This can't be anything that exists in LiquibaseCommand.
        activity.globalArg "activityGlobalValue" // this one will be unsupported.
        activity.logFile "activityLog" // Can't use a logLevel, it has a default.
        activity.logFormat "activityFormat"  // This will be overridden.

        // some changelog params
        activity.changelogParameters(["param1": "value1", "param2": "value2"])

        // Simulate a command line with "-P" options by adding them to the project/s ext collection.
        project = newProject()

        project.ext.liquibaseTag = "extTag" // unsupported by command
        // Set up a "-P" property to pass extra arguments.
        project.ext.liquibasePassword = "extPassword" // override a command arg
        project.ext.liquibaseUrl="extUrl" // Supply a new command arg
        project.ext.liquibaseLogFormat="extFormat" // Override a global arg
        project.ext.liquibaseClasspath="extClasspath" // supply a new global arg
        project.ext.liquibaseVersion="extVersion" // invalid global

        // Some changelog parameters from the command line. One overrides an activity parameter,
        // the other is new.
        project.ext.liquibaseChangelogParameters="param2=ext2,param3=ext3"

        // Create an ArgumentBuilder and add all the  liquibase commands to it.  This makes our test
        // more like an integration test, but it is the best we can do since we're coupled to the
        // Liquibase API anyway.
        argumentBuilder = new ArgumentBuilder()
        argumentBuilder.initializeGlobalArguments()
        Set<CommandDefinition> commands = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommands(false)
        def supportedCommands = commands.findAll { !it.hidden && !it.name.contains("init") }
        supportedCommands.each { cmd ->
            // Let the builder know about the command so it can process arguments later
            argumentBuilder.addCommandArguments(argumentsForCommand(cmd))
        }
    }

    protected static Project newProject(Closure<Activity> activityFactory = { activity }) {
        Project project = ProjectBuilder.builder().build()
        LiquibasePlugin.doApplyExtension(project, activityFactory)
        return project
    }

    /**
     * Test building arguments when we have all the argument types that could exist in a command
     * line.  Expect the following arguments in exactly this order:
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-file, with the value from the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --changelog-file, with the value from the activity
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     * --username, with the value from the activity
     * --verbose, with no value because the activity didn't specify one
     * the three -D parameters.
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase
     */
    @Test
    void buildLiquibaseArgsFullArguments() {
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "status",
                "--changelog-file=activityChangelog",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
                "--verbose",
                "-Dparam1=value1",
                "-Dparam2=ext2",
                "-Dparam3=ext3"
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have all the argument types that could exist in a command
     * line, but the command doesn't support the changelog-file.  We should omit "-D" args, because
     * they only get sent with changelog-file.
     *
     * The diff task fits that description.
     *
     * Expect the following arguments in exactly this order.
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-file, with the value from the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * diff, which is the command
     * --include-objects, with a value because the diff command supports this one.
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     * --username, with the value from the activity
     *
     * Expect changelogFile, includeObjects and tag to be filtered out because they are not
     * supported by the command, globalArg and version to be filtered out because they aren't
     * supported by Liquibase, and the "-D" arguments should be filtered out because we aren't
     * sending a changelog.
     */
    @Test
    void buildLiquibaseArgsNoChangelog() {
        // DropAll doesn't send a changelog...
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("diff")
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "diff",
                "--include-objects=activityIncludes",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
        ]

        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments.  Did we forget to filter out the changelog parms when not using changelog-file?",
                expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have all the argument types that could exist in a command
     * line, but the command is drop-all.  This tests that we work around
     * https://github.com/liquibase/liquibase/issues/3380 by omitting the changelog when we are
     * running drop-all.  We should also omit the "-D" args.
     *
     * Expect the following arguments in exactly this order.
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-file, with the value from the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * diff, which is the command
     * --include-objects, with a value because the diff command supports this one.
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     * --username, with the value from the activity
     *
     * Expect password and exclude-objects to be filtered out because the command doesn't support
     * those arguments.
     */
    @Test
    void buildLiquibaseArgsDropAll() {
        // The drop-all command has special handling.
        // DropAll doesn't send a changelog...
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("dropAll")
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "drop-all",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments.  Did we forget to filter out the changelog and changelog parms with drop-all?",
                expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have all the argument types that could exist in a command
     * line but we don't set an output directory for the db-doc command.  This is the only command
     * that has a default value.
     *
     * Expect the following arguments in exactly this order.
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-file, with the value from the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --changelog-file, with the value from the activity
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     * --username, with the value from the activity
     * --output-directory, with a default value. It is here because the special handling comes
     *     after we process the other args.
     * the three -D parameters.  These are always last.
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase
     */
    @Test
    void buildLiquibaseDbDocWithoutDir() {
        // The db-doc command has special handling
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("dbDoc")
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "db-doc",
                "--changelog-file=activityChangelog",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
                "--output-directory=${project.buildDir}/database/docs",
                "-Dparam1=value1",
                "-Dparam2=ext2",
                "-Dparam3=ext3"
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments.  Did we use the default value for output-dir with db-doc?",
                expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have all the argument types that could exist in a command
     * line, but no changelog parameters.
     *
     * Expect the following arguments in exactly this order.
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-file, with the value from the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --changelog-file, with the value from the activity
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     * --username, with the value from the activity
     * --verbose, with no value because the activity didn't specify one
     * the 2 -D params that came from the command line.
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase
     */
    @Test
    void buildLiquibaseArgsNoChangeLogParms() {
        activity.changelogParameters.clear()
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "status",
                "--changelog-file=activityChangelog",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
                "--verbose",
                "-Dparam2=ext2",
                "-Dparam3=ext3"
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we the activity doesn't define any arguments at all.
     *
     * Expect the following arguments in exactly this order.
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase.  We also expect the usual values from the activity to be filtered out because we
     * aren't setting any activity arguments.
     */
    @Test
    void buildLiquibaseArgsActivityHasNoArgs() {
        activity = new Activity("main")

        expectedArgs = [
                "--classpath=extClasspath",
                "--log-format=extFormat",
                "--log-level=info",
                "status",
                "--password=extPassword",
                "--url=extUrl"
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when the version of Liquibase is newer, and thus the
     * --integration-name argument should be included.
     *
     * Expect the following arguments in exactly this order.
     * --integration-name, with value gradle
     * --classpath, with the value from the command line because it isn't in the activity
     * --log-format, with an overridden value
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --password, with an overridden value
     * --url, with the value from the command line because it isn't in the activity
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase.  We also expect the usual values from the activity to be filtered out because we
     * aren't setting any activity arguments.
     */
    @Test
    void buildLiquibaseArgsActivityWithRecentVersionOfLiquibase() {
        activity = new Activity("main")

        argumentBuilder.allGlobalArguments.add("integrationName")

        expectedArgs = [
                "--integration-name=gradle",
                "--classpath=extClasspath",
                "--log-format=extFormat",
                "--log-level=info",
                "status",
                "--password=extPassword",
                "--url=extUrl"
        ]

        actualArgs = buildLiquibaseArgs()
        argumentBuilder.allGlobalArguments.remove("integrationName")
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have no command line arguments.
     * line.  Expect the following arguments in exactly this order.
     * --log-file, with the value from the activity
     * --log-format, with the value from the activity
     * --log-level=info, because it is global and the Activity has a default value
     * status, which is the command
     * --changelog-file, with the value from the activity
     * --password, with the value from the activity
     * --username, with the value from the activity
     * --verbose, with no value because the activity didn't specify one
     * the two -D parameters that came from the activity.
     *
     * Expect includeObjects and tag to be filtered out because they are not supported by the
     * command, and globalArg and version to be filtered out because they aren't supported by
     * Liquibase.  We also won't have a classpath or url because we no longer supply one.
     */
    @Test
    void buildLiquibaseArgsNoExtraArgs() {
        // new project to clear the command value
        project = newProject()

        expectedArgs = [
                "--log-file=activityLog",
                "--log-format=activityFormat", // because we no longer override it
                "--log-level=info",
                "status",
                "--changelog-file=activityChangelog",
                "--password=activityPassword", // because we no longer override it
                "--username=activityUsername",
                "--verbose",
                "-Dparam1=value1",
                "-Dparam2=value2"
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    /**
     * Test building arguments when we have absolutely nothing.  Expect to just send the command
     */
    @Test
    void buildLiquibaseArgsWithNothing() {
        // new activity and we'll even clear out the default argument.
        activity = new Activity("main")
        activity.changelogParameters = [:]
        activity.arguments = [:]
        // new project to clear the command value
        project = newProject()

        expectedArgs = [
                "status",
        ]
        actualArgs = buildLiquibaseArgs()
        // For some reason, comparing arrays, doesn't work right, so join into single strings.
        assertEquals("Wrong arguments", expectedArgs.join(" "),  actualArgs.join(" "))
    }

    protected List<Object> buildLiquibaseArgs() {
        return argumentBuilder.buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command), new ProjectInfo(project))
    }
}
