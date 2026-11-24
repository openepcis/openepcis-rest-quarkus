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
package io.openepcis.ratelimit.exception;

import io.github.bucket4j.ConsumptionProbe;

/**
 * Custom exception class to override the RuntimeException. Used to throw the exception if the user
 * exceeds the allowed number of API request provided to them based on the roles in application.yml
 * file
 */
public class RateLimitExceededException extends RuntimeException {
  private final transient ConsumptionProbe consumptionProbe;
  private final long permittedUses;
  private final String urlEndpoint;

  public RateLimitExceededException(final String exceptionMessage, final ConsumptionProbe consumptionProbe, final long permittedUses, final String urlEndpoint) {
    super(exceptionMessage);
    this.consumptionProbe = consumptionProbe;
    this.permittedUses = permittedUses;
    this.urlEndpoint = urlEndpoint;
  }

  public ConsumptionProbe getConsumptionProbe() {
    return this.consumptionProbe;
  }

  public long getPermittedUses() {
    return this.permittedUses;
  }

  public String getUrlEndpoint() {
    return this.urlEndpoint;
  }
}
