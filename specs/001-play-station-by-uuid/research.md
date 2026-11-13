# Research: Play a station by its UUID (RADIO-001)

Phase 0 of the plan. Every decision below was read from the hub (`sonora-multiroom` master,
`multiroom-api` 0.1.33) or the sibling `sonora-tts` on 2026-11-13, unless it says otherwise.
radio-browser.info was **not** contacted (constitution XII); its facts come from `AGENTS.md`
(checked 2026-11-12) and are confirmed by the live acceptance checks.

## R1. How the hub calls a resolver, and what it does on failure

- **Decision**: implement `InputEndpointResolver` (`supports`, `resolve`, `getOrder`) and throw
  **only** `InputResolutionException` from `resolve`, never another runtime exception.
- **Rationale**: `DefaultInputResolutionService.resolve` offers the input to each resolver in
  `getOrder` order, calls the first whose `supports` is true, and propagates its exception.
  `PlaybackControllerImpl` catches `RouteException | InputResolutionException` around route
  creation and unregisters the ephemeral input it made (`unregisterQuietly`) before answering.
  Any other exception reaches the generic 500 handler and **leaves the input behind**, which would
  break the spec's "nothing left behind". `V2ExceptionHandler` maps the failure code to the HTTP
  status: `INVALID_INPUT` 400; `UNSUPPORTED_RESOURCE`, `PRIVATE_RESOURCE`, `BLOCKED_RESOURCE` 422;
  `UPSTREAM_AUTH_FAILED`, `UPSTREAM_PROTOCOL_ERROR` 502; `UPSTREAM_UNAVAILABLE` 503.
- **Order**: 120 (SoundCloud is 100, YouTube 110). Schemes do not overlap, so the value only places
  the resolver in the hub's logged chain.
- **Alternatives considered**: letting `RuntimeException`s escape (rejected: leaks inputs, 500s).

## R2. Failure codes for each way a play can fail

| Situation | Failure code | HTTP | Retried by the hub's refresh? |
|---|---|---|---|
| Not a UUID (or empty) | `INVALID_INPUT` | 400 | no |
| Well-formed UUID, no such station | `INVALID_INPUT` | 400 | no |
| Codec known and not decodable | `UNSUPPORTED_RESOURCE` | 422 | no |
| HLS station while HLS is refused | `UNSUPPORTED_RESOURCE` | 422 | no |
| Stream address not `http`/`https`, or unparseable | `UNSUPPORTED_RESOURCE` | 422 | no |
| Every server tried timed out, refused or answered 5xx | `UPSTREAM_UNAVAILABLE` | 503 | yes |
| Servers answered, but with JSON that cannot be read | `UPSTREAM_PROTOCOL_ERROR` | 502 | no |

- **Rationale**: 400 means "fix the request" in the hub's REST v2, which is right for a wrong UUID.
  `RefreshExecutor.isRetriable` retries only `UPSTREAM_UNAVAILABLE` and `UPSTREAM_AUTH_FAILED`, so
  a catalogue outage during a re-lookup is retried with the hub's backoff, while a deleted station
  ends the route at once.
- Every message names the station UUID (or the malformed value, truncated), and the codec label
  when that is the reason.

## R3. Two catalogue calls per play: look up, then report the play

- **Decision**: `resolve` makes, in order:
  1. `GET /json/stations/byuuid?uuids=<uuid>`: existence, `name`, `codec`, `hls`, `url_resolved`.
     An empty array means unknown station.
  2. Codec and HLS policy checks (R5, R6). A refused station is never reported as played.
  3. `GET /json/url/<uuid>`: counts the click and returns the station's current `url`.
  4. If step 3 fails on every server tried (or answers `ok: false`, or without a usable `url`), use
     `url_resolved` from step 1, record the outcome `fallback`, and play.
- **Rationale**: `/json/url` reports the play, as the service asks, but does not return the codec
  or HLS flag the policy needs, so a lookup is needed anyway. Doing the lookup first means a
  refused station (bad codec, HLS refused) costs no click (constitution IX, "clicks only for real
  plays"), and step 1 already holds the fallback address that spec FR-006 asks for. Both calls are
  small; a play costs two requests to the same server.
- **Alternatives considered**: `/json/url` only (rejected: no codec check possible); `/json/url`
  first, then `byuuid` only on failure as the backlog sketched (rejected: would count a click for a
  station that is then refused).
- **To confirm live**: the `/json/url` answer's fields (`ok`, `message`, `stationuuid`, `name`,
  `url`) are taken from the service's documentation; WireMock fixtures are written by hand from it
  and the live acceptance run confirms the shape. A mismatch is a fixture fix, not a design change.

## R4. The station's name as the input's title

