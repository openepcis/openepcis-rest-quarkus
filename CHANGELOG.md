# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), the versions follow the OpenEPCIS
platform release.

## [Unreleased]

### Removed
- The OpenEPCIS extensions leave the Community Edition, which now offers the EPCIS 2.0 REST
  binding and nothing else: `GET /events/aggregate`, `POST /events/validate`,
  `PUT /queries/{queryName}` and `/userExtension/jsonSchema` (`POST`, `PUT`, `/url`). They are
  part of the Business edition. The aggregation itself stays in `openepcis-query`.

### Added
- `NonStandardOperationsOASFilter`: every operation that the GS1 EPCIS 2.0 REST binding does not
  define is marked in the OpenAPI document, with "(non-standard)" in its summary, a note in its
  description and `x-openepcis-non-standard: true`. Decided by the standard's operation list, so
  an edition's own endpoints are marked without anyone remembering to.
- `ResourceExtension`: a resource that adds a method to a path another resource serves names
  that resource, and the `Allow` header of a discovery request on the path includes the method.

## [0.9.4]

First public release under Apache-2.0, as the REST application of the OpenEPCIS Community
Edition.

### Included
- `quarkus-service-main`: the EPCIS 2.0 REST resources, named queries, subscriptions, WebSocket
  delivery, rate limiting, EPCIS 1.2 XML on request through the version transformer.
- `quarkus-capture-topology`: the Kafka Streams capture pipeline and event persistence.
- `quarkus-rest-application`: the Community Edition application. No authentication, every
  request is an administrator; single-tenant; OpenSearch without its security plugin; local
  storage; JobRunr in memory. For trying OpenEPCIS out, not for production.
- EPCIS 1.2 XML through XSLT only; the SAX converter is not part of this edition.
- `CommunityEditionCaptureQueryTest`: capture, query and EPCIS 1.2 output end to end, without
  credentials.

### Fixed
- An unnamed event query, and `GET /events/{eventID}`, answer with `queryName`
  `SimpleEventQuery`.
- A rejected document no longer stops the capture stream: its job finishes as failed, a
  Streams thread that dies is replaced, and the processing and deserialization handlers log and
  continue.

### Not included
- Authentication of any kind, multi-tenancy, Keycloak API keys, capability tokens, Keycloak
  user management, onboarding, the files server, lot and serial master data, possession-bound
  capture. They live in the Business edition.
