/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.resource;

import io.openepcis.config.ApplicationConfigService;
import io.openepcis.core.exception.ImplementationException;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.model.rest.ProblemResponseBody;
import io.openepcis.service.api.UserExtensionService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// security annotation not being inherited from interface
@Tag(name = "User Extensions", description = "The User Extensions API")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
@Path("userExtension")
@RolesAllowed("admin")
public class UserExtensionResource {
  private final UserExtensionService userExtensionService;
  private final RestResourceUtil restResourceUtil;
  private final ApplicationConfigService applicationConfigService;
  Logger log = LoggerFactory.getLogger(UserExtensionResource.class);

  @Operation(description = "Upload single schema file", summary = "Upload schema file for a namespace.")
  @Parameters({@Parameter(description = "", required = true, in = ParameterIn.QUERY), @Parameter(description = "default namespace prefix", required = true, in = ParameterIn.QUERY), @Parameter(description = "file")})
  @APIResponses({@APIResponse(responseCode = "200", description = "Successful capture of JSON schema provided by the " + "client. "), @APIResponse(responseCode = "401", description = "Authorization information is missing or invalid.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)), @APIResponse(responseCode = "403", description = "Client is unauthorized to access this resource.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)), @APIResponse(responseCode = "500", description = "An error occurred on the backend.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))})
  @POST
  @Path("jsonSchema")
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  @Produces({"application/problem+json", "application/ld+json"})
  public Response userExtensionJSonSchemaPost(@Context SecurityIdentity securityIdentity, @NotNull @RestQuery String namespace, @NotNull @RestQuery String defaultPrefix, @RestQuery String jsonldContextUrl, @NotNull InputStream inputStream) {
    java.nio.file.Path path = null;
    try {
      path = Files.createTempFile(UUID.randomUUID().toString(), ".json");
      Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error(e.getMessage());
      FileUtils.deleteQuietly(path.toFile());
      throw new ImplementationException(e.getMessage(), e);
    }
    try {
      String createdBy = restResourceUtil.getUserId(securityIdentity);
      userExtensionService.storeJsonSchema(path, namespace, defaultPrefix, createdBy, jsonldContextUrl);
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new PersistenceException(e.getMessage(), e);
    } finally {
      FileUtils.deleteQuietly(path.toFile());
    }
    return Response.status(Response.Status.CREATED).build();
  }

  @Operation(description = "define URL-based schema", summary = "setup URL-based schema for prefix.")
  @Parameters({@Parameter(description = "url", required = true, in = ParameterIn.QUERY), @Parameter(description = "default namespace prefix", required = true, in = ParameterIn.QUERY)})
  @APIResponses({@APIResponse(responseCode = "200", description = "Successful import of JSON schema provided by the " + "client. "), @APIResponse(responseCode = "401", description = "Authorization information is missing or invalid.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)), @APIResponse(responseCode = "403", description = "Client is unauthorized to access this resource.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)), @APIResponse(responseCode = "500", description = "An error occurred on the backend.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))})
  @POST
  @Path("jsonSchema/url")
  @Produces({"application/problem+json", "application/ld+json"})
  public Response userExtensionJSonSchemaUrlPost(@Context SecurityIdentity securityIdentity, @NotNull @RestQuery String url, @NotNull @RestQuery String defaultPrefix) {
    try {
      userExtensionService.storeJsonSchema(new URL(url), defaultPrefix, restResourceUtil.getUserId(securityIdentity));
      return Response.status(Response.Status.CREATED).build();
    } catch (MalformedURLException e) {
      return Response.status(Response.Status.NOT_ACCEPTABLE.getStatusCode(), e.getMessage()).build();
    }
  }

  @Operation(description = "Upload single schema file to update", summary = "Upload updated schema file for a namespace.")
  @Parameters({@Parameter(description = "", required = true, in = ParameterIn.QUERY), @Parameter(description = "default namespace prefix", required = true, in = ParameterIn.QUERY), @Parameter(description = "file")})
  @APIResponses({@APIResponse(responseCode = "200", description = "Successful capture of JSON schema provided by the " + "client. "), @APIResponse(responseCode = "401", description = "Authorization information is missing or invalid.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)), @APIResponse(responseCode = "403", description = "Client is unauthorized to access this resource.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)), @APIResponse(responseCode = "500", description = "An error occurred on the backend.", content = @Content(schema = @Schema(implementation = ProblemResponseBody.class), example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))})
  @PUT
  @Path("jsonSchema")
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  @Produces({"application/problem+json", "application/ld+json"})
  public Response updateUserExtensionSchema(@Context SecurityIdentity securityIdentity, @NotNull @RestQuery String namespace, @NotNull @RestQuery String defaultPrefix, @NotNull InputStream inputStream) {
    java.nio.file.Path path = null;
    try {
      path = Files.createTempFile(UUID.randomUUID().toString(), ".json");
      Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error(e.getMessage());
      FileUtils.deleteQuietly(path.toFile());
      throw new ImplementationException(e.getMessage(), e);
    }
    try {
      String updatedBy = restResourceUtil.getUserId(securityIdentity);
      userExtensionService.updateJSONSchema(path, namespace, defaultPrefix, updatedBy);
    } finally {
      FileUtils.deleteQuietly(path.toFile());
    }
    return Response.status(Response.Status.CREATED).build();
  }

  public UserExtensionResource(UserExtensionService userExtensionService, RestResourceUtil restResourceUtil, ApplicationConfigService applicationConfigService) {
    this.userExtensionService = userExtensionService;
    this.restResourceUtil = restResourceUtil;
    this.applicationConfigService = applicationConfigService;
  }
}
