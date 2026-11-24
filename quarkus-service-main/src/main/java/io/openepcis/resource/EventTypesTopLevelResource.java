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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.model.dto.EPCPageResult;
import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.rest.EPCISEventTypes;
import io.openepcis.query.context.QueryContext;
import io.openepcis.resource.specs.EventTypesTopLevelApi;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.util.QueryDocumentUtil;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static io.openepcis.service.util.Constants.*;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class EventTypesTopLevelResource implements IsTopLevelResource, EventTypesTopLevelApi {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventTypesTopLevelResource.class);
    private final RestResourceUtil restResourceUtil;
    private final ObjectMapper objectMapper;
    private final QueryService queryService;

    @PermissionsAllowed("query")
    @Override
    public Uni<Response> getEventTypes(SecurityIdentity securityIdentity, Integer perPage, String nextPageToken, UriInfo uriInfo) {
        if (restResourceUtil.getAcceptHeader().isPresent()) {
            final ObjectNode node = objectMapper.createObjectNode();
            node.put(CommonConstants.CONTEXT, CommonConstants.EPCIS_DEFAULT_NAMESPACE);
            node.put(TYPE, COLLECTION);
            ArrayNode members = objectMapper.createArrayNode();
            Set.of(EPCISEventTypes.values()).stream().filter(t -> !EPCISEventTypes.ALL.equals(t)).map(t -> t.toString()).sorted().forEach(members::add);
            node.putIfAbsent("member", members);
            return Uni.createFrom().item(Response.ok(node).build());
        } else {
            log.warn("ObjectMapper or HttpServletRequest not configured in default EventsApi interface so no example is generated");
        }
        return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
    }

    @PermissionsAllowed("query")
    @Override
    public Uni<Response> getSubResourceOfEventTypes(SecurityIdentity securityIdentity, UriInfo uriInfo, String eventType) {
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        final URI uri = uriInfo.getBaseUriBuilder().path(EventTypesTopLevelApi.class).path(eventType).path(EVENTS).build();
        final Link link = Link.fromUri(uri).rel(EVENTS).build();
        final EPCPageResult response = restResourceUtil.createTopLevelResourcePageResult();
        response.setMember(List.of(EVENTS));
        final Response.ResponseBuilder builder = Response.ok(response).links(link).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
        return Uni.createFrom().item(builder.build());
    }

    @PermissionsAllowed("query")
    public Uni<Response> getEventsFromEventTypes(SecurityIdentity securityIdentity, String eventType, Integer perPage, String nextPageToken, UriInfo uriInfo) {
        final QueryContext ctx = restResourceUtil.queryContext();
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        if (StringUtils.isNotBlank(nextPageToken)) {
            return queryService.getEventsOfNextPage(nextPageToken, perPage, ctx).map(response -> {
                final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersionMax().getVersion()));
                final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get()));
                builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
                return restResourceUtil.linkNextPage(response, ctx, builder).build();
            });
        } else {
            return queryService.getEventsByType(eventType.trim(), Optional.ofNullable(perPage), ctx).map(response -> {
                final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersionMax().getVersion()));
                final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get()));
                builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
                return restResourceUtil.linkNextPage(response, ctx, builder).build();
            });
        }
    }

    @Override
    public Set<String> topLevelResourceNames() {
        return Set.of("eventTypes");
    }

    public EventTypesTopLevelResource(RestResourceUtil restResourceUtil, ObjectMapper objectMapper, QueryService queryService) {
        this.restResourceUtil = restResourceUtil;
        this.objectMapper = objectMapper;
        this.queryService = queryService;
    }
}
