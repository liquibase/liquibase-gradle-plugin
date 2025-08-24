Liquibase Gradle Plugin
-----------------------

A plugin for [Gradle](http://gradle.org) that allows you to use [Liquibase](http://liquibase.org)
to manage your database upgrades.  This project was originally created by Tim Berglund, and is
currently maintained by Steve Saliman.

Release 3.0.2 has been released
-------------------------------

Release 3.0.2 has been released with support for Liquibase versions through 4.31.1.  See
[Releases](./doc/releases.md) and the [Changelog](./doc/changelog.md) for more details.

Note that Liquibase 4.30.0 added a dependency to Commons Lang, but didn't declare it as a transitive
dependency.  `liquibaseRuntime 'org.apache.commons:commons-lang3:3.14.0'` will need to be added to
the `liquibaseRuntime` classpath for it to work.

Release 3.0.0 fixed compatability issues with newer versions of Liquibase. 
**THIS WAS A BREAKING CHANGE!**.  The plugin no longer works with versions of Liquibase older than
4.24.

Users updating from prior versions of this plugin should look at the [Releases](./doc/releases.md)
page for more information about the releases, including any breaking changes.

Documentation
-------------

- [Releases](./doc/releases.md)
- [How it works](./doc/how-it-works.md)
- [Usage](./doc/usage.md)
- [Upgrading Liquibase](./doc/upgrading-liquibase.md)
- [Publishing the plugin](./doc/publishing-new-version.md)

