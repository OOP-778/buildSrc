package dev.oop778.gradle.common

import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class CommonExtension {
    protected Project project

    private static final Set<String> ignoredProjects = ["jcstress", "jmh"]

    CommonExtension(Project project) {
        this.project = project
    }

    void applyCommonToAllProjects() {
        project.allprojects { subproject ->
            if (ignoredProjects.any { subproject.name.contains(it) }) {
                return
            }

            subproject.apply plugin: CommonPlugin
        }
    }

    void createPublicationFrom(SoftwareComponent component, String componentName) {
        if (!project.pluginManager.hasPlugin("maven-publish")) {
            project.apply plugin: "maven-publish"
            println "Applied maven-publish plugin to ${project.name} (You should usually apply it yourself, this is just a warning for " +
                    "unexpected behavior)"
        }

        project.extensions.configure(PublishingExtension.class) { publishing ->
            println "Configuring publishing for ${project.name}"
            publishing.publications.register(componentName, MavenPublication.class).configure {
                from component

                groupId = project.properties["maven.groupId"]
                version = project.properties["version"]
                artifactId = componentName
            }
        }
    }
}
