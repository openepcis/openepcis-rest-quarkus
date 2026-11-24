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

import io.openepcis.capture.context.message.EventSavedMessage;
import io.openepcis.capture.service.EPCISEventPersistenceService;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.service.util.Constants;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * One status message applied to the aggregated capture job. A finished, successful job completes
 * by the documents its events were stored as (one multi get, one bulk update by id); when they are
 * not all known it falls back to finding the events by search, after one refresh.
 */
public record CaptureJobStatusMessageAggregation(
    CaptureJobStatusMessage captureJob,
    EventStatusMessage statusMessage,
    EPCISEventPersistenceService epcisEventPersistenceService,
    int maxDocuments) {

  /** Most documents a job carries; a larger job completes by search. */
  public static final int DEFAULT_MAX_DOCUMENTS = 1000;
  private static final OpenEPCISLogger log =
      OpenEPCISLogger.getLogger(CaptureContextTopology.class);

  public Uni<CaptureJobAggregate> update(CaptureJobAggregate job) {
    job.setCaptureID(captureJob.getCaptureID());
    job.setCreatedAt(captureJob.getCreatedAt());
    job.setCaptureErrorBehaviour(captureJob.getCaptureErrorBehaviour());
    job.setStorageBucket(captureJob.getStorageBucket());
    job.setStorageKey(captureJob.getStorageKey());
    job.setTraceId(statusMessage.getTraceId());
    job.setSpanId(statusMessage.getSpanId());
    job.setDefaultGroup(statusMessage.getDefaultGroup());
    job.setMetadata(statusMessage.getMetadata());
    if (statusMessage.getType() != null) {
      switch (statusMessage.getType()) {
        case CAPTURED -> {
          job.setCapturedEventCount(statusMessage.getValue());
          // The count of a small document can arrive after its events were already processed.
          // Then this is the message that completes the job.
          if (job.getProcessedEventCount() > 0 && job.getFinishedAt() == null) {
            return completeIfAllProcessed(job);
          }
        }
        case INVALID -> {
          job.setInvalidEventCount(job.getInvalidEventCount() + statusMessage.getValue());
          if (statusMessage.getErrors() != null) {
            job.getErrors().addAll(statusMessage.getErrors());
          }
          if (Constants.CAPTURE_ERROR_BEHAVIOUR_ROLLBACK.equalsIgnoreCase(
              job.getCaptureErrorBehaviour())) {
            job.setFinishedAt(OffsetDateTime.now());
            job.setSuccess(job.getErrors().isEmpty());
            job.setRunning(false);
          }
        }
        case VALID -> job.setValidEventCount(job.getValidEventCount() + statusMessage.getValue());
        case PROCESSED -> {
          job.setProcessedEventCount(job.getProcessedEventCount() + statusMessage.getValue());
          job.collect(statusMessage.getDocument(), maxDocuments);
          return completeIfAllProcessed(job);
        }
      }
    }
    return Uni.createFrom().item(job);
  }

  // Finishes the job once every captured event is processed. Before the count arrived the
  // captured count is 0 and the job stays open.
  private Uni<CaptureJobAggregate> completeIfAllProcessed(CaptureJobAggregate job) {
    if (job.getCapturedEventCount() == 0
        || job.getCapturedEventCount() != job.getProcessedEventCount()) {
      return Uni.createFrom().item(job);
    }
    job.setFinishedAt(OffsetDateTime.now());
    job.setSuccess(job.getErrors().isEmpty());
    job.setRunning(false);
    if (!job.isSuccess()) {
      job.setDocuments(null);
      return Uni.createFrom().item(job);
    }
    if (Constants.CAPTURE_ERROR_BEHAVIOUR_PROCEED.equalsIgnoreCase(job.getCaptureErrorBehaviour())) {
      // Proceed: each event was written visible and announced by its own event-saved when it was
      // persisted. Completing it again sent every event-saved twice.
      job.setDocuments(null);
      return Uni.createFrom().item(job);
    }
    try {
      if (job.completeByDocuments()) {
        return epcisEventPersistenceService
            .completeByDocuments(job.getCaptureID(), job.getDocuments(), job)
            .map(
                published -> {
                  log.debug(
                      "capture {} completed by {} documents, {} event-saved",
                      job.getCaptureID(),
                      job.getDocuments().size(),
                      published);
                  return job;
                })
            .onFailure()
            .recoverWithUni(
                failure -> {
                  log.warn(
                      "capture {} could not complete by its documents, searching instead: {}",
                      job.getCaptureID(),
                      failure.getMessage());
                  return produceMessageToEventSavedTopic(job);
                })
            .invoke(done -> done.setDocuments(null));
      }
      return produceMessageToEventSavedTopic(job).invoke(done -> done.setDocuments(null));
    } catch (Exception e) {
      log.error(
          "ERROR while producing event to event-saved topic for events with captureId= {}",
          job.getCaptureID(),
          e);
      return Uni.createFrom().failure(e);
    }
  }

  // Method to get the eventID for the persisted event after the process of persisting
  // Includes retry logic: max 3 attempts with exponential backoff, then skip on failure
  private Uni<CaptureJobAggregate> produceMessageToEventSavedTopic(CaptureJobAggregate job) {
    log.debug(
        "Total events to be produced in event saved topic with captured ID= {} are: {}",
        job.getCaptureID(),
        job.getProcessedEventCount());

    // Captures write without waiting for a refresh: one refresh, then the search finds them all.
    return epcisEventPersistenceService
        .refreshEvents()
        .onFailure()
        .recoverWithNull()
        .chain(
            () ->
                epcisEventPersistenceService.checkAllEventsAvailableForGivenCaptureID(
                    job.getCaptureID(), job.getProcessedEventCount()))
        .chain(
            eventCount -> {
              log.debug(
                  "updating visible field value to true for events with captureID = {}",
                  captureJob.getCaptureID());
              return epcisEventPersistenceService.updateEventVisibility(
                  job.getCaptureID(), job.getCreatedAt(), job.getDefaultGroup());
            })
        .chain(
            success -> {
              if (success.equals(Boolean.FALSE)) {
                return Uni.createFrom()
                    .failure(
                        new PersistenceException(
                            String.format(
                                "unable to update event visibility for captureID = %s",
                                job.getCaptureID())));
              }
              return epcisEventPersistenceService
                  .getEventIdsByCaptureId(job.getCaptureID())
                  .onItem()
                  .transformToUni(
                      eventID -> {
                        log.debug("producing event to event-saved topic with eventID= {}", eventID);
                        final EventSavedMessage eventSavedMessage =
                            EventSavedMessage.builder().eventID(eventID).build();
                        eventSavedMessage.setTraceId(job.getTraceId());
                        eventSavedMessage.setSpanId(job.getSpanId());
                        eventSavedMessage.setDefaultGroup(job.getDefaultGroup());
                        log.debug("Sending message for event with eventID: {}", eventID);
                        return epcisEventPersistenceService
                            .produceMessageToEventSavedTopic(eventSavedMessage)
                            .chain(v -> Uni.createFrom().item(eventID));
                      })
                  .merge()
                  .collect()
                  .asList()
                  .chain(l -> Uni.createFrom().item(job));
            })
        // Retry up to 3 times with exponential backoff (1s, 2s, 4s)
        .onFailure()
        .retry()
        .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(4))
        .atMost(3)
        // After 3 failed attempts, log error and skip (return job without failing)
        .onFailure()
        .recoverWithUni(
            failure -> {
              log.error(
                  "Failed to produce event-saved messages for captureID = {} after 3 retries. "
                      + "Skipping to avoid blocking stream processing. Error: {}",
                  job.getCaptureID(),
                  failure.getMessage());
              // Mark job as failed but don't throw exception to avoid blocking Kafka Streams
              job.setSuccess(false);
              job.getErrors()
                  .add(
                      new io.openepcis.model.dto.InvalidEPCISEventInfo(
                          "urn:epcisci:error:event-saved-production-failed",
                          "Event Saved Production Failed",
                          500,
                          "Failed to produce event-saved messages after 3 retries: "
                              + failure.getMessage()));
              return Uni.createFrom().item(job);
            });
  }
}
