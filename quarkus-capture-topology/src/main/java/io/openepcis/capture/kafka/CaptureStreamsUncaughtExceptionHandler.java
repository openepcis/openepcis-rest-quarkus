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

import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

/**
 * Replaces a stream thread that died instead of shutting the client down. Without it Kafka
 * Streams goes to ERROR on the first uncaught exception, health reports DOWN, and capture stops
 * for everyone until a restart. Quarkus picks this bean up when it builds the KafkaStreams.
 */
@ApplicationScoped
public class CaptureStreamsUncaughtExceptionHandler implements StreamsUncaughtExceptionHandler {

  private static final OpenEPCISLogger log =
      OpenEPCISLogger.getLogger(CaptureStreamsUncaughtExceptionHandler.class);

  @Override
  public StreamThreadExceptionResponse handle(final Throwable exception) {
    log.error("Kafka Streams thread died, replacing it", exception);
    return StreamThreadExceptionResponse.REPLACE_THREAD;
  }
}
