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

import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.constants.CommonConstants;
import java.util.List;

/**
 * The error a capture job reports for an event whose processing threw. An identifier the
 * translator refuses is a defect in the document, so it is a 400 like any validation error; the
 * rest is a 500. The stack trace stays in the log, the job carries the message.
 */
final class EventFailure {

  private EventFailure() {}

  static InvalidEPCISEventInfo of(final Throwable failure, final int eventIndex) {
    if (failure instanceof ValidationException) {
      return new InvalidEPCISEventInfo(
          CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
          CommonConstants.VALIDATION_FAILED,
          400,
          failure.getMessage(),
          List.of(eventIndex));
    }
    return new InvalidEPCISEventInfo(
        failure.getClass().getSimpleName(),
        "Event processing failed",
        500,
        failure.getMessage(),
        List.of(eventIndex));
  }
}
