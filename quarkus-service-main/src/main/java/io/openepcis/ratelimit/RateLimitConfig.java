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
package io.openepcis.ratelimit;

import java.time.temporal.ChronoUnit;

public class RateLimitConfig {
  private final int permittedUses;
  private final int timeDuration;
  private final ChronoUnit durationType;
  private final Boolean shared;

  public RateLimitConfig(final int permittedUses, final int timeDuration, final ChronoUnit durationType, final Boolean shared) {
    this.permittedUses = permittedUses;
    this.timeDuration = timeDuration;
    this.durationType = durationType;
    this.shared = shared;
  }

  public int getPermittedUses() {
    return this.permittedUses;
  }

  public int getTimeDuration() {
    return this.timeDuration;
  }

  public ChronoUnit getDurationType() {
    return this.durationType;
  }

  public Boolean getShared() {
    return this.shared;
  }

  @Override
  public String toString() {
    return "RateLimitConfig(permittedUses=" + this.getPermittedUses() + ", timeDuration=" + this.getTimeDuration() + ", durationType=" + this.getDurationType() + ", shared=" + this.getShared() + ")";
  }
}
