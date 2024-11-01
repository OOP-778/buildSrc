package dev.oop778.gradle.common.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Files

abstract class MavenCentralPublishTask extends DefaultTask {

    @Inject
    MavenCentralPublishTask() {
        setGroup("deployer")
        setDescription("Publishes bundle to maven central for release")
    }

    @Input
    abstract Property<String> getUsername();

    @Input
    abstract Property<String> getPassword();

    @InputFile
    abstract RegularFileProperty getArchiveFile();

    @Input
    abstract Property<String> getReleaseManagement();

    @TaskAction
    void publish() {
        String boundary = "------Boundary" + System.currentTimeMillis()
        HttpURLConnection connection = (HttpURLConnection) new URL("https://central.sonatype.com/api/v1/publisher/upload?publishingType=${getReleaseManagement().get()}").openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty("accept", "text/plain")
        connection.setRequestProperty("Authorization", "Bearer " + Base64.getEncoder().encodeToString((username.get() + ":" + password.get()).getBytes()))
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        connection.setDoOutput(true)

        def archive = getArchiveFile().get().getAsFile()

        try (OutputStream outputStream = connection.getOutputStream()
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

            // Write boundary start
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"bundle\"; filename=\"${archive.name}\"\r\n")
            writer.append("Content-Type: application/x-zip-compressed\r\n\r\n").flush()

            // Write file content
            Files.copy(archive.toPath(), outputStream)
            outputStream.flush()

            // Write boundary end
            writer.append("\r\n").append("--").append(boundary).append("--").append("\r\n").flush()
        }

        // Check response
        int responseCode = connection.getResponseCode()
        if (responseCode == 201) {
            println("Successfully uploaded publication bundle to Maven Central. Release it in the web panel at https://central.sonatype.com/publishing")
        } else {
            throw new GradleException("Failed to upload publication bundle: " + connection.getResponseMessage() + " " + responseCode)
        }

        connection.disconnect()
    }
}