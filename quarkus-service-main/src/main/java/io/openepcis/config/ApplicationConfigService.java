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
package io.openepcis.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import java.time.temporal.ChronoUnit;
import java.util.List;

@StaticInitSafe
@ConfigMapping(prefix = "app")
public interface ApplicationConfigService {

  GS1 gs1();

  Http http();

  Event event();

  RateLimit ratelimit();

  ErrorDeclarationEventRetriever errorDeclarationEventRetriever();

  Subscription subscription();

  Roles roles();

  interface GS1 {
    Vendor vendor();

    CBV cbv();

    interface Vendor {
      String[] jsonLdKnownConstructs();

      String version();
    }

    interface CBV {
      String version();

      String min();

      String max();
    }
  }

  interface Http {
    Integer connectTimeoutInSeconds();
  }

  interface Event {
    HashGenerator hashgenerator();

    interface HashGenerator {
      String url();
    }

    String eventAccessRole();
  }

  interface ErrorDeclarationEventRetriever {
    String count();
  }

  interface Subscription {
    int streamingInterval();

    int perPage();
  }

  interface Roles {
    List<String> filter();
  }

  interface RateLimit {
    List<DefaultRoles> defaultroles();

    interface DefaultRoles {
      // Name of the default role/group associated to users for which rateLimit is applied
      String role();

      // Number of permitted api limit for the particular role/group
      int permittedUses();

      // Duration of time user need to wait for next api request after exhausting their limits
      int timeDuration();

      // Duration of the unit based on which api rate limit is refreshed in ChronoUnit MINUTES,
      // SECONDS, HOURS, etc.
      ChronoUnit durationUnit();

      // To share the same bucket with multiple endpoints. true if capture & query share the same
      // bucket and false if separate bucket limits for capture & query
      Boolean shared();
    }
  }
}
