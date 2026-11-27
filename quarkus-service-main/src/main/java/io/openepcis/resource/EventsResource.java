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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.query.data.EpcisQueryType;
import io.openepcis.model.epcis.*;
import io.openepcis.query.context.QueryContext;
import io.openepcis.resource.specs.EventsApi;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.api.UserDetailsExtractor;
import io.openepcis.service.util.QueryDocumentUtil;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class EventsResource implements IsTopLevelResource, EventsApi {
  private final QueryService queryService;
  private final RestResourceUtil restResourceUtil;
  private final UserDetailsExtractor userDetailsExtractor;
  private final ObjectMapper objectMapper;
  Logger log = LoggerFactory.getLogger(EventsResource.class);

  public Uni<Response> eventsGet(SecurityIdentity securityIdentity, UriInfo uriInfo) {
    final QueryContext queryContext = restResourceUtil.queryContext();
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    return queryService.queryEvents(queryContext).map(responseContainer -> {
      final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(responseContainer.getQueryResults()), Optional.of(queryContext.getEpcisVersionMax().getVersion()));
      final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, queryContext.getGs1Extensions().get()));
      builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
      return restResourceUtil.linkNextPage(responseContainer, queryContext, builder).build();
    });
  }

  @PermissionsAllowed("query")
  public Uni<Response> getEventById(SecurityIdentity securityIdentity, String eventID) {
    final QueryContext queryContext = restResourceUtil.queryContext();
    final Set<String> eventAccessRoles = RestResourceUtil.getEventAccessRoles(securityIdentity.getRoles());
    // An error declaration carries the eventID of the event it declares erroneous, so this can be
    // two records. EPCIS 2.0 12.7.4: "this may return more than one event". Both go out, oldest
    // first; before, one of them was picked at random.
    final Uni<List<EPCISEvent>> epcisEvents = queryService.getEventsById(eventID, userDetailsExtractor.getDefaultGroup(securityIdentity));
    return epcisEvents.map(events -> {
      final QueryResultsBody queryResultsBody = new QueryResultsBody(new ArrayList<>(events));
      final EpcisQueryResult epcisQueryResult = new EpcisQueryResult();
      // EPCIS 2.0 requires queryName; a lookup by event ID is a SimpleEventQuery (8.2.7.1)
      epcisQueryResult.setQueryName(EpcisQueryType.SIMPLE_EVENT_QUERY.getType());
      epcisQueryResult.setResultsBody(queryResultsBody);
      return Response.ok(new EPCISQueryDocument(new EPCISQueryBody(epcisQueryResult), Optional.of(queryContext.getEpcisVersionMax().getVersion()))).build();
    }).onFailure().call(throwable -> Uni.createFrom().item(Response.status(404, throwable.getClass().getSimpleName()).entity(throwable.getMessage()).build()));
  }

  @PermissionsAllowed("query")
  public Uni<Response> getEventsOptions() {
    return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
  }

  @PermissionsAllowed("query")
  public Uni<Response> eventsEventIdOptions() {
    return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
  }

  @PermissionsAllowed("query")
  public Uni<Response> eventTypesOptions() {
    return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
  }

  @PermissionsAllowed("query")
  public Uni<Response> eventsEventTypeOptions() {
    return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
  }

  @PermissionsAllowed("query")
  public Uni<Response> eventsOptions() {
    return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
  }

  @Override
  public Set<String> topLevelResourceNames() {
    return Set.of("events");
  }

  public EventsResource(QueryService queryService, RestResourceUtil restResourceUtil, UserDetailsExtractor userDetailsExtractor, ObjectMapper objectMapper) {
    this.queryService = queryService;
    this.restResourceUtil = restResourceUtil;
    this.userDetailsExtractor = userDetailsExtractor;
    this.objectMapper = objectMapper;
  }
}
