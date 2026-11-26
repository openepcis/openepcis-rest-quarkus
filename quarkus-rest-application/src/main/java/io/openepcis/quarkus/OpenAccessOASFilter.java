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
package io.openepcis.quarkus;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Operation;

/**
 * The resources carry {@code @SecurityRequirement} annotations for the schemes the Business
 * edition declares. The Community Edition declares no schemes and authenticates nobody, so those
 * requirements pointed at nothing and the Swagger UI showed a lock on every operation. In the
 * Community Edition this filter drops them; in any other edition it does nothing.
 */
@OpenApiFilter
public class OpenAccessOASFilter implements OASFilter {

  @Override
  public Operation filterOperation(final Operation operation) {
    if (communityEdition()) {
      operation.setSecurity(null);
    }
    return operation;
  }

  private static boolean communityEdition() {
    return "ce"
        .equals(
            ConfigProvider.getConfig()
                .getOptionalValue("openepcis.edition", String.class)
                .orElse(null));
  }
}
