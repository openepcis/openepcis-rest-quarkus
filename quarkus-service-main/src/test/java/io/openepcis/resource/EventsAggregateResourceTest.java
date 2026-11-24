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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.context.QueryContext;
import io.openepcis.resource.specs.EventsApi;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.api.UserDetailsExtractor;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GET /events/aggregate: parameters, the answer handed through, and the gate. */
class EventsAggregateResourceTest {

  private final QueryService queryService = mock(QueryService.class);
  private final RestResourceUtil restResourceUtil = mock(RestResourceUtil.class);
  private final QueryContext queryContext = mock(QueryContext.class);
  private final EventsResource resource =
      new EventsResource(queryService, restResourceUtil, mock(UserDetailsExtractor.class), new ObjectMapper());

  private Response call(final String groupBy, final String size) {
    when(restResourceUtil.queryContext()).thenReturn(queryContext);
    when(restResourceUtil.getSupportedGS1Extensions()).thenReturn("");
    return resource.eventsAggregate(null, null, groupBy, size).await().indefinitely();
  }

  @Test
  @DisplayName("the counts go out as they come, parsed from the same query context as /events, size 20 by default")
  void answersWithTheAggregation() {
    final EventAggregation aggregation =
        new EventAggregation("bizStep", 12, List.of(new EventAggregation.Bucket("urn:epcglobal:cbv:bizstep:shipping", 5)), 1);
    when(queryService.aggregateEvents(queryContext, EventGroupBy.bizStep, 20)).thenReturn(Uni.createFrom().item(aggregation));
    final Response response = call("bizStep", null);
    assertEquals(200, response.getStatus());
    assertSame(aggregation, response.getEntity());
    assertEquals("application/json", response.getMediaType().toString());
  }

  @Test
  @DisplayName("size is handed through within 1..100")
  void sizeWithinRange() {
    when(queryService.aggregateEvents(any(), any(), anyInt()))
        .thenReturn(Uni.createFrom().item(new EventAggregation("day", 0, List.of(), 0)));
    call("day", "100");
    verify(queryService).aggregateEvents(queryContext, EventGroupBy.day, 100);
    call("month", "1");
    verify(queryService).aggregateEvents(queryContext, EventGroupBy.month, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "epc", "bizstep", "eventTime", "parentID"})
  @DisplayName("an unknown or missing groupBy is a 400 (QueryParameterException)")
  void unknownGroupBy(final String groupBy) {
    assertThrows(QueryParameterException.class, () -> call(groupBy, null));
    assertThrows(QueryParameterException.class, () -> call(null, null));
    verify(queryService, never()).aggregateEvents(any(), any(), anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "-1", "abc", "2.5"})
  @DisplayName("a size outside 1..100 or not a number is a 400 (QueryParameterException)")
  void badSize(final String size) {
    assertThrows(QueryParameterException.class, () -> call("eventType", size));
    verify(queryService, never()).aggregateEvents(any(), any(), anyInt());
  }

  @Test
  @DisplayName("GET events/aggregate asks for query, like GET /events/{eventID}")
  void gatedOnQuery() {
    final Method method =
        Arrays.stream(EventsApi.class.getMethods())
            .filter(m -> m.getName().equals("eventsAggregate"))
            .findFirst()
            .orElseThrow();
    assertNotNull(method.getAnnotation(GET.class));
    assertEquals("events/aggregate", method.getAnnotation(Path.class).value());
    assertArrayEquals(new String[] {"query"}, method.getAnnotation(PermissionsAllowed.class).value());
  }
}
