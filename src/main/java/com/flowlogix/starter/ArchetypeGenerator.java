/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.flowlogix.starter;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.maven.cli.MavenCli;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static java.util.function.Predicate.not;

@Slf4j
public class ArchetypeGenerator {
    public record ReturnValue(int status, String output, byte[] zipBytes) { }

    @SneakyThrows(IOException.class)
    public ReturnValue generate() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("archetypeGroupId", "com.flowlogix.archetypes");
        parameters.put("archetypeArtifactId", "starter");
        parameters.put("archetypeVersion", "LATEST");
        parameters.put("interactiveMode", "false");

        parameters.put("groupId", "com.example");
        parameters.put("artifactId", "starter");
        parameters.put("package", "com.example");
        parameters.put("version", "1.x-SNAPSHOT");
        parameters.put("baseType", "payara");

        MavenCli cli = new MavenCli();
        Path temporaryPath = getTemporaryPath();
        try (var out = new ByteArrayOutputStream();
             var zipFileStream = new ByteArrayOutputStream()) {
            String projectDirectory = temporaryPath.toString();
            System.setProperty(MavenCli.MULTIMODULE_PROJECT_DIRECTORY, projectDirectory);
            List<String> options = Stream.concat(Stream.of("archetype:generate"),
                    parameters.entrySet().stream().map(entry -> "-D%s=%s"
                            .formatted(entry.getKey(), entry.getValue()))).toList();
            log.debug("Options: {}", options);
            int statusCode = cli.doMain(options.toArray(String[]::new),
                    projectDirectory, new PrintStream(new NullOutputStream()),
                    new PrintStream(new BufferedOutputStream(out)));
            createZipFileStream(temporaryPath, new BufferedOutputStream(zipFileStream));
            return new ReturnValue(statusCode, out.toString(), zipFileStream.toByteArray());
        } finally {
            try (var paths = Files.walk(temporaryPath)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ArchetypeGenerator::deleteFile);
            }
        }
    }

    private Path getTemporaryPath() throws IOException {
        Path path =  Files.createTempDirectory("starter-generator-project-");
        if (!path.resolve(".mvn").toFile().mkdirs()) {
            throw new IOException("Unable to create directory");
        }
        log.debug("Created temporary project directory: {}", path);
        return path;
    }

    @SneakyThrows(IOException.class)
    private static void deleteFile(Path path) {
        Files.delete(path);
    }

    private void createZipFileStream(Path sourceDirPath, OutputStream outputStream) throws IOException {
        try (var zipOutputStream = new ZipArchiveOutputStream(outputStream);
             var sourceDirPaths = Files.walk(sourceDirPath)) {
            sourceDirPaths.filter(not(Files::isDirectory))
                    .forEach(path -> addZipEntry(sourceDirPath, path, zipOutputStream));
        }
    }

    @SneakyThrows(IOException.class)
    @SuppressWarnings({"checkstyle:IllegalTokenText", "checkstyle:MagicNumber"})
    private static void addZipEntry(Path sourceDirPath, Path path, ZipArchiveOutputStream zipOutputStream) {
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(sourceDirPath.relativize(path).toString());
        if (Files.isExecutable(path)) {
            zipEntry.setUnixMode(0755);
        }
        zipOutputStream.putArchiveEntry(zipEntry);
        Files.copy(path, zipOutputStream);
        zipOutputStream.closeArchiveEntry();
    }
}
