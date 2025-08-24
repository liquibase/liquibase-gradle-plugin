# Publishing a new version of the Liquibase Gradle Plugin

## Introduction

This project uses 3 plugins to sign and publish artifacts:

[Gradle's Signing Plugin](https://docs.gradle.org/current/userguide/signing_plugin.html)
[Vanniktech's Publishing Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
[Gradle's Plugin Publishing Plugin](https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html)

Publishing also requires credentials and keys that are authorized to publish artifacts for this
project.

## Configuration

Before publishing this plugin, several properties need to be configured so that the various plugins
can do their work.  These properties can be specified on the command line for each build, but the
recommended approach is to store them in `~/.gradle/gradle.properties`
```properties
# The location of the GPG keyring
signing.secretKeyRingFile=$HOME/.gnupg/secring.gpg
# The id of the GPG key in the keyring that Maven and Gradle trust
signing.keyId=<key>
# The password for the signing key.
signing.password=<password>
# The id of the Maven Central User Token
mavenCentralUsername=<token id>
# The secret for the Maven Central User Token
mavenCentralPassword=<secret>
# The id of the key used to publish to the Gradle Plugin portal
gradle.publish.key=<key>
# The secret used to publish to the Gradle Plugin portal
gradle.publish.secret=<secret>

```

## Steps

Publishing new versions of the plugin generally involves 3 steps:

1. Publishing to a local maven repository to test changes locally.  To publish locally, run
  `gradlew publishToMevanLocal`

2. Publishing to Sonatype's snapshot repository to allow others to test work in progress.  This is
  necessary because the Gradle Plugin Portal doesn't support SNAPSHOT releases.  To publish to
  the snapshot repository, make sure the version number in gradle.properties ends with `-SNAPSHOT`,
  and run `gradlew publishToMavenCentral`

3. Publishing to the Gradle Plugin Portal for production releases.  To publish a production release,
  run `gradlew publishPlugins publishToMavenCentral`

