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
package com.flowlogix.starter.api;

import com.flowlogix.starter.ArchetypeGenerator;
import com.flowlogix.starter.ArchetypeGenerator.Parameter;
import com.flowlogix.starter.ArchetypeGenerator.ReturnValue;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;

@Path("/")
@Slf4j
public class DownloadResource {
    @Inject
    ArchetypeGenerator generator;
    @Resource
    ManagedExecutorService executorService;

    @GET
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    public Response downloadFile(@MatrixParam("group") @DefaultValue("com.example") String groupId,
                                 @MatrixParam("artifact") @DefaultValue("starter") String artifactId,
                                 @MatrixParam("projectName") String projectName,
                                 @MatrixParam("package") String packageName,
                                 @MatrixParam("baseType") String baseType,
                                 @MatrixParam("version") String version) {
        ReturnValue result = generator.generateArchetype(new Parameter[] {
                new Parameter("groupId", groupId),
                new Parameter("artifactId", artifactId),
                new Parameter("projectName", projectName),
                new Parameter("package", packageName),
                new Parameter("baseType", baseType),
                new Parameter("version", version)
        });

        if (result.status() != 0) {
            result.close();
            return Response.serverError().type(MediaType.TEXT_PLAIN)
                    .entity(result.output()).build();
        }

        StreamingOutput stream = outputStream -> generator.createZipStream(result, outputStream, executorService);
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"%s.zip\"".formatted(artifactId))
                .build();
    }
}