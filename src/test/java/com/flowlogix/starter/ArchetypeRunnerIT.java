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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import static com.flowlogix.starter.ArchetypeGenerator.ReturnValue;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class ArchetypeRunnerIT {
    @Test
    void generateArchetype() {
        ReturnValue result = new ArchetypeGenerator().generate();
        assertThat(result.status()).withFailMessage(result.output()).isZero();
        log.debug("Generated project: {}", result.output());
        createZipFileFromOutputStream(result.zipBytes(), "target/starter.zip");
    }

    @SneakyThrows(IOException.class)
    static void createZipFileFromOutputStream(byte[] zipBytes, String zipFilePath) {
        Files.copy(new BufferedInputStream(new ByteArrayInputStream(zipBytes)),
                java.nio.file.Path.of(zipFilePath),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @GET
    @Path("/download")
    @Produces("application/octet-stream")
    public Response downloadFile(String fileName, byte[] fileContent) {
        StreamingOutput stream = output -> {
            output.write(fileContent);
            output.flush();
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"%s\"".formatted(fileName))
                .build();
    }
}
