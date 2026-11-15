---

description: "Task list for RADIO-002: search the catalogue through the hub"
---

# Tasks: Search the catalogue through the hub (RADIO-002)

**Input**: Design documents from `specs/002-station-search-api/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. The constitution (III) mandates test-first: write each test task, run it and
see it fail, then implement. Tests use `@WebMvcTest`, WireMock and an injected `DnsResolver`; nothing
contacts radio-browser.info or real DNS (XII). Timeout tests use configured timeouts of a few hundred
milliseconds, never seconds.

**Organization**: by user story (spec.md), so each story is testable on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US5 from spec.md
- Paths are relative to the repository root. `main/` = `src/main/java/multiroom/radio/`,
  `test/` = `src/test/java/multiroom/radio/`, `fixtures/` = `src/test/resources/radiobrowser/`.
  Each task names the full path anyway.

## Reference while implementing

- Wire format of the API: [contracts/radio-rest-api.yaml](contracts/radio-rest-api.yaml). Calls to the
  catalogue: [contracts/radio-browser-calls.md](contracts/radio-browser-calls.md). Fixed values and
  shapes: [data-model.md](data-model.md). Decisions: research.md R1–R14.
- Model for the REST layer: `D:\projects-sonora\sonora-tts` (`api/`, `controller/`, `dto/`, `advice/`).
  Hub's springdoc is read-only reference in `D:\projects-sonora\sonora-multiroom\multiroom-rest`.
- Live answers to shape fixtures after: [verification.md](verification.md) (recorded 2026-11-15).
- Existing code to extend: `main/catalogue/` (`RadioBrowserClient`, `HttpRadioBrowserClient`,
  `CachingRadioBrowserClient`, `CatalogueHttp`, `Deadline`, `Station`), `main/catalogue/servers/`,
  `main/resolve/` (`Text`, `StationReference`, `StationTitle`), `main/RadioAutoConfiguration`.
- RADIO-001's suite (`test/resolve`, `test/catalogue`) must stay green after every task.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the build knows about the web layer and the new version; nothing behaves differently.

- [ ] T001 In `pom.xml` raise `<version>` to `0.2.0`; add `provided` dependencies `org.springframework.boot:spring-boot-starter-web` and `io.swagger.core.v3:swagger-annotations-jakarta:2.2.47` (confirm 2.2.47 against the `multiroom-rest` module's dependency in `D:\projects-sonora\sonora-multiroom` before keeping it); leave `multiroom.require_api_version` at `0.1.33`; extend the JaCoCo 90% LINE rule list with `multiroom.radio.rest` (the package holds the mapping from catalogue JSON to the API's shapes); until T022 creates the package, run the build with `-Djacoco.skip=true`
- [ ] T002 [P] Create `src/test/java/multiroom/radio/RadioTestApplication.java`: an empty `@SpringBootConfiguration` class that `@WebMvcTest` slices need to find a configuration (plan, Project Structure)
- [ ] T003 Run `mvn verify -Djacoco.skip=true > "$TEMP/radio-verify.log" 2>&1; echo $?` and confirm exit 0 with RADIO-001's tests green and no Spring, Jackson or Swagger classes in `target/multiroom-radio-0.2.0.jar` (`unzip -l`); manifest shows `Extension-Version: 0.2.0`, `Require-API-Version: [0.1.33,0.2.0)`

**Checkpoint**: the JAR builds as 0.2.0 with the web dependencies available.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: moves, shared types and the setting every story uses. No behaviour change for plays.

### Tests (write first, see them fail)

- [ ] T004 [P] Write `test/catalogue/StationUuidTest.java`: accepts a canonical 36-character UUID in any letter case and returns it lower-cased; refuses blank, null, 35 or 37 characters, braces, spaces, a non-hex digit, and a UUID with surrounding whitespace (the strict pattern `StationReference` uses today)
- [ ] T005 [P] Write `test/catalogue/BoundedTtlCacheTest.java` with a `MutableClock`: a value is returned before its time-to-live ends and absent after; with a size bound of 2 the third insert drops the oldest; a re-inserted key replaces the old value; `get` of a missing key is empty; concurrent `put`/`get` from 8 threads loses no entry within the bound
- [ ] T006 [P] Extend `test/config/RadioPropertiesTest.java`: `request-timeout` defaults to `5s`; `0`, `-1s` and `abc` each fail binding with a message naming `multiroom.radio.request-timeout`
- [ ] T007 [P] Write `test/catalogue/StationParsingTest.java` (or extend `HttpRadioBrowserClientTest`) for the grown `Station`: from `fixtures/station-mp3.json` extended with the fields below, `favicon`, `homepage`, `countryCode`, `languages` (raw comma-separated text), `tags` (raw comma-separated text), `bitrate`, `lastCheckOk`, `votes`, `clickCount` are read; `hls` and `lastcheckok` as `0`/`1`, `true`/`false` and absent; a field of the wrong type counts as missing; unknown fields are ignored

### Implementation

- [ ] T008 Move `main/resolve/Text.java` to `main/text/Text.java`, make it `public`, and update imports in `main/resolve/` and its tests; no behaviour change
- [ ] T009 Create `main/catalogue/StationUuid.java`: the strict UUID pattern moved out of `main/resolve/StationReference.java`; `static Optional<UUID> parse(String)` accepting any letter case; `StationReference` now calls it. RADIO-001's `StationReferenceTest` stays green
- [ ] T010 Create `main/catalogue/BoundedTtlCache.java`: generic `<K, V>` map with a fixed time-to-live and size bound, oldest dropped first, backed by a concurrent map, no lock held while a caller does anything slow. Extract the logic from `CachingRadioBrowserClient`'s station cache and switch that class to it
- [ ] T011 Add `requestTimeout` (`Duration`, default `PT5S`, `@Positive`-style validation like the other durations) to `main/config/RadioProperties.java`; the failure message names `multiroom.radio.request-timeout`
- [ ] T012 Grow `main/catalogue/Station.java` with `favicon`, `homepage`, `countryCode`, `languages` (String), `tags` (String), `bitrate` (int, 0 when missing), `lastCheckOk` (boolean), `votes`, `clickCount` (long, 0 when missing) per the data-model table and read them tolerantly in `main/catalogue/HttpRadioBrowserClient.java`; update every existing constructor call and fixture helper in tests (`test/support/Fixtures.java`)
- [ ] T013 [P] Create the value types in `main/catalogue/`: `StationOrder` (enum `NAME`, `VOTES`, `CLICK_COUNT`, `BITRATE` with the catalogue's order word), `SortDirection` (`ASC`, `DESC`), `StationQuery` (record with the ten fields of data-model "StationQuery"), `TagQuery` (`name`, `limit`, `offset`; used as a cache key so it needs `equals`), `StationPage` and `TagPage` (kept records plus `received`), `Country` (`code`, `name`, `stationCount`), `Language` (`name`, `code`, `stationCount`), `TagCount` (`name`, `stationCount`). Values are plain; validation is the parsers' job
- [ ] T014 Run `mvn verify -Djacoco.skip=true > "$TEMP/radio-verify.log" 2>&1; echo $?`: exit 0, RADIO-001's resolver and catalogue tests unchanged and green

**Checkpoint**: foundations in place; plays behave as before.

---

## Phase 3: User Story 1 - Find a station and play it (Priority: P1) 🎯 MVP

**Goal**: `GET /api/radio/stations` returns cleaned stations with a ready `uri`, filters, ordering, paging and `nextOffset`.

**Independent Test**: a `@WebMvcTest` and a WireMock-backed context answer a search by name and country limited to 10: at most 10 stations, each with `uri` `radiobrowser://<lower-case uuid>`, and the catalogue received `hidebroken=true`, `limit=10`, `countrycode=UA`, `name=…`, `order`, `reverse`.

