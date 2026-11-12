# Implementation Plan: Play a station by its UUID

**Branch**: `001-play-station-by-uuid` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-play-station-by-uuid/spec.md`

## Summary

RADIO-001 creates the extension (`ai.multiroom:multiroom-radio` 0.1.0, extension id `radio`) and
its one feature: an `InputEndpointResolver` for `radiobrowser://<stationuuid>`. On resolve it looks
the station up (`/json/stations/byuuid`), applies the codec and HLS policy, reports the play
(`/json/url/{uuid}`) and hands the hub the station's `http(s)` address with the station's name as
title and `RE_RESOLVE_ORIGINAL_INPUT` refresh. Catalogue servers are discovered lazily (DNS, then
`/json/servers`) or pinned, tried with failover inside a 10 s budget. No hub change, no REST API.
Design reasoning in [research.md](research.md).

## Technical Context

**Language/Version**: Java 17 (the host's)

**Primary Dependencies**: `multiroom-api` 0.1.33 (via parent `multiroom-extension-starter`
0.1.33); Spring Boot 3.x, Jackson, Micrometer, Bean Validation, all `provided`; JDK
`java.net.http.HttpClient`

**Storage**: none (an in-memory, TTL-bound server list only)

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot `ApplicationContextRunner`, WireMock 3.9.2
(fixtures in `src/test/resources/radiobrowser/`), JaCoCo 0.8.14 with an 80% line gate

**Target Platform**: the hub's JVM on Raspberry Pi OS (ARM64) in production; Windows for
development

**Project Type**: drop-in Spring Boot extension JAR (single Maven module)

**Performance Goals**: a play adds at most 2 s over playing the stream address directly (two small
requests to one mirror); hub start-up unchanged

**Constraints**: no network during bean construction; one resolution bounded by `resolve-timeout`
(10 s), failing within 15 s with every server down; no lock held across a network call; tests never
contact radio-browser.info or real DNS

**Scale/Scope**: one household; a handful of plays an hour; ~15 production classes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design: still passing.*

| Principle | How this plan complies |
|---|---|
| I Clean code | Small single-purpose classes (URI parsing, codec policy, title cleaning, server list, client, resolver); specific exceptions |
| II Java 17 / host Spring | All Spring, Jackson, Micrometer `provided`; JDK `HttpClient`, no second HTTP stack; beans from `RadioAutoConfiguration` |
| III Test-first | Each task in `tasks.md` starts with a failing test; WireMock for HTTP; timeouts tested with short configured values |
| IV SOLID | `RadioBrowserClient` interface in front of HTTP; `ServerSource` (discovered or pinned) and `DnsResolver` behind interfaces; `RadioMetrics` interface |
| V Quality, two-part gate | `mvn verify` green **and** the live inventory check (quickstart part 2, step 2), with permission |
| VI Git | Branch `001-play-station-by-uuid`; Conventional Commits; PR; tag `v0.1.0` after merge |
| VII Cross-platform | JDK DNS through an interface, bounded by a timeout; HTTPS by host name only, never bare IP; UUIDs in lower case |
| VIII Extension boundary | `multiroom-api` only; entry point in `AutoConfiguration.imports`; no root config file (version file lives under `multiroom/radio/`); `radio` everywhere; lazy discovery; malformed config fails start-up; resolver returns only `http(s)`; `enabled=false` contributes nothing. No REST API, so no `@RestControllerAdvice` is needed yet |
| IX Good citizen | `sonora-radio/<version>` UA with nothing about the home; discovery, shuffle, failover; no hard-coded mirror; play reported only on resolve and only after the station passed the checks; no polling or scheduled refresh. No list or search requests, so no `limit` applies yet |
| X Untrusted catalogue | UUID validated before any request; stream address must be `http(s)`; name stripped of control characters and bounded at 256 code points; names never in tags or logs; tolerant `JsonNode` mapping |
| XI Hub keeps working | Per-request timeouts plus a per-resolve deadline; bounded server count; `UPSTREAM_UNAVAILABLE` on outage; no shared lock across I/O; discovery on a bounded executor |
| XII No live service in tests | WireMock and injected DNS only; live checks once, by hand, recorded in `verification.md` |

No violations; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-play-station-by-uuid/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1: values, cached state, outcomes
├── quickstart.md        # Phase 1: offline and live validation
├── contracts/
│   ├── station-uri.md         # what clients send and get back
│   ├── configuration.md       # multiroom.radio.* properties
│   ├── observability.md       # meters, tags, log lines
│   └── radio-browser-calls.md # outbound requests and failover rules
├── checklists/requirements.md # spec quality checklist (git-ignored)
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml                                   # parent multiroom-extension-starter 0.1.33, as sonora-tts
src/main/java/multiroom/radio/
├── RadioAutoConfiguration.java           # entry point; @ConditionalOnProperty multiroom.radio.enabled
├── RadioVersion.java                     # reads radio-version.properties
├── config/
│   └── RadioProperties.java              # @ConfigurationProperties("multiroom.radio"), validation
├── resolve/
│   ├── RadioStationResolver.java         # InputEndpointResolver for radiobrowser://
│   ├── StationReference.java             # URI parsing (record)
│   ├── CodecPolicy.java                  # label mapping + SupportedCodecRegistry
│   ├── StationTitle.java                 # strip, collapse, bound
│   └── StreamAddress.java                # http(s) validation
├── catalogue/
│   ├── RadioBrowserClient.java           # interface: station(uuid), reportPlay(uuid)
│   ├── HttpRadioBrowserClient.java       # JDK HttpClient, failover, deadline
│   ├── Station.java, PlayReport.java     # records mapped from JsonNode
│   ├── Deadline.java                     # remaining-time arithmetic
│   ├── CatalogueUnavailableException.java
│   ├── CatalogueProtocolException.java
│   └── servers/
│       ├── ServerSource.java             # interface
│       ├── PinnedServerSource.java
│       ├── DiscoveredServerSource.java   # DNS → /json/servers, TTL cache, AtomicReference
│       ├── DnsResolver.java              # interface over InetAddress
│       ├── JdkDnsResolver.java
│       └── CatalogueServer.java
└── metrics/
    ├── RadioMetrics.java
    ├── MicrometerRadioMetrics.java
    └── NoopRadioMetrics.java
src/main/resources/
├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── multiroom/radio/radio-version.properties   # filtered: version=${project.version}

src/test/java/multiroom/radio/            # mirrors main; *Test per class
├── RadioAutoConfigurationTest.java       # ApplicationContextRunner: enabled/disabled, no I/O at start, bad config
├── resolve/…  catalogue/…  catalogue/servers/…  metrics/…
src/test/resources/radiobrowser/          # hand-written fixtures (see contracts/radio-browser-calls.md)
```

**Structure Decision**: one Maven module laid out like `sonora-tts` (package by concern under
`multiroom.radio`). `resolve` depends on `catalogue` only through `RadioBrowserClient`;
`catalogue` knows nothing of the hub API, so the search feature can reuse it unchanged.

## Delivery notes

- **Order**: skeleton and build gate (POM, entry point, properties, `enabled=false`) → URI,
  title, address and codec value types → server sources → HTTP client with failover → resolver →
  metrics → live verification. Each step is test-first.
- **Live verification** follows [quickstart.md](quickstart.md) part 2 and needs the owner's
  permission for the deploy, the restart, every configuration change and every audible step.
- **After merge**: delete or trim `docs/backlog/play-station-by-uuid.md` and update
  `docs/backlog/INDEX.md`; tag `v0.1.0`; update `AGENTS.md` ("no code yet" no longer holds).

## Complexity Tracking

No constitution violations to justify.
