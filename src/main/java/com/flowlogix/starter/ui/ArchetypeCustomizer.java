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
package com.flowlogix.starter.ui;

import com.flowlogix.starter.ArchetypeGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.omnifaces.util.Faces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.util.Callbacks;
import java.io.InputStream;
import java.io.Serializable;
import static com.flowlogix.starter.ArchetypeGenerator.Parameter;
import static com.flowlogix.starter.ArchetypeGenerator.ReturnValue;

@Named("archetype")
@SessionScoped
@Getter @Setter
@Slf4j
public class ArchetypeCustomizer implements Serializable {
    private static final long serialVersionUID = 1L;
    @SuppressWarnings("checkstyle:MagicNumber")
    private static final int BUFFER_SIZE = 4096;

    @Inject
    ArchetypeGenerator generator;
    @Resource
    ManagedExecutorService executorService;

    private String artifact;
    private String group;
    private String projectName;
    private String packageName;
    @Pattern(regexp = "infra|payara|", message = "Base type must be either 'infra' or 'payara'")
    private String baseType;
    private String version;

    private boolean useShiro = true;

    @PostConstruct
    void init() {
        projectName = "";
    }

    public StreamedContent getDownload() {
        ReturnValue result = generator.generateArchetype(new Parameter[] {
                new Parameter("groupId", group),
                new Parameter("artifactId", artifact),
                new Parameter("projectName", projectName),
                new Parameter("package", packageName),
                new Parameter("baseType", baseType),
                new Parameter("version", version),
                new Parameter("useShiro", Boolean.toString(useShiro)),
        });

        if (result.status() != 0) {
            result.close();
            return null;
        }

        InputStream input = generator.createZipStream(result, executorService);
        Callbacks.SerializableSupplier<InputStream> callback = () -> input;

        return DefaultStreamedContent.builder()
                .name("%s.zip".formatted(artifact.isBlank() ? "starter" : artifact))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .stream(callback)
                .writer(output -> generator.writer(result, input, output, true))
                .build();
    }

    public void resetSession() {
        log.debug("Resetting session");
        Faces.invalidateSession();
        Faces.redirect(Faces.getRequestURI());
    }
}