### Tests for User Story 1 (write first, see them fail) ⚠️

- [ ] T015 [P] [US1] Write `test/rest/query/StationQueryParserTest.java` for every rule in data-model "StationQuery": defaults (`limit` 20, `offset` 0, `order` `VOTES`, direction `DESC`; `NAME` and `BITRATE` default `ASC`, `CLICK_COUNT` `DESC`); text filters trimmed, blank means absent, `tag` and `language` lower-cased, `countryCode` upper-cased; refused with `InvalidParameterException` naming the parameter: text of 101 code points (`codec` 33), a control character, `%`, `countryCode` of 1, 3 or non-letter characters, `bitrateMin` -1 and 1000001, `limit` 0 and 101, `offset` -1 and 10001, `order` and `direction` outside the allowed words or in the wrong case; a filter containing `/` is accepted for `tag`
- [ ] T016 [P] [US1] Write `test/rest/query/RequestParametersTest.java`: a name outside the allowed set is refused naming it; a name sent twice is refused naming it; allowed names pass; an empty map passes
- [ ] T017 [P] [US1] Write `test/rest/RadioStationMapperTest.java` for every row of research R9 for stations: name via the `StationTitle` rules, empty when nothing is left; tags and languages split on `,`, trimmed, control characters removed, blanks and repeats dropped, an entry over 64 code points dropped (not cut), at most 30 tags and 10 languages in the catalogue's order (include a 58-tag station); `favicon` and `homepage` kept only if at most 2048 characters, parseable, `http`/`https` with a host, else null (`javascript:`, `ftp:`, relative, 2049 characters); `countryCode` two ASCII letters upper-cased else null; `codec` cleaned, 32 code points, blank null; `bitrate` 0, negative or missing is null; `uri` is `radiobrowser://` plus the lower-case UUID; empty lists, never null
- [ ] T018 [P] [US1] Extend `test/catalogue/HttpRadioBrowserClientTest.java` (WireMock, fixtures `fixtures/search-*.json` shaped like contracts/radio-browser-calls.md): `search` sends `hidebroken=true`, `limit`, `offset`, `order`, `reverse` (`desc` is `reverse=true`, `asc` `reverse=false`, always present); `name` as `name=`; `tag` as lower-case `tag=` with `tagExact=true`; `language` as lower-case `language=` with `languageExact=true`; `countryCode` as `countrycode=` upper-case; `codec`; `bitrateMin`; `order=CLICK_COUNT` sent as `clickcount`; a non-ASCII value and a value with `&`, `=`, `+` and a space are percent-encoded as UTF-8; the answer is a `StationPage` with `received` counting every array element, an element that is not an object or has no valid `stationuuid` is dropped but counted; `[]` is an empty page; a body that is not an array is `CatalogueProtocolException`; the metric endpoint is `search`
- [ ] T019 [P] [US1] Write `test/rest/RadioCatalogueServiceSearchTest.java` (mock `RadioBrowserClient`): `nextOffset` is `offset + limit` for a full page (counted before drops), null for a short page, null for an empty page, null for a full page whose sum is above 10000, and still `offset + limit` for a full page where one record was dropped; the deadline handed to the client is `request-timeout` from the injected clock
- [ ] T020 [P] [US1] Write `test/rest/controller/RadioStationsControllerSearchTest.java` (`@WebMvcTest`, mocked `RadioCatalogueService`): `GET /api/radio/stations` with no parameters answers 200 `application/json` with `stations` and `nextOffset` (null written as `null`, every station has the same keys); the parsed query reaches the service (`order=clickCount` maps to `CLICK_COUNT`, `direction=asc`); each invalid value from T015, an unknown parameter (`countrycode`, `q`) and a repeated parameter answer 400 naming the parameter and the service is never called; `limit=abc` answers 400 naming `limit`. (Problem-detail media type and body are asserted in T034.)
- [ ] T021 [P] [US1] Write the search part of `test/RadioAutoConfigurationTest.java`: in a servlet web application context with `multiroom.radio.enabled` unset the stations controller, service and mapper beans exist; with `enabled=false` none exist; in a non-web context no controller exists but the resolver still does

