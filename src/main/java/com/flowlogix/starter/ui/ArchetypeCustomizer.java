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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import static com.flowlogix.starter.ArchetypeGenerator.Parameter;
import static com.flowlogix.starter.ArchetypeGenerator.ReturnValue;

@Named("archetype")
@SessionScoped
@Getter @Setter
@Slf4j
public class ArchetypeCustomizer implements Serializable {
    private static final long serialVersionUID = 1L;

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
    private String packagingType;
    private String version;
    private String archetypeVersion;

    private boolean useShiro = true;
    private boolean useOmniFaces = true;
    private boolean usePrimeFaces = true;
    private boolean useLazyModel = true;

    @PostConstruct
    void init() {
        projectName = "";
        artifact = "";
    }

    public StreamedContent getDownload() {
        ReturnValue result = generator.generateArchetype(getParameters(false));
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

    private Parameter[] getParameters(boolean forCurl) {
        return new Parameter[] {
                new Parameter(forCurl ? "group" : "groupId", group),
                new Parameter(forCurl ? "artifact" : "artifactId", artifact),
                new Parameter("projectName", projectName),
                new Parameter("package", packageName),
                new Parameter("baseType", baseType),
                new Parameter("packagingType", packagingType),
                new Parameter("version", version),
                new Parameter("archetypeVersion", archetypeVersion),
                new Parameter("useShiro", Boolean.toString(useShiro)),
                new Parameter("useOmniFaces", Boolean.toString(useOmniFaces)),
                new Parameter("usePrimeFaces", Boolean.toString(usePrimeFaces)),
                new Parameter("useLazyModel", Boolean.toString(useLazyModel)),
        };
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public String getCurlCommand() throws MalformedURLException, URISyntaxException {
        String parameters = Arrays.asList(getParameters(true)).stream()
                .filter(parameter -> parameter.value() != null && !parameter.value().isBlank())
                .map(parameter -> "%s=%s".formatted(parameter.key(),
                        URLEncoder.encode(parameter.value(), StandardCharsets.UTF_8)
                                .replace("+", "%20")))
                .collect(Collectors.joining(";"));
        var baseURL = Faces.getRequestBaseURL();
        String proto = "X-Forwarded-Proto";
        if (!Faces.isRequestSecure() &&
                ("https".equalsIgnoreCase(Faces.getRequestHeader(proto)) ||
                        "https".equalsIgnoreCase(Faces.getRequestHeader(proto.toLowerCase())))) {
            var httpUrl = URI.create(baseURL).toURL();
            if (!httpUrl.getProtocol().endsWith("s")) {
                baseURL = new URI(httpUrl.getProtocol() + "s", null, httpUrl.getHost(),
                        443, httpUrl.getPath(), null, null).toString();
            }
        }
        return "curl -X GET -H \"Accept: application/octet-stream\" -o %s.zip \"%sdownload/;%s\""
                .formatted(artifact.isBlank() ? "starter" : artifact,
                        baseURL, parameters);
    }

    public void resetSession() {
        log.debug("Resetting session");
        Faces.invalidateSession();
        Faces.redirect(Faces.getRequestURI());
    }
}
