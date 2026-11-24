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
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.UnsupportedMediaTypeException;
import io.openepcis.resource.specs.EventsCaptureApi;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "apiKeySecret")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oidc")
@SecurityRequirement(name = "basicAuth")
public class EventsCaptureResource implements EventsCaptureApi {
  private final CaptureEventManagementService captureEventManagementService;
  private final RestResourceUtil restResourceUtil;
  private final CaptureContextUtil captureContextUtil;

  @PermissionsAllowed("query")
  public Uni<Response> validateEvents(SecurityIdentity securityIdentity, InputStream body) throws IOException {
    CaptureContext ctx = captureContextUtil.createCaptureContext(securityIdentity);
    final Optional<CaptureEventService> captureEventService = captureEventManagementService.getCaptureEventService(ctx);
    if (captureEventService.isEmpty()) {
      throw new UnsupportedMediaTypeException(String.format("unable to find CaptureEventService for Content-Type: %s and EPCISVersion: %s", ctx.getContentType(), ctx.getEpcisVersion()));
    }
    return captureEventService.get().validateCaptureDocument(body).map(invalidEventsInfo -> {
      Response.ResponseBuilder builder = Response.ok(invalidEventsInfo);
      builder.header(CommonConstants.GS1_EPCIS_Capture_Limit, String.valueOf(captureEventService.get().getCaptureLimit()));
      builder.header(CommonConstants.GS1_EPCIS_Capture_File_Size_Limit, String.valueOf(captureEventService.get().getCaptureFileSizeLimit()));
      return builder.build();
    });
  }

  @PermissionsAllowed("capture")
  public Uni<Response> postEvent(SecurityIdentity securityIdentity, String epcisDocumentStr) {
    final CaptureContext ctx = captureContextUtil.createCaptureContext(securityIdentity);
    final Optional<CaptureEventService> captureEventService = captureEventManagementService.getCaptureEventService(ctx);
    final Uni<EPCISEvent> epcisEvent = captureEventService.get().captureSingleEvent(epcisDocumentStr, ctx);
    final String supportGS1Extensions = restResourceUtil.getSupportedGS1Extensions();
    try {
      return epcisEvent.onItem().transform(event -> {
        try {
          final Response.ResponseBuilder builder = restResourceUtil.getRequestURL().isPresent() && event != null ? Response.created(new URI(String.format("%s/%s", restResourceUtil.getRequestURL().get(), event.getEventID()))) : Response.serverError();
          builder.header(CommonConstants.GS1_EPCIS_Capture_Limit, String.valueOf(captureEventService.get().getCaptureLimit()));
          builder.header(CommonConstants.GS1_EPCIS_Capture_File_Size_Limit, String.valueOf(captureEventService.get().getCaptureFileSizeLimit()));
          builder.header(CommonConstants.GS1_EPCIS_VERSION, restResourceUtil.epcisVersion().map(EPCISVersion::getVersion).orElse(EPCISVersion.VERSION_2_0_1.getVersion())).header(CommonConstants.GS1_CBV_VERSION, restResourceUtil.cbvVersion().map(CBVVersion::getVersion).orElse(CBVVersion.VERSION_2_0_0.getVersion())).header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
          builder.entity(event);
          return builder.build();
        } catch (Exception e) {
          return Response.serverError().entity(e).build();
        }
      });
    } catch (Exception e) {
      return Uni.createFrom().item(Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getClass().getSimpleName()).entity(e.getMessage()).build());
    }
  }

  public EventsCaptureResource(CaptureEventManagementService captureEventManagementService, RestResourceUtil restResourceUtil, CaptureContextUtil captureContextUtil) {
    this.captureEventManagementService = captureEventManagementService;
    this.restResourceUtil = restResourceUtil;
    this.captureContextUtil = captureContextUtil;
  }
}