### Implementation for User Story 1

- [ ] T022 [P] [US1] Create `main/rest/query/InvalidParameterException.java` (`parameter`, `reason`) and `main/rest/query/RequestParameters.java` (allowed-set and repeat check over the raw `MultiValueMap<String,String>`, research R2)
- [ ] T023 [US1] Create `main/rest/query/StationQueryParser.java` implementing the rules in data-model "StationQuery" exactly (fixed values: text 100 code points, `codec` 32, `countryCode` two ASCII letters, `bitrateMin` 0–1000000, `limit` 1–100 default 20, `offset` 0–10000 default 0; control characters and `%` refused; `order` and `direction` case-sensitive); reasons name the allowed range
- [ ] T024 [US1] Extend `main/catalogue/RadioBrowserClient.java` with `CatalogueAnswer<StationPage> search(StationQuery, Deadline)`; implement in `main/catalogue/HttpRadioBrowserClient.java` with one request-building helper that percent-encodes every client value as UTF-8 and always sends the parameters listed in T018; `main/catalogue/CachingRadioBrowserClient.java` passes `search` straight through (never cached); add `ENDPOINT_SEARCH = "search"` to `main/metrics/RadioMetrics.java` (+ both implementations) and count it
- [ ] T025 [P] [US1] Create the DTO records in `main/rest/dto/`: `RadioStation`, `RadioStationPage`, each with `@Schema(name = "RadioStation")` / `@Schema(name = "RadioStationPage")` and the fields of data-model "What the API returns" (nullable fields annotated, null written as `null` not omitted)
- [ ] T026 [US1] Create `main/rest/RadioStationMapper.java` implementing research R9 for stations, using `main/text/Text.java` and `main/resolve/StationTitle.java`; no catalogue or client text in logs
- [ ] T027 [US1] Create `main/rest/RadioCatalogueService.java` with `search(StationQuery)`: builds `Deadline.after(clock, requestTimeout)`, calls `search`, maps with `RadioStationMapper`, works out `nextOffset` per research R10, returns `RadioStationPage`
- [ ] T028 [US1] Create `main/rest/api/RadioStationsApi.java` (`@Tag(name = "Radio")`, `@RequestMapping("/api/radio")`, `GET /stations` with `@Operation(operationId = "searchRadioStations")`, typed optional `@RequestParam`s with `minimum`, `maximum`, `enum` and defaults as in the YAML, and the hidden `@RequestParam MultiValueMap<String,String>`; every error response documented) and `main/rest/controller/RadioStationsController.java` implementing it (thin: `RequestParameters` check, `StationQueryParser`, service)
- [ ] T029 [US1] In `main/RadioAutoConfiguration.java` add the nested web configuration (`@ConditionalOnWebApplication(type = SERVLET)`, gated by `multiroom.radio.enabled`) declaring `RadioStationMapper`, `RadioCatalogueService` and `RadioStationsController` as `@Bean`s; inject the `Clock` and the properties the same way the resolver does
- [ ] T030 [US1] Add `fixtures/search-*.json` used by T018 (full page of 3, empty `[]`, a record without UUID, wrong-typed fields, 58-tag station, `bitrate` 0, `hls` as 0/1), shaped like contracts/radio-browser-calls.md; run `mvn verify -Djacoco.skip=true`, confirm exit 0 and that T015–T021 pass

