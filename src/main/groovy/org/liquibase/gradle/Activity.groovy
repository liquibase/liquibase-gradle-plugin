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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty

import javax.inject.Inject

/**
 * This class represents a single activity that can be performed as part of a liquibase task.  It
 * is basically the intersection of changelogs and databases. Each named activity in the
 * {@code activities} closure of the {@code liquibase} block will create one of these objects.
 *
 * @author Steven C. Saliman
 */
class Activity implements Serializable {
    /**
     * The name of the activity.  This translates to the name of the block inside "activities".
     */
    String name

    /**
     * The arguments to pass with the command, such as username or password. Default includes
     * logLevel=info.
     */
    final MapProperty<String, String> arguments

    /**
     * Changelog parameters. These are passed to Liquibase via "-D" options.
     */
    final MapProperty<String, String> changelogParameters

    @Inject
    Activity(String name, ObjectFactory objects) {
        this.name = name
        this.arguments = objects.mapProperty(String, String)
        // default logLevel to info
        this.arguments.set(['logLevel': 'info'])
        this.changelogParameters = objects.mapProperty(String, String)
        this.changelogParameters.convention([:])
    }

    /**
     * Define the ChangeLog parameters to use for this activity.  ChangeLog parameters are used by
     * Liquibase to perform token substitution on change sets.
     *
     * @param tokenMap the map of tokens and their values.
     */
    void changelogParameters(Map<String, String> tokenMap) {
        if (tokenMap == null || tokenMap.isEmpty()) return
        this.changelogParameters.putAll(tokenMap)
    }

    /**
     * Used to configure the Liquibase arguments  in this activity.  The method name is assumed to
     * be a valid Liquibase argument.  Not worrying about validity here is one way we decouple
     * ourselves from any particular version of Liquibase.
     *
     * @param name the name of the Liquibase argument
     * @param args Technically an array, the first value will be taken as the value of the Liquibase
     * argument.
     */
    def methodMissing(String name, args) {
        this.arguments.put(name, args ? (String) args.first() : '')
    }

}
