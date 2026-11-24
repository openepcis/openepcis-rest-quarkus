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

import static io.openepcis.model.epcis.exception.ExceptionMessages.ERROR_WHILE_PERSISTING_EVENT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.EPCISEventObjectNodeUtil;
import io.openepcis.capture.context.StorageService;
import io.openepcis.capture.context.message.EPCISValidationMessage;
import io.openepcis.capture.service.EPCISEventPersistenceService;
import io.openepcis.model.dto.CaptureStatusMessage;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.service.util.Constants;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

/**
 * Reactive-messaging persistence stage for validated EPCIS events.
 *
 * <p>This replaces the former Kafka Streams {@code persistenceStream} map, which called the
 * reactive persistence service with a fire-and-forget {@code .subscribe()} on the synchronous
 * stream thread. That pattern had no back-pressure, no bounded completion and no offset coupling:
 * a hung persistence {@code Uni} leaked a worker forever, exhausted the OpenSearch client pool and
 * pushed the topology into an endless poll-timeout rebalance loop where offsets never committed and
 * events were never indexed.
 *
 * <p>Here each message is processed by a SmallRye Reactive Messaging pipeline instead:
 *
 * <ul>
 *   <li>{@code @Blocking(ordered = false)} runs the handler on a bounded worker pool (never the
 *       Vert.x event loop), so the blocking storage read / JSON parse and the bounded persistence
 *       await are safe and naturally back-pressured by the pool size.
 *   <li>The incoming message is acknowledged only when the handler returns, and the outgoing
 *       {@code epcis-event-persisted} record is produced from the return value — so completion is
 *       coupled to the message, and a definitive failure is routed to the channel's configured
 *       dead-letter queue rather than reprocessed forever.
 *   <li>Every reactive call is bounded ({@link #PERSIST_TIMEOUT} + retry with backoff, capped by
 *       {@link #AWAIT_CEILING}) so it can never hang indefinitely.
 * </ul>
 *
 * The capture-job counting semantics are preserved exactly: every event still emits a valid/invalid
 * {@link CaptureStatusMessage} on {@code capture-document-event} and a processed marker on
 * {@code epcis-event-persisted}.
 */
@ApplicationScoped
public class EventPersistenceProcessor {

  private static final OpenEPCISLogger log =
      OpenEPCISLogger.getLogger(EventPersistenceProcessor.class);

  // Every reactive side effect must terminate within a bounded window: a per-attempt no-item
  // timeout, bounded retries with backoff, and a hard await ceiling as the final backstop.
  private static final Duration PERSIST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
  private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(5);
  private static final long PERSIST_MAX_RETRIES = 3;
  // Backstop >= worst-case (timeout * (retries + 1) + backoff); the inner ifNoItem/retry normally
  // terminates first, so this only fires if the reactive chain itself never settles.
  private static final Duration AWAIT_CEILING =
      PERSIST_TIMEOUT.multipliedBy(PERSIST_MAX_RETRIES + 2);

  @Inject ObjectMapper objectMapper;
  @Inject Tracer tracer;
  @Inject StorageService storageService;
  @Inject EPCISEventPersistenceService epcisEventPersistenceService;

  // Emits the per-event valid/invalid status onto capture-document-event (drives job counting).
  @Channel("capture-document-event-out")
  @OnOverflow(OnOverflow.Strategy.UNBOUNDED_BUFFER)
  Emitter<Record<String, CaptureStatusMessage>> captureStatusMessageEmitter;

