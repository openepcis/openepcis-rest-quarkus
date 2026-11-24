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

import io.openepcis.repository.util.DigitalLinkHostContext;
import io.openepcis.resource.HeaderConstants;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Reads the OpenEPCIS extension header {@code GS1-Digital-Link-Host} on every
 * inbound request and stores the value on the request-scoped
 * {@link DigitalLinkHostContext}. The repository-layer EPC formatter then
 * substitutes the authority (host[:port]) of every outgoing GS1 Digital Link
 * URI with the supplied host. The URI's scheme is preserved; AI paths are
 * preserved.
 */
@Provider
public class DigitalLinkHostRequestFilter implements ContainerRequestFilter {

  @Inject DigitalLinkHostContext hostContext;

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    final String header = requestContext.getHeaderString(HeaderConstants.GS1_DIGITAL_LINK_HOST);
    if (header != null && !header.isBlank()) {
      hostContext.setHost(header.trim());
    }
  }
}
