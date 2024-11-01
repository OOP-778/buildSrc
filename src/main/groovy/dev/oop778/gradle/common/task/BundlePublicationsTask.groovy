package dev.oop778.gradle.common.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.*
import org.gradle.plugins.signing.SignOperation
import org.gradle.plugins.signing.SigningExtension

import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

abstract class BundlePublicationsTask extends DefaultTask {

    BundlePublicationsTask() {
        destinationDirectory.convention(project.layout.buildDirectory.dir("bundles"))
        outputFile.convention(destinationDirectory.file(publication.map { "${it.groupId}-${it.artifactId}-${it.version}.zip" }))
        setGroup("deployer")
        setDescription("Bundles publication artifacts into a zip file")
    }

    @Internal
    abstract Property<MavenPublication> getPublication();

    @OutputDirectory
    abstract DirectoryProperty getDestinationDirectory();

    @OutputFile
    abstract RegularFileProperty getOutputFile();

    @InputFiles
    @Optional
    abstract ConfigurableFileCollection getExtraFiles();

    @Input
    @Optional
    abstract MapProperty<String, String> getFileNameRemaps()

    @TaskAction
    void bundleArtifacts() {
        if (!this.getPublication().isPresent()) {
            throw new GradleException("Publication not set")
        }

        final File destinationDirectory = this.getDestinationDirectory().get().getAsFile()
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs()
        }

        final File outputFile = this.getOutputFile().get().getAsFile()

        final MavenPublication publication = this.getPublication().get()
        final String start = "${convertDotsToPath(publication.groupId)}/${publication.artifactId}/${publication.version}"
        final String fileStart = "${start}/${publication.artifactId}-${publication.version}"

        final ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(outputFile.toPath()))
        try {
            // Add artifacts
            for (final def artifact in publication.getArtifacts()) {
                def archiveFileName = normalizeArtifactName(fileStart, artifact.getClassifier(), artifact.getExtension())
                putZipEntryWithHashesAndSignature(artifact.file, archiveFileName, zipOut)
            }

            // Add extra files
            for (final def file in extraFiles.getFiles()) {
                final String extension
                if (file.name == "pom-default.xml") {
                    extension = "pom"
                } else {
                    extension = "module"
                }

                def archiveFileName = normalizeArtifactName(fileStart, null, extension)
                putZipEntryWithHashesAndSignature(file, archiveFileName, zipOut)
            }
        } finally {
            zipOut.close()
        }
    }

    protected void putZipEntry(File file, String archiveName, ZipOutputStream zipOut) {
        final ZipEntry zipEntry = new ZipEntry(archiveName)
        zipOut.putNextEntry(zipEntry)

        Files.copy(file.toPath(), zipOut)
        zipOut.closeEntry()
    }

    protected String normalizeArtifactName(String start, String classifier, String extension) {
        return classifier == null ? "${start}.${extension}" : "${start}-${classifier}.${extension}"
    }

    protected String convertDotsToPath(String input) {
        return input.replaceAll("\\.", "/")
    }

    protected void putZipEntryWithHashesAndSignature(File file, String archiveName, ZipOutputStream zipOut) {
        // Add main file
        putZipEntry(file, archiveName, zipOut)

        // Generate and add hash files
        generateHashFiles(file, archiveName, zipOut, "MD5")
        generateHashFiles(file, archiveName, zipOut, "SHA-1")

        // Generate signature using in-memory PGP key if signing is configured
        generateSignature(file, archiveName, zipOut)
    }

    protected void generateHashFiles(File file, String archiveName, ZipOutputStream zipOut, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm.replace("-", ""))
        byte[] fileBytes = Files.readAllBytes(file.toPath())
        byte[] hash = digest.digest(fileBytes)

        String hashFileName = "${archiveName}.${algorithm.toLowerCase().replace("-", "")}"
        ZipEntry hashEntry = new ZipEntry(hashFileName)
        zipOut.putNextEntry(hashEntry)
        zipOut.write(hash.encodeHex().toString().getBytes())
        zipOut.closeEntry()
    }

    protected void generateSignature(File file, String archiveName, ZipOutputStream zipOut) {
        def signingExtension = project.extensions.findByType(SigningExtension.class)
        if (signingExtension == null) {
            return
        }

        SignOperation signatureOperation = signingExtension.sign(file)
        def first = signatureOperation.signatures.first()
        putZipEntry(first.file, "${archiveName}.asc", zipOut)
    }
}
