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

import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a capture job reports for an event whose processing threw. */
class EventFailureTest {

  @Test
  @DisplayName("an identifier the translator refuses is a 400 validation error")
  void refusedIdentifierIsBadRequest() {
    final InvalidEPCISEventInfo info =
        EventFailure.of(
            new ValidationException(
                "Invalid LGTIN, LGTIN should be of 14 digits: urn:epc:class:lgtin:952012.LOT2026A"),
            3);

    assertEquals(400, info.getStatus());
    assertEquals("Validation failed", info.getTitle());
    assertEquals(
        "Invalid LGTIN, LGTIN should be of 14 digits: urn:epc:class:lgtin:952012.LOT2026A",
        info.getDetail());
    assertEquals(List.of(3), info.getSequenceInEPCISDoc());
  }

  @Test
  @DisplayName("any other failure is a 500 with its message and without a stack trace")
  void otherFailureIsServerError() {
    final InvalidEPCISEventInfo info =
        EventFailure.of(new IllegalStateException("storage unavailable"), 0);

    assertEquals(500, info.getStatus());
    assertEquals("IllegalStateException", info.getType());
    assertEquals("storage unavailable", info.getDetail());
    assertFalse(info.getDetail().contains(".java:"));
  }
}