**Checkpoint**: search works end to end and is usable alone. MVP.

---

## Phase 4: User Story 4 - Bad requests and catalogue trouble fail quickly and clearly (Priority: P2)

**Goal**: every error is a problem detail; a slow first server and an expired server list never eat the budget; 503 and 502 are distinct. Built before Story 2 and 3 so they inherit it.

**Independent Test**: with every WireMock server down a search answers 503 with `Retry-After` within `request-timeout`; with a slow first server the second answers within the budget; an expired server list is used at once.

### Tests for User Story 4 (write first, see them fail) ⚠️

- [ ] T031 [P] [US4] Extend `test/catalogue/DeadlineTest.java`: `Deadline.after(clock, budget)` has no attempt limit; a deadline created with an attempt limit reports it; `attemptCap(Duration configured)` is the smallest of the configured limit, the attempt limit and what is left; the limit never exceeds the deadline
- [ ] T032 [P] [US4] Extend `test/catalogue/CatalogueHttpTest.java` (WireMock, short timeouts): with an attempt limit of 300 ms a server that stalls is given up near 300 ms and counted `timeout`, while a deadline with no attempt limit behaves as before; a stalled body after the headers is also bounded
- [ ] T033 [P] [US4] Extend `test/catalogue/servers/DiscoveredServerSourceTest.java` with a controllable `DnsResolver` and `MutableClock`: (a) an expired list is returned at once, one background refresh starts and replaces the list when it finishes; (b) two requests during one refresh start only one refresh; (c) a failed refresh keeps the old list and none is started for 60 s, then one is; (d) no list, or a list whose servers all failed (`allFailed`), runs discovery inside the request limited to half of what the caller's deadline has left (a 5 s deadline gives 2.5 s, a play deadline keeps its previous share); (e) a late DNS answer is kept for the next request (RADIO-001); (f) a background refresh counts in `radio.discoveries` and logs INFO with source and number of servers
- [ ] T034 [P] [US4] Write `test/rest/advice/RadioExceptionHandlerTest.java` (`@WebMvcTest` with a stub controller implementing the API interfaces): `InvalidParameterException`, `MethodArgumentTypeMismatchException` and `MissingServletRequestParameterException` answer 400 `application/problem+json` with type `urn:multiroom:radio:error:invalid-parameter` and `parameter`; `CatalogueProtocolException` 502 `…:catalogue-bad-answer`; `CatalogueUnavailableException` 503 `…:catalogue-unavailable` with `Retry-After: 30`; any other exception 500 `…:internal-error` with a fixed detail sentence and no exception text; a client value longer than 32 characters is never echoed in `detail`; `RadioProblem` carries `type`, `title`, `detail`, `status`, `instance`, `timestamp` and omits `parameter` when it does not apply; a handler outside the radio API interfaces (a stub controller of another extension) is not touched by the advice
- [ ] T035 [P] [US4] Write `test/rest/controller/RadioFallbackControllerTest.java`: `POST`, `PUT`, `DELETE` to `/api/radio/stations` answer 405 problem detail `…:method-not-allowed` with `Allow: GET`; `GET /api/radio/nothing` and `GET /api/radio/stations/x/y` answer 404 `…:not-found`; the fallback does not shadow the real mappings; it is absent from the generated API description
- [ ] T036 [P] [US4] Write `test/rest/RadioApiTimeBudgetTest.java` (full context, WireMock servers, `request-timeout` 600 ms): every server stalling or refusing answers 503 within the budget plus a small margin; the first server slow, the second fast: 200 within the budget; the catalogue answering 4xx or an unreadable body answers 502 with no failover; while those requests run a `GET` of another path in the same context is answered at once (no shared lock held across a catalogue call)

