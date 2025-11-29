package org.liquibase.gradle.helper


import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider

import java.util.stream.Stream

/**
 * JUnit 5 TestTemplateInvocationContextProvider that supplies a matrix of Gradle versions (from
 * GradleVersionProvider) x Liquibase core versions.
 *
 * Each invocation will provide two String parameters to the test method:
 *  - gradleVersion
 *  - liquibaseVersion
 *
 * The display name will include both versions for easy identification.
 */
class GradleLiquibaseMatrixProvider implements TestTemplateInvocationContextProvider {

    @Override
    boolean supportsTestTemplate(ExtensionContext context) {
        return true
    }

    @Override
    Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        // Define Gradle versions to test. Adjust as needed.
        List<String> gradleVersions = [
                null,        // current
                '8.14.3',
                '9.0.0'
        ]

        // Define Liquibase Core versions to test. Adjust as needed.
        List<String> liquibaseVersions = [
                '4.24.0',
                '4.27.0',
                '4.31.1',
                '4.33.0',
        ]

        return gradleVersions.stream().flatMap { String gv ->
            return liquibaseVersions.stream().map { String lv ->
                return invocation(gv, lv)
            }
        }
    }

    private static TestTemplateInvocationContext invocation(String gradleVersion, String liquibaseVersion) {
        return new TestTemplateInvocationContext() {
            @Override
            String getDisplayName(int invocationIndex) {
                if ( gradleVersion ) {
                    return "Gradle ${gradleVersion} | Liquibase ${liquibaseVersion}"
                }
                "Gradle current | Liquibase ${liquibaseVersion}"
            }

            @Override
            List<Extension> getAdditionalExtensions() {
                return [
                        new ParameterResolver() {
                            @Override
                            boolean supportsParameter(ParameterContext pc, ExtensionContext ec) throws ParameterResolutionException {
                                Class<?> type = pc.parameter.type
                                int index = pc.index
                                // First parameter: gradleVersion (String)
                                // Second parameter: liquibaseVersion (String)
                                return type == String && (index == 0 || index == 1)
                            }

                            @Override
                            Object resolveParameter(ParameterContext pc, ExtensionContext ec) throws ParameterResolutionException {
                                int index = pc.index
                                return index == 0 ? gradleVersion : liquibaseVersion
                            }
                        }
                ]
            }
        }
    }
}
