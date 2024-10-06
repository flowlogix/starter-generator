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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Locked;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.MavenCli;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import static java.util.function.Predicate.not;

@Slf4j
@ApplicationScoped
public class ArchetypeGenerator {
    private static final VarHandle MULTI_MODULE_HANDLE;

    static class ArchetypeInitializationException extends RuntimeException {
        ArchetypeInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static {
        try {
            MULTI_MODULE_HANDLE = MethodHandles.privateLookupIn(CliRequest.class, MethodHandles.lookup())
                    .findVarHandle(CliRequest.class, "multiModuleProjectDirectory", File.class);
        } catch (ReflectiveOperationException e) {
            throw new ArchetypeInitializationException("Unable to set CliRequest.multiModuleProjectDirectory", e);
        }
    }

    public record Parameter(@NonNull String key, String value) { }
    public record ReturnValue(Path temporaryPath, int status, String output) implements AutoCloseable {
        @Override
        @SneakyThrows(IOException.class)
        public void close() {
            cleanup(this);
        }
    }

    @SneakyThrows(IOException.class)
    @Locked
    public ReturnValue generateArchetype(Parameter[] inputParameters) {
        Path temporaryPath = getTemporaryPath();
        try (var out = new ByteArrayOutputStream()) {
            String projectDirectory = temporaryPath.toString();
            MavenCli cli = new MavenCli() {
                @Override
                public int doMain(CliRequest request) {
                    MULTI_MODULE_HANDLE.set(request, new File(projectDirectory));
                    return super.doMain(request);
                }
            };
            List<String> options = Stream.concat(Stream.of("archetype:generate"),
                    extractParameters(inputParameters).entrySet().stream().map(entry -> "-D%s=%s"
                            .formatted(entry.getKey(), entry.getValue()))).toList();
            log.debug("Options: {}", options);
            int statusCode = cli.doMain(options.toArray(String[]::new),
                    projectDirectory, new PrintStream(new NullOutputStream()),
                    new PrintStream(new BufferedOutputStream(out)));
            return new ReturnValue(temporaryPath, statusCode, out.toString());
        }
    }

    public Future<ReturnValue> zipToStream(ReturnValue returnValue, OutputStream zipFileStream,
                                           ExecutorService executorService) {
        return executorService.submit(() -> zipToStream(returnValue, zipFileStream));
    }

    @SneakyThrows(IOException.class)
    private static ReturnValue zipToStream(ReturnValue returnValue, OutputStream zipFileStream) {
        if (returnValue.status == 0) {
            createZipFile(returnValue.temporaryPath, zipFileStream);
        }
        return returnValue;
    }

    private static void cleanup(ReturnValue returnValue) throws IOException {
        try (var paths = Files.walk(returnValue.temporaryPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(ArchetypeGenerator::deleteFile);
        }
    }

    private static Map<String, String> extractParameters(Parameter[] inputParameters) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (inputParameters != null) {
            for (Parameter parameter : inputParameters) {
                if (parameter.value() != null) {
                    parameters.put(parameter.key(), parameter.value());
                }
            }
        }
        parameters.putIfAbsent("archetypeGroupId", "com.flowlogix.archetypes");
        parameters.putIfAbsent("archetypeArtifactId", "starter");
        parameters.putIfAbsent("archetypeVersion", "LATEST");
        parameters.putIfAbsent("interactiveMode", "false");

        parameters.putIfAbsent("groupId", "com.example");
        parameters.putIfAbsent("artifactId", "starter");
        parameters.putIfAbsent("projectName", "Starter Project");
        parameters.putIfAbsent("package", "%s.%s".formatted(parameters.get("groupId"), parameters.get("artifactId")));
        parameters.putIfAbsent("version", "1.x-SNAPSHOT");
        parameters.putIfAbsent("baseType", "payara");
        return parameters;
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

    private static void createZipFile(Path sourceDirPath, OutputStream outputStream) throws IOException {
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