### Implementation for User Story 4

- [ ] T037 [US4] Add the optional attempt limit to `main/catalogue/Deadline.java` (new record component with a factory `Deadline.after(clock, budget, attemptLimit)`; existing `after` keeps no limit) and make `main/catalogue/CatalogueHttp.java` cap each attempt by `min(connect-timeout + read-timeout, attempt limit, remaining)`; plays pass no attempt limit and behave as before
- [ ] T038 [US4] Change `main/catalogue/servers/DiscoveredServerSource.java` per research R7 and contracts/configuration.md: expired list returned at once with one background refresh on the existing discovery executor (own bound half of `discovery-timeout`, 60 s pause after a failure, INFO log, `radio.discoveries` counted); no list or all failed: discovery inside the request limited to half of the caller's remaining time; no network during construction
- [ ] T039 [US4] In `main/rest/RadioCatalogueService.java` build every API deadline with the attempt limit `request-timeout / 2` and keep request threads free of shared locks
- [ ] T040 [P] [US4] Create `main/rest/dto/RadioProblem.java` (`@Schema(name = "RadioProblem")`), `main/rest/StationNotFoundException.java` (UUID) and `main/rest/advice/RadioExceptionHandler.java`: `@RestControllerAdvice(assignableTypes = {RadioStationsApi.class, RadioBrowseApi.class, RadioFallbackController.class})`, the table in research R3, `application/problem+json`, `Retry-After: 30` on 503; log WARN once for 502/503 (endpoint, servers tried) and ERROR with the stack trace for 500, never filter text or catalogue text
- [ ] T041 [P] [US4] Create `main/rest/controller/RadioFallbackController.java`: hidden (`@Hidden`) catch-all mapped to `/api/radio/**` for every method answering 405 with `Allow: GET` for a known path and 404 otherwise, as `RadioProblem`; declare it and the advice as `@Bean`s in the nested web configuration of `main/RadioAutoConfiguration.java`
- [ ] T042 [US4] Run `mvn verify -Djacoco.skip=true`, confirm exit 0, that T031–T036 pass and that RADIO-001's `DiscoveredServerSourceTest` expectations that changed (expired list) were updated deliberately with a comment naming RADIO-002

**Checkpoint**: errors and time are bounded for search; plays benefit from the same server-list behaviour.

---

## Phase 5: User Story 3 - Show one station again (Priority: P2)

**Goal**: `GET /api/radio/stations/{uuid}` returns the same shape, even for a broken station.

**Independent Test**: a lookup by a UUID from a search answers the same station; an unknown UUID answers 404 naming it; a malformed UUID answers 400 with no catalogue request recorded.

### Tests for User Story 3 (write first, see them fail) ⚠️

