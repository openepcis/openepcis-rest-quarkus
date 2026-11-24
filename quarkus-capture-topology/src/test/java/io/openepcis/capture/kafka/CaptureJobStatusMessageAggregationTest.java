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
package io.openepcis.capture.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openepcis.capture.context.message.EventSavedMessage;
import io.openepcis.capture.service.EPCISEventPersistenceService;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.dto.CaptureStatusMessage;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaptureJobStatusMessageAggregationTest {

  private static final String DOC_A = "epcis-event-2026-11/ni:///sha-256;aaa?ver=CBV2.0";
  private static final String DOC_B = "epcis-event-2026-11/ni:///sha-256;bbb?ver=CBV2.0";

  private EPCISEventPersistenceService persistence;
  private CaptureJobStatusMessage captureJob;

  @BeforeEach
  void setUp() {
    persistence = mock(EPCISEventPersistenceService.class);
    when(persistence.completeByDocuments(anyString(), anyList(), any())).thenReturn(Uni.createFrom().item(1));
    when(persistence.refreshEvents()).thenReturn(Uni.createFrom().voidItem());
    when(persistence.checkAllEventsAvailableForGivenCaptureID(anyString(), anyInt()))
        .thenReturn(Uni.createFrom().item(1L));
    when(persistence.updateEventVisibility(anyString(), any(), any()))
        .thenReturn(Uni.createFrom().item(true));
    when(persistence.getEventIdsByCaptureId(anyString()))
        .thenReturn(Multi.createFrom().item("ni:///sha-256;abc?ver=CBV2.0"));
    when(persistence.produceMessageToEventSavedTopic(any(EventSavedMessage.class)))
        .thenReturn(Uni.createFrom().voidItem());
    captureJob = new CaptureJobStatusMessage();
    captureJob.setCaptureID("cap-1");
    captureJob.setCreatedAt(OffsetDateTime.now());
    captureJob.setCaptureErrorBehaviour("rollback");
  }

  private static EventStatusMessage of(final CaptureStatusMessage base) {
    final EventStatusMessage m = new EventStatusMessage();
    m.setType(base.getType());
    m.setValue(base.getValue());
    m.setErrors(base.getErrors());
    m.setMetadata(base.getMetadata());
    return m;
  }

  private static EventStatusMessage counted(final int n) {
    return of(CaptureStatusMessage.eventCapturedCount(n, Map.of()));
  }

  private static EventStatusMessage processed(final String document) {
    return EventStatusMessage.processed(document, Map.of());
  }

  private CaptureJobAggregate apply(final CaptureJobAggregate job, final EventStatusMessage status) {
    return apply(job, status, CaptureJobStatusMessageAggregation.DEFAULT_MAX_DOCUMENTS);
  }

  private CaptureJobAggregate apply(final CaptureJobAggregate job, final EventStatusMessage status, final int max) {
    return new CaptureJobStatusMessageAggregation(captureJob, status, persistence, max)
        .update(job)
        .await()
        .indefinitely();
  }

  private void verifySearchFallback(final int times) {
    verify(persistence, times(times)).refreshEvents();
    verify(persistence, times(times)).updateEventVisibility(anyString(), any(), any());
  }

  @Test
  void completesByDocumentsWithoutSearchOrRefresh() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), counted(2));
    job = apply(job, processed(DOC_A));
    assertNull(job.getFinishedAt());
    job = apply(job, processed(DOC_B));
    assertNotNull(job.getFinishedAt());
    assertTrue(job.isSuccess());
    assertFalse(job.isRunning());
    verify(persistence, times(1)).completeByDocuments(eq("cap-1"), eq(List.of(DOC_A, DOC_B)), any());
    verifySearchFallback(0);
    verify(persistence, never()).checkAllEventsAvailableForGivenCaptureID(anyString(), anyInt());
    assertTrue(job.getDocuments().isEmpty(), "a finished job does not keep its documents");
  }

  @Test
  void processedBeforeCountFinishesWhenTheCountArrives() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), processed(DOC_A));
    assertNull(job.getFinishedAt());
    verify(persistence, never()).completeByDocuments(anyString(), anyList(), any());
    job = apply(job, counted(1));
    assertNotNull(job.getFinishedAt());
    assertTrue(job.isSuccess());
    verify(persistence, times(1)).completeByDocuments(eq("cap-1"), eq(List.of(DOC_A)), any());
  }

  @Test
  void countArrivingWhileEventsAreStillProcessingKeepsTheJobOpen() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), processed(DOC_A));
    job = apply(job, counted(2));
    assertNull(job.getFinishedAt());
    job = apply(job, processed(DOC_B));
    assertNotNull(job.getFinishedAt());
    verify(persistence, times(1)).completeByDocuments(anyString(), anyList(), any());
  }

  @Test
  void anEventWithoutDocumentMakesTheJobCompleteBySearch() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), counted(2));
    job = apply(job, processed(DOC_A));
    job = apply(job, processed(null));
    assertTrue(job.isSuccess());
    verify(persistence, never()).completeByDocuments(anyString(), anyList(), any());
    verifySearchFallback(1);
    verify(persistence, times(1)).produceMessageToEventSavedTopic(any());
  }

  @Test
  void aJobLargerThanTheBoundCompletesBySearch() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), counted(2), 1);
    job = apply(job, processed(DOC_A), 1);
    job = apply(job, processed(DOC_B), 1);
    assertTrue(job.isDocumentsIncomplete());
    assertTrue(job.getDocuments().isEmpty());
    verify(persistence, never()).completeByDocuments(anyString(), anyList(), any());
    verifySearchFallback(1);
  }

  @Test
  void aFailedCompletionByDocumentsFallsBackToSearch() {
    when(persistence.completeByDocuments(anyString(), anyList(), any()))
        .thenReturn(Uni.createFrom().failure(new IllegalStateException("1 of 2 documents found")));
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), counted(1));
    job = apply(job, processed(DOC_A));
    assertTrue(job.isSuccess());
    verifySearchFallback(1);
    verify(persistence, times(1)).produceMessageToEventSavedTopic(any());
  }

  @Test
  void proceedNeedsNoCompletionBecauseEachEventWasAnnouncedWhenPersisted() {
    captureJob.setCaptureErrorBehaviour("proceed");
    CaptureJobAggregate job = apply(new CaptureJobAggregate(), counted(1));
    job = apply(job, processed(DOC_A));
    assertNotNull(job.getFinishedAt());
    assertTrue(job.isSuccess());
    verify(persistence, never()).completeByDocuments(anyString(), anyList(), any());
    verifySearchFallback(0);
    verify(persistence, never()).produceMessageToEventSavedTopic(any());
  }

  @Test
  void rollbackFinishedJobIsNotFinishedAgainByALateCount() {
    CaptureJobAggregate job = apply(new CaptureJobAggregate(),
        of(CaptureStatusMessage.invalid(1, List.of(new InvalidEPCISEventInfo("urn:test", "bad", 400, "bad")), Map.of())));
    assertNotNull(job.getFinishedAt());
    job = apply(job, processed(DOC_A));
    job = apply(job, counted(1));
    assertFalse(job.isSuccess());
    verify(persistence, never()).completeByDocuments(anyString(), anyList(), any());
    verify(persistence, never()).produceMessageToEventSavedTopic(any());
  }
}
