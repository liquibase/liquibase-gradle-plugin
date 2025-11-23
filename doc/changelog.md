Changes for 3.0.3
=================

- Updated to Gradle 8.6 and replaced the maven publisher with vanniktech's maven publisher to be
  compatible with Sonatype's new publishing model. 

Changes for 3.0.2
=================

- add `--integration-name parameter` to all commands executed through gradle, with thanks 
  @StevenMassaro. Fixes #155.

- Make argumentBuilder internal to avoid fingerprinting issues, with thanks to Avery Aube
  (@averyaube). Fixes #162.


Changes for 3.0.1
=================
- Fixed a "fingerprinting" issue, with thanks to @averyaube.  Fixes #120. 

- Added support for Liquibase up to version 4.31.1.  Note that using version 4.30+ requires an
  explicit dependency to `org.apache.commons:commons-lang3:3.14.0` in the `liquibaseRuntime`
  classpath.

Changes for 3.0.1
=================
- Changed the way the plugin initializes.  It now gets everything it needs from Liquibase during the
  apply phase so that we don't need to query Liquibase at execution time.  This works around a bug
  that was related to the fact that Liquibase came from two different jars depending on which phase
  we were running.

Changes for 3.0.0
=================
- The Liquibase Groovy Plugin now supports Liquibase 4.24+.  **THIS IS A BREAKING CHANGE!**.  This
  plugin is known to be broken on versions prior to 4.24.  It now uses Liquibase's CommandScope and
  LiquibaseConfiguration apis to discover the supported tasks and their arguments.
  
Changes for 2.2.2
=================
- Added support for the label-filter, labels, adn contexts command arguments to the
  `markNextChangeSetRan` and `markNextChangeSetRanSql` tasks, with thanks to @Tylorjg

Changes for 2.2.1
=================
- Added support for the `author` argument of the `diffChangeLog` and `generateChangeLog` commands
  with thanks to @EggOxygen.

Changes for 2.2.0
=================
- Removed the old `liquibase` id from the plugin.  The plugin must be applied with its new standard
  id of `org.liquibase.gradle`.

- Changed the way Liquibase tasks are created to line up with the newer Liquibase 4.4+ commands.

- Changed the way Liquibase commands are run. The plugin now sends the correct Liquibase 4.4+
  commands when it detects a newer Liquibase version on the classpath.  In other words, it will
  send `drop-all` for Liquibase 4.4+ instead of the legacy `dropAll` command.

- Added the liquibaseOutputFile property so users can specify output files at runtime.

- Added support for Gradle 8, with thanks to Peter Trifanov (@petertrr), dropped support for Gradle
  prior to 6.4.  Fixes #91

- Fixed the way we set the JVM arguments, with thanks to @dthompson-galileo.

- Added a `liquibaseExtraArgs` property to override and supplement the arguments in an activity
  block.  Fixes #106.

- Updated the version of Gradle used to build the project.

Changes for 2.1.1
=================
- Fixed the code that auto-detects the version of Liquibase.  Fixed #94

Changes for 2.1.0
=================
- The plugin will now throw an exception when there are no activities defined, which would happen if
  users forget to configure the plugin.  Fixes #30

- The plugin now uses Gradle's task configuration avoidance API.  Fixes #87.

- The plugin now supports Liquibase 4.4.x and 4.5.x.  Fixes #89 and #92, though there is some custom
  configuration required.
  
- The plugin auto-detects which version of Liquibase is being used, and chooses the main class
  accordingly.
  
- Tasks ending with "SQL" now end in "Sql" to make the plugin more future-proof when the day comes
  that Liquibase stops supporting camel case commands in favor of kebab case.
  
Changes for 2.0.4
=================
- Added a `jvmArgs` property to the extension object to fix an issue when debugging in Idea.  This
  fixes #72.
  
- Re-added the Groovy dependency so that the OutputEnablingLiquibaseRunner works (Issue #74)
  
Changes for 2.0.3
=================
- Fixed a problem caused by changes in Gradle 6.4 (Issue #70), with thanks to Patrick Haun (@bomgar). 
  
- Fixed a deprecation warning that started showing up in Gradle 6.0.
  
Changes for 2.0.2
=================
- Fixed the way the plugin handles System properties.  Liquibase will now inherit System properties
  from the parent JVM when it runs, so you can now define System properties when you invoke Gradle,
  or in your build.gradle file, and Liquibase will use them.  This fixes a problem with overriding
  the change log table that Liquibase uses.

- Fixed a bug that was preventing some command arguments from being processed correctly by
  Liquibase.  Specifically, I improved the list of arguments that need to come *after* the command
  (Issue #64).
 
- Updated the Gradle Wrapper to use Gradle 6.
  
Changes for 2.0.1
=================
- Updated the version of Groovy to 2.4.12 to remove the CVE-2016-6814 vulnerability

Changes for 2.0.0
=================
- The plugin no longer has a transitive dependency on the Liquibase Groovy DSL.
  **THIS IS A BREAKING CHANGE!** The Groovy DSL is what brought in Liquibase itself.  It is now up
  to you to make sure the Groovy DSL and Liquibase itself are on the classpath via
  `liquibaseRuntime` dependencies. This resolves Issue #11, Issue #29, and Issue #36.  Thank you to
  Jasper de Vries (@litpho) for his contribution to this release.

Changes for 1.2.4
=================
- fixed support for the excludeObjects/includeObjects options with thanks to @manuelsanchezortiz
  (Issue #23).
  
Changes for 1.2.3
=================
- Updated the plugin to use the correct, non-snapshot release of the Groovy DSL (Issue #22).
  
Changes for 1.2.2
=================
- Updated the plugin to use the latest Groovy DSL bug fixes

- Worked around a Liquibase bug that was causing problems with the ```status``` command (Issue #3).
  
Changes for 1.2.1
=================
- Updated the DSL to fix a customChange issue.

Changes for 1.2.0
=================
- Updated the DSL to support most of Liquibase 3.4.2 (Issue #4 and Issue #6)

Changes for 1.1.1
=================
- Added support for Liquibase 3.3.5

- Fixed the task descriptions to correctly identify the property that is used to pass values to
  commands (Issue #2)
  
Changes for 1.1.0
=================
- Refactored the project to fit into the Liquibase organization.

Changes for 1.0.2
=================
- Bumped the dependency on the Groovy DSL to a version that works with Java versions before JKD8 
  (Issue #27)

Changes for 1.0.1
=================
- Added support for prefixes for liquibase task names (Issue #20)

- Added support for Liquibase 3.3.2.

- Fixed the ```status``` and ```unexpectedChangeSets``` commands to support the ```--verbose```
  command value.
