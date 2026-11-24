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

import io.openepcis.model.dto.EPCPageResult;
import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.query.context.QueryContext;
import io.openepcis.resource.specs.BizStepsTopLevelApi;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.util.QueryDocumentUtil;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static io.openepcis.service.util.Constants.BIZ_STEP;
import static io.openepcis.service.util.Constants.EVENTS;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class BizStepsTopLevelResource implements IsTopLevelResource, BizStepsTopLevelApi {
  private final QueryService queryService;
  private final RestResourceUtil restResourceUtil;

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> getEventsFromBizSteps(SecurityIdentity securityIdentity, String bizStep, Integer perPage, String nextPageToken, UriInfo uriInfo) {
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
      return queryService.getTopLevelByFieldName(BIZ_STEP, bizStep.trim(), Optional.ofNullable(perPage), ctx).map(response -> {
        final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersionMax().getVersion()));
        final Response.ResponseBuilder builder = Response.ok(QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get()));
        builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
        return restResourceUtil.linkNextPage(response, ctx, builder).build();
      });
    }
  }

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> getBizSteps(SecurityIdentity securityIdentity, Integer perPage, String nextPageToken, UriInfo uriInfo) {
    final QueryContext ctx = restResourceUtil.queryContext();
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    if (StringUtils.isNotBlank(nextPageToken)) {
      return queryService.getTopLevelResourceNextPage(nextPageToken, perPage, ctx, "bizStep.asCaptured").map(response -> restResourceUtil.buildResponse(response, ctx, supportGS1Extensions, "bizStep"));
    } else {
      return queryService.getTopLevelResource(Optional.of(perPage), ctx, "bizStep.asCaptured").map(response -> restResourceUtil.buildResponse(response, ctx, supportGS1Extensions, "bizStep"));
    }
  }

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> getSubResourceOfBizStep(@Context SecurityIdentity securityIdentity, @Context UriInfo uriInfo, String bizStep) {
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    final URI uri = uriInfo.getBaseUriBuilder().path(BizStepsTopLevelApi.class).path(bizStep).path(EVENTS).build();
    final Link link = Link.fromUri(uri).rel(EVENTS).build();
    final EPCPageResult response = restResourceUtil.createTopLevelResourcePageResult();
    response.setMember(List.of(EVENTS));
    final Response.ResponseBuilder builder = Response.ok(response).links(link).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
    return Uni.createFrom().item(builder.build());
  }

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> getBizStepsOptions() {
    return null;
  }

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> bizStepOptions(String bizStep) {
    return null;
  }

  @PermissionsAllowed("query")
  @Override
  public Uni<Response> bizStepEventsOptions(String bizStep) {
    return null;
  }

  @PermissionsAllowed("query")
  @Override
  public Set<String> topLevelResourceNames() {
    return Set.of("bizSteps");
  }

  public BizStepsTopLevelResource(QueryService queryService, RestResourceUtil restResourceUtil) {
    this.queryService = queryService;
    this.restResourceUtil = restResourceUtil;
  }
}
