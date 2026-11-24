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
import io.openepcis.capture.context.CaptureContextFactory;
import io.openepcis.config.ApplicationConfigService;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.exception.CaptureLimitExceededException;
import io.openepcis.model.epcis.exception.NotAcceptedException;
import io.openepcis.model.epcis.exception.UnsupportedMediaTypeException;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.service.api.UserDetailsExtractor;
import io.openepcis.service.util.Constants;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

@ApplicationScoped
public class CaptureContextUtil {
  @Inject
  RestResourceUtil restResourceUtil;
  @Inject
  HttpServerRequest request;
  private final ApplicationConfigService appConfigService;
  private final CaptureContextFactory captureContextFactory;
  private final UserDetailsExtractor userDetailsExtractor;
  private final CaptureEventManagementService captureEventManagementService;
  private final CaptureClaimsProvider captureClaimsProvider;

  public CaptureContext createCaptureContext(final SecurityIdentity securityIdentity) {
    verifyRequestHeaders();
    final String captureErrorBehaviour = getCaptureErrorBehaviour().orElse("rollback");
    final String contentType = restResourceUtil.getContentType().orElseThrow(() -> new UnsupportedMediaTypeException("invalid content-type"));
    final Optional<Long> contentLength = restResourceUtil.getContentLength();
    final EPCISVersion epcisVersion = restResourceUtil.epcisVersion().orElse(EPCISVersion.VERSION_2_0_1);
    final CBVVersion cbvVersion = restResourceUtil.cbvVersion().orElse(CBVVersion.VERSION_2_0_0);
    final EPCISFormat epcisFormat = EPCISFormat.fromString(contentType).orElse(EPCISFormat.JSON_LD);
    if (contentLength.isPresent()) {
      final Optional<CaptureEventService> captureEventService = captureEventManagementService.getCaptureEventService(epcisFormat, epcisVersion);
      if (captureEventService.isPresent()) {
        if (contentLength.get() > captureEventService.get().getCaptureFileSizeLimit()) {
          throw new CaptureLimitExceededException(String.format("document size must be within %s bytes", captureEventService.get().getCaptureFileSizeLimit()));
        }
      }
    }
    final CapturedByES capturedByES = CapturedByES.builder().userID(restResourceUtil.getUserId(securityIdentity)).defaultGroup(userDetailsExtractor.getDefaultGroup(securityIdentity)).build();
    final Map<String, Object> metadata = new java.util.HashMap<>();
    metadata.put(Constants.CAPTURED_BY, capturedByES);
    metadata.put(Constants.VISIBLE, false);
    captureClaimsProvider.contribute(securityIdentity, metadata);
    final String gs1Extensions = request.getHeader(HeaderConstants.GS1_EXTENSIONS);
    final CaptureContext context = captureContextFactory.createContext(epcisVersion, cbvVersion, epcisFormat, captureErrorBehaviour, contentLength, contentType, Optional.ofNullable(gs1Extensions), metadata, userDetailsExtractor.getDefaultGroup(securityIdentity));
    return context;
  }

  public Optional<String> getCaptureErrorBehaviour() {
    return Optional.ofNullable(request.getHeader(HeaderConstants.GS1_CAPTURE_ERROR_BEHAVIOUR));
  }

  public void verifyRequestHeaders() {
    final Optional<String> epcisMin = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_EPCIS_MIN));
    if (epcisMin.isPresent()) {
      if (captureEventManagementService.validate(EPCISVersion.class, epcisMin).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_EPCIS_MIN);
      }
    }
    final Optional<String> epcisMax = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_EPCIS_MAX));
    if (epcisMax.isPresent()) {
      if (captureEventManagementService.validate(EPCISVersion.class, epcisMax).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_EPCIS_MAX);
      }
    }
    final Optional<String> cbvVersion = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_CBV_VERSION));
    if (cbvVersion.isPresent()) {
      if (captureEventManagementService.validate(CBVVersion.class, cbvVersion).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_CBV_VERSION);
      }
    }
    final Optional<String> cbvMin = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_CBV_MIN));
    if (cbvMin.isPresent()) {
      if (captureEventManagementService.validate(CBVVersion.class, cbvMin).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_CBV_MIN);
      }
    }
    final Optional<String> cbvMax = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_CBV_MAX));
    if (cbvMax.isPresent()) {
      if (captureEventManagementService.validate(CBVVersion.class, cbvMax).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_CBV_MAX);
      }
    }
    final Optional<String> epcFormat = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_EPC_FORMAT));
    if (epcFormat.isPresent()) {
      if (captureEventManagementService.validate(EPCFormat.class, epcFormat).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_EPC_FORMAT);
      }
    }
    final Optional<String> cbvXmlFormat = Optional.ofNullable(request.getHeader(HeaderConstants.GS1_CBV_XML_FORMAT));
    if (cbvXmlFormat.isPresent()) {
      final Optional<String> acceptHeader = restResourceUtil.getAcceptHeader();
      if (acceptHeader.isPresent() && !acceptHeader.get().toLowerCase().contains("+xml") && !acceptHeader.get().toLowerCase().contains("/xml")) {
        throw new NotAcceptedException(HeaderConstants.GS1_CBV_XML_FORMAT + " header is applicable for application/xml contentType only.");
      }
      if (captureEventManagementService.validate(CBVFormat.class, cbvXmlFormat).isEmpty()) {
        throw new NotAcceptedException(HeaderConstants.GS1_CBV_XML_FORMAT);
      }
    }
  }

  public CaptureContextUtil(ApplicationConfigService appConfigService, CaptureContextFactory captureContextFactory, UserDetailsExtractor userDetailsExtractor, CaptureEventManagementService captureEventManagementService, CaptureClaimsProvider captureClaimsProvider) {
    this.captureClaimsProvider = captureClaimsProvider;
    this.appConfigService = appConfigService;
    this.captureContextFactory = captureContextFactory;
    this.userDetailsExtractor = userDetailsExtractor;
    this.captureEventManagementService = captureEventManagementService;
  }
}
