package dev.oop778.gradle.common

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

class CommonPlugin implements Plugin<Project> {
    private Project project

    @Override
    void apply(Project project) {
        this.project = project
        println "Applying common plugin to ${project.name}"
        project.extensions.create("common", CommonExtension.class, project)

        project.pluginManager.withPlugin("maven-publish") {
            setupPublishing()
        }

        project.tasks.withType(Jar.class).configureEach {
            destinationDirectory.set(project.rootProject.rootDir.toPath().resolve("out").toFile())
        }
    }

    private void setupSigning(PublishingExtension publishing) {
        project.findProperty("signingKey")?.with { signingKey ->
            project.findProperty("signingPassword")?.with { signingPassword ->
                if (!project.pluginManager.hasPlugin("signing")) {
                    project.apply plugin: "signing"
                    println "Applied signing plugin to ${project.name} (You should usually apply it yourself, this is just a warning for " +
                            "unexpected behavior)"
                }

                project.extensions.configure(SigningExtension.class) { signing ->
                    signing.useInMemoryPgpKeys(signingKey, signingPassword)
                    publishing.publications.configureEach {
                        println "Signing publication ${it.name} for ${project.name}"
                        signing.sign(it)
                    }
                }
            }
        }
    }

    private void setupPublishingRepository(PublishingExtension publishing) {
        project.findProperty("osshrUsername")?.with { osshrUsername ->
            project.findProperty("osshrPassword")?.with { osshrPassword ->
                boolean isSnapshot = (project.properties["version"] as String).contains("SNAPSHOT")

                publishing.repositories.maven {
                    name = "OSSRH"
                    url = isSnapshot
                            ? "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                            : "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    credentials {
                        username = osshrUsername
                        password = osshrPassword
                    }
                }
            }
        }
    }

    private void setupPublishing() {
        project.extensions.configure(PublishingExtension.class) {publishing ->
            // Setup signing
            setupSigning(publishing)

            // Setup repositories
            setupPublishingRepository(publishing)

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
