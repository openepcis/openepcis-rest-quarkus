<p align="center">
  <img src="https://openepcis.io/img/openepcis-logo.svg" alt="OpenEPCIS" width="30%">
</p>

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Build](https://github.com/openepcis/openepcis-rest-quarkus/actions/workflows/maven.yml/badge.svg)](https://github.com/openepcis/openepcis-rest-quarkus/actions/workflows/maven.yml)

<h1 align="center">OpenEPCIS REST Quarkus</h1>

The EPCIS 2.0 REST application of [OpenEPCIS](https://openepcis.io): the resources, the Kafka
Streams capture pipeline and the Community Edition application, on top of
[openepcis-core](https://github.com/openepcis/openepcis-core).
[epcis-repository-ce](https://github.com/openepcis/epcis-repository-ce) runs it as one container.

## Modules

| Module | What it does |
|---|---|
| `quarkus-service-main` | The EPCIS 2.0 REST resources: capture, events, named queries, subscriptions, WebSocket delivery, rate limiting, EPCIS 1.2 XML on request |
| `quarkus-capture-topology` | The Kafka Streams pipeline: validation, persistence, capture job status |
| `quarkus-rest-application` | The Community Edition application and its `application.yml`, with the end-to-end test |

## Editions

The Community Edition is for trying OpenEPCIS out, not for production. It has no
authentication: `OpenAccessAuthenticationMechanism` makes every request an administrator with
the roles capture, query and admin, so anyone who can reach the service can read, write and
delete everything. It is single-tenant, has no document-level security, and OpenSearch runs
without its security plugin. EPCIS 1.2 XML is written with XSLT.

Authentication, multi-tenancy, API keys, capability tokens, onboarding, the files server and all
checks beyond EPCIS and the configured rules are the Business edition, which builds on these
artifacts; the Maven enforcer in this reactor refuses the reverse dependency.

## Build

```bash
mvn -B clean verify
```

Java 25. The end-to-end test starts OpenSearch and Kafka with Testcontainers and is skipped
without Docker. Nothing resolves from anywhere but Maven Central and its snapshot repository.

## License

Apache License 2.0, see [LICENSE](LICENSE).
