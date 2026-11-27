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
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.dto.QuerySubscription;
import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.NamedQueryMetaData;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.query.context.QueryContext;
import io.openepcis.resource.specs.QueryApi;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.api.SubscriptionService;
import io.openepcis.service.util.QueryDocumentUtil;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static io.openepcis.repository.Constants.LOCATION;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class QueriesResource implements IsTopLevelResource, QueryApi {
    private final QueryService queryService;
    private final SubscriptionService subscriptionService;
    private final RestResourceUtil restResourceUtil;
    private final ApplicationConfigService applicationConfigService;

    @PermissionsAllowed("query")
    public Uni<Response> queriesGet(String nextPageToken, Integer perPage) {
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        if (StringUtils.isNotEmpty(nextPageToken)) {
            return queryService.getEpcisQuery(nextPageToken, Optional.ofNullable(perPage)).map(response -> {
                final Response.ResponseBuilder builder = Response.ok(response.getNamedQueryMetaDataList()).header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(CommonConstants.GS1_CBV_VERSION, restResourceUtil.cbvVersion().map(CBVVersion::getVersion).orElse(CBVVersion.VERSION_2_0_1.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
                return restResourceUtil.linkNextPage(response, builder).build();
            });
        } else {
            return queryService.getEpcisQuery(perPage).map(response -> {
                final Response.ResponseBuilder builder = Response.ok(response.getNamedQueryMetaDataList()).header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(CommonConstants.GS1_CBV_VERSION, restResourceUtil.cbvVersion().map(CBVVersion::getVersion).orElse(CBVVersion.VERSION_2_0_1.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
                return restResourceUtil.linkNextPage(response, builder).build();
            });
        }
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNamePost(String epcisVersion, String cbvVersion, String epcisQuery, HttpServerRequest request) {
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        restResourceUtil.validateFormatOfGS1Headers(epcisVersion, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cbvVersion), Optional.empty(), restResourceUtil.getCBVFormatValue(Optional.empty()));
        restResourceUtil.verifyGS1Headers(epcisVersion, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cbvVersion));
        return queryService.saveEpcisQuery(epcisQuery, Optional.of(supportGS1Extensions)).map(queryName -> {
            final Optional<String> url = Optional.of(request.uri().toString());
            // A query that was not there before has been created, and EPCIS 2.0 answers 201 with
            // the Location of the new query. This said 200, which tells a client the request was
            // handled without telling it a resource now exists.
            final Response.ResponseBuilder builder = Response.status(Response.Status.CREATED);
            final String locationUrl = String.format("%s/%s", url.get(), queryName);
            builder.header(LOCATION, locationUrl).header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(CommonConstants.GS1_CBV_VERSION, restResourceUtil.cbvVersion().map(CBVVersion::getVersion).orElse(CBVVersion.VERSION_2_0_1.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
            return builder.build();
        });
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameGet(String queryName) {
        // The failure is left to the exception mappers, which turn a missing name into a 404 with
        // a problem body. The handler that used to sit here built a response and dropped it:
        // onFailure().call runs its action and then re-raises the original failure, so the 404 it
        // assembled never reached anybody and the mapper decided the answer anyway.
        return queryService.getEpcisQueryByName(queryName).map(response -> Response.ok(response).build());
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameEventsGet(SecurityIdentity securityIdentity, Integer perPage, String nextPageToken, String queryName, UriInfo uriInfo) {
        final QueryContext ctx = restResourceUtil.queryContext();
        if (StringUtils.isNotBlank(nextPageToken)) return queryService.getEventsOfNextPage(nextPageToken, perPage, ctx).map(response -> {
            response.getQueryResults().setQueryName(queryName);
            final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersionMax().getVersion()));
            final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get()));
            return restResourceUtil.linkNextPage(response, ctx, builder).build();
        });
         else return queryService.queryEvents(queryName, perPage, ctx).map(response -> {
            response.getQueryResults().setQueryName(queryName);
            final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersionMax().getVersion()));
            final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get()));
            return restResourceUtil.linkNextPage(response, ctx, builder).build();
        });
    }

    @PermissionsAllowed("query")
    public Response queriesOptions() {
        return Response.ok(Map.of("description", "Server can comply with the GS1-EPCIS-related requirements " + "from the client.")).build();
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameDelete(String queryName) {
        return queryService.deleteQuery(queryName).chain(r -> Uni.createFrom().item(Response.noContent().build()));
    }

    @PermissionsAllowed("query")
    public Response queriesQueryNameEventsOptions() {
        return Response.ok(Map.of("description", "Server can comply with the GS1-EPCIS-related requirements " + "from the client.")).build();
    }

    @PermissionsAllowed("query")
    public Response queriesQueryNameOptions(String queryName, String gs1Extensions) {
        return Response.ok(Map.of("description", "Server can comply with the GS1-EPCIS-related requirements " + "from the client.")).build();
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameSubscriptionGet(String queryName, Integer perPage, String nextPageToken) {
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        // Asking for the subscriptions of a query that does not exist used to answer 200 with an
        // empty list, which reads as "this query has no subscribers" and hides a misspelt name.
        // The query is what is missing, so looking it up first turns that into the 404 it is.
        if (StringUtils.isNotBlank(nextPageToken)) {
            // a page token already stands for a search that was accepted
            return subscriptionService.getSubscriptionOfNextPage(nextPageToken).map(response -> page(response.getQuerySubscriptions(), response, supportGS1Extensions));
        }
        return queryService.getEpcisQueryByName(queryName).chain(ignored -> subscriptionService.getSubscriptionFromQueryName(queryName, perPage).map(response -> page(response.getQuerySubscriptions(), response, supportGS1Extensions)));
    }

    private Response page(final Object body, final io.openepcis.model.dto.QuerySubscriptionResults results, final String supportGS1Extensions) {
        final Response.ResponseBuilder builder = Response.ok(body);
        builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
        return restResourceUtil.linkNextPage(results, builder).build();
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameSubscriptionPost(String querySubscription, String queryName, String epcisVersion, String epcFormat, String cbvFormat) {
        Optional<String> cbvFormatOptional = StringUtils.isEmpty(cbvFormat) ? Optional.empty() : Optional.of(cbvFormat);
        restResourceUtil.isGs1CBVXmlFormatHeaderApplicable(cbvFormatOptional, restResourceUtil.getAcceptHeader());
        restResourceUtil.validateFormatOfGS1Headers(epcisVersion, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(epcFormat), restResourceUtil.getCBVFormatValue(cbvFormatOptional));
        final Uni<QuerySubscription> subscriptionUni = subscriptionService.subscribeViaWebhook(queryName, querySubscription, FormatPreference.getInstance(Optional.ofNullable(epcFormat), restResourceUtil.getCBVFormatValue(cbvFormatOptional)), restResourceUtil.subscriptionContext(), epcisVersion);
        final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
        return subscriptionUni.map(subscription -> {
            // A subscription now exists, so the answer is 201 with its Location, and the body is
            // the subscription itself. It used to be 200 carrying the bare id as the whole body,
            // which is not JSON under the application/json it was served as.
            final String subscriptionID = subscription.getSubscriptionID().toString();
            final Response.ResponseBuilder builder = Response.status(Response.Status.CREATED).entity(subscription);
            if (restResourceUtil.getRequestURL().isPresent()) {
                builder.header(LOCATION, restResourceUtil.getRequestURL().get().concat("/").concat(subscriptionID)).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
            }
            return builder.build();
        });
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesSubscriptionIDGet(String subscriptionID, String queryName) {
        // As above, the mappers answer the failure; the response built here was discarded.
        return subscriptionService.getSubscriptionByID(subscriptionID, queryName).map(querySubscription -> Response.ok(querySubscription).build());
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesSubscriptionIDDelete(String queryName, String subscriptionID) {
        // The subscription is addressed through the query it belongs to, and the query name was
        // being ignored: a DELETE under any query name unsubscribed the given id, so one query's
        // path could cancel another query's subscriber. Looking it up under the name in the path
        // first refuses that with the 404 such an address deserves.
        return subscriptionService.getSubscriptionByID(subscriptionID, queryName)
                .chain(found -> subscriptionService.unsubscribe(subscriptionID, restResourceUtil.subscriptionContext()))
                .chain(r -> Uni.createFrom().item(Response.noContent().build()));
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesQueryNameSubscriptionOptions() {
        return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
    }

    @PermissionsAllowed("query")
    public Uni<Response> queriesSubscriptionIDOptions() {
        return Uni.createFrom().item(Response.status(Response.Status.NOT_IMPLEMENTED).build());
    }

    @Override
    public Set<String> topLevelResourceNames() {
        return Set.of("queries");
    }

    public QueriesResource(QueryService queryService, SubscriptionService subscriptionService, RestResourceUtil restResourceUtil, ApplicationConfigService applicationConfigService) {
        this.queryService = queryService;
        this.subscriptionService = subscriptionService;
        this.restResourceUtil = restResourceUtil;
        this.applicationConfigService = applicationConfigService;
    }
}