  @Incoming("epcis-event-validated-success-in")
  @Outgoing("epcis-event-persisted-out")
  @Blocking(value = "event-persistence-pool", ordered = false)
  public Record<String, EPCISValidationMessage> persist(
      final Record<String, EPCISValidationMessage> record) {
    final String captureID = record.key();
    final EPCISValidationMessage eventValidationMessage = record.value();

    final SpanContext spanContext =
        SpanContext.createFromRemoteParent(
            eventValidationMessage.getTraceId(),
            eventValidationMessage.getSpanId(),
            TraceFlags.getSampled(),
            TraceState.getDefault());
    final Context context = Context.current().with(Span.wrap(spanContext));
    final Span span =
        tracer.spanBuilder("epcis-event-persistence").setParent(context).startSpan();

    try (Scope scope = span.makeCurrent()) {
      final ObjectNode singleEventNode;
      // If event is above 4kb and stored in S3 then get it from storage; otherwise read the inline
      // objectNodeString. Both are blocking, which is safe here because @Blocking runs this handler
      // off the event loop on the event-persistence worker pool.
      if (StringUtils.isBlank(eventValidationMessage.getObjectNodeString())) {
        Log.info("Getting the event with Hash >4kb from StorageService for persisting");
        singleEventNode =
            (ObjectNode)
                objectMapper.readTree(
                    storageService.get(eventValidationMessage.getEventStorageKey()));
      } else {
        Log.info("Event with Hash <4kb from DocumentCaptureMessage objectNodeString");
        singleEventNode =
            (ObjectNode) objectMapper.readTree(eventValidationMessage.getObjectNodeString());
      }

      final Pair<ObjectNode, Map<String, Object>> eventAndContextPair =
          EPCISEventObjectNodeUtil.extractEventAndContextNodeFromDocument(
              singleEventNode, objectMapper);
      final Map<String, Object> contextAsMap = eventAndContextPair.getValue();
      final ObjectNode eventNode = eventAndContextPair.getKey();
      final List<InvalidEPCISEventInfo> invalidEventsInfo =
          new ArrayList<>(eventValidationMessage.getErrors());

      // An error declaration is stored as an event of its own, next to the event it declares
      // erroneous. EPCIS 2.0 7.4.1.2: "The prior event is not modified in any way, and subsequent
      // queries will return both the prior event and the error declaration." This used to delete
      // the prior event first, across all tenants; it only ever missed because the eventID was
      // passed with its JSON quotes.

      // For proceed we ignore invalid events, and just skip persisting this one.
      final boolean skipPersist =
          eventValidationMessage.isProceed()
              && !invalidEventsInfo.isEmpty()
              && invalidEventsInfo.stream()
                  .flatMap(info -> info.getSequenceInEPCISDoc().stream())
                  .anyMatch(i -> i == eventValidationMessage.getEventIndex());

      String document = null;
      if (skipPersist) {
        log.debug(
            "Invalid event at index: {}, proceeding to next one ",
            eventValidationMessage.getEventIndex());
      } else if (!(eventValidationMessage.isProceed() && !invalidEventsInfo.isEmpty())) {
        Log.info("Persisting the event to OpenSearch");
        try {
          final io.openepcis.model.epcis.EPCISEvent persisted =
              epcisEventPersistenceService
              .persistEvent(
                  singleEventNode,
                  eventNode,
                  eventValidationMessage.getCaptureID(),
                  contextAsMap,
                  invalidEventsInfo,
                  eventValidationMessage.isProceed(),
                  eventValidationMessage.getEventIndex(),
                  eventValidationMessage.getMetadata(),
                  false,
                  eventValidationMessage.getDefaultGroup())
              // Bounded so a hung OpenSearch/S3 write can never hang the worker; retry transient
              // failures with backoff, then surface a definitive failure to the catch below.
              .ifNoItem()
              .after(PERSIST_TIMEOUT)
              .failWith(
                  () ->
                      new PersistenceException(
                          "Event persistence timed out after " + PERSIST_TIMEOUT))
              // A rejected event (an identifier that is not GS1, for one) fails the same way on
              // every attempt; only failures that left no rejection behind are retried.
              .onFailure(failure -> invalidEventsInfo.isEmpty())
              .retry()
              .withBackOff(RETRY_MIN_BACKOFF, RETRY_MAX_BACKOFF)
              .atMost(PERSIST_MAX_RETRIES)
              .onFailure()
              .transform(failure -> new PersistenceException(ERROR_WHILE_PERSISTING_EVENT, failure))
              .await()
              .atMost(AWAIT_CEILING);
          document =
              epcisEventPersistenceService
                  .documentOf(persisted, eventValidationMessage.getMetadata())
                  .orElse(null);

          Log.info(" ✅ Event persistence was successful!!! ✅ ");
          emitStatus(
              captureID, CaptureStatusMessage.valid(1, eventValidationMessage.getMetadata()),
              eventValidationMessage);
        } catch (Exception persistFailure) {
          Log.info(" ❌ Failure occurred during event persistence!!! ❌ ");
          log.error("Event persistence failed for captureID: {}", captureID, persistFailure);
          span.recordException(persistFailure);
          emitStatus(
              captureID,
              CaptureStatusMessage.invalid(1, invalidEventsInfo, eventValidationMessage.getMetadata()),
              eventValidationMessage);
        }
      }

      final EPCISValidationMessage msg = new EPCISValidationMessage();
      msg.updateFrom(eventValidationMessage, invalidEventsInfo);
      msg.setDocument(document);
      log.debug("persistence result for {} = {} ", eventValidationMessage.getCaptureID(), msg);
      return Record.of(captureID, msg);
    } catch (Exception e) {
      // Unexpected (parse/extraction) failure — mirror the previous behaviour: emit a processed
      // marker carrying the error so the capture job still completes (as failed) rather than stall.
      span.recordException(e);
      log.error(" {}", e.getMessage(), e);
      final EPCISValidationMessage exceptionMessage = new EPCISValidationMessage();
      exceptionMessage.updateFrom(
          eventValidationMessage,
          List.of(
              new InvalidEPCISEventInfo(
                  e.getClass().getSimpleName(),
                  e.getMessage(),
                  500,
                  List.of(e.getStackTrace()).toString(),
                  List.of(eventValidationMessage.getEventIndex()))));
      return Record.of(captureID, exceptionMessage);
    } finally {
      span.end();
    }
  }

  private void emitStatus(
      final String captureID,
      final CaptureStatusMessage captureStatusMessage,
      final EPCISValidationMessage eventValidationMessage) {
    captureStatusMessage.setTraceId(eventValidationMessage.getTraceId());
    captureStatusMessage.setSpanId(eventValidationMessage.getSpanId());
    captureStatusMessage.setDefaultGroup(eventValidationMessage.getDefaultGroup());
    captureStatusMessageEmitter.send(Record.of(captureID, captureStatusMessage));
  }
}