- **Decision**: put the station name in `ResolvedInputEndpoint.title` after stripping control
  characters (`Character.isISOControl`, plus line and paragraph separators), collapsing whitespace,
  and cutting at **256 code points**. `creator` stays `null`. A blank result means `title = null`.
- **Rationale**: core's `ResolvedInputNamer` → `InputNaming.nameFromResolvedTitle` names a
  `GENERATED` input only, and `ResolvedNameFormatter` already cuts to `InputDisplayNameRule`'s
  limit (64 code points today) with an ellipsis. But `InputDisplayNameRule.normalise` **refuses** a
  name containing a control character, so an unstripped catalogue name could lose its title
  entirely. The API's Javadoc asks resolvers not to shorten titles; the owner confirmed
  (2026-11-13) that core owns the display length and may raise it. The 256 limit therefore only
  bounds untrusted text (constitution X) and sits well above any display limit.
- **Alternatives considered**: cutting to 64 here (rejected: duplicates core's rule and goes stale
  when core raises it); passing the name raw (rejected: control characters make core drop it, and
  unbounded text breaks constitution X).

## R5. Codec policy

- **Decision**: map radio-browser's free-form `codec` label (trimmed, upper-cased) as
  `MP3` → `mp3`, `AAC` / `AAC+` → `aac`, `OPUS` → `opus`, `FLAC` → `flac`. Any other label,
  `OGG`, `UNKNOWN` and empty are **let through**. A mapped name is checked with the hub's
  `SupportedCodecRegistry.isSupported`; refused only when that answers `false`.
- **Rationale**: agreed with the owner (backlog decisions). The registry is a bean in
  `DecoderAutoConfiguration` (`SupportedCodecRegistryImpl`, backed by the `AudioCodec` enum), so the
  extension injects it rather than keeping a list. Today every mapped codec is supported, so the
  refusal path is exercised in tests with a stub registry and, live, only if a codec is ever
  removed; acceptance check 6 records which station was used.
- **No registry bean** (a host built without the decoder): treated as "cannot tell", so nothing is
  refused. Injected through `ObjectProvider`.

## R6. HLS stations

- **Decision**: a property `multiroom.radio.allow-hls`, default `true`. When `false`, a station
  with `hls = 1` is refused with `UNSUPPORTED_RESOURCE` and a message saying HLS is switched off.
- **Rationale**: the decision (backlog) is to allow HLS and refuse it only if acceptance check 5
  fails. A switch makes that fallback a default change plus a recorded decision, not a code change
  under time pressure, and lets the owner turn it off in production without a release.

## R7. Server discovery and failover

- **Decision**: a `ServerSource` chosen by configuration:
  - **Pinned** (`multiroom.radio.servers` non-empty): exactly that list, in the order given (so
    acceptance check 7 can put an unreachable host first); no DNS.
  - **Discovered** (default): resolve `discovery-host` (`all.api.radio-browser.info`) to its
    addresses, reverse-look-up each to a host name, keep names ending in `.api.radio-browser.info`,
    shuffle, use `https://<name>`. If DNS gives no usable names, fetch
    `https://<discovery-host>/json/servers` and take its `name` fields. Never call HTTPS by bare IP
    (the certificate names the host; constitution VII).
- The discovered list is **cached for `server-list-ttl`** (default 1 h) and discovered again lazily
  on the next play after it expires, or after a play on which every server failed. No scheduled
  refresh (constitution IX).
- **Failover**: each request tries the servers in list order (rotated so that successive plays
  start at the server that last answered), at most `max-servers-per-request` (default 3), and moves
  on after a connect error, a timeout, or a 5xx/429 answer. A 4xx other than 429 is not retried on
  another server (another mirror would answer the same).
- **DNS is bounded**: JDK `InetAddress` lookups have no timeout, so discovery runs on a small
  daemon executor and is awaited for at most `discovery-timeout` (default 3 s); a timed-out
  discovery counts as "no names" and falls back to `/json/servers`, then fails as
  `UPSTREAM_UNAVAILABLE`. The lookup goes through a `DnsResolver` interface so tests inject names
  and never touch real DNS (constitution XII).
- **No lock across the network** (constitution XI): the cached list is an `AtomicReference`; two
  plays that find it empty may both discover, and the last one wins. That costs at most one extra
  DNS lookup and never makes one play wait for another.
- **Alternatives considered**: `/json/servers` first (rejected: needs a working HTTPS host before
  discovery, and DNS is what the service documents first); a fixed default mirror (forbidden by
  constitution IX).

## R8. Time budget

- **Decision** (all configurable): `connect-timeout` 2 s, `read-timeout` 4 s per request,
  `resolve-timeout` 10 s for the whole of one `resolve` (discovery included). Each request's
  timeout is the smaller of its own and what is left of the budget; when the budget runs out the
  play fails `UPSTREAM_UNAVAILABLE`.
