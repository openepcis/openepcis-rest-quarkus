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

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

/**
 * Marks every operation the EPCIS 2.0 standard does not define, so that nobody reading the
 * OpenAPI document or the Swagger UI takes an OpenEPCIS extension for part of the standard and
 * builds a client that only works against OpenEPCIS.
 *
 * <p>The standard is the operation list of the GS1 EPCIS 2.0 REST binding ({@code REST
 * Bindings/openapi.yaml} in github.com/gs1/EPCIS at 6f714682). Operations are compared by method
 * and path, with path parameter names ignored. Everything else gets the note in summary and
 * description and the extension {@code x-openepcis-non-standard: true}. Deciding by list rather
 * than by annotation means a new endpoint is marked without anyone remembering to; the filter
 * runs in every application that includes these resources, so the Business edition's own
 * endpoints are covered too.
 */
@OpenApiFilter
public class NonStandardOperationsOASFilter implements OASFilter {

  static final String NOTE = "Note: This is a non-standard EPCIS 2.0 endpoint.";
  static final String SUMMARY_SUFFIX = " (non-standard)";
  static final String EXTENSION = "x-openepcis-non-standard";

  /** Method and path of every operation in the GS1 EPCIS 2.0 REST binding. */
  static final Set<String> STANDARD_OPERATIONS =
      Set.of(
          "OPTIONS /",
          "GET /",
          "OPTIONS /capture",
          "POST /capture",
          "GET /capture",
          "OPTIONS /capture/{}",
          "GET /capture/{}",
          "OPTIONS /events",
          "GET /events",
          "POST /events",
          "OPTIONS /events/{}",
          "GET /events/{}",
          "OPTIONS /eventTypes",
          "GET /eventTypes",
          "OPTIONS /eventTypes/{}",
          "GET /eventTypes/{}",
          "OPTIONS /eventTypes/{}/events",
          "GET /eventTypes/{}/events",
          "OPTIONS /epcs",
          "GET /epcs",
          "OPTIONS /epcs/{}",
          "GET /epcs/{}",
          "OPTIONS /epcs/{}/events",
          "GET /epcs/{}/events",
          "OPTIONS /bizSteps",
          "GET /bizSteps",
          "OPTIONS /bizSteps/{}",
          "GET /bizSteps/{}",
          "OPTIONS /bizSteps/{}/events",
          "GET /bizSteps/{}/events",
          "OPTIONS /bizLocations",
          "GET /bizLocations",
          "OPTIONS /bizLocations/{}",
          "GET /bizLocations/{}",
          "OPTIONS /bizLocations/{}/events",
          "GET /bizLocations/{}/events",
          "OPTIONS /readPoints",
          "GET /readPoints",
          "OPTIONS /readPoints/{}",
          "GET /readPoints/{}",
          "OPTIONS /readPoints/{}/events",
          "GET /readPoints/{}/events",
          "OPTIONS /dispositions",
          "GET /dispositions",
          "OPTIONS /dispositions/{}",
          "GET /dispositions/{}",
          "OPTIONS /dispositions/{}/events",
          "GET /dispositions/{}/events",
          "OPTIONS /queries",
          "GET /queries",
          "POST /queries",
          "OPTIONS /queries/{}",
          "GET /queries/{}",
          "DELETE /queries/{}",
          "OPTIONS /queries/{}/subscriptions",
          "GET /queries/{}/subscriptions",
          "POST /queries/{}/subscriptions",
          "OPTIONS /queries/{}/subscriptions/{}",
          "GET /queries/{}/subscriptions/{}",
          "DELETE /queries/{}/subscriptions/{}",
          "OPTIONS /queries/{}/events",
          "GET /queries/{}/events",
          "DELETE /nextPageToken/{}");

  @Override
  public void filterOpenAPI(final OpenAPI openAPI) {
    if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
      return;
    }
    for (final Map.Entry<String, PathItem> path : openAPI.getPaths().getPathItems().entrySet()) {
      final Map<PathItem.HttpMethod, Operation> operations = path.getValue().getOperations();
      if (operations == null) {
        continue;
      }
      for (final Map.Entry<PathItem.HttpMethod, Operation> operation : operations.entrySet()) {
        if (!isStandard(operation.getKey().name(), path.getKey())) {
          mark(operation.getValue());
        }
      }
    }
  }

  static boolean isStandard(final String method, final String path) {
    final String normalized = path.replaceAll("\\{[^}]*}", "{}");
    final String trimmed =
        normalized.length() > 1 && normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    return STANDARD_OPERATIONS.contains(method + " " + trimmed);
  }

  private static void mark(final Operation operation) {
    if (operation.getExtensions() != null && operation.getExtensions().containsKey(EXTENSION)) {
      return;
    }
    final String summary = operation.getSummary();
    if (summary == null || summary.isBlank()) {
      operation.setSummary(NOTE);
    } else if (!summary.endsWith(SUMMARY_SUFFIX)) {
      operation.setSummary(summary + SUMMARY_SUFFIX);
    }
    final String description = operation.getDescription();
    if (description == null || description.isBlank()) {
      operation.setDescription("**" + NOTE + "**");
    } else if (!description.contains(NOTE)) {
      operation.setDescription(description + "\n\n**" + NOTE + "**");
    }
    operation.addExtension(EXTENSION, Boolean.TRUE);
  }
}
