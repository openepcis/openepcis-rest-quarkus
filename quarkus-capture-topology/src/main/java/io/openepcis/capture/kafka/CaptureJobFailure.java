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

import io.openepcis.model.dto.InvalidEPCISEventInfo;
import java.time.OffsetDateTime;
import java.util.ArrayList;

/** Ends a capture job as failed when its status could not be aggregated. */
final class CaptureJobFailure {

  static final String ERROR_TYPE = "urn:epcisci:error:capture-aggregation-failed";

  private CaptureJobFailure() {}

  /**
   * Marks the job finished and failed, with the cause among its errors. The job is returned so
   * the aggregate stays consistent and later status messages for it are ignored.
   */
  static CaptureJobAggregate markFailed(final CaptureJobAggregate job, final Throwable failure) {
    if (job.getErrors() == null) {
      job.setErrors(new ArrayList<>());
    }
    final Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
    job.getErrors()
        .add(
            new InvalidEPCISEventInfo(
                ERROR_TYPE,
                "Capture Aggregation Failed",
                500,
                cause.getClass().getSimpleName() + ": " + cause.getMessage()));
    job.setSuccess(false);
    job.setRunning(false);
    if (job.getFinishedAt() == null) {
      job.setFinishedAt(OffsetDateTime.now());
    }
    job.setDocuments(null);
    return job;
  }
}