- **Rationale**: spec SC-003 asks for a failure within 15 s with every server unreachable. A
  per-resolve deadline is the only way to bound two calls times three servers; 10 s leaves room
  for the hub's own route set-up. A healthy mirror answers in well under a second, so SC-002's
  "no more than 2 s slower" is met.

## R9. HTTP client and JSON

- **Decision**: JDK `java.net.http.HttpClient` (one instance, built lazily on first use: building
  it opens no connection, but laziness keeps construction trivially network-free), redirects
  `NORMAL`, HTTP/1.1. JSON through Jackson's `ObjectMapper` (supplied by the host, `provided`),
  reading into `JsonNode` and mapping field by field, tolerant of unknown, missing and wrongly
  typed fields (constitution X).
- **Rationale**: constitution II (no second HTTP stack); `YouTubeInputResolver`'s `JdkDownloader`
  uses the same client. Reading through `JsonNode` rather than binding records keeps a renamed or
  retyped upstream field from failing the whole play.

## R10. User agent and the extension's version

- **Decision**: default `User-Agent: sonora-radio/<version>`, the version read from a
  build-filtered resource `multiroom/radio/radio-version.properties` (under the package path, not
  the classpath root). `multiroom.radio.user-agent` overrides it; it must not be blank or contain
  CR/LF.
- **Rationale**: the starter writes `Extension-Version` into the manifest, but in the hub's shared
  classloader `getResource("META-INF/MANIFEST.MF")` can return another JAR's manifest, and
  `Package.getImplementationVersion()` is not set by the starter. A filtered resource in this
  module's own package is unambiguous. A root properties file is avoided (constitution VIII).

## R11. Configuration validation

- **Decision**: `@ConfigurationProperties("multiroom.radio")` with `@Validated` and explicit checks
  in the properties class, so a fault fails start-up naming the property:
  timeouts positive; `resolve-timeout` ≥ `connect-timeout`; `max-servers-per-request` 1–10;
  `server-list-ttl` positive; every `servers` entry an absolute `http`/`https` URL with a host and
  no path beyond `/`, query or fragment; `discovery-host` a plain host name; `user-agent` not blank,
  no CR/LF, at most 200 characters.
- **Rationale**: constitution VIII (configuration faults abort start-up, catalogue faults never
  do). `sonora-tts` validates the same way.

## R12. Metrics and logs

- **Decision**: a `RadioMetrics` interface (Micrometer and no-op implementations, chosen exactly as
  `TtsAutoConfiguration` does, Micrometer class named by string). Meters in
  [contracts/observability.md](contracts/observability.md). Tag values come from fixed enums and
  from the server's host name (the mirror set is small and operator-controlled when pinned). No
  station name or UUID in any tag.
- Logs: INFO one line per resolution (UUID, outcome, answering server, elapsed ms); DEBUG the
  request URL and the `User-Agent` sent; WARN when a server fails over. The station name never
  appears in a log line (the UUID identifies it).

## R13. What this feature does not need

- **No REST API and no `@RestControllerAdvice`**: errors travel as `InputResolutionException`
  through the hub's own handler. Search (`station-search-api.md`) brings the first controller and
  its scoped advice.
- **Station lookups are cached, play reports are not** (constitution IX): a
  `CachingRadioBrowserClient` decorates `RadioBrowserClient` and keeps the result of `station(uuid)`
  (name, codec, HLS flag, stream address) for `station-cache-ttl` (default 5 min), at most
  `station-cache-size` entries (default 100, oldest dropped first). Only found stations are cached,
  never an empty answer or a failure. `reportPlay` always goes to the catalogue, so every play,
  re-lookups after a refused address included, is still counted. The cached fields only decide the
  codec/HLS checks, the title and the fallback address; the address that plays comes from the play
  report. Search later reuses the same decorator.
- **No hub change**: confirmed in the backlog's findings and in R1.

## R14. Build and project skeleton

- **Decision**: copy `sonora-tts`'s POM shape: parent `multiroom-extension-starter` 0.1.33 with an
  empty `<relativePath/>`; `extension.id` `radio`; `extension.name` `Radio Extension`;
  `multiroom.require_api_version` 0.1.33; version 0.1.0; `skip.wagon.upload` false; the `local`
  profile override pointing at `D:/projects-sonora/sonora-multiroom/extensions`; JaCoCo 0.8.14
  with the 80% line gate. Dependencies `provided`: `spring-boot-starter` (no web needed),
  `spring-boot-starter-validation`, `jackson-databind`, `micrometer-core`; optional
  `spring-boot-configuration-processor`. Test: `spring-boot-starter-test`, `wiremock-standalone`
  3.9.2.
- Resource filtering is enabled **only** for `multiroom/radio/radio-version.properties`.
- **Rationale**: AGENTS.md says copy `sonora-tts` rather than invent; constitution V's gate needs
  `mvn verify` plus a live inventory check.
