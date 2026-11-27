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
package io.openepcis.filters;

import io.openepcis.capture.api.CaptureEventManagementService;
import io.openepcis.capture.api.CaptureEventService;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.resource.RestResourceUtil;
import io.quarkus.arc.ClientProxy;
import io.vertx.core.http.HttpMethod;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

public class RootDiscoveryResponseFilter {
  private static final String ALLOW = "Allow";
  public static final String CAPTURE_URL_PREFIX = "/capture";
  public static final String QUERY_URL_PREFIX = "/queries";
  private final RestResourceUtil restResourceUtil;
  @Inject
  Instance<CaptureEventManagementService> captureEventManagementServices;
  @Inject
  Instance<ResourceExtension> resourceExtensions;
  private static final Set<Pattern> ROOT_RESOURCES = Set.of("/", "/capture", "/capture/.*", "/events", "/events/.*", "/eventTypes", "/eventTypes/.*", "/epcs", "/epcs/.*", "/bizSteps", "/bizSteps/.*", "/bizLocations", "/bizLocations/.*", "/readPoints", "/readPoints/.*", "/dispositions", "/dispositions/.*", "/queries", "/queries/.*").stream().map(Pattern::compile).collect(Collectors.toSet());

  @ServerResponseFilter
  public void getFilter(ContainerResponseContext responseContext, ResourceInfo resourceInfo) {
    if (restResourceUtil.getRequestURL().isPresent() && restResourceUtil.getHttpMethod().isPresent() && HttpMethod.OPTIONS.equals(restResourceUtil.getHttpMethod().get())) {
      final String requestUrl = restResourceUtil.getRequestURL().get();
      if (ROOT_RESOURCES.stream().anyMatch(p -> p.matcher(requestUrl).matches())) {
        {
          final Map<String, String> headers = restResourceUtil.getDiscoverOptionHeaders(findCaptureEventService(requestUrl));
          headers.entrySet().stream().forEach(entry -> {
            responseContext.getHeaders().put(entry.getKey(), List.of(entry.getValue()));
          });
          // EPCIS 2.0 has a discovery request name the methods of the resource it asked about.
          // This sent every other discovery header and not this one, so a client could read our
          // versions and extensions but still had to guess what it was allowed to do here.
          AllowedMethods.of(resourceInfo.getResourceClass(), resourceInfo.getResourceMethod(), extensionsOf(resourceInfo.getResourceClass()))
              .ifPresent(allow -> responseContext.getHeaders().put(ALLOW, List.of(allow)));
          responseContext.setStatus(204);
        }
      }
    }
  }

  /** The classes that add methods to the paths of {@code resourceClass}. */
  private List<Class<?>> extensionsOf(final Class<?> resourceClass) {
    if (resourceClass == null) {
      return List.of();
    }
    return resourceExtensions.stream()
        .filter(extension -> extension.extendedResource().isAssignableFrom(resourceClass))
        .<Class<?>>map(extension -> ClientProxy.unwrap(extension).getClass())
        .toList();
  }

  private Optional<CaptureEventService> findCaptureEventService(final String requestUrl) {
    if (captureEventManagementServices.isResolvable() && requestUrl.startsWith("/capture")) {
      return captureEventManagementServices.get().getCaptureEventService(restResourceUtil.epcisFormat().orElse(EPCISFormat.JSON_LD), restResourceUtil.epcisVersion().orElse(EPCISVersion.VERSION_2_0_1));
    }
    return Optional.empty();
  }

  public RootDiscoveryResponseFilter(RestResourceUtil restResourceUtil) {
    this.restResourceUtil = restResourceUtil;
  }
}
