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
}