- [ ] T043 [P] [US3] Extend `test/rest/RadioCatalogueServiceTest.java` (or create it): `station(String uuid)` rejects a malformed UUID with `InvalidParameterException("uuid")` before the client is touched; accepts upper and mixed case and asks for the lower-case UUID; a station with `lastCheckOk` false is returned; an empty answer raises `StationNotFoundException` naming the UUID; it uses the client's `station` (the 5-minute station cache), never `refreshStation` and never `reportPlay`
- [ ] T044 [P] [US3] Extend `test/rest/controller/RadioStationsControllerTest.java` (`@WebMvcTest`): `GET /api/radio/stations/{uuid}` 200 with the same `RadioStation` shape as search; `not-a-uuid` and a UUID with a trailing space answer 400 naming `uuid`; an unknown UUID answers 404 `…:station-not-found` naming the UUID; `GET /api/radio/stations/{uuid}?x=1` answers 400 (unknown parameter); upper-case UUID answers the lower-case `uuid` and `uri`

### Implementation for User Story 3

- [ ] T045 [US3] Add `getRadioStation` (`GET /stations/{uuid}`) to `main/rest/api/RadioStationsApi.java` and `main/rest/controller/RadioStationsController.java`; add `station(String)` to `main/rest/RadioCatalogueService.java` using `StationUuid`, `RadioBrowserClient.station` and `RadioStationMapper`; the 404 detail names the lower-case UUID
- [ ] T046 [US3] Run `mvn verify -Djacoco.skip=true`, confirm T043–T044 pass and that `RadioMetrics` records no `search` request for a lookup served by the station cache

**Checkpoint**: search plus one station both work.

---

## Phase 6: User Story 2 - Browse by country, language and genre (Priority: P2)

**Goal**: `/api/radio/countries`, `/languages` and `/tags` return cleaned lists ordered by station count, remembered for 6 hours.

**Independent Test**: each list answers ordered by `stationCount`; a second call makes no catalogue request; a failed call is not remembered.

### Tests for User Story 2 (write first, see them fail) ⚠️

- [ ] T047 [P] [US2] Extend `test/catalogue/HttpRadioBrowserClientTest.java` with fixtures `fixtures/countries.json`, `fixtures/languages.json`, `fixtures/tags-*.json` (shapes in contracts/radio-browser-calls.md): countries request `/json/countries?hidebroken=true&order=stationcount&reverse=true&limit=2000`, languages likewise, tags `/json/tags?…&limit=<n>&offset=<n>` and `/json/tags/<filter>?…` with the filter lower-cased and percent-encoded as one path segment; `iso_3166_1`, `iso_639` and `stationcount` read; a country without a valid code, a tag or language without a name is dropped but counted in `received`; a language with `iso_639: null` is kept; endpoints `countries`, `languages`, `tags` counted; a non-array body is a protocol error
- [ ] T048 [P] [US2] Extend `test/catalogue/CachingRadioBrowserClientTest.java` with `MutableClock` and `RecordingMetrics`: a second `countries` call within 6 hours makes no catalogue request and counts nothing; after 6 hours it asks again; the same for `languages`; tag pages are keyed by `(name, limit, offset)` so a different `limit` asks again; at most 200 tag pages are kept, oldest dropped; an exception stores nothing and the next call asks again; `search` is never cached
- [ ] T049 [P] [US2] Extend `test/rest/RadioStationMapperTest.java` for lists (research R9): country `name` cleaned and cut to 100 code points, code two ASCII letters upper-cased; language `name` blank or over 64 code points drops the entry, `code` of 2 or 3 ASCII letters lower-cased else null; tag `name` rules as language; order is the catalogue's, not re-sorted
- [ ] T050 [P] [US2] Write `test/rest/query/TagQueryParserTest.java`: defaults (`limit` 20, `offset` 0), same caps and wording as the search parser, `name` trimmed, lower-cased, blank is absent, 101 code points, control characters, `%` and `/` refused naming `name`
- [ ] T051 [P] [US2] Write `test/rest/controller/RadioBrowseControllerTest.java` (`@WebMvcTest`): `/countries` and `/languages` return plain JSON arrays of `RadioCountry` / `RadioLanguage` (`code` null when unknown) and accept no parameters (any parameter answers 400 naming it); `/tags` returns `RadioTagPage` (`tags`, `nextOffset`) with `name`, `limit`, `offset` handled as in search; `nextOffset` follows the tag page's `received`; invalid values answer 400 with no service call
- [ ] T052 [P] [US2] Extend `test/RadioAutoConfigurationTest.java`: the browse controller bean exists only where the stations controller does

### Implementation for User Story 2

