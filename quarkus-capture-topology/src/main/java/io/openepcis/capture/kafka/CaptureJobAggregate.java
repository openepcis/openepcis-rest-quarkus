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
import io.openepcis.model.dto.CaptureJobStatusMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * The capture job as the topology aggregates it: the status plus the documents its events were
 * stored as. The list is bounded; once an event's document is unknown or the bound is reached it
 * is dropped and marked incomplete, and the job completes by search. Read as a
 * {@link CaptureJobStatusMessage} the extra fields are ignored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaptureJobAggregate extends CaptureJobStatusMessage {

  private List<String> documents = new ArrayList<>();
  private boolean documentsIncomplete;

  public CaptureJobAggregate() {
    super();
  }

  /** Adds the document of a processed event, within the bound. */
  public void collect(final String document, final int maxDocuments) {
    if (documentsIncomplete) {
      return;
    }
    if (document == null || document.isBlank() || documents.size() >= maxDocuments) {
      documentsIncomplete = true;
      documents = new ArrayList<>();
      return;
    }
    documents.add(document);
  }

  /** Whether every processed event is known by its document. */
  public boolean completeByDocuments() {
    return !documentsIncomplete && !documents.isEmpty() && documents.size() == getProcessedEventCount();
  }

  public List<String> getDocuments() {
    return documents;
  }

  public void setDocuments(final List<String> documents) {
    this.documents = documents == null ? new ArrayList<>() : new ArrayList<>(documents);
  }

  public boolean isDocumentsIncomplete() {
    return documentsIncomplete;
  }

  public void setDocumentsIncomplete(final boolean documentsIncomplete) {
    this.documentsIncomplete = documentsIncomplete;
  }
}
