---

description: "Task list for RADIO-001: play a station by its UUID"
---

# Tasks: Play a station by its UUID (RADIO-001)

**Input**: Design documents from `specs/001-play-station-by-uuid/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. The constitution (III) mandates test-first: write each test task, run it and
see it fail, then implement. Tests use WireMock and an injected `DnsResolver`; nothing contacts
radio-browser.info or real DNS (XII). Timeout tests use configured timeouts of a few hundred
milliseconds, never seconds.

**Organization**: by user story (spec.md), so each story is testable on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US4 from spec.md
- Paths are relative to the repository root. `main/` = `src/main/java/multiroom/radio/`,
  `test/` = `src/test/java/multiroom/radio/`, `fixtures/` = `src/test/resources/radiobrowser/`.
  Each task names the full path anyway.

## Reference while implementing

- Hub API (read-only): `D:\projects-sonora\sonora-multiroom\multiroom-api\src\main\java\multiroom\api\io\resolve\`
  (`InputEndpointResolver`, `ResolvedInputEndpoint`, `EndpointRefreshMode`,
  `InputResolutionException`, `InputResolutionFailure`, `SupportedCodecRegistry`) and
  `multiroom\api\model\AudioInputDefinition`.
- Model extension: `D:\projects-sonora\sonora-tts` (`pom.xml`, `TtsAutoConfiguration`,
  `TtsInputResolver`, `metrics/`).
- Failure codes per case: research.md R2. Call order: R3. Title rules: R4. Codec map: R5.
  Discovery and failover: R7. Time budget: R8.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: a building, empty extension JAR that passes the inherited gates.

- [X] T001 Create `pom.xml` copying `D:\projects-sonora\sonora-tts\pom.xml`'s shape: parent `ai.multiroom:multiroom-extension-starter:0.1.33` with empty `<relativePath/>`; `artifactId` `multiroom-radio`, `version` `0.1.0`, `name` `multiroom-radio`, description "radio-browser.info stations for the multiroom audio hub"; properties `extension.name` = `Radio Extension`, `extension.id` = `radio` (with the comment on why it is not derived from the artifact id), `multiroom.require_api_version` = `0.1.33`, `skip.wagon.upload` = `false`, `multiroom.local-extensions-dir` = `D:/projects-sonora/sonora-multiroom/extensions`; `provided` dependencies `spring-boot-starter`, `spring-boot-starter-validation`, `jackson-databind`, `micrometer-core`; optional provided `spring-boot-configuration-processor`; test dependencies `spring-boot-starter-test`, `micrometer-registry-prometheus`, `org.wiremock:wiremock-standalone:3.9.2`; JaCoCo 0.8.14 with the 80% BUNDLE LINE `check` in `verify` plus a 90% LINE rule (the constitution asks for more than the 80% minimum there) for each of the packages `multiroom.radio.resolve`, `multiroom.radio.catalogue` and `multiroom.radio.catalogue.servers` (resolver, discovery and failover, timeouts, JSON mapping); the `local` profile override copying to `${multiroom.local-extensions-dir}/${project.artifactId}.jar`
- [X] T002 In `pom.xml`, enable resource filtering **only** for `src/main/resources/multiroom/radio/radio-version.properties` (one filtered `<resource>` with an include, one unfiltered for everything else), and create that file with the single line `version=${project.version}`
- [X] T003 [P] Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` containing `multiroom.radio.RadioAutoConfiguration`
- [X] T003a [P] Write `src/test/java/multiroom/radio/RadioAutoConfigurationTest.java` (`ApplicationContextRunner` with `AutoConfigurations.of(RadioAutoConfiguration.class)`) and see it fail: the default context contains a `RadioAutoConfiguration` bean; with `multiroom.radio.enabled=false` it contains none. This test class grows in T008a, T023a and T048, each time ahead of the wiring it checks
- [X] T004 Create `src/main/java/multiroom/radio/RadioAutoConfiguration.java`: `@AutoConfiguration`, `@ConditionalOnProperty(name = "multiroom.radio.enabled", havingValue = "true", matchIfMissing = true)`, no beans yet; class Javadoc as in `TtsAutoConfiguration` (entry point named by the imports file; core knows nothing of this module)
- [X] T005 Run `mvn verify > "$TEMP/radio-verify.log" 2>&1; echo $?` with `-Djacoco.skip=true` added (there is almost no code to cover yet) and confirm exit 0, that T003a passes, that the enforcer passes, and that `target/multiroom-radio-0.1.0.jar`'s manifest has `Extension-Id: radio`, `Extension-Version: 0.1.0`, `Require-API-Version: [0.1.33,0.2.0)`

**Checkpoint**: the JAR builds; the hub would load it and contribute nothing.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: configuration, version, time budget, catalogue types and the metrics seam that every
story uses.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

### Tests (write first, see them fail)

