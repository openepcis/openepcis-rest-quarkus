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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A failure while aggregating one capture job ends that job, not the stream. Before, the
 * exception escaped onto the Kafka Streams thread and every later capture stayed running.
 */
class CaptureJobFailureTest {

  @Test
  @DisplayName("a job whose aggregation failed is finished, failed and carries the cause")
  void failedJobIsFinished() {
    final CaptureJobAggregate job = new CaptureJobAggregate();
    job.setRunning(true);
    job.setSuccess(true);
    job.setErrors(null);

    final CaptureJobAggregate result =
        CaptureJobFailure.markFailed(
            job,
            new CompletionException(
                new IllegalStateException("unable to update event visibility for captureID = x")));

    assertFalse(result.isRunning());
    assertFalse(result.isSuccess());
    assertNotNull(result.getFinishedAt());
    assertEquals(1, result.getErrors().size());
    assertEquals(CaptureJobFailure.ERROR_TYPE, result.getErrors().get(0).getType());
    assertTrue(result.getErrors().get(0).getDetail().contains("unable to update event visibility"));
  }

  @Test
  @DisplayName("the uncaught-exception handler replaces the thread instead of stopping the client")
  void handlerReplacesThread() {
    assertEquals(
        org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
            .StreamThreadExceptionResponse.REPLACE_THREAD,
        new CaptureStreamsUncaughtExceptionHandler().handle(new RuntimeException("boom")));
  }
}
