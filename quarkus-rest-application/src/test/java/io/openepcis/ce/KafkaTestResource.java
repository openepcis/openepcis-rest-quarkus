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
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.kafka.KafkaContainer;

/**
 * One Kafka in KRaft mode with the topics the capture pipeline reads and writes. The service
 * creates no topics itself; the Kafka Streams topology waits for its input topic to exist, so
 * they are laid out here before the application starts, as the compose file of the public
 * distribution does.
 */
public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

  static final List<String> TOPICS =
      List.of(
          "epcis-event-captured",
          "capture-documents",
          "capture-documents-agg",
          "capture-document-event",
          "capture-document-event-count",
          "capture-document-event-error",
          "epcis-event-validated",
          "epcis-event-validated-success",
          "epcis-event-validated-failure",
          "epcis-event-persisted",
          "event-saved",
          "websocket-subscription",
          "streaming-subscription");

  private KafkaContainer container;

  @Override
  public Map<String, String> start() {
    container = new KafkaContainer("apache/kafka:3.8.0");
    container.start();
    final String bootstrap = container.getBootstrapServers();
    try (AdminClient admin =
        AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
      admin
          .createTopics(TOPICS.stream().map(t -> new NewTopic(t, 1, (short) 1)).toList())
          .all()
          .get();
      // A fresh KRaft broker elects the leaders a moment after creation; a consumer that
      // subscribes before that backs off for a long while. Wait until every partition leads.
      final Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
      while (true) {
        final boolean allLed =
            admin.describeTopics(TOPICS).allTopicNames().get().values().stream()
                .allMatch(d -> d.partitions().stream().allMatch(p -> p.leader() != null));
        if (allLed) {
          break;
        }
        if (Instant.now().isAfter(deadline)) {
          throw new IllegalStateException("Kafka topics have no leader after 60 s");
        }
        Thread.sleep(500);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while creating the Kafka topics", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("could not create the Kafka topics", e);
    }
    // The Kafka Streams state directory defaults to the OS temp directory and outlives the
    // test; a checkpoint of the global store from an earlier run does not fit a fresh broker,
    // and the global thread either dies or joins against stale state.
    final String stateDir;
    try {
      stateDir = Files.createTempDirectory("ce-kafka-streams").toString();
    } catch (IOException e) {
      throw new IllegalStateException("could not create the Kafka Streams state directory", e);
    }
    return Map.of(
        "quarkus.kafka.devservices.enabled", "false",
        "kafka.bootstrap.servers", bootstrap,
        "kafka-streams.state.dir", stateDir);
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
  }
}
