/*
 * Configuration-cache friendly Activity data transfer object (DTO).
 *
 * Purpose:
 * - Provide a detached, serializable snapshot of an Activity configuration for task execution.
 * - Avoid accessing Gradle model objects (e.g., NamedDomainObjectContainer<Activity>) at execution
 *   time, which violates Gradle Configuration Cache requirements.
 * - Preserve the DSL ergonomics: users configure Activities via the container; during
 *   configuration, those are transformed into ActivitySpec instances that tasks consume as
 *   regular @Input properties.
 *
 * See also:
 * - Configuration Cache requirements:
 *   https://docs.gradle.org/current/userguide/configuration_cache_requirements.html
 * - NamedDomainObjectContainer (lazy realization):
 *   https://docs.gradle.org/current/dsl/org.gradle.api.NamedDomainObjectContainer.html
 */
package org.liquibase.gradle

class ActivitySpec implements Serializable {
    String name
    Map<String, String> arguments = [:]
    Map<String, String> changelogParameters = [:]

    ActivitySpec() {}

    ActivitySpec(String name, Map<String, String> arguments, Map<String, String> changelogParameters) {
        this.name = name
        this.arguments = new LinkedHashMap<>(arguments ?: [:])
        this.changelogParameters = new LinkedHashMap<>(changelogParameters ?: [:])
    }

    ActivitySpec(Activity activity) {
        this(
            activity?.name,
            activity?.arguments ? new LinkedHashMap<>(activity.arguments.get()) : [:],
            activity?.changelogParameters ? new LinkedHashMap<>(activity.changelogParameters.get()) : [:]
        )
    }
}
