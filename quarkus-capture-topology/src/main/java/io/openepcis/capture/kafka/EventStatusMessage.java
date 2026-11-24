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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.openepcis.model.dto.CaptureStatusMessage;
import java.util.ArrayList;
import java.util.Map;

/**
 * A capture status message that can name the document a persisted event is stored as
 * ({@code index/_id}). The job completes by these documents instead of searching for its events.
 * Without one it reads as a plain {@link CaptureStatusMessage}, in both directions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventStatusMessage extends CaptureStatusMessage {

  private String document;

  public EventStatusMessage() {
    super();
  }

  public static EventStatusMessage processed(final String document, final Map<String, Object> metadata) {
    final EventStatusMessage message = new EventStatusMessage();
    message.setType(Type.PROCESSED);
    message.setValue(1);
    message.setErrors(new ArrayList<>());
    message.setMetadata(metadata);
    message.setDocument(document);
    return message;
  }

  public String getDocument() {
    return document;
  }

  public void setDocument(final String document) {
    this.document = document;
  }
}
