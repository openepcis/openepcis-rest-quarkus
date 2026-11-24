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
package io.openepcis.ce;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** One OpenSearch without its security plugin, as the Community Edition expects it. */
public class OpenSearchTestResource implements QuarkusTestResourceLifecycleManager {

  private GenericContainer<?> container;

  @Override
  public Map<String, String> start() {
    container =
        new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.19.2"))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(
                Wait.forHttp("/_cluster/health")
                    .forPort(9200)
                    .forStatusCodeMatching(code -> code >= 200 && code < 300));
    container.start();
    final String hosts = container.getHost() + ":" + container.getMappedPort(9200);
    return Map.ofEntries(
        // the pipeline's debug log is the only account of where a document went
        Map.entry("quarkus.log.category.\"io.openepcis.capture\".level", "DEBUG"),
        Map.entry("quarkus.opensearch.devservices.enabled", "false"),
        Map.entry("quarkus.opensearch.hosts", hosts),
        Map.entry("quarkus.opensearch.protocol", "http"),
        Map.entry("quarkus.opensearch.username", ""),
        Map.entry("quarkus.opensearch.password", ""),
        Map.entry("quarkus.opensearch.\"admin\".hosts", hosts),
        Map.entry("quarkus.opensearch.\"admin\".protocol", "http"),
        Map.entry("quarkus.opensearch.\"admin\".username", ""),
        Map.entry("quarkus.opensearch.\"admin\".password", ""));
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
  }
}