- [ ] T053 [P] [US2] Create `main/rest/dto/RadioCountry.java`, `RadioLanguage.java`, `RadioTag.java`, `RadioTagPage.java` with `@Schema(name = "Radio…")`
- [ ] T054 [US2] Extend `main/catalogue/RadioBrowserClient.java` with `countries(Deadline)`, `languages(Deadline)` and `tags(TagQuery, Deadline)`; implement in `main/catalogue/HttpRadioBrowserClient.java` (reuse the encoding helper from T024; fixed limit 2000 for countries and languages); add `ENDPOINT_COUNTRIES`, `ENDPOINT_LANGUAGES`, `ENDPOINT_TAGS` to `main/metrics/RadioMetrics.java` and both implementations
- [ ] T055 [US2] In `main/catalogue/CachingRadioBrowserClient.java` remember countries and languages (one entry each) and tag pages (at most 200) for a fixed 6 hours using `BoundedTtlCache`; store only successful answers; no lock held across a catalogue call; concurrent identical requests are not shared
- [ ] T056 [P] [US2] Create `main/rest/query/TagQueryParser.java` (rules of data-model "TagQuery", shared limit/offset rules with `StationQueryParser`; extract the common code into one small helper rather than copying)
- [ ] T057 [US2] Extend `main/rest/RadioStationMapper.java` and `main/rest/RadioCatalogueService.java` with `countries()`, `languages()` and `tags(TagQuery)` (deadline and attempt limit as in T039; `nextOffset` as in research R10)
- [ ] T058 [US2] Create `main/rest/api/RadioBrowseApi.java` (`listRadioCountries`, `listRadioLanguages`, `listRadioTags`, all documented as in the YAML) and `main/rest/controller/RadioBrowseController.java`; declare it as a `@Bean` in the nested web configuration and add `RadioBrowseApi` to the advice's `assignableTypes` if T040 did not already (it should)
- [ ] T059 [US2] Run `mvn verify -Djacoco.skip=true`, confirm T047–T052 pass

**Checkpoint**: browse works; search, station and lists are all functional.

---

## Phase 7: User Story 5 - The operator stays in control (Priority: P3)

**Goal**: the setting, the metrics and the API description are right; with the extension off the requests do not exist.

**Independent Test**: a context with `request-timeout=0` fails naming the setting; `radio.catalogue.requests` is tagged `search`, `countries`, `languages`, `tags` with no station text; the generated API description lists the five paths and the `Radio*` shapes.

### Tests for User Story 5 (write first, see them fail) ⚠️

