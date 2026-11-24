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

import io.openepcis.model.rest.ProblemResponseBody;
import io.openepcis.service.api.QueryService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
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
import org.jboss.resteasy.reactive.RestPath;
import io.smallrye.mutiny.Uni;

@Tag(
    name = "Queries",
    description =
        "Endpoints to create large named or anonymous queries using the EPCIS Query Language. Named queries have a custom\n"
            + "      name and are stored until deleted by the user. Anonymous queries are not persisted and only available to the caller.\n"
            + "      EPCIS events queries also support query subscription.")
@Path("nextPageToken")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class PageTokenResource {

  private final QueryService queryService;

  public Optional<QueryService> getQueryService() {
    return Optional.of(queryService);
  }

  public PageTokenResource(QueryService queryService) {
    this.queryService = queryService;
  }

  @Operation(
      description = "",
      summary =
          "Optional endpoint that allows on-demand release of any resources associated with `nextPageToken`.")
  @Parameters(value = {@Parameter(description = "", required = true, in = ParameterIn.PATH)})
  @APIResponses(
      value = {
        @APIResponse(responseCode = "204", description = "nextPageToken invalidated successfully."),
        @APIResponse(
            responseCode = "501",
            description = "Functionality not supported by server.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_501_NOT_IMPLEMENTED))
      })
  @DELETE
  @Path("{token}")
  @Produces({MediaType.APPLICATION_JSON, "application/problem+json", "application/ld+json"})
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  public Uni<Response> deleteNextPageToken(@RestPath("token") String token) {
    // Uni, so 204 means the token really is invalidated: the service used to
    // fire-and-forget the delete and this endpoint answered before it ran.
    return getQueryService()
        .get()
        .deleteNextPageToken(token)
        .replaceWith(() -> Response.noContent().build());
  }
}