- [X] T006 [P] Write `src/test/java/multiroom/radio/config/RadioPropertiesTest.java` (bind with `Binder` over a `MapConfigurationPropertySource` plus a `Validator`): defaults are `enabled` true, `user-agent` null (meaning "sonora-radio/<version>"), `connect-timeout` 2s, `read-timeout` 4s, `resolve-timeout` 10s, `max-servers-per-request` 3, `servers` empty, `discovery-host` `all.api.radio-browser.info`, `discovery-timeout` 3s, `server-list-ttl` 1h, `station-cache-ttl` 5m, `station-cache-size` 100, `allow-hls` true. Each rule fails with a message naming the property: timeouts "Positive"; `resolve-timeout` "at least `connect-timeout`"; `max-servers-per-request` "1–10"; `server-list-ttl` "Positive"; `station-cache-ttl` "Positive"; `station-cache-size` "1–10000"; each `servers` entry "an absolute `http`/`https` URL with a host, no path beyond `/`, no query or fragment"; `discovery-host` "a plain host name (no scheme, port or path)"; `user-agent` "Not blank, no CR or LF, at most 200 characters"
- [X] T007 [P] Write `src/test/java/multiroom/radio/RadioVersionTest.java`: the version read from `multiroom/radio/radio-version.properties` is non-blank and not the literal `${project.version}`; the default user agent is `sonora-radio/` + that version; a missing resource yields `sonora-radio/unknown` rather than an exception
- [X] T008 [P] Write `src/test/java/multiroom/radio/catalogue/DeadlineTest.java` against an injected `java.time.Clock`: `remaining()` shrinks with the clock, never goes negative; `expired()`; `cap(Duration)` returns the smaller of the argument and what remains
- [X] T008a Extend `src/test/java/multiroom/radio/RadioAutoConfigurationTest.java`: the default context has a `RadioProperties` bean and a `RadioMetrics` bean (`NoopRadioMetrics`); `enabled=false` → neither; `servers[0]=not a url`, `connect-timeout=0s` and `max-servers-per-request=11` each fail start-up with a message containing `multiroom.radio` and the property

### Implementation

