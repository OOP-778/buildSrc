package dev.oop778.gradle.common

import org.gradle.api.Project

class HierarchyProjectName {
    static String get(Project project) {
        var path = []
        var currentProject = project
        while (currentProject != null) {
            path.add(currentProject.name)
            currentProject = currentProject.parent
        }

        return path.reverse().join("-")
    }
}
