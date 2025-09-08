package org.liquibase.gradle

import liquibase.Scope
import liquibase.command.CommandDefinition
import liquibase.command.CommandFactory
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.liquibase.gradle.Util.argumentsForCommand

class ArgumentBuilderServiceTest {

    def activity
    def command
    def actualArgs
    def expectedArgs

    def project
    Provider<ArgumentBuilderService> service
    Set<String> unionCommandArgs

    @BeforeEach
    void setUp() {
        // project and properties
        project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()

        // Simulate -P properties
        project.ext.liquibaseTag = "extTag" // unsupported by command
        project.ext.liquibasePassword = "extPassword" // override a command arg
        project.ext.liquibaseUrl = "extUrl" // Supply a new command arg
        project.ext.liquibaseLogFormat = "extFormat" // Override a global arg
        project.ext.liquibaseClasspath = "extClasspath" // supply a new global arg
        project.ext.liquibaseVersion = "extVersion" // invalid global
        project.ext.liquibaseChangelogParameters = "param2=ext2,param3=ext3"

        // Activity with defaults
        activity = new Activity("main", project.objects)
        activity.changelogFile "activityChangelog"
        activity.username "activityUsername"
        activity.password "activityPassword"
        activity.verbose()
        activity.includeObjects "activityIncludes"
        activity.globalArg "activityGlobalValue"
        activity.logFile "activityLog"
        activity.logFormat "activityFormat"
        activity.changelogParameters.set([param1: 'value1', param2: 'value2'])

        // Determine command sets and union for service params
        Set<CommandDefinition> commands = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommands(false)
        def supportedCommands = commands.findAll { !it.hidden && !it.name.contains("init") }
        unionCommandArgs = new HashSet<>()
        supportedCommands.each { cmd -> unionCommandArgs.addAll(argumentsForCommand(cmd)) }

        // Register the service with parameters
        Map<String, String> safeProps = [
                liquibaseTag: project.ext.liquibaseTag.toString(),
                liquibasePassword: project.ext.liquibasePassword.toString(),
                liquibaseUrl: project.ext.liquibaseUrl.toString(),
                liquibaseLogFormat: project.ext.liquibaseLogFormat.toString(),
                liquibaseClasspath: project.ext.liquibaseClasspath.toString(),
                liquibaseVersion: project.ext.liquibaseVersion.toString(),
                liquibaseChangelogParameters: project.ext.liquibaseChangelogParameters.toString(),
        ]
        service = project.gradle.sharedServices.registerIfAbsent("testArgumentBuilderService", ArgumentBuilderService) {
            parameters.buildDir.set(project.layout.buildDirectory)
            parameters.properties.set(safeProps)
            parameters.allCommandArguments.set(unionCommandArgs)
        }
    }

