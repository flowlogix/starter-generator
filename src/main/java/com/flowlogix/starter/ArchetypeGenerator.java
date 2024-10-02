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
import org.apache.maven.cli.MavenCli;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Slf4j
public class ArchetypeGenerator {
    public record ReturnValue(int status, String output) { }

    @SneakyThrows(IOException.class)
    public ReturnValue generate() {
        MavenCli cli = new MavenCli();
        Path temporaryPath = getTemporaryPath();
        try {
            String projectDirectory = temporaryPath.toString();
            System.setProperty(MavenCli.MULTIMODULE_PROJECT_DIRECTORY, projectDirectory);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return new ReturnValue(cli.doMain(new String[] { "archetype:generate",
                            "-DarchetypeGroupId=com.flowlogix.archetypes",
                            "-DarchetypeArtifactId=starter", "-DarchetypeVersion=LATEST",
                            "-DgroupId=com.flowlogix", "-DartifactId=sample", "-Dversion=1.x-SNAPSHOT",
                            "-DbaseType=payara", "-Dpackage=com.flowlogix.example",
                            "-DinteractiveMode=false"},
                    projectDirectory, new PrintStream(new NullOutputStream()), new PrintStream(new BufferedOutputStream(out))),
                    out.toString());
        } finally {
            try (var paths = Files.walk(temporaryPath)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ArchetypeGenerator::deleteFile);
            }
        }
    }

    private Path getTemporaryPath() throws IOException {
        Path path =  Files.createTempDirectory("starter-generator-project-");
        path.resolve(".mvn").toFile().mkdirs();
        log.debug("Created temporary project directory: {}", path);
        return path;
    }

    @SneakyThrows(IOException.class)
    private static void deleteFile(Path path) {
        Files.delete(path);
    }
}
