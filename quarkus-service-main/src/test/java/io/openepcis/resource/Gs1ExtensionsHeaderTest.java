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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Gs1ExtensionsHeaderTest {

  @Test
  void eachPrefixOnceWithTheFirstNamespace() {
    assertEquals(
        "oec=https://ref.openepcis.org/extensions/common/core/,eubat=https://ref.openepcis.org/extensions/eu/battery/",
        RestResourceUtil.gs1ExtensionsHeader(
            List.of(
                Map.entry("oec", "https://ref.openepcis.org/extensions/common/core/"),
                Map.entry("eubat", "https://ref.openepcis.org/extensions/eu/battery/"),
                Map.entry("oec", "https://ref.openepcis.io/extensions/common/core/"),
                Map.entry("eubat", "https://ref.openepcis.io/extensions/eu/battery/"))));
  }
}
