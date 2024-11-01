package dev.oop778.gradle.common

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.jvm.tasks.Jar

class CommonPlugin implements Plugin<Project> {
    private Project project

    @Override
    void apply(Project project) {
        this.project = project
        println "Applying common plugin to ${project.name}"

        project.extensions.create("common", CommonExtension.class, project)
        project.extensions.create("deployer", DeployerExtension.class, project)

        project.pluginManager.withPlugin("maven-publish") {
            setupPublishing()
        }

        project.tasks.withType(Jar.class).configureEach {
            destinationDirectory.set(project.rootProject.rootDir.toPath().resolve("out").toFile())
        }
    }

    private void setupPublishing() {
        project.extensions.configure(PublishingExtension.class) {publishing ->

            publishing.publications.configureEach {
                it.pom {
                    name = project.rootProject.name
                    description = project.properties["maven.pom.description"]
                    url = project.properties["maven.pom.url"]

                    developers {
                        developer {
                            id = 'oop778'
                            name = 'Povilas'
                            email = 'contact@oop778.dev'
                        }
                    }

                    licenses {
                        license {
                            name = 'The Apache License, Version 2.0'
                            url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                        }
                    }

                    scm {
                        url = project.properties["maven.pom.url"]
                    }
                }
            }
        }
    }
}
