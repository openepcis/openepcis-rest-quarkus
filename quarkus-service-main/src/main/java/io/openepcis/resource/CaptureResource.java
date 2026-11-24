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

import io.openepcis.capture.api.CaptureEventManagementService;
import io.openepcis.capture.api.CaptureEventService;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.UnsupportedMediaTypeException;
import io.openepcis.resource.specs.CaptureApi;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class CaptureResource implements IsTopLevelResource, CaptureApi {
  static final Logger log = LoggerFactory.getLogger(CaptureResource.class);
  private final CaptureEventManagementService captureEventManagementService;
  private final CaptureContextUtil captureContextUtil;
  private final RestResourceUtil restResourceUtil;

  @PermissionsAllowed("capture")
  public Response captureCaptureIDOptions() {
    return Response.noContent().build();
  }

  @PermissionsAllowed("capture")
  public Response captureOptions() {
    final EPCISFormat format = restResourceUtil.epcisFormat().orElse(EPCISFormat.JSON_LD);
    final EPCISVersion version = restResourceUtil.epcisVersion().orElse(captureEventManagementService.getEPCISVersion());
    return Response.noContent().build();
  }

  @PermissionsAllowed("capture")
  public Uni<Response> capturePost(SecurityIdentity securityIdentity, InputStream body) {
    final CaptureContext ctx = captureContextUtil.createCaptureContext(securityIdentity);
    final Optional<CaptureEventService> captureEventService = captureEventManagementService.getCaptureEventService(ctx);
    if (captureEventService.isEmpty()) {
      throw new UnsupportedMediaTypeException("unable to find captureEventService for request");
    }
    final Uni<CaptureContext> uniContext = captureEventService.get().captureDocument(body, ctx);
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    return uniContext.onItem().transform(captureContext -> {
      final Response.ResponseBuilder builder = Response.accepted();
      if (restResourceUtil.getRequestURL().isPresent()) {
        final String captureURL = String.format("%s/%s", restResourceUtil.getRequestURL().get(), captureContext.getCaptureID());
        builder.header(CommonConstants.LOCATION, captureURL).header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(CommonConstants.GS1_CBV_VERSION, restResourceUtil.cbvVersion().map(CBVVersion::getVersion).orElse(CBVVersion.VERSION_2_0_0.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
      }
      return builder.build();
    }).onFailure().recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", ex.getClass().getSimpleName(), "detail", ex.getMessage())).build());
  }

  @Override
  public Set<String> topLevelResourceNames() {
    return Set.of("capture");
  }

  public CaptureResource(CaptureEventManagementService captureEventManagementService, CaptureContextUtil captureContextUtil, RestResourceUtil restResourceUtil) {
    this.captureEventManagementService = captureEventManagementService;
    this.captureContextUtil = captureContextUtil;
    this.restResourceUtil = restResourceUtil;
  }
}
