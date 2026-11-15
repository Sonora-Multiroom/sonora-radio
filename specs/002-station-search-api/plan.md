# Implementation Plan: Search the catalogue through the hub

**Branch**: `002-station-search-api` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-station-search-api/spec.md`

## Summary

RADIO-002 adds five read-only requests under `/api/radio/**` to the extension: search stations,
one station by UUID, and the country, language and tag lists. Each validates its parameters before
any network call, asks radio-browser through RADIO-001's client (extended with search and list
calls, always with `hidebroken=true`, an explicit `limit` and exact tag and language matching),
cleans the untrusted answer into the extension's own shapes, and answers within a 5 s budget.
Search and tag results carry `nextOffset`; lists are remembered for 6 hours; errors are RFC 7807
problem details. Two changes reach RADIO-001's code: an expired server list is used while it is
refreshed in the background, and one attempt can be bounded below `connect + read` timeouts. No hub
change. The extension becomes 0.2.0. Design reasoning in [research.md](research.md).

## Technical Context

**Language/Version**: Java 17 (the host's)

**Primary Dependencies**: `multiroom-api` 0.1.33 (via parent `multiroom-extension-starter`
0.1.33); Spring Boot 3.x with Spring MVC, Jackson, Micrometer, Bean Validation, all `provided`;
`swagger-annotations-jakarta` 2.2.47 (`provided`, annotations only; the hub's `multiroom-rest`
supplies springdoc); JDK `java.net.http.HttpClient`

**Storage**: none on disk; in memory, the server list, the station cache (RADIO-001), the country
and language lists and at most 200 tag pages, each time-limited

**Testing**: JUnit 5, Mockito, AssertJ, `@WebMvcTest` slices for the controllers and the advice,
`ApplicationContextRunner` for wiring, WireMock 3.9.2 with hand-written fixtures in
`src/test/resources/radiobrowser/` shaped like the live answers recorded in `verification.md`;
JaCoCo 80% line gate for the module and 90% for `resolve`, `catalogue`, `catalogue.servers`, the
new `text`, and the new `rest` with each of its sub-packages

**Target Platform**: the hub's JVM on Raspberry Pi OS (ARM64) in production; Windows for
development

**Project Type**: drop-in Spring Boot extension JAR (single Maven module)

**Performance Goals**: a search or list answered within 2 s when the first mirror answers (live
answers took 0.4–0.8 s); a remembered list answered without a network call; hub start-up unchanged

**Constraints**: one API request bounded by `request-timeout` (5 s), one attempt by half of it;
no network during bean construction; no lock held across a catalogue call; nothing sent to the
catalogue for an invalid request; never more than 100 records asked for per search or tag page;
no play reported and no vote cast; tests never contact radio-browser.info or real DNS

**Scale/Scope**: one household, a few clients; about 25 new production classes and changes to 8
existing ones

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design: still passing, with the
one deviation listed under Complexity Tracking.*

| Principle | How this plan complies |
|---|---|
| I Clean code | Thin controllers; one parser per query; one mapper for cleaning; one service that owns the deadline and `nextOffset`; the bounded time-limited map written once (`BoundedTtlCache`) |
| II Java 17 / host Spring | Spring MVC and Swagger annotations `provided`; JDK `HttpClient`; beans declared in `RadioAutoConfiguration` |
| III Test-first | Each task starts with a failing test; `@WebMvcTest` for controllers; WireMock for the catalogue; short configured timeouts, no real waits; 90% gate on `rest` and its sub-packages (JSON to DTO mapping, parsers, errors) and on `text`; the OpenAPI annotations are written after the test that describes them |
| IV SOLID | `RadioBrowserClient` stays the one interface in front of HTTP, used by the resolver and the API; server discovery stays behind `ServerSource`; metrics behind `RadioMetrics` |
| V Quality, two-part gate | `mvn verify` green **and** the live inventory check (quickstart part 2, step 1), with permission |
| VI Git | Branch `002-station-search-api`; Conventional Commits; PR; tag `v0.2.0` after merge |
| VII Cross-platform | No new platform-dependent code; UUIDs compared in lower case. The play path changes (server list, attempt limit, station cache), so one play is verified on the Pi before merge (quickstart part 2, step 8, with permission), unless RADIO-001's live verification ran on this build |
| VIII Extension boundary | `multiroom-api` only; no root config file; controllers conditional on a servlet web application and on `multiroom.radio.enabled`; `@RestControllerAdvice` scoped with `assignableTypes` to this module's API interfaces; problem details; the one new setting fails start-up when not positive. The constitution's example "a result-limit cap outside its allowed range" does not arise: the cap is a fixed value, not a setting (spec, Assumptions) |
| IX Good citizen | Same `User-Agent`, discovery and failover; every list and search request sends an explicit `limit` (at most 100 for searches and tag pages, 2000 for the two short lists); lists cached 6 h; nothing requested unless a client asked (the server-list refresh starts only on a request); `/json/url` is never called by the API. "Capped whatever the client asks" is met by refusing a larger `limit` with 400, so nothing above the cap is ever sent |
| X Untrusted catalogue | UUIDs and every parameter validated before any request; client values percent-encoded; names, tags, languages and addresses bounded and stripped in `RadioStationMapper`; `favicon` and `homepage` returned only as `http(s)` and never fetched; own DTOs; tolerant `JsonNode` mapping; no catalogue or client text in metric tags or logs |
| XI Hub keeps working | 5 s budget with an attempt limit; 503 as a problem detail; an expired server list no longer makes a request (or a play) wait; request threads only, no shared lock across I/O |
| XII No live service in tests | WireMock and injected DNS only. Live checks once, by hand, recorded in `verification.md`. Two of them were run before the build: see Complexity Tracking |

## Project Structure

### Documentation (this feature)

```text
specs/002-station-search-api/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1: queries, shapes, remembered state, fixed values
├── quickstart.md        # Phase 1: offline and live validation
├── contracts/
│   ├── radio-rest-api.yaml    # the API clients see (OpenAPI 3)
│   ├── radio-browser-calls.md # outbound requests added, discovery changes
│   ├── configuration.md       # request-timeout; changed meanings
│   └── observability.md       # new endpoint tag values, log lines
├── verification.md      # upstream facts checked 2026-11-15; live results later
├── checklists/requirements.md # spec quality checklist (git-ignored)
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml                                   # version 0.2.0; + spring-boot-starter-web, swagger-annotations-jakarta (provided); JaCoCo 90% rule for rest* and text
src/main/java/multiroom/radio/
├── RadioAutoConfiguration.java           # CHANGED: nested web configuration declaring the service, controllers and advice
├── config/
│   └── RadioProperties.java              # CHANGED: request-timeout (5s, positive)
├── text/
│   ├── Text.java                         # MOVED from resolve, made public
│   └── StationTitle.java                 # MOVED from resolve
├── resolve/                              # unchanged behaviour; StationReference uses StationUuid
├── catalogue/
│   ├── RadioBrowserClient.java           # CHANGED: + search, countries, languages, tags
│   ├── HttpRadioBrowserClient.java       # CHANGED: the four calls, request building with encoding, list parsing
│   ├── CachingRadioBrowserClient.java    # CHANGED: lists and tag pages for 6 h; uses BoundedTtlCache
│   ├── BoundedTtlCache.java              # NEW: size- and time-bounded map, oldest dropped first
│   ├── CatalogueHttp.java                # CHANGED: honours the deadline's attempt limit
│   ├── Deadline.java                     # CHANGED: optional attempt limit
│   ├── Station.java                      # CHANGED: + favicon, homepage, countryCode, languages, tags, bitrate, lastCheckOk, votes, clickCount
│   ├── StationUuid.java                  # NEW: the strict UUID pattern, shared by resolve and rest
│   ├── StationQuery.java, StationOrder.java, SortDirection.java, TagQuery.java   # NEW
│   ├── StationPage.java, TagPage.java    # NEW: kept records + how many were received
│   ├── Country.java, Language.java, TagCount.java                               # NEW
│   └── servers/
│       └── DiscoveredServerSource.java   # CHANGED: expired list used while refreshed; discovery share from the caller's remaining time
├── rest/
│   ├── RadioCatalogueService.java        # NEW: builds the deadline, calls the client, maps, works out nextOffset
│   ├── RadioStationMapper.java           # NEW: catalogue records to API shapes, all cleaning rules
│   ├── api/
│   │   ├── RadioStationsApi.java         # NEW: /api/radio/stations, /stations/{uuid}; OpenAPI annotations
│   │   └── RadioBrowseApi.java           # NEW: /countries, /languages, /tags
│   ├── controller/
│   │   ├── RadioStationsController.java  # NEW
│   │   ├── RadioBrowseController.java    # NEW
│   │   └── RadioFallbackController.java  # NEW: hidden /api/radio/** catch-all, 404 and 405 as problem details
│   ├── query/
│   │   ├── RequestParameters.java        # NEW: unknown and repeated parameter check
│   │   ├── StationQueryParser.java, TagQueryParser.java   # NEW
│   │   └── InvalidParameterException.java                 # NEW
│   ├── dto/                              # NEW: RadioStation, RadioStationPage, RadioCountry, RadioLanguage, RadioTag, RadioTagPage, RadioProblem
│   ├── advice/
│   │   └── RadioExceptionHandler.java    # NEW: scoped to the API interfaces and the fallback
│   └── StationNotFoundException.java     # NEW
└── metrics/
    └── RadioMetrics.java                 # CHANGED: ENDPOINT_SEARCH, _COUNTRIES, _LANGUAGES, _TAGS

src/test/java/multiroom/radio/            # mirrors main; *Test per class
├── RadioTestApplication.java             # NEW: the @SpringBootConfiguration @WebMvcTest needs
├── rest/…                                # controller slices, parsers, mapper, service, advice
├── catalogue/…                           # new client calls, list cache, BoundedTtlCache, attempt limit
└── catalogue/servers/DiscoveredServerSourceTest.java   # + expired list, background refresh, share
src/test/resources/radiobrowser/          # + search-*.json, countries.json, languages.json, tags-*.json
```

**Structure Decision**: the single Maven module stays; the API is one new package, `rest`, that
depends on `catalogue` only through `RadioBrowserClient` and its value types, as `resolve` does.
`rest` and `resolve` do not depend on each other; what they share (`Text` and `StationTitle` in
`text`, `StationUuid` in `catalogue`) moves below both.

## Delivery notes

- **Order**: (1) foundations with no behaviour change for plays: `Text`, `StationTitle` and `StationUuid` moves,
  `BoundedTtlCache` extraction, `Deadline` attempt limit, `request-timeout`; (2) server-list
  changes in `DiscoveredServerSource`; (3) catalogue calls and the grown `Station`; (4) search end
  to end (parser, service, mapper, controller, advice): the P1 story, usable alone; (5) one
  station; (6) lists and their cache; (7) fallback handler, API description, metrics and logs;
  (8) version 0.2.0 and live verification. Each step is test-first, and RADIO-001's suite stays
  green throughout.
- **Step 2 changes how plays behave**: an expired list no longer delays a play, and discovery
  inside a play that has less than its full budget left gets half of what is left (RADIO-001 gave
  it up to half of `resolve-timeout`). RADIO-001's live verification is still pending and will run
  against this behaviour; if it has not run on this build by the time this feature is verified,
  quickstart part 2 step 8 plays one station instead. One of the two is required before merge.
- **Live verification** follows [quickstart.md](quickstart.md) part 2 and needs the owner's
  permission for the deploy and the restart.
- **After merge**: delete `docs/backlog/station-search-api.md` or trim it to what remains (the
  codec-label question becomes its own backlog item), update `docs/backlog/INDEX.md`, tag
  `v0.2.0`, update `AGENTS.md` (package layout, "still planned" no longer holds), and add
  `sonora radio` commands, the MCP tool and the mobile screen to those repositories' backlogs.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| XII: radio-browser.info was contacted before the feature was built (24 read-only requests on 2026-11-15, acceptance steps 6 and 7, with the owner's permission), not "while the feature is verified against the real hub" | The answers decide the design: exact-match flags, list sizes, 0/1 booleans, tag bounds | Checking after the build, as the constitution orders it, would have found the same facts when changing the spec and the code was most expensive. Still once, by hand, recorded in `verification.md`, and not repeated |
