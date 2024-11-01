package dev.oop778.gradle.common

import dev.oop778.gradle.common.enums.MavenCentralReleaseManagement
import dev.oop778.gradle.common.task.BundlePublicationsTask
import dev.oop778.gradle.common.task.MavenCentralPublishTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.plugins.signing.SigningExtension

class DeployerExtension {
    protected Project project

    DeployerExtension(Project project) {
        this.project = project
    }

    void setupMavenPublishRepository(String repositoryUrl, String repositoryUsername, String repositoryPassword) {
        project.extensions.configure(PublishingExtension.class) {
            it.repositories {
                it.maven {
                    name = url
                    url = repositoryUrl

                    if (repositoryPassword && repositoryUsername) {
                        credentials {
                            it.username = repositoryUsername
                            it.password = repositoryPassword
                        }
                    }
                }
            }
        }
    }

    void setupSigning(String base64Key, String password) {
        project.extensions.configure(SigningExtension.class) { signing ->
            signing.useInMemoryPgpKeys(new String(Base64.getDecoder().decode(base64Key.getBytes())), password)
            project.extensions.configure(PublishingExtension.class) {
                it.publications.configureEach {
                    println "Signing publication ${it.name} for ${project.name}"
                    signing.sign(it)
                }
            }
        }
    }

    NamedDomainObjectProvider<MavenPublication> createPublicationFrom(SoftwareComponent component, String componentName, String componentGroup, String componentVersion) {
        if (!project.pluginManager.hasPlugin("maven-publish")) {
            project.apply plugin: "maven-publish"
            println "Applied maven-publish plugin to ${project.name} (You should usually apply it yourself, this is just a warning for " +
                    "unexpected behavior)"
        }

        def publishingExt = project.extensions.getByType(PublishingExtension.class)
        println "Configuring publishing for ${componentGroup}:${componentName}:${componentVersion}"

        def register = publishingExt.publications.register(componentName, MavenPublication.class)
        register.configure {
            from(component)

            groupId = componentGroup
            version = componentVersion
            artifactId = componentName
        }

        return register
    }

    void publishToMavenCentral(NamedDomainObjectProvider<MavenPublication> publicationProvider, String username, String token, MavenCentralReleaseManagement releaseManagement) {
        publicationProvider.configure { publication ->
            def pomTasks = project.getTasks().withType(GenerateMavenPom.class)
            def metadataTasks = project.getTasks().withType(GenerateModuleMetadata.class)

            def bundlerTask = project.getTasks().register("${publication.name}Bundler", BundlePublicationsTask.class) {
                it.publication.set(publication)

                // Find pom file
                def pomTask = pomTasks.first()
                if (pomTask) {
                    it.extraFiles.from(pomTask.destination)
                    it.dependsOn(pomTask)
                } else {
                    throw new GradleException("Project ${project.name} publication does not contain pom file")
                }

                // Find module.json
                def metadataTask = metadataTasks.first()
                if (metadataTask) {
                    it.extraFiles.from(metadataTask)
                    it.dependsOn(metadataTask)
                } else {
                    project.getLogger().warn("Project ${project.name} publication ${publication.name} does not contain module.json")
                }
            }

            def publishTask = project.getTasks().register("${publication.name}PublishToMavenCentral", MavenCentralPublishTask.class) {
                it.username.set(username)
                it.password.set(token)
                it.archiveFile.set(bundlerTask.map { it.outputFile }.get())
                it.releaseManagement.set(releaseManagement.name())

                project.getTasks().named("publish").configure {publish -> publish.finalizedBy(it)}
            }
        }
    }

}
