/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class CustomLibraryPluginIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

interface SampleLibrary extends LibrarySpec {}
class DefaultSampleLibrary extends DefaultLibrarySpec implements SampleLibrary {}
"""
    }

    def "plugin declares custom library"() {
        when:
        buildWithCustomComponentPlugin()
        and:
        buildFile << """
task checkModel << {
    assert project.componentSpecs.size() == 1
    def sampleLib = project.componentSpecs.sampleLib
    assert sampleLib instanceof SampleLibrary
    assert sampleLib.projectPath == project.path
    assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"
}
"""
        then:
        succeeds "checkModel"
    }

    def "can register custom component model without creating"() {
        when:
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultSampleLibrary)
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
            assert project.componentSpecs.size() == 0
        }
"""

        then:
        succeeds "checkModel"
    }

    def "custom component listed in components report"() {
        given:
        buildWithCustomComponentPlugin()
        when:
        succeeds "components"
        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":components

------------------------------------------------------------
Root project
------------------------------------------------------------

DefaultSampleLibrary 'sampleLib'
--------------------------------

Source sets
    No source sets.

Binaries
    No binaries.

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""))
    }

    def "can have component declaration and creation in separate plugins"() {
        when:
        buildFile << """
        class MyComponentDeclarationModel implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultSampleLibrary)
                }
            }
        }

        class MyComponentCreationPlugin implements Plugin<Project> {
            void apply(final Project project) {
                project.apply(plugin:MyComponentDeclarationModel)
            }

            @RuleSource
            static class Rules {
                @Mutate
                void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

            }
        }

        apply plugin:MyComponentCreationPlugin

        task checkModel << {
             assert project.componentSpecs.size() == 1
             def sampleLib = project.componentSpecs.sampleLib
             assert sampleLib instanceof SampleLibrary
             assert sampleLib.projectPath == project.path
             assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "Can define and create multiple component types in the same plugin"(){
        when:
        buildFile << """
        interface AnotherSampleLibrary extends LibrarySpec {}
        class DefaultAnotherSampleLibrary extends DefaultLibrarySpec implements AnotherSampleLibrary {}

        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultSampleLibrary)
                }

                @ComponentType
                void registerAnother(ComponentTypeBuilder<AnotherSampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultAnotherSampleLibrary)
                }

                @Mutate
                void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

                @Mutate
                void createAnotherSampleLibraryComponents(NamedItemCollectionBuilder<AnotherSampleLibrary> componentSpecs) {
                    componentSpecs.create("anotherSampleLib")
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
             assert project.componentSpecs.size() == 2

             def sampleLib = project.componentSpecs.sampleLib
             assert sampleLib instanceof SampleLibrary
             assert sampleLib.projectPath == project.path
             assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"

             def anotherSampleLib = project.componentSpecs.anotherSampleLib
             assert anotherSampleLib instanceof AnotherSampleLibrary
             assert anotherSampleLib.projectPath == project.path
             assert anotherSampleLib.displayName == "DefaultAnotherSampleLibrary 'anotherSampleLib'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "Cannot register same library type multiple times"(){
        given:
        buildWithCustomComponentPlugin()
        and:
        buildFile << """
        class MyOtherPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules1 {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultSampleLibrary)
                }
                @Mutate
                void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }
        }

        apply plugin:MyOtherPlugin
"""
        when:
        fails "tasks"
        then:
        errorOutput.contains(TextUtil.toPlatformLineSeparators("""
> Exception thrown while executing model rule: MyOtherPlugin\$Rules1#register(org.gradle.runtime.base.ComponentTypeBuilder<SampleLibrary>)
   > Cannot register a factory for type SampleLibrary because a factory for this type already registered."""))
    }

    def buildWithCustomComponentPlugin() {
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.setDefaultImplementation(DefaultSampleLibrary)
                }
                @Mutate
                void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }
}