- [ ] T060 [P] [US5] Extend `test/metrics/MicrometerRadioMetricsTest.java`: the four new `endpoint` values are recorded with the outcome tag; a Prometheus scrape shows them; no tag value is derived from a client or catalogue string; the no-op implementation works with no registry; a request answered from memory counts nothing
- [ ] T061 [P] [US5] Extend `test/RadioAutoConfigurationTest.java`: `multiroom.radio.request-timeout=0` and `-1s` fail context start with a message naming `multiroom.radio.request-timeout`; context start makes no catalogue request and no DNS lookup (assert with a counting `DnsResolver` and WireMock's request journal); `enabled=false` contributes no controller, service, advice or fallback
- [ ] T062 [P] [US5] Write `test/rest/RadioApiDescriptionTest.java`: build the OpenAPI document the way the hub does if springdoc is on the test classpath, otherwise assert the annotations by reflection: the five `operationId`s, the `Radio` tag, parameter `minimum`/`maximum`/`enum`/defaults matching `contracts/radio-rest-api.yaml`, every documented error status, schema names `RadioStation`, `RadioStationPage`, `RadioCountry`, `RadioLanguage`, `RadioTag`, `RadioTagPage`, `RadioProblem`, and the fallback controller absent. If springdoc is not available at test scope, state in the test's class comment that the live check (T069) covers `/api-docs` and keep the reflection assertions

### Implementation for User Story 5

- [ ] T063 [US5] Complete the API annotations in `main/rest/api/RadioStationsApi.java` and `main/rest/api/RadioBrowseApi.java` and the `@Schema` descriptions on every DTO so T062 passes; keep descriptions to what a client needs (bounds, `nextOffset` rules, "drop stations already seen by `uuid`")
- [ ] T064 [US5] Add the DEBUG line per answered API request (endpoint, number of records, answering server or `cache`, elapsed ms) in `main/rest/RadioCatalogueService.java`; never the filter text or catalogue text; stations only by UUID (FR-032)
- [ ] T065 [US5] Run `mvn verify -Djacoco.skip=true`, confirm T060–T062 pass

**Checkpoint**: operable and documented.

---

## Phase 8: Polish, gates and live verification

**Purpose**: the full gate, documentation and the one-time live run (needs the owner's permission).

- [ ] T066 Run the full gate: `mvn verify > "$TEMP/radio-verify.log" 2>&1; echo $?` (no `-Djacoco.skip`); exit 0, JaCoCo at least 80% for the module and 90% for `resolve`, `catalogue`, `catalogue.servers` and `rest`; fix gaps with tests, not by lowering a rule. Inspect the JAR as in quickstart Part 1 (`Extension-Version: 0.2.0`, `Require-API-Version: [0.1.33,0.2.0)`, no Spring, Jackson or Swagger classes); record the command, date and counts in `specs/002-station-search-api/verification.md`
- [ ] T067 [P] Check `contracts/radio-rest-api.yaml` against the implemented annotations and DTOs (names, bounds, enums, statuses); correct whichever is wrong and note it in `verification.md`
- [ ] T068 Check line endings of everything touched: `git ls-files --eol | grep 'w/crlf'` prints nothing
- [ ] T069 **Needs the owner's permission (deploy and restart; the restart stops whatever is playing).** Run quickstart Part 2 steps 1–7 on `multiroom.lan` with `mvn deploy` and `sudo -n systemctl restart multiroom.service` as in the parent AGENTS.md; every request is a read-only `GET` and makes no sound. Record commands, date and results (including both numbers of acceptance step 3 and `/api-docs` contents) in `verification.md`; if a result differs from the spec, correct the spec first
- [ ] T070 After merge: delete `docs/backlog/station-search-api.md` or trim it to what remains (the codec-label question becomes its own backlog item), update `docs/backlog/INDEX.md`, update `AGENTS.md` (package layout gains `rest/`, "still planned" no longer holds), add `sonora radio` commands, the MCP tool and the browse screen to the backlogs of `sonora-cli`, `sonora-mcp` and `sonora-mobile`, then tag `v0.2.0` (only when asked)

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)** → **Foundational (2)** → every story.
- **US1 (3)** needs Phase 2 only. It is the MVP.
- **US4 (4)** needs US1 (it tests and hardens the search path and creates the advice and fallback every later story uses).
- **US3 (5)** needs US1 and US4's advice (T040).
- **US2 (6)** needs US1 and US4's advice; independent of US3.
- **US5 (7)** needs US1–US3 and US2's controllers to exist for its description checks.
- **Polish (8)** needs everything; T069 also needs the owner's permission.

### Within each story

Tests first and seen to fail; value types and parsers before client calls; client before service; service before API interface and controller; wiring last; end each phase with the `mvn verify` task.

### Parallel opportunities

- Phase 2: T004–T007 together; T013 beside T008–T012.
- US1: T015–T021 (all different files) together; T022, T025 beside T023/T024.
- US4: T031–T036 together; T040 and T041 together.
- US3 and US2 can proceed in parallel after US4; inside US2, T047–T052 together and T053, T056 beside T054.

## Parallel Example: User Story 1

```text
Task: "Write test/rest/query/StationQueryParserTest.java"          (T015)
Task: "Write test/rest/query/RequestParametersTest.java"           (T016)
Task: "Write test/rest/RadioStationMapperTest.java"                (T017)
Task: "Extend test/catalogue/HttpRadioBrowserClientTest.java"      (T018)
Task: "Write test/rest/controller/RadioStationsControllerSearchTest.java" (T020)
```

## Implementation Strategy

### MVP (US1)

1. Phases 1 and 2.
2. Phase 3: search end to end. **Stop and validate**: `mvn verify`, and a WireMock-backed request returns stations with ready `uri`s.
3. Phase 4 before shipping anything: without it an error is Spring's default body and a slow mirror can eat the budget. US1 + US4 is the smallest releasable increment.

### Incremental delivery

1. US1 + US4: search with bounded errors and time (also changes how plays treat an expired server list; RADIO-001's live verification will run against it).
2. US3: one station.
3. US2: browse lists.
4. US5: description, metrics, logs; then the full gate and the live run.

## Notes

- `[P]` tasks touch different files and depend on no unfinished task.
- Do not cite `FR-`, `SC-`, `US` or `R<n>` numbers in code or code comments; `RADIO-001`, `RADIO-002` and `HUB-NNN` are fine.
- Commit per logical group when asked; read each build's own exit code (`cmd > log; echo $?`), never through a pipe.
- Never contact radio-browser.info from a test; the only live contact is T069, by hand.
- Mark each task `[X]` as it is done.
