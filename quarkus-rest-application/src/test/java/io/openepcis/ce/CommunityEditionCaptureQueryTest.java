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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * The Community Edition end to end, on one class path with nothing but Community Edition
 * artifacts: a caller without any credentials captures a document, the pipeline validates and
 * stores it, the query interface answers with it, in EPCIS 2.0 JSON and in EPCIS 1.2 XML.
 */
@QuarkusTest
@QuarkusTestResource(OpenSearchTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@EnabledIf("io.openepcis.ce.CommunityEditionCaptureQueryTest#dockerAvailable")
class CommunityEditionCaptureQueryTest {

  private static final String EPC = "urn:epc:id:sgtin:0614141.107346.2017";
  private static final String OTHER_EPC = "urn:epc:id:sgtin:0614141.107346.2018";
  private static final String DOCUMENT =
      """
      {
        "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
        "type": "EPCISDocument",
        "schemaVersion": "2.0",
        "creationDate": "2026-10-21T09:00:00.000Z",
        "epcisBody": {
          "eventList": [
            {
              "type": "ObjectEvent",
              "eventTime": "2026-10-21T08:00:00.000+01:00",
              "eventTimeZoneOffset": "+01:00",
              "epcList": ["%s"],
              "action": "OBSERVE",
              "bizStep": "shipping",
              "disposition": "in_transit",
              "readPoint": {"id": "urn:epc:id:sgln:0614141.00777.0"}
            }
          ]
        }
      }
      """
          .formatted(EPC);

  /**
   * Asks the Docker CLI, in plain Java on purpose: a Testcontainers call here would load its
   * classes outside the Quarkus test class loader and clash with the resources above.
   */
  static boolean dockerAvailable() {
    try {
      final Process docker =
          new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
      docker.getInputStream().readAllBytes();
      return docker.waitFor(30, TimeUnit.SECONDS) && docker.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Test
  @DisplayName("no credentials needed: every caller is admin")
  void everyCallerIsAdmin() {
    given().when().get("/events").then().statusCode(200);
    given().when().get("/capture").then().statusCode(200);
  }

  @Test
  @DisplayName("a captured document is queryable, in 2.0 JSON and in 1.2 XML")
  void captureThenQuery() {
    awaitReadiness();
    final String location =
        given()
            .contentType(ContentType.JSON)
            .body(DOCUMENT)
            .when()
            .post("/capture")
            .then()
            .statusCode(202)
            .extract()
            .header("Location");
    assertTrue(location != null && location.contains("/capture/"), "Location: " + location);
    final String captureId = location.substring(location.lastIndexOf('/') + 1);

    // the event in the store is the proof of the pipeline; the job record follows it
    awaitEvent();
    awaitCaptureJob(captureId);

    given()
        .accept(ContentType.XML)
        .header("GS1-EPCIS-Version", "1.2.0")
        .when()
        .get("/events")
        .then()
        .statusCode(200)
        .body(containsString("urn:epcglobal:epcis-query:xsd:1"))
        .body(containsString(EPC))
        .body(not(containsString("urn:epcglobal:epcis:xsd:2")));
  }

  @Test
  @DisplayName("an unnamed event query answers as SimpleEventQuery (EPCIS 2.0 requires queryName)")
  void unnamedQueryHasQueryName() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/events")
        .then()
        .statusCode(200)
        .body(containsString("\"queryName\":\"SimpleEventQuery\""));
  }

  @Test
  @DisplayName("a document with a non-GS1 epcClass fails its job and capture goes on")
  void rejectedDocumentDoesNotStopCapture() {
    awaitReadiness();
    final String poison =
        DOCUMENT
            .replace("\"epcList\": [\"" + EPC + "\"]",
                "\"quantityList\": [{\"epcClass\": \"not-a-gs1-class\", \"quantity\": 1}]");
    final var response = given().contentType(ContentType.JSON).body(poison).when().post("/capture");
    if (response.statusCode() == 202) {
      final String location = response.header("Location");
      final var job = finishedJob(location.substring(location.lastIndexOf('/') + 1));
      assertTrue(!job.getBoolean("success"), "the job of the rejected document succeeded: " + job.prettify());
    } else {
      assertTrue(response.statusCode() == 400, "rejected with " + response.statusCode());
    }

    final String next = DOCUMENT.replace(EPC, OTHER_EPC);
    final String location =
        given()
            .contentType(ContentType.JSON)
            .body(next)
            .when()
            .post("/capture")
            .then()
            .statusCode(202)
            .extract()
            .header("Location");
    awaitCaptureJob(location.substring(location.lastIndexOf('/') + 1));
    given().when().get("/q/health").then().statusCode(200).body(containsString("RUNNING"));
  }

  /**
   * Readiness alone answers before the Kafka Streams topology runs, and a document captured in
   * that window is counted but never finished. The health report names the streams state, so
   * wait for RUNNING.
   */
  private static void awaitReadiness() {
    final Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
    while (true) {
      final var health = given().when().get("/q/health");
      if (health.statusCode() == 200 && health.asString().contains("RUNNING")) {
        return;
      }
      assertTrue(
          Instant.now().isBefore(deadline),
          "Kafka Streams not RUNNING after 120 s: " + health.asString());
      sleep();
    }
  }

  private static void awaitCaptureJob(final String captureId) {
    final var job = finishedJob(captureId);
    assertTrue(job.getBoolean("success"), "capture job failed: " + job.prettify());
  }

  /** The capture job once it is no longer running. */
  private static io.restassured.path.json.JsonPath finishedJob(final String captureId) {
    final Instant deadline = Instant.now().plus(Duration.ofSeconds(180));
    while (true) {
      final var response =
          given().when().get("/capture/" + captureId);
      if (response.statusCode() != 200) {
        // the job record appears once the pipeline has taken the document up
        assertTrue(Instant.now().isBefore(deadline), "capture job not found after 180 s");
        sleep();
        continue;
      }
      final var job = response.jsonPath();
      if (!job.getBoolean("running")) {
        return job;
      }
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError(
            "capture job still running after 180 s: " + job.prettify() + "\n" + topicDump());
      }
      sleep();
    }
  }

  private static void awaitEvent() {
    final Instant deadline = Instant.now().plus(Duration.ofSeconds(180));
    while (true) {
      final String body =
          given()
              .accept(ContentType.JSON)
              .when()
              .get("/events")
              .then()
              .statusCode(200)
              .extract()
              .asString();
      if (body.contains(EPC)) {
        return;
      }
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("event not queryable after 180 s: " + body + "\n" + topicDump());
      }
      sleep();
    }
  }

  /** What the pipeline topics hold, for the failure message: key, topic and value of each record. */
  private static String topicDump() {
    final String bootstrap =
        org.eclipse.microprofile.config.ConfigProvider.getConfig()
            .getValue("kafka.bootstrap.servers", String.class);
    final java.util.Properties props = new java.util.Properties();
    props.put("bootstrap.servers", bootstrap);
    props.put("group.id", "ce-test-dump-" + System.nanoTime());
    props.put("auto.offset.reset", "earliest");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    final StringBuilder out = new StringBuilder();
    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(props)) {
      consumer.subscribe(
          java.util.List.of("capture-documents", "capture-document-event", "capture-documents-agg", "epcis-event-persisted"));
      final Instant until = Instant.now().plus(Duration.ofSeconds(10));
      while (Instant.now().isBefore(until)) {
        for (var record : consumer.poll(Duration.ofSeconds(1))) {
          out.append(record.topic()).append(" key=").append(record.key()).append(" value=")
              .append(record.value() == null ? "null" : record.value().substring(0, Math.min(300, record.value().length())))
              .append('\n');
        }
      }
    } catch (RuntimeException e) {
      out.append("topic dump failed: ").append(e);
    }
    return out.toString();
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