    /**
     * Build arguments for the "status" command when we have all argument types present.
     * The service collects global arguments and command arguments, preserves ordering, and
     * emits changelog parameters in two places when a changelog is provided:
     *   --changelog-parameters (aggregated key=value pairs)
     *   and -Dparam=value entries at the end of the list.
     *
     * Expect in order:
     * --classpath from -P (not set in activity)
     * --log-file from activity
     * --log-format overridden by -P
     * --log-level=info (global with default on Activity)
     * status (the command)
     * --changelog-file from activity
     * --changelog-parameters aggregated from -P
     * --password overridden by -P
     * --url from -P (not set in activity)
     * --username from activity
     * --verbose (boolean with no value)
     * -Dparam1 from activity and -Dparam2/-Dparam3 from -P
     *
     * Unsupported or unknown items (e.g., includeObjects, tag, globalArg, version) are filtered out.
     */
    @Test
    void buildLiquibaseArgs_FullArguments_Status() {
        // status supports boolean --verbose
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("status")
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "status",
                "--changelog-file=activityChangelog",
                "--changelog-parameters=param2=ext2,param3=ext3",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
                "--verbose",
                "-Dparam1=value1",
                "-Dparam2=ext2",
                "-Dparam3=ext3"
        ]
        actualArgs = service.get().buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command))
        assertEquals(expectedArgs.join(" "), actualArgs.join(" "), "Wrong arguments.\nExpected: ${expectedArgs}\nActual:   ${actualArgs}")
    }

    /**
     * Build arguments for the "diff" command, which does not use a changelog.
     * Since no changelog is sent, the service should omit both
     *   --changelog-file and any -D changelog parameters.
     *
     * Expect in order:
     * --classpath, --log-file, --log-format, --log-level=info, diff
     * --include-objects (supported by diff)
     * --password, --url, --username
     *
     * Unsupported/unknown arguments and all changelog-related items are filtered out.
     */
    @Test
    void buildLiquibaseArgs_NoChangelogParams_Diff() {
        // diff does not use changelogFile
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
        actualArgs = service.get().buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command))
        assertEquals(expectedArgs.join(" "), actualArgs.join(" "),
                "Wrong arguments when not sending changelog params with diff.\nExpected: ${expectedArgs}\nActual:   ${actualArgs}")
    }

    /**
     * Build arguments for the "dbDoc" command without specifying an output directory.
     * The service should supply the default output directory and, because a changelog is present,
     * include both --changelog-parameters and the -D changelog parameters at the end.
     *
     * Expect in order:
     * --classpath, --log-file, --log-format, --log-level=info, db-doc
     * --changelog-file, --changelog-parameters
     * --password, --url, --username
     * --output-directory with default value under buildDir
     * -Dparam1, -Dparam2, -Dparam3
     */
    @Test
    void buildLiquibaseArgs_DbDoc_DefaultOutputDir() {
        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("dbDoc")
        expectedArgs = [
                "--classpath=extClasspath",
                "--log-file=activityLog",
                "--log-format=extFormat",
                "--log-level=info",
                "db-doc",
                "--changelog-file=activityChangelog",
                "--changelog-parameters=param2=ext2,param3=ext3",
                "--password=extPassword",
                "--url=extUrl",
                "--username=activityUsername",
                "--output-directory=${project.layout.buildDirectory.get().asFile}/database/docs",
                "-Dparam1=value1",
                "-Dparam2=ext2",
                "-Dparam3=ext3"
        ]
        actualArgs = service.get().buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command))
        assertEquals(expectedArgs.join(" "), actualArgs.join(" "),
                "Wrong arguments. Expected db-doc output directory default.\nExpected: ${expectedArgs}\nActual:   ${actualArgs}")
    }

    /**
     * Build arguments for the "dropAll" command.
     * Due to Liquibase issue 3380, the plugin omits the changelog and all -D parameters
     * when the command is drop-all. Only global and supported command arguments remain.
     *
     * Expect in order:
     * --classpath, --log-file, --log-format, --log-level=info, drop-all
     * --password, --url, --username
     *
     * No --changelog-file, no --changelog-parameters, and no -D entries.
     */
    @Test
    void buildLiquibaseArgs_DropAll_OmitsChangelogAndParams() {
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
        actualArgs = service.get().buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command))
        assertEquals(expectedArgs.join(" "), actualArgs.join(" "),
                "Wrong arguments. dropAll should omit changelog-file and -D params.\nExpected: ${expectedArgs}\nActual:   ${actualArgs}")
    }

    /**
     * Build arguments when there are no -P liquibase* properties.
     * The service should use only values from the activity and include the -D entries
     * that originate from the activity's changelog parameters.
     *
     * Expect in order:
     * --log-file, --log-format, --log-level=info, status
     * --changelog-file, --password, --username, --verbose
     * -Dparam1 and -Dparam2 (from activity)
     */
    @Test
    void buildLiquibaseArgs_NoExtraProperties_UsesOnlyActivityValues() {
        // register a service with empty properties
        def emptyPropsService = project.gradle.sharedServices.registerIfAbsent("testArgumentBuilderServiceEmpty", ArgumentBuilderService) {
            parameters.buildDir.set(project.layout.buildDirectory)
            parameters.properties.set([:])
            parameters.allCommandArguments.set(unionCommandArgs)
        }

        command = Scope.getCurrentScope().getSingleton(CommandFactory.class).getCommandDefinition("status")
        expectedArgs = [
                "--log-file=activityLog",
                "--log-format=activityFormat",
                "--log-level=info",
                "status",
                "--changelog-file=activityChangelog",
                "--password=activityPassword",
                "--username=activityUsername",
                "--verbose",
                "-Dparam1=value1",
                "-Dparam2=value2"
        ]
        actualArgs = emptyPropsService.get().buildLiquibaseArgs(activity, command.name[0], argumentsForCommand(command))
        assertEquals(expectedArgs.join(" "), actualArgs.join(" "), "Wrong arguments with no -P properties.\nExpected: ${expectedArgs}\nActual:   ${actualArgs}")
    }
}
