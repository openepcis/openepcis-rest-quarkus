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
package io.openepcis.resource.specs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NonStandardOperationsOASFilterTest {

  private static final String NOTE = NonStandardOperationsOASFilter.NOTE;

  @Test
  @DisplayName("operations of the EPCIS 2.0 REST binding are standard, whatever the parameter names")
  void standardOperations() {
    assertTrue(NonStandardOperationsOASFilter.isStandard("GET", "/events"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("POST", "/events"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("GET", "/events/{eventID}"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("GET", "/events/{id}"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("DELETE", "/queries/{queryName}"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("GET", "/capture/"));
    assertTrue(NonStandardOperationsOASFilter.isStandard("DELETE", "/nextPageToken/{token}"));
  }

  @Test
  @DisplayName("OpenEPCIS extensions are not, including another method on a standard path")
  void nonStandardOperations() {
    assertFalse(NonStandardOperationsOASFilter.isStandard("GET", "/events/aggregate"));
    assertFalse(NonStandardOperationsOASFilter.isStandard("POST", "/events/validate"));
    assertFalse(NonStandardOperationsOASFilter.isStandard("PUT", "/queries/{queryName}"));
    assertFalse(NonStandardOperationsOASFilter.isStandard("GET", "/admin/commission-register/backfill"));
    assertFalse(NonStandardOperationsOASFilter.isStandard("GET", "/me/tokens"));
    assertFalse(NonStandardOperationsOASFilter.isStandard("POST", "/userExtension/jsonSchema"));
  }

  @Test
  @DisplayName("the filter marks extensions once and leaves standard operations alone")
  void filterMarksExtensionsOnly() {
    final Operation events = OASFactory.createOperation().summary("Query events").description("Returns events.");
    final Operation aggregate =
        OASFactory.createOperation().summary("Counts EPCIS events, grouped").description("Counts events.");
    final Operation bare = OASFactory.createOperation();
    final OpenAPI openAPI =
        OASFactory.createOpenAPI()
            .paths(
                OASFactory.createPaths()
                    .addPathItem("/events", OASFactory.createPathItem().GET(events))
                    .addPathItem("/events/aggregate", OASFactory.createPathItem().GET(aggregate))
                    .addPathItem("/me/tokens", OASFactory.createPathItem().GET(bare)));

    final NonStandardOperationsOASFilter filter = new NonStandardOperationsOASFilter();
    filter.filterOpenAPI(openAPI);
    filter.filterOpenAPI(openAPI);

    assertEquals("Query events", events.getSummary());
    assertEquals("Returns events.", events.getDescription());
    assertNull(events.getExtensions());

    assertEquals("Counts EPCIS events, grouped (non-standard)", aggregate.getSummary());
    assertEquals("Counts events.\n\n**" + NOTE + "**", aggregate.getDescription());
    assertEquals(Boolean.TRUE, aggregate.getExtensions().get(NonStandardOperationsOASFilter.EXTENSION));

    assertEquals(NOTE, bare.getSummary());
    assertEquals("**" + NOTE + "**", bare.getDescription());
  }
}
