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
package io.openepcis.quarkus;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Enables percentile histograms for the HTTP server timer so Prometheus
 * gets `_bucket` series and Grafana can compute p50/p95/p99 via
 * `histogram_quantile`. The
 * `quarkus.micrometer.binder.http-server.percentiles-histogram` config
 * property was removed in Quarkus 3.27, so a MeterFilter bean is now the
 * canonical knob.
 *
 * Class-level `@Singleton` is the bean-defining annotation Quarkus's
 * "annotated" discovery mode requires; a plain `@Produces` method is
 * picked up by the micrometer extension at registry initialization.
 */
@Singleton
public class MetricsConfig {

  @Produces
  public MeterFilter httpServerPercentileHistograms() {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (!"http.server.requests".equals(id.getName())) {
          return config;
        }
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            // Explicit nanosecond range so Quarkus's Vert.x metrics
            // integration always sees a valid SLO range — without these
            // the previous attempt silently dropped the meter entirely.
            .minimumExpectedValue(1_000_000.0)        // 1 ms
            .maximumExpectedValue(30_000_000_000.0)   // 30 s
            .build()
            .merge(config);
      }
    };
  }
}
