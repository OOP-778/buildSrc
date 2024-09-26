package dev.oop778.gradle.common

import org.apache.tools.ant.Task
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
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

        project.afterEvaluate {
            project.getExtensions().configure(JavaPluginExtension.class, extension -> {
                println("configuring: ${project.name}")

                for (final def sourceSet in [extension.sourceSets.getByName("test"), extension.sourceSets.getByName("main")] ) {
                    if (sourceSet.annotationProcessorPath.isEmpty()) {
                        return
                    }

                    if (sourceSet.allSource.isEmpty()) {
                        return
                    }

                    println("registered annotation processor for ${project.name} ${sourceSet.name}")
                    registerAnnotationProcessorFor(sourceSet)
                }
            })

            project.tasks.register("runAnnotationProcessors", task -> {
                task.setGroup("annotationProcessor")

                org.gradle.api.Task mainProcessorTask = project.tasks.findByName("runMainAnnotationProcessor")
                if (mainProcessorTask != null) {
                    task.dependsOn(mainProcessorTask)
                }

                org.gradle.api.Task testProcessorTask = project.tasks.findByName("runTestAnnotationProcessor")
                if (testProcessorTask != null) {
                    task.dependsOn(testProcessorTask)
                }
            })
        }
    }

    private void registerAnnotationProcessorFor(SourceSet sourceSet) {
        project.tasks.register("run${sourceSet.name.capitalize()}AnnotationProcessor", JavaCompile.class, task -> {
            task.setGroup("annotationProcessor")

            // Set the source set to process
            task.setSource(sourceSet.getJava().getSrcDirs())

            // Set the classpath to include the necessary dependencies
            task.setClasspath(sourceSet.getCompileClasspath())

            // Set the destination directory for generated sources (not compiled classes)
            task.destinationDirectory.set(project.file("${project.buildDir}/generated"))

            // Use the annotation processor classpath
            task.getOptions().setAnnotationProcessorPath(sourceSet.annotationProcessorPath)

            // Disable the actual bytecode output and only run annotation processors
            task.getOptions().getCompilerArgs().add("-proc:only")

            // Optionally, set the directory where the generated sources will be placed
            task.getOptions().generatedSourceOutputDirectory.set(
                    project.file("${project.buildDir}/generated/sources/oxygen/java/${sourceSet.name}")
            )

            // Ensure the task is up-to-date only when sources or annotation processors change
            task.getInputs().files(sourceSet.getJava().getSrcDirs())
            task.getOutputs().dir("${project.buildDir}/generated/sources/oxygen/java/${sourceSet.name}")
        })
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
        project.extensions.configure(PublishingExtension.class) { publishing ->
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