- [X] T009 Implement `src/main/java/multiroom/radio/config/RadioProperties.java`: `@ConfigurationProperties("multiroom.radio")`, `@Validated`, fields and defaults per [contracts/configuration.md](contracts/configuration.md) as field initialisers (no `application.yml`), validation per T006 (JSR-380 annotations plus an `@AssertTrue`-style cross-check or `InitializingBean`-free explicit checks), `servers` as `List<URI>`; Javadoc on every property
- [X] T010 [P] Implement `src/main/java/multiroom/radio/RadioVersion.java`: loads `radio-version.properties` through `RadioVersion.class.getResourceAsStream("radio-version.properties")` (package-relative, never the classpath root) and exposes `version()` and `defaultUserAgent()`
- [X] T011 [P] Implement `src/main/java/multiroom/radio/catalogue/Deadline.java` (record over a `Clock` and an end `Instant`)
- [X] T012 [P] Create the catalogue types in `src/main/java/multiroom/radio/catalogue/`: `Station.java` (record `uuid`, `name`, `streamUrl`, `codecLabel`, `hls`), `PlayReport.java` (record `ok`, `url`), `CatalogueUnavailableException.java` (no server answered; carries the servers tried), `CatalogueProtocolException.java` (unreadable answer), `CatalogueAnswer.java` (generic record `value`, `Optional<String> serverTag`: the tag of the server that answered, empty when the value came from the cache), and the interface `RadioBrowserClient.java` with `CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline)`, `CatalogueAnswer<Optional<Station>> refreshStation(UUID uuid, Deadline deadline)` (always asks the catalogue, never a cache) and `CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline)`; all three throw only the two exceptions above
- [X] T013 [P] Create `src/main/java/multiroom/radio/catalogue/servers/CatalogueServer.java` (record `baseUri`, `tag` = host, plus `:port` when not the scheme's default) and the interfaces `ServerSource.java` (`List<CatalogueServer> servers(Deadline)`, `void answered(CatalogueServer)`, `void allFailed()`) and `DnsResolver.java` (`List<InetAddress> lookup(String host)`, `Optional<String> reverse(InetAddress address)`)
- [X] T014 [P] Create `src/main/java/multiroom/radio/metrics/RadioMetrics.java` (no Micrometer types; methods `resolution(String outcome, String serverTag, Duration elapsed)`, `catalogueRequest(String serverTag, String endpoint, String result)`, `discovery(String source, String result)`; tag constants for every closed set in [contracts/observability.md](contracts/observability.md); "no method throws") and `src/main/java/multiroom/radio/metrics/NoopRadioMetrics.java`
- [X] T015 Register in `src/main/java/multiroom/radio/RadioAutoConfiguration.java`: `@EnableConfigurationProperties(RadioProperties.class)` and a `NoopRadioMetrics` bean for now (the Micrometer one arrives in US4)

**Checkpoint**: foundation ready; `mvn test` green for T006–T008a.

---

## Phase 3: User Story 1 - Play a station by naming it (Priority: P1) 🎯 MVP

**Goal**: `radiobrowser://<uuid>` plays through `POST /api/v2/play`, named after the station.

**Independent Test**: with WireMock serving `byuuid` and `url` for one station on a pinned server,
`RadioStationResolver.resolve` returns that station's `http(s)` address, title, and
`RE_RESOLVE_ORIGINAL_INPUT`; live, quickstart part 2 step 3.

### Tests for User Story 1 (write first, see them fail) ⚠️

- [X] T016 [P] [US1] Write `src/test/java/multiroom/radio/resolve/StationReferenceTest.java` for the accepted forms: `radiobrowser://<uuid>`, `radiobrowser://<uuid>/`, `radiobrowser://<uuid>#<label>`, upper- and mixed-case scheme and UUID; the parsed UUID is in canonical lower case; the label is held but irrelevant to equality
- [X] T017 [P] [US1] Write `src/test/java/multiroom/radio/resolve/StationTitleTest.java`: control characters, line and paragraph separators removed; whitespace runs collapsed to one space; trimmed; "cut at 256 code points (never inside a surrogate pair)"; blank → empty `Optional`; a 300-character name keeps exactly 256 code points; a name with HTML is kept as text (only bounded)
- [X] T018 [P] [US1] Write `src/test/java/multiroom/radio/resolve/StreamAddressTest.java` for the accepted side: `http` and `https` (any case) with a host parse to a `URI` unchanged
- [X] T019 [P] [US1] Create fixtures `src/test/resources/radiobrowser/station-mp3.json` (one-element array: `stationuuid`, `name` "Test FM", `url`, `url_resolved` `https://stream.example.test/live.mp3`, `codec` "MP3", `hls` 0, `lastcheckok` 1, plus a few unknown fields) and `src/test/resources/radiobrowser/url-ok.json` (`ok` true, `message`, `stationuuid`, `name`, `url` `https://stream.example.test/live-current.mp3`), plus `url-ok-missing.json` (no `ok` field) and `url-ok-string.json` (`ok` as the string `"true"`), both with the same `url`, all shaped per [contracts/radio-browser-calls.md](contracts/radio-browser-calls.md)
- [X] T020 [P] [US1] Write `src/test/java/multiroom/radio/catalogue/HttpRadioBrowserClientTest.java` (WireMock, one server via a stub `ServerSource`): `station` requests `GET /json/stations/byuuid?uuids=<uuid>&hidebroken=false` and maps the fixture, preferring `url_resolved` over `url`; `refreshStation` makes the same request as `station`; `reportPlay` requests `GET /json/url/<uuid>` and maps `ok`/`url`, where `ok` is false only for an explicit `false` (the boolean or the string `"false"`) and true when missing or of another type; every answer carries the answering server's tag (request headers are tested in T020a); unknown fields ignored; `[]` → empty `Optional`
- [X] T020a [P] [US1] Write `src/test/java/multiroom/radio/catalogue/CatalogueHttpTest.java` (WireMock): a GET carries `User-Agent` from properties (default `sonora-radio/<version>`) and `Accept: application/json`; an attempt is classified `ok`, `http_4xx`, `http_429`, `http_5xx`, `connect_error` (closed port) or `timeout`; the wait is `deadline.cap(connect-timeout + read-timeout)` with timeouts of a few hundred milliseconds, for a delayed response and for a body that stalls after the headers (`withChunkedDribbleDelay`), and the call returns within that limit; a log-capturing appender shows the DEBUG line with the request URL and the `User-Agent` sent; constructing it opens no connection
- [X] T021 [P] [US1] Write `src/test/java/multiroom/radio/catalogue/servers/PinnedServerSourceTest.java`: returns the configured servers in the order given; never expires; `answered(server)` and `allFailed()` change nothing (a pinned list is never rotated and never discovered again)
- [X] T022 [P] [US1] Write `src/test/java/multiroom/radio/catalogue/servers/DiscoveredServerSourceTest.java` for the DNS path with a fake `DnsResolver`: forward lookup of `discovery-host`, reverse lookup of each address, keep names ending in `.api.radio-browser.info`, `https://<name>` each, shuffled (inject a `Random` with a seed to assert order); the result is cached and the second call makes no DNS lookup; the first lookup happens on the first `servers()` call, never at construction; the lookup runs off the calling thread and is awaited for at most the smaller of `discovery-timeout` and what is left of the `Deadline` (a fake resolver that blocks, short configured values): `servers()` then fails with `CatalogueUnavailableException` within that limit (the `/json/servers` fallback arrives in US3)
- [X] T023 [US1] Write `src/test/java/multiroom/radio/resolve/RadioStationResolverTest.java` (mock `RadioBrowserClient`, stub `SupportedCodecRegistry` that supports everything): `supports` is true only for the `radiobrowser` scheme (any case) and false for `http`, `tts`, `soundcloud` URLs and null; `supports` never throws and never calls the client (the hub calls it at start-up for configured inputs, HUB-039): a definition with a null, blank or unparseable URI answers false; `resolve` calls `station` then `reportPlay` in that order and returns `effectiveUri` = the play report's `url`, `headers` empty, `expiresAt` null, `refreshMode` `RE_RESOLVE_ORIGINAL_INPUT`, `resolverId` `radiobrowser`, `title` = cleaned name, `creator` null, `originalInput` the input given; `getOrder()` is 120; resolving the same input twice reports the play twice (re-lookup)
- [X] T023a [US1] Extend `src/test/java/multiroom/radio/RadioAutoConfigurationTest.java` ahead of T031: the default context has `RadioStationResolver`, `RadioBrowserClient` and `ServerSource` (discovered when `servers` is empty, pinned otherwise); `enabled=false` → none of them; with a WireMock server pinned, starting the context records zero requests, and with no servers pinned (discovery), starting the context makes no DNS lookup (a counting `DnsResolver` bean replaces the JDK one)

### Implementation for User Story 1

- [X] T024 [P] [US1] Implement `src/main/java/multiroom/radio/resolve/StationReference.java` (record `uuid`, `label`; static `parse(String uri)` throwing `IllegalArgumentException` with a reason; strict 8-4-4-4-12 hex pattern; refusals are tested in US2 but the parser rejects everything outside the grammar in [contracts/station-uri.md](contracts/station-uri.md) from the start)
- [X] T025 [P] [US1] Implement `src/main/java/multiroom/radio/resolve/StationTitle.java` (static `of(String name)` → `Optional<String>`, rules per T017)
- [X] T026 [P] [US1] Implement `src/main/java/multiroom/radio/resolve/StreamAddress.java` (static `parse(String)` → `URI`; scheme `http`/`https` and a host required, else `IllegalArgumentException` naming the scheme only)
- [X] T027 [P] [US1] Implement `src/main/java/multiroom/radio/catalogue/servers/PinnedServerSource.java`
- [X] T028 [P] [US1] Implement `src/main/java/multiroom/radio/catalogue/servers/JdkDnsResolver.java` (`InetAddress.getAllByName`, `getCanonicalHostName`; a reverse lookup that returns the literal address counts as empty) and the DNS path of `src/main/java/multiroom/radio/catalogue/servers/DiscoveredServerSource.java` (cached list in an `AtomicReference`, no lock held across a lookup; the lookup runs on a daemon executor that never queues one lookup behind another (a cached pool or a thread per lookup, never a single thread) and is awaited for at most the smaller of `discovery-timeout` and `deadline.remaining()`, so the playback path is bounded from the first build that can be deployed)
- [X] T028a [P] [US1] Implement `src/main/java/multiroom/radio/catalogue/CatalogueHttp.java` (research.md R9), the transport shared by the client and the discovery fallback: one `java.net.http.HttpClient` built lazily on first use (`Redirect.NORMAL`, HTTP/1.1, `connectTimeout` from properties); `User-Agent` and `Accept` on every request; each attempt sent with `sendAsync` and awaited for at most `deadline.cap(connect-timeout + read-timeout)`, then cancelled and counted as a timeout (research.md R8: the client's own timeouts do not cover a body that stalls after the headers), with `HttpRequest.timeout(deadline.cap(read-timeout))` still set as a second line; returns the body or the attempt's classification per T020a; DEBUG log of the request URL and `User-Agent`
- [X] T029 [US1] Implement `src/main/java/multiroom/radio/catalogue/HttpRadioBrowserClient.java` for a single answering server, on top of `CatalogueHttp`: results returned as `CatalogueAnswer` with the answering server's tag; Jackson `ObjectMapper.readTree` and field-by-field mapping into `Station`/`PlayReport` (wrong-typed field = missing)
- [X] T030 [US1] Implement `src/main/java/multiroom/radio/resolve/RadioStationResolver.java` (`InputEndpointResolver`): parse → `Deadline` of `resolve-timeout` → `station` → (codec/HLS hooks, filled in US2) → `reportPlay` → `StreamAddress.parse(url)` → `ResolvedInputEndpoint` per T023; INFO one line per resolution with UUID, outcome, answering server (the last non-empty `serverTag` seen during the resolution, else `none`), elapsed ms (never the name or label)
- [X] T031 [US1] Wire beans in `src/main/java/multiroom/radio/RadioAutoConfiguration.java`: `CatalogueHttp`, a `DnsResolver` bean (`JdkDnsResolver`, `@ConditionalOnMissingBean` so a test can replace it), `ServerSource` (pinned when `servers` is non-empty, else discovered), `RadioBrowserClient`, `RadioStationResolver` (with `ObjectProvider<SupportedCodecRegistry>`); nothing in any constructor touches the network

**Checkpoint**: US1 works with a healthy catalogue; `mvn verify` green.

---

## Phase 4: User Story 2 - A bad station fails clearly and leaves nothing behind (Priority: P1)

**Goal**: every failure is an `InputResolutionException` with the right code and a message naming
the station, so the hub answers 400/422/502/503 and removes its ephemeral input.

**Independent Test**: each failure case in research.md R2 produces its failure code and message
from `resolve`, refused stations make no `/json/url` request, and malformed input makes no request
at all.

### Tests for User Story 2 (write first, see them fail) ⚠️

- [X] T032 [P] [US2] Extend `src/test/java/multiroom/radio/resolve/StationReferenceTest.java` with the refused forms: empty authority, non-UUID authority (`radiobrowser://kissfm`), a path other than `/`, a query, user info, a port, a UUID with wrong group lengths or non-hex characters
- [X] T033 [P] [US2] Extend `src/test/java/multiroom/radio/resolve/StreamAddressTest.java` with refusals: `file:`, `jar:`, `alsa:`, `ftp:`, relative, no host, unparseable
- [X] T034 [P] [US2] Write `src/test/java/multiroom/radio/resolve/CodecPolicyTest.java`: labels trimmed and upper-cased; `MP3` → `mp3`, `AAC` and `AAC+` → `aac`, `OPUS` → `opus`, `FLAC` → `flac`; a mapped codec the stub registry rejects is refused; `OGG`, `UNKNOWN`, empty, null and any other label are allowed; no registry (empty `ObjectProvider`) → allowed
- [X] T035 [P] [US2] Create fixtures `src/test/resources/radiobrowser/station-empty.json` (`[]`), `station-hls.json` (`hls` 1, codec `AAC`), `station-file-url.json` (`url_resolved` `file:///etc/passwd`), `station-unknown-codec.json` (codec `UNKNOWN`), `station-malformed.json` (an object, not an array) and `url-not-ok.json` (`ok` false)
- [X] T036 [US2] Extend `src/test/java/multiroom/radio/resolve/RadioStationResolverTest.java` with the R2 table, asserting failure code and that the message names the UUID (or the malformed value truncated to 64 characters): malformed → `INVALID_INPUT`, no client call; unknown station → `INVALID_INPUT` with "not found in radio-browser"; codec refused → `UNSUPPORTED_RESOURCE` naming the label, **no `reportPlay`**; HLS with `allow-hls` false → `UNSUPPORTED_RESOURCE` naming "HLS", **no `reportPlay`**; HLS with `allow-hls` true → plays; non-HTTP play-report address → `UNSUPPORTED_RESOURCE` naming the scheme; `CatalogueUnavailableException` from `station` → `UPSTREAM_UNAVAILABLE` with "radio-browser unavailable" (what a failing `reportPlay` does is decided in US3, T044: do not assert it here); `CatalogueProtocolException` from `station` → `UPSTREAM_PROTOCOL_ERROR`; any unexpected `RuntimeException` from a collaborator is wrapped as `UPSTREAM_PROTOCOL_ERROR` (never escapes raw)
- [X] T037 [P] [US2] Extend `src/test/java/multiroom/radio/catalogue/HttpRadioBrowserClientTest.java`: a non-array `byuuid` body → `CatalogueProtocolException`; a `/json/url` body that is not a JSON object → `CatalogueProtocolException` (the resolver turns it into a fallback); an array whose entry's `stationuuid` differs from the request → empty; a 4xx other than 429 → `CatalogueProtocolException` without trying another server

### Implementation for User Story 2

- [X] T038 [P] [US2] Implement `src/main/java/multiroom/radio/resolve/CodecPolicy.java` (constructor takes `ObjectProvider<SupportedCodecRegistry>`; `Optional<String> refusal(String codecLabel)` returns the label to name when refused)
- [X] T039 [US2] Complete `src/main/java/multiroom/radio/resolve/RadioStationResolver.java`: map every failure to `InputResolutionException` per research.md R2; codec and HLS checks before `reportPlay`; bound any catalogue text put in a message (codec label to 32 characters, control characters removed); catch-all wrap so nothing but `InputResolutionException` leaves `resolve`
- [X] T040 [US2] Wire `CodecPolicy` and `allow-hls` into the resolver bean in `src/main/java/multiroom/radio/RadioAutoConfiguration.java`

**Checkpoint**: US1 and US2 green; every failure path ends in an `InputResolutionException`.

---

## Phase 5: User Story 3 - Playback survives catalogue and station trouble (Priority: P2)

**Goal**: failover across mirrors inside the time budget, fallback when the play report fails, and
discovery that recovers from DNS trouble.

**Independent Test**: with WireMock servers that refuse, stall or answer 5xx ahead of a healthy
one, a play succeeds through the healthy one; with all of them failing, `resolve` fails
`UPSTREAM_UNAVAILABLE` within `resolve-timeout`.

### Tests for User Story 3 (write first, see them fail) ⚠️

- [X] T041 [P] [US3] Extend `src/test/java/multiroom/radio/catalogue/HttpRadioBrowserClientTest.java` with several WireMock servers (or one server with per-path delays and a closed port): moves to the next server after a connect error, a timeout (`read-timeout` 200 ms against a 1 s delay), a body that stalls after the headers (the bound itself is tested in T020a; here only that the client moves on), HTTP 5xx and HTTP 429 (recorded as `http_429`); tries at most `max-servers-per-request`; stops when the `Deadline` expires and throws `CatalogueUnavailableException` listing the servers tried; tells the `ServerSource` which server `answered` and calls `allFailed` when none did
- [X] T042 [P] [US3] Extend `src/test/java/multiroom/radio/catalogue/servers/DiscoveredServerSourceTest.java`: no usable reverse names → `GET https://<discovery-host>/json/servers` (WireMock stands in; the source's constructor takes this fallback base URI) and keep `name`s ending in `.api.radio-browser.info`; a DNS lookup that times out (bounded since T022) now counts as no names and falls back to `/json/servers` instead of failing, and a second discovery started while the first lookup is still stuck is not queued behind it (it completes on its own); a lookup that answers after its wait timed out still stores its names, so the next `servers()` call returns them with no new lookup and no `/json/servers` request, but it does not replace a list discovered after that lookup started; DNS and the `/json/servers` fallback together stop at half of `resolve-timeout` even when the `Deadline` passed in has more left (short configured values), and then fail with `CatalogueUnavailableException`; with `resolve-timeout` below twice `discovery-timeout`, the DNS wait itself ends at the half-budget mark; the list is discovered again after `server-list-ttl` (injected `Clock`) and after `allFailed()`; `answered(server)` rotates the list so the next request starts there; two concurrent first calls both complete without waiting on each other's lookup
- [X] T043 [P] [US3] Create fixture `src/test/resources/radiobrowser/servers.json` (three entries, one whose `name` is not under `.api.radio-browser.info`)
- [X] T044 [US3] Extend `src/test/java/multiroom/radio/resolve/RadioStationResolverTest.java`: `reportPlay` throwing `CatalogueUnavailableException` or `CatalogueProtocolException`, or returning `ok` false or a missing or blank `url` → plays `Station.streamUrl` with outcome `fallback`; when the station answer came from the cache (empty `serverTag`) the fallback first calls `refreshStation` and plays that answer's address, fails `INVALID_INPUT` when it finds no station and `UPSTREAM_UNAVAILABLE` when it throws `CatalogueUnavailableException` (a cached address is never the one played); the codec and HLS checks are applied again to the freshly looked-up station (refused → `UNSUPPORTED_RESOURCE` with outcome `unsupported_codec` or `hls_refused`, and no second play report), and a `CatalogueProtocolException` from `refreshStation` → `UPSTREAM_PROTOCOL_ERROR`; when the station answer came from a server, `refreshStation` is not called; a play-report `url` that is present but not `http(s)` stays a refusal, not a fallback (tested in T036); a fallback address that is not `http(s)` → `UNSUPPORTED_RESOURCE`; the `Deadline` given to every call is the same one (one budget per resolution)
- [X] T044a [P] [US3] Write `src/test/java/multiroom/radio/catalogue/CachingRadioBrowserClientTest.java` (fake delegate, injected `Clock`): a second `station(uuid)` within `station-cache-ttl` makes no delegate call and its answer has an empty `serverTag`; after the TTL it calls again; an empty `Optional` and an exception are never cached; at `station-cache-size` the oldest entry is dropped; `refreshStation` always calls the delegate, keeps the delegate's `serverTag`, replaces the cached entry when the station is found and removes it when it is not; `reportPlay` is delegated every time and never cached, so two plays of one cached station report twice

### Implementation for User Story 3

- [X] T045 [US3] Add failover to `src/main/java/multiroom/radio/catalogue/HttpRadioBrowserClient.java` per [contracts/radio-browser-calls.md](contracts/radio-browser-calls.md) "Failover classification"; WARN on each server failure (server, endpoint, result); WARN once when none answered
- [X] T046 [US3] Complete `src/main/java/multiroom/radio/catalogue/servers/DiscoveredServerSource.java`: the DNS lookup (bounded since T028) is left running when its wait times out so that a late answer still stores its names (never over a list discovered after it started); discovery as a whole gets its own `Deadline` of `min(deadline.remaining(), resolve-timeout / 2)` (research.md R7), and the DNS wait becomes the smaller of `discovery-timeout` and what is left of that discovery deadline; `/json/servers` fallback through `CatalogueHttp`, its base URI a constructor parameter (production derives `https://<discovery-host>` from `discovery-host`); TTL and stale-after-all-failed; rotation on `answered`; INFO when discovery completes (source, server count)
- [X] T047 [US3] Add the play-report fallback to `src/main/java/multiroom/radio/resolve/RadioStationResolver.java` (outcome `fallback`) per research.md R3: every kind of failed report falls back, and a station that came from the cache is looked up again with `refreshStation` first, with the codec and HLS checks applied to the fresh answer
- [X] T047a [US3] Implement `src/main/java/multiroom/radio/catalogue/CachingRadioBrowserClient.java` per research.md R13 and data-model.md (decorator over `RadioBrowserClient`, concurrent map, no lock across a call; `refreshStation` bypasses and updates the cache), reading `station-cache-ttl` and `station-cache-size` from `RadioProperties` (they exist since T009), and wrap the client bean in `src/main/java/multiroom/radio/RadioAutoConfiguration.java`

**Checkpoint**: US1–US3 green; a stalled or dead mirror never fails a play while another answers.

---

## Phase 6: User Story 4 - The operator stays in control (Priority: P3)

**Goal**: the switch-off flag, fail-fast configuration, no I/O at start-up, metrics and logs.

**Independent Test**: `ApplicationContextRunner` shows no beans with `enabled=false`, zero WireMock
requests during context start, and start-up failure for each malformed property; a
`SimpleMeterRegistry` shows the meters with closed-set tags only.

### Tests for User Story 4 (write first, see them fail) ⚠️

- [X] T048 [P] [US4] Extend `src/test/java/multiroom/radio/RadioAutoConfigurationTest.java` with the metrics wiring (beans, `enabled=false`, no I/O at start and malformed properties are already covered by T003a, T008a and T023a): without a `MeterRegistry` bean → `NoopRadioMetrics`; with `SimpleMeterRegistry` → `MicrometerRadioMetrics`; with `FilteredClassLoader(MeterRegistry.class)` the context still starts with the no-op
- [X] T049 [P] [US4] Write `src/test/java/multiroom/radio/metrics/MicrometerRadioMetricsTest.java`: `radio.resolutions` (tags `outcome`, `server`), `radio.resolution.duration` (`outcome`), `radio.catalogue.requests` (`server`, `endpoint`, `result`), `radio.discoveries` (`source`, `result`); `http_429` is a `result` of its own, apart from `http_4xx`; a value outside the closed set of `outcome`, `endpoint`, `result` or `source` is recorded as `unknown` rather than passed through (the `server` tag is not a closed set and is recorded as given); Prometheus names (`radio_resolutions_total` …) checked with `PrometheusMeterRegistry`; no method throws when the registry does
- [X] T050 [US4] Extend `src/test/java/multiroom/radio/resolve/RadioStationResolverTest.java` and `src/test/java/multiroom/radio/catalogue/HttpRadioBrowserClientTest.java` with a recording `RadioMetrics` fake: one `resolution` per `resolve` with the right outcome and server tag (the last server that answered during the resolution; `none` when nothing answered, including a cached station whose play report and fresh lookup both failed); one `catalogueRequest` per HTTP attempt; no station name, UUID or codec label ever passed as a tag; a log-capturing appender shows the INFO resolution line contains the UUID and the answering server's tag, and never the station name or `#label`

### Implementation for User Story 4

- [X] T051 [P] [US4] Implement `src/main/java/multiroom/radio/metrics/MicrometerRadioMetrics.java` (only class importing Micrometer; guards every call)
- [X] T052 [US4] In `src/main/java/multiroom/radio/RadioAutoConfiguration.java` replace the T015 no-op bean with the two nested configurations copied from `TtsAutoConfiguration` (`@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")` using `ObjectProvider<MeterRegistry>`, and `@ConditionalOnMissingClass` with the no-op)
- [X] T053 [US4] Record metrics in `src/main/java/multiroom/radio/resolve/RadioStationResolver.java`, `src/main/java/multiroom/radio/catalogue/HttpRadioBrowserClient.java` and `src/main/java/multiroom/radio/catalogue/servers/DiscoveredServerSource.java` per [contracts/observability.md](contracts/observability.md)

**Checkpoint**: all four stories green offline.

---

## Phase 7: Polish, gates and live verification

- [X] T054 [P] Rewrite `README.md`: what the extension does, the URI grammar, configuration table (link the contract), build and deploy (`-Plocal` while developing; a bare `mvn deploy` uploads to production)
- [X] T055 [P] Update `AGENTS.md`: the extension now has code (drop "planned, no code yet"), package layout from plan.md, the decision that core owns the display length of titles (R4), and that `/json/url` is called only after the codec and HLS checks (R3)
- [X] T056 [P] Add `.github/workflows/test.yml` modelled on `D:\projects-sonora\sonora-tts\.github\workflows\test.yml` only if it can resolve `ai.multiroom:*` there; otherwise skip and note why in `specs/001-play-station-by-uuid/verification.md`. If added, follow section 5 of the hub's `docs/extensions/extracting-an-extension-to-its-own-repo.md`: the last step is `if: always()` running `rm -rf ~/.m2/repository/ai/multiroom`, so `setup-java`'s `cache: maven` never saves the hub's artifacts where a fork's workflow can restore them; the trigger is `pull_request` (never `pull_request_target`); never upload `hub/` or its JARs as artifacts
- [X] T057 Run `mvn verify > "$TEMP/radio-verify.log" 2>&1; echo $?` (read the build's own exit code); confirm exit 0, JaCoCo ≥ 80% for the module and ≥ 90% for each of the `resolve`, `catalogue` and `catalogue.servers` packages, test count, and that every public type has Javadoc (constitution V); check `git ls-files --eol` shows no `w/crlf`; record command, date and counts in `specs/001-play-station-by-uuid/verification.md`
- [X] T058 Run quickstart part 1's JAR inspection (manifest entries; no root `application.yml`/`.properties`; no `org/springframework`, `com/fasterxml` or `io/micrometer` classes shaded in) and record it in `specs/001-play-station-by-uuid/verification.md`
- [ ] T059 Optional (constitution V, recommended): run SpotBugs on the module with the upstream noise filter (`EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `CT_CONSTRUCTOR_THROW`, `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`); fix or justify findings in `specs/001-play-station-by-uuid/verification.md`
- [X] T060 **Ask the owner first.** Deploy (`mvn deploy`) and restart `multiroom.service`; record the hub's start-up time before and after the deploy (time until `/actuator/extensions` answers; after the deploy it is at most 2 s longer than before); check acceptance 1 (inventory, then `enabled: false` and restore) per [quickstart.md](quickstart.md) part 2 steps 1–2; record in `specs/001-play-station-by-uuid/verification.md`
- [X] T061 **Ask the owner first** (audible). Record the `office` volume, mute and master mute; run acceptance 2–7 per quickstart part 2 steps 3–8 at low volume, stopping each test route before the next step; time `POST /api/v2/play` for one station by UUID and for its plain stream address and record both (the UUID play is at most 2 s slower); register an input with `POST /api/v2/inputs` for a UUID and check `radio.resolutions` does not move until it plays; compare one real response from each endpoint used (`byuuid`, `url`, `servers`) with the hand-written fixtures and fix the fixtures where they differ; for a station whose `url` and `url_resolved` differ in `byuuid`, check which of the two `/json/url` returns as `url`: if it is the raw `url`, change the resolver to play `url_resolved` from `refreshStation` and keep `/json/url` only for counting the play (research.md R3), with a test, before the audible steps are repeated; if HLS fails, set the `allow-hls` default to `false` in `src/main/java/multiroom/radio/config/RadioProperties.java`, update `contracts/configuration.md`, and record the evidence; record everything in `specs/001-play-station-by-uuid/verification.md`
- [X] T062 Acceptance 8 (clean-up): no ALSA playback device open, `audio.output.routes` for `office` reads 0, `office` volume and mute restored, any test configuration in `/home/tiger/multiroom.yml` reverted (restart with permission); record in `specs/001-play-station-by-uuid/verification.md`
- [ ] T063 After merge: delete `docs/backlog/play-station-by-uuid.md`, remove its row from `docs/backlog/INDEX.md` and update `station-search-api.md`'s "Depends on" (the client now exists); tag `v0.1.0` only when the owner asks

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)** → **Foundational (2)** → user stories.
- **US1 (3)** needs Foundational. **US2 (4)** builds on US1's resolver and parser (same files).
  **US3 (5)** builds on US1's client and server source. **US4 (6)** needs the beans from US1–US3 to
  record into. The auto-configuration test grows with the wiring (T003a, T008a, T023a, T048), so
  `enabled=false` and "no network at start-up" are proven before the first live deploy.
- **Polish (7)**: T054–T059 after US4; T060–T062 in order, each with permission; T063 after merge.

### Within each story

Tests first and failing → value types → collaborators → resolver → wiring.

### Parallel opportunities

- T003 ∥ T003a, then T004 (Setup).
- T006 ∥ T007 ∥ T008, then T008a; then T010 ∥ T011 ∥ T012 ∥ T013 ∥ T014 (Foundational).
- US1 tests T016–T022 (T020a included) together; then T024–T028a together.
- US2 tests T032–T035, T037 together; T038 alongside.
- US3 tests T041–T043 and T044a together.
- US4 tests T048 ∥ T049; T051 alongside.
- T054 ∥ T055 ∥ T056.

## Parallel Example: User Story 1

```text
Task: "T016 StationReferenceTest (accepted forms)"
Task: "T017 StationTitleTest"
Task: "T018 StreamAddressTest (accepted)"
Task: "T020 HttpRadioBrowserClientTest (single server)"
Task: "T022 DiscoveredServerSourceTest (DNS path)"
# then
Task: "T024 StationReference"   Task: "T025 StationTitle"   Task: "T026 StreamAddress"
Task: "T027 PinnedServerSource" Task: "T028 JdkDnsResolver + DiscoveredServerSource (DNS)"
```

---

## Implementation Strategy

### MVP (US1 + US2)

US1 alone plays a station, but US2 is also P1: without it a wrong UUID can leave inputs behind on
the owner's real system. Deliver both before any live deployment; verify offline, then run T060
and acceptance 2–3.

Every network wait is already bounded at that point (HTTP in T028a, DNS in T028), as the
constitution's playback-path rule requires of anything deployed. What the MVP lacks is failover and
the play-report fallback: with a dead first mirror, or a failed play report, a play fails cleanly
with 503 until US3.

### Incremental delivery

1. Setup + Foundational → empty extension that loads.
2. US1 + US2 → MVP: plays stations, fails cleanly.
3. US3 → resilient to mirror outages; acceptance 7.
4. US4 → operator controls and metrics; acceptance 1 and 4.
5. Polish and the remaining live checks.

---

## Notes

- Never cite `FR-`, `SC-` or `US` numbers in code comments; `RADIO-001` and `HUB-NNN` are fine.
- Commit after each task or logical group (Conventional Commits, explaining why).
- Mark tasks `[X]` as they are done.
- Live steps (T060–T062) need the owner's permission each time and follow the parent `AGENTS.md`.
