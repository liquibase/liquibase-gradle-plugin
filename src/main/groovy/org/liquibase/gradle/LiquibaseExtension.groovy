/*
 * Copyright 2011-2025 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 */

package org.liquibase.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration

/**
 * This is the Gradle extension that configures the Liquibase plugin.  All configuration options
 * will be in the {@code liquibase} block of the build.gradle file.  This block consists of a list
 * of activities and a run list.
 *
 * @author Steven C. Saliman
 */
class LiquibaseExtension {
    final NamedDomainObjectContainer<Activity> activities

    /**
     * Define the name of the Main class in Liquibase that the plugin should call to run Liquibase
     * itself.
     */
    def mainClassName

    /**
     * Define the JVM arguments to use when running Liquibase.  This defaults to an empty array,
     * which is almost always what you want.
     */
    def jvmArgs = []

    /**
     * Define the list of activities that run for each liquibase task.  This is a string of comma
     * separated activity names.  This is a string instead of an array to facilitate the use of
     * Gradle properties.  If no runList is defined, the plugin will run all activities.
     */
    def runList

    LiquibaseExtension(NamedDomainObjectContainer<Activity> activities) {
        this.activities = activities
    }

    @SuppressWarnings('unused')
    def activities(Closure closure) {
        activities.configure(closure)
    }
}
