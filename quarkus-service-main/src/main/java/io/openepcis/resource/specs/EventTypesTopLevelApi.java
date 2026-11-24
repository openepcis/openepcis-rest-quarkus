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
package io.openepcis.resource.specs;

import static io.openepcis.resource.ParameterConstants.*;

import io.openepcis.model.epcis.Action;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.rest.ProblemResponseBody;
import io.openepcis.resource.ParameterDescriptions;
import io.openepcis.resource.ResponseBodyExamples;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Tag(
    name = "Top-level Resources",
    description =
        "Endpoints to browse or retrieve information about EPCIS resources:\n"
            + "\n"
            + "- events\n"
            + "- types of events\n"
            + "- electronic product codes\n"
            + "- business steps\n"
            + "- business locations\n"
            + "- read points\n"
            + "- dispositions")
@Path("/eventTypes")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
@RegisterRestClient(configKey = "eventTypes-api")
public interface EventTypesTopLevelApi {

  @Operation(
      summary = "Returns all EPCIS event types currently available in the EPCIS repository",
      description =
          "EPCIS event types specify the schema of an event. This endpoint returns the 5 standard event types as well as any custom event types supported by this repository.")
  @Parameters(
      value = {
        @Parameter(
            name = "perPage",
            description = ParameterDescriptions.PER_PAGE,
            example = DEFAULT_PER_PAGE_PARAMETER_VALUE,
            in = ParameterIn.QUERY,
            content = @Content(example = "30")),
        @Parameter(
            name = "nextPageToken",
            description = ParameterDescriptions.NEXT_PAGE_TOKEN,
            in = ParameterIn.QUERY,
            content = @Content(example = "3A15506738749783AU6D7DENAKwM2gQRRwGritaeq")),
        @Parameter(
            name = "GS1-CBV-Min",
            description = ParameterDescriptions.GS1_CBV_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-CBV-Max",
            description = ParameterDescriptions.GS1_CBV_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Min",
            description = ParameterDescriptions.GS1_EPCIS_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Max",
            description = ParameterDescriptions.GS1_EPCIS_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-Extensions",
            description = ParameterDescriptions.GS1_EXTENSIONS,
            in = ParameterIn.HEADER)
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "Returns all supported EPCIS event types.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_200_SUPPORTED_EVENT_TYPES)),
        @APIResponse(
            responseCode = "400",
            description = "Query exceptions defined in EPCIS.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_400_QUERY_ISSUES)),
        @APIResponse(
            responseCode = "401",
            description = "Authorization information is missing or invalid.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)),
        @APIResponse(
            responseCode = "403",
            description = "Client is unauthorized to access this resource",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)),
        @APIResponse(
            responseCode = "404",
            description = "The EPCIS event does not exist.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_404_RESOURCE_NOT_FOUND)),
        @APIResponse(
            responseCode = "406",
            description = "The server cannot return the response as requested.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_406_NOT_ACCEPTABLE)),
        @APIResponse(
            responseCode = "500",
            description = "An error occurred on the backend.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))
      })
  @PermissionsAllowed("query")
  @GET
  @Produces({
    MediaType.APPLICATION_JSON,
    "application/problem+json",
    "application/ld+json",
    "application/xml"
  })
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  Uni<Response> getEventTypes(
      @Context SecurityIdentity securityIdentity,
      @Valid @RestQuery @DefaultValue(DEFAULT_PER_PAGE_PARAMETER_VALUE) Integer perPage,
      @Valid @RestQuery String nextPageToken,
      @Context UriInfo uriInfo);

  @Operation(
      summary = "Returns all sub-resources of an EPCIS event type.",
      description =
          "This endpoint returns all sub-resources of an EPCIS event type (for HATEOAS discovery), which includes at least `events`. A server may add additional endpoints, for example `schema` to access the EPCIS event type schema.")
  @Parameters(
      value = {
        @Parameter(
            name = "GS1-CBV-Min",
            description = ParameterDescriptions.GS1_CBV_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-CBV-Max",
            description = ParameterDescriptions.GS1_CBV_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Min",
            description = ParameterDescriptions.GS1_EPCIS_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Max",
            description = ParameterDescriptions.GS1_EPCIS_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-Extensions",
            description = ParameterDescriptions.GS1_EXTENSIONS,
            in = ParameterIn.HEADER),
        @Parameter(
            name = "eventType",
            description = "Names of EPCIS event types.",
            required = true,
            in = ParameterIn.PATH,
            content = @Content(example = "ObjectEvent"))
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description =
                "Returns a sub-resource list that contains at least the `events` sub-resource (for HATEOAS discovery).",
            headers = {
              @Header(
                  name = "GS1-EPCIS-version",
                  description = ParameterDescriptions.GS1_EPCIS_VERSION,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "GS1-CBV-version",
                  description = ParameterDescriptions.GS1_CBV_VERSION,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "GS1-Extensions",
                  description = ParameterDescriptions.GS1_EXTENSIONS,
                  schema = @Schema(implementation = String.class)),
            },
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_200_SUPPORTED_EVENT_TYPES)),
        @APIResponse(
            responseCode = "400",
            description = "Query exceptions defined in EPCIS.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_400_QUERY_ISSUES)),
        @APIResponse(
            responseCode = "401",
            description = "Authorization information is missing or invalid.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)),
        @APIResponse(
            responseCode = "403",
            description = "Client is unauthorized to access this resource",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)),
        @APIResponse(
            responseCode = "404",
            description = "The EPCIS event does not exist.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_404_RESOURCE_NOT_FOUND)),
        @APIResponse(
            responseCode = "406",
            description = "The server cannot return the response as requested.",
            content =
                @Content(
                    schema = @Schema(implementation = Object.class),
                    example = ResponseBodyExamples.RESPONSE_406_NOT_ACCEPTABLE)),
        @APIResponse(
            responseCode = "500",
            description = "An error occurred on the backend.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))
      })
  @PermissionsAllowed("query")
  @GET
  @Path("/{eventType}")
  @Produces({
    MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML,
    MediaType.TEXT_XML,
    "application/problem+json",
    "application/ld+json"
  })
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  Uni<Response> getSubResourceOfEventTypes(
      @Context SecurityIdentity securityIdentity,
      @Context UriInfo uriInfo,
      @RestPath String eventType);

  @Tags(
      value =
          @Tag(
              name = "Events",
              description = "Endpoints that allow you to retrieve EPCIS events as Web resources."))
  @Operation(
      summary =
          "Returns EPCIS events of a given EPCIS event type. If `all` is used instead of an EPCIS event type, every event in the repository is returned.",
      description =
          """
                            This endpoint returns all EPCIS events of a specific EPCIS event type up to the amount defined in `perPage`.The server returns a 'Link' header to point to the remaining results.The client can further restrict the list of events returned by filtering events using the EPCIS querylanguage using URL query string parameters as specified by the EPCIS Query Language. The parameter `eventCountLimit` is used to restrict the maximum number of
                            events to be returned in total, whereas `perPage` restricts the number of events to return per page or paginated results.
                            Example:
                            ```
                            https://example.com/eventTypes/ObjectEvent/events?EQ_bizStep=shipping%7Cdecommissioning&GE_eventTime=2015-03-15T00%3A00%3A00.000-04%3A00
                            ```
                            An EPCIS 2.0 query may also be expressed via parameters in the URI query string.  The query parameters with fixed field-names are included in this OpenAPI interface.  However, this list is not exhaustive and the EPCIS 2.0 standard defines additional query parameters with flexible names, depending on the specific value of `uom`, `type` or `fieldname` that appears within the name of the parameter.""")
  @Parameters(
      value = {
        @Parameter(
            name = "eventType",
            description = ParameterDescriptions.EVENT_TYPE,
            required = true,
            in = ParameterIn.PATH),
        @Parameter(
            name = "perPage",
            description = ParameterDescriptions.PER_PAGE,
            example = DEFAULT_PER_PAGE_PARAMETER_VALUE,
            in = ParameterIn.QUERY,
            content = @Content(example = DEFAULT_PER_PAGE_PARAMETER_VALUE)),
        @Parameter(
            name = "nextPageToken",
            description = ParameterDescriptions.NEXT_PAGE_TOKEN,
            in = ParameterIn.QUERY,
            content = @Content(example = "3A15506738749783AU6D7DENAKwM2gQRRwGritaeq")),
        @Parameter(
            name = "GE_eventTime",
            description = ParameterDescriptions.GE_EVENT_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "LT_eventTime",
            description = ParameterDescriptions.LT_EVENT_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "GE_recordTime",
            description = ParameterDescriptions.GE_RECORD_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "LT_recordTime",
            description = ParameterDescriptions.LT_RECORD_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "EQ_action",
            description = ParameterDescriptions.EQ_ACTION,
            in = ParameterIn.QUERY,
            content = @Content(schema = @Schema(implementation = Action.class))),
        @Parameter(
            name = "EQ_bizStep",
            description = ParameterDescriptions.EQ_BIZ_STEP,
            in = ParameterIn.QUERY,
            example = "shipping"),
        @Parameter(
            name = "EQ_disposition",
            description = ParameterDescriptions.EQ_DISPOSITION,
            in = ParameterIn.QUERY,
            example = "in_transit"),
        @Parameter(
            name = "EQ_persistentDisposition_set",
            description = ParameterDescriptions.EQ_PERSISTENT_DISPOSITION_SET,
            in = ParameterIn.QUERY,
            example = "in_transit"),
        @Parameter(
            name = "EQ_persistentDisposition_unset",
            description = ParameterDescriptions.EQ_PERSISTENT_DISPOSITION_UNSET,
            in = ParameterIn.QUERY,
            example = "in_transit"),
        @Parameter(
            name = "EQ_readPoint",
            description = ParameterDescriptions.EQ_READ_POINT,
            in = ParameterIn.QUERY,
            content = @Content(example = "urn:epc:id:sgln:0012345.11111.400")),
        @Parameter(
            name = "WD_readPoint",
            description = ParameterDescriptions.WD_READ_POINT,
            in = ParameterIn.QUERY,
            content = @Content(example = "urn:epc:id:sgln:0012345.11111.400")),
        @Parameter(
            name = "EQ_bizLocation",
            description = ParameterDescriptions.EQ_BIZ_LOCATION,
            in = ParameterIn.QUERY,
            content = @Content(example = "urn:epc:id:sgln:0012345.11111.400")),
        @Parameter(
            name = "WD_bizLocation",
            description = ParameterDescriptions.WD_BIZ_LOCATION,
            in = ParameterIn.QUERY,
            content = @Content(example = "urn:epc:id:sgln:0012345.11111.400")),
        @Parameter(
            name = "EQ_transformationID",
            description = ParameterDescriptions.EQ_TRANSFORMATION_ID,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_epc",
            description = ParameterDescriptions.MATCH_EPC,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_parentID",
            description = ParameterDescriptions.MATCH_PARENT_ID,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_inputEPC",
            description = ParameterDescriptions.MATCH_INPUT_EPC,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_outputEPC",
            description = ParameterDescriptions.MATCH_OUTPUT_EPC,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_anyEPC",
            description = ParameterDescriptions.MATCH_ANY_EPC,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_epcClass",
            description = ParameterDescriptions.MATCH_EPC_CLASS,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "MATCH_inputEPCClass",
            description = ParameterDescriptions.MATCH_INPUT_EPC_CLASS),
        @Parameter(
            name = "MATCH_outputEPCClass",
            description = ParameterDescriptions.MATCH_OUTPUT_EPC_CLASS),
        @Parameter(
            name = "MATCH_anyEPCClass",
            description = ParameterDescriptions.MATCH_ANY_EPC_CLASS),
        @Parameter(
            name = "EQ_quantity",
            description = ParameterDescriptions.EQ_QUANTITY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "GT_quantity",
            description = ParameterDescriptions.GT_QUANTITY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "GE_quantity",
            description = ParameterDescriptions.GE_QUANTITY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "LT_quantity",
            description = ParameterDescriptions.LT_QUANTITY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "LE_quantity",
            description = ParameterDescriptions.LE_QUANTITY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_eventID",
            description = ParameterDescriptions.EQ_EVENT_ID,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EXISTS_errorDeclaration",
            description = ParameterDescriptions.EXISTS_ERROR_DECLARATION,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "GE_errorDeclarationTime",
            description = ParameterDescriptions.GE_ERROR_DECLARATION_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "LT_errorDeclarationTime",
            description = ParameterDescriptions.LT_ERROR_DECLARATION_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "EQ_errorReason",
            description = ParameterDescriptions.EQ_ERROR_REASON,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_correctiveEventID",
            description = ParameterDescriptions.EQ_CORRECTIVE_EVENT_ID,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "orderBy",
            description = ParameterDescriptions.ORDER_BY,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "orderDirection",
            description = ParameterDescriptions.ORDER_DIRECTION,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "eventCountLimit",
            description = ParameterDescriptions.EVENT_COUNT_LIMIT,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "maxEventCount",
            description = ParameterDescriptions.MAX_EVENT_COUNT,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "GE_startTime",
            description = ParameterDescriptions.GE_START_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "LT_startTime",
            description = ParameterDescriptions.LT_START_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "GE_endTime",
            description = ParameterDescriptions.GE_END_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "LT_endTime",
            description = ParameterDescriptions.LT_END_TIME,
            in = ParameterIn.QUERY,
            content = @Content(example = "2022-06-30T00:15:47.000-05:00")),
        @Parameter(
            name = "EQ_type",
            description = ParameterDescriptions.EQ_TYPE,
            in = ParameterIn.QUERY,
            example = "Temperature"),
        @Parameter(
            name = "EQ_deviceID",
            description = ParameterDescriptions.EQ_DEVICE_ID,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_dataProcessingMethod",
            description = ParameterDescriptions.EQ_DATA_PROCESSING_METHOD,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_microorganism",
            description = ParameterDescriptions.EQ_MICROORGANISM,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_chemicalSubstance",
            description = ParameterDescriptions.EQ_CHEMICAL_SUBSTANCE,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_bizRules",
            description = ParameterDescriptions.EQ_BIZ_RULES,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_stringValue",
            description = ParameterDescriptions.EQ_STRING_VALUE,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_hexBinaryValue",
            description = ParameterDescriptions.EQ_HEX_BINARY_VALUE,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_uriValue",
            description = ParameterDescriptions.EQ_URI_VALUE,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "EQ_booleanValue",
            description = ParameterDescriptions.EQ_BOOLEAN_VALUE,
            in = ParameterIn.QUERY),
        @Parameter(
            name = "GS1-CBV-Min",
            description = ParameterDescriptions.GS1_CBV_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-CBV-Max",
            description = ParameterDescriptions.GS1_CBV_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_CBV_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Min",
            description = ParameterDescriptions.GS1_EPCIS_MIN,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MIN_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPCIS-Max",
            description = ParameterDescriptions.GS1_EPCIS_MAX,
            in = ParameterIn.HEADER,
            content = @Content(example = DEFAULT_EPCIS_MAX_PARAMETER_VALUE)),
        @Parameter(
            name = "GS1-EPC-Format",
            description = ParameterDescriptions.GS1_EPC_FORMAT,
            in = ParameterIn.HEADER,
            content = @Content(schema = @Schema(implementation = EPCFormat.class))),
        @Parameter(
            name = "GS1-CBV-XML-Format",
            description = ParameterDescriptions.GS1_CBV_XML_FORMAT,
            in = ParameterIn.HEADER,
            content = @Content(schema = @Schema(implementation = CBVFormat.class))),
        @Parameter(
            name = "GS1-Extensions",
            description = ParameterDescriptions.GS1_EXTENSIONS,
            in = ParameterIn.HEADER)
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "Returns EPCIS events.",
            headers = {
              @Header(
                  name = "GS1-EPCIS-version",
                  description = ParameterDescriptions.GS1_EPCIS_VERSION,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "GS1-CBV-version",
                  description = ParameterDescriptions.GS1_CBV_VERSION,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "GS1-Extensions",
                  description = ParameterDescriptions.GS1_EXTENSIONS,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "Link",
                  description = ParameterDescriptions.LINK,
                  schema = @Schema(implementation = String.class)),
              @Header(
                  name = "GS1-Next-Page-Token-Expires",
                  description = ParameterDescriptions.GS1_NEXT_PAGE_TOKEN_EXPIRES,
                  schema = @Schema(implementation = String.class))
            },
            content =
                @Content(
                    schema = @Schema(implementation = EPCISEvent.class),
                    example = ResponseBodyExamples.RESPONSE_200_EPCIS_QUERY_DOCUMENT)),
        @APIResponse(
            responseCode = "400",
            description = "Query exceptions defined in EPCIS",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_400_QUERY_ISSUES)),
        @APIResponse(
            responseCode = "401",
            description = "Authorization information is missing or invalid.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_401_UNAUTHORIZED_REQUEST)),
        @APIResponse(
            responseCode = "403",
            description = "Client is unauthorized to access this resource",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_403_CLIENT_UNAUTHORIZED)),
        @APIResponse(
            responseCode = "404",
            description =
                "At least one resource was not found. For example, the EPCIS event does not exist or the query does not exist.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_404_RESOURCE_NOT_FOUND)),
        @APIResponse(
            responseCode = "406",
            description = "The server cannot return the response as requested.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_406_NOT_ACCEPTABLE)),
        @APIResponse(
            responseCode = "414",
            description = "Query too long, if the total URL length exceeds 2000 characters.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_414_URL_TOO_LONG)),
        @APIResponse(
            responseCode = "500",
            description = "An error occurred on the backend.",
            content =
                @Content(
                    schema = @Schema(implementation = ProblemResponseBody.class),
                    example = ResponseBodyExamples.RESPONSE_500_IMPLEMENTATION_EXCEPTION))
      })
  @PermissionsAllowed("query")
  @GET
  @Path("/{eventType}/events")
  @Produces({
    MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML,
    MediaType.TEXT_XML,
    "application/problem+json",
    "application/ld+json"
  })
  @Consumes({MediaType.APPLICATION_JSON, "application/ld+json"})
  Uni<Response> getEventsFromEventTypes(
      @Context SecurityIdentity securityIdentity,
      @RestPath String eventType,
      @Valid @RestQuery @DefaultValue(DEFAULT_PER_PAGE_PARAMETER_VALUE) Integer perPage,
      @Valid @RestQuery String nextPageToken,
      @Context UriInfo uriInfo);
}
