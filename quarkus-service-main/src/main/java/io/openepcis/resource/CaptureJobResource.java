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

import static io.openepcis.repository.Constants.TYPE_XML;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.dto.CaptureJob;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.resource.specs.CaptureJobApi;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class CaptureJobResource implements CaptureJobApi {
  private final QueryRepository queryRepository;
  private final RestResourceUtil restResourceUtil;

  @PermissionsAllowed("capture")
  public Uni<Response> captureIDGet(String captureID) {
    final Uni<CaptureJob> responseUni = queryRepository.getCaptureJobFromCaptureID(captureID);
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    return responseUni.map(response -> {
      final Response.ResponseBuilder builder =  //
      Response.ok().entity(response).header(CommonConstants.GS1_Capture_Error_Behaviour, response.getCaptureErrorBehaviour().toString()).header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
      return builder.build();
    });
  }

  @PermissionsAllowed("capture")
  public Uni<Response> captureGet(Integer perPage, String nextPageToken) {
    Optional<String> acceptHeader = restResourceUtil.getAcceptHeader();
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    if (StringUtils.isNotBlank(nextPageToken)) return queryRepository.getCaptureJobs(nextPageToken, Optional.ofNullable(perPage)).map(captureJobPageEvent -> {
      final Response.ResponseBuilder builder = Response.ok(acceptHeader.isPresent() && acceptHeader.get().contains(TYPE_XML) ? captureJobPageEvent : captureJobPageEvent.getCaptureJobs()).header(CommonConstants.GS1_EPCIS_VERSION, EPCISVersion.VERSION_2_0_1.getVersion()).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
      return restResourceUtil.linkNextPage(captureJobPageEvent, builder).build();
    });
     else return queryRepository.getCaptureJobs(perPage).map(captureJobPageResults -> {
      final Response.ResponseBuilder builder = Response.ok(acceptHeader.isPresent() && acceptHeader.get().contains(TYPE_XML) ? captureJobPageResults : captureJobPageResults.getCaptureJobs()).header(CommonConstants.GS1_EPCIS_VERSION, EPCISVersion.VERSION_2_0_1.getVersion()).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
      return restResourceUtil.linkNextPage(captureJobPageResults, builder).build();
    });
  }

  public CaptureJobResource(QueryRepository queryRepository, RestResourceUtil restResourceUtil) {
    this.queryRepository = queryRepository;
    this.restResourceUtil = restResourceUtil;
  }
}
