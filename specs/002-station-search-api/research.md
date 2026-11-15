# Research: Search the catalogue through the hub (RADIO-002)

Decisions taken while planning, each with its reason and what was rejected. Upstream facts come
from the catalogue's documentation, its server source (`segler-alex/radiobrowser-api-rust`) and the
live checks of 2026-11-15 in [verification.md](verification.md). `R<n>` numbers are local to this
feature; "RADIO-001 R<n>" points at `specs/001-play-station-by-uuid/research.md`.

## R1. Where the REST layer lives and how it is registered

- **Decision**: a new package `multiroom.radio.rest` shaped like `sonora-tts`'s: `api/` (interfaces
  carrying the mappings and the OpenAPI annotations), `controller/`, `dto/`, `advice/`. The
  controllers and the advice are declared as `@Bean`s in a nested configuration of
  `RadioAutoConfiguration`, conditional on a servlet web application, not found by a component
  scan.
- **Rationale**: every bean of this extension is declared explicitly today, which is what lets
  `ApplicationContextRunner` tests prove "`enabled=false` contributes nothing". A `@RestController`
  or `@RestControllerAdvice` class registered through `@Bean` is picked up by Spring MVC exactly
  like a scanned one. The web condition keeps the resolver usable on a host without Spring MVC.
- **Build**: `spring-boot-starter-web` and `io.swagger.core.v3:swagger-annotations-jakarta`
  (2.2.47, the version `multiroom-rest` ships; confirm against the installed hub when
  implementing) are added at `provided` scope, as in `sonora-tts`. Nothing new is bundled.
- **Alternatives**: `@ComponentScan("multiroom.radio")` as `sonora-tts` does: rejected, it would
  be the only implicit registration in this module.

## R2. Reading and validating request parameters

- **Decision**: each handler declares its parameters as typed, optional `@RequestParam`s (so the
  generated API description is accurate) plus one hidden `@RequestParam MultiValueMap<String,
  String>` holding everything the client sent. A small `RequestParameters` helper checks that map
  first: a name outside the handler's allowed set, or a name sent twice, is refused. Values are
  then validated by `StationQueryParser` / `TagQueryParser`, which throw one exception type,
  `InvalidParameterException(parameter, reason)`.
- **Rationale**: Spring ignores unknown parameters and joins repeated ones with a comma, so both
  must be checked from the raw map. One exception type gives one 400 path that always names the
  parameter. A value Spring cannot convert (`limit=abc`) raises
  `MethodArgumentTypeMismatchException`, which the advice maps to the same answer.
- **Rules** (fixed values): text filters are trimmed, a blank one counts as absent; `name`, `tag`
  and `language` at most 100 code points, `codec` at most 32; control characters and `%` refused;
  `countryCode` exactly two ASCII letters; `bitrateMin` 0–1000000; `limit` 1–100; `offset`
  0–10000; `order` and `direction` exactly as written in the contract (case-sensitive).
- **Tag list `name` filter**: also refuses `/`. The catalogue takes this filter as a path segment
  (`/json/tags/<filter>`), and whether its servers accept an encoded `/` there is not documented.
  Refusing can be relaxed later without breaking clients.
- **Alternatives**: an interceptor on `/api/radio/**`: rejected, more wiring for the same check.
  All parameters as strings parsed by hand: rejected, the API description would lose the types.

## R3. Errors

- **Decision**: one record `RadioProblem` (`type`, `title`, `detail`, `status`, `instance`,
  `timestamp`, and `parameter` on 400s), the same fields as the hub's `ErrorResponseV2` without
  its route-specific ones. `RadioExceptionHandler` is a `@RestControllerAdvice(assignableTypes =
  {the API interfaces})` answering `application/problem+json`:

  | Exception | Status | `type` |
  |---|---|---|
  | `InvalidParameterException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 | `urn:multiroom:radio:error:invalid-parameter` |
  | `StationNotFoundException` | 404 | `urn:multiroom:radio:error:station-not-found` |
  | `CatalogueProtocolException` | 502 | `urn:multiroom:radio:error:catalogue-bad-answer` |
  | `CatalogueUnavailableException` | 503, `Retry-After: 30` | `urn:multiroom:radio:error:catalogue-unavailable` |
  | any other `Exception` | 500 | `urn:multiroom:radio:error:internal-error` |

- **Unknown paths and wrong methods**: a scoped advice is not consulted when no handler was chosen
  (Spring applies `assignableTypes` to the handler's type, and there is none), so a `POST` to
  `/api/radio/stations` would get Spring's default body. A hidden fallback handler mapped to
  `/api/radio/**` for every method catches what the real mappings do not: it answers 405
  (`method-not-allowed`, with `Allow: GET`) for a known path and 404 (`not-found`) otherwise, as
  problem details. It is excluded from the API description.
- **Rationale**: the hub's `V2ExceptionHandler` is limited to its own package, so nothing else
  shapes this extension's errors. `Retry-After` is a fixed 30 s: the hub cannot know when a mirror
  returns, and half a minute is short enough for a person and long enough not to hammer.
- **Detail text** never echoes a client's value longer than 32 characters and never contains
  catalogue text; the 500 detail is a fixed sentence (the exception goes to the log).
- **Alternatives**: reuse `ErrorResponseV2`: impossible, it is in `multiroom-rest`. Spring's
  `ProblemDetail`: rejected, its schema name would collide across extensions in `/api-docs` and it
  cannot carry a named `parameter` without an untyped map.

## R4. The catalogue interface

- **Decision**: `RadioBrowserClient` stays the one interface (constitution IV) and gains
  `search(StationQuery, Deadline)`, `countries(Deadline)`, `languages(Deadline)` and
  `tags(TagQuery, Deadline)`. The `Station` record gains the fields the API shows. Lists come back
  as `StationPage` / `TagPage`: the kept records plus `received`, the number of records in the
  catalogue's answer before any was dropped, which is what `nextOffset` is worked out from.
- **Untrusted text stays raw in `catalogue`** (as today for `name`); it is cleaned where it leaves
  the extension: `StationTitle` for plays, the new `RadioStationMapper` for the API. `Text` moves
  from `resolve` to its own package so both can use it, and the strict UUID pattern moves from
  `StationReference` to a shared `StationUuid`.
- **Alternatives**: a second interface for search: rejected, two interfaces over one HTTP client
  and one decorator chain for seven methods.

## R5. What is asked of the catalogue

- **Decision**: see [contracts/radio-browser-calls.md](contracts/radio-browser-calls.md). In short:
  search always sends `hidebroken=true`, `limit`, `offset`, `order`, `reverse`, and with a `tag` or
  `language` also `tagExact=true` / `languageExact=true`; lists send `hidebroken=true`,
  `order=stationcount`, `reverse=true` and a `limit` (2000 for countries and languages).
- **`direction` to `reverse`**: `desc` is `reverse=true`, `asc` is `reverse=false`, always sent.
- **`tag` and `language` are sent in lower case**: the catalogue stores them that way (seen live),
  and it makes the list cache key and the request the same for `Jazz` and `jazz`.
- **Values are percent-encoded as UTF-8** by one helper; nothing from a client is concatenated
  into a URL unencoded.
- **A 4xx from the catalogue** stays a protocol error (502) with no failover, as in RADIO-001:
  every invalid input is refused before the call, so a 4xx means the two sides disagree.

## R6. Time budget

- **Decision**: a new setting `multiroom.radio.request-timeout` (default 5 s, positive). A REST
  request builds `Deadline.after(clock, requestTimeout)` with an **attempt limit** of half the
  budget. `Deadline` gains an optional attempt limit; `CatalogueHttp.get` caps its wait by it as
  well as by `connect-timeout + read-timeout` and what is left. Plays pass no attempt limit and
  behave as before.
- **Rationale**: today one attempt may wait 6 s (`connect` 2 s + `read` 4 s), longer than the
  whole search budget, so a slow first mirror would leave nothing for the second. Carrying the
  limit on the `Deadline` changes no method signature in the client or the server source.
- **Alternatives**: a fixed 2 s constant (the first draft): rejected in the second review, it had
  to be validated against the configurable budget. Separate search timeouts for connect and read:
  rejected, three more settings for one behaviour.

## R7. Server list: expiry no longer costs a request

- **Decision**, in `DiscoveredServerSource`, for plays and REST requests alike:
  1. **A list past `server-list-ttl` is returned at once** and a refresh starts on the existing
     discovery executor, at most one at a time. The refresh runs DNS and then the `/json/servers`
     fallback with its own bound (half of `resolve-timeout`) and replaces the list when it
     succeeds. When it fails the old list stays, and the next refresh is not tried for 60 s.
  2. **No list, or a list whose servers all failed** (`allFailed`): discovery runs inside the
     request as today, but its share is **half of what the caller has left** instead of half of
     `resolve-timeout`. For a play that is the same 5 s as before; for a search it is 2.5 s, so
     half the budget is left for the search itself.
- **Rationale**: as written for RADIO-001, an expired list was rediscovered inside the request for
  up to 5 s, the whole search budget, once an hour, on a network whose DNS is slow. The refresh is
  still started only by a request, so it is not a scheduled refresh (constitution IX). A DNS
  answer that arrives after its wait is already kept for the next request (RADIO-001).
- **Cold start**: the first request after a hub restart with DNS slower than its share answers 503
  within the budget; the late DNS answer then serves the next request. Accepted and stated in the
  spec.
- **Alternatives**: discover at start-up: forbidden (no network during bean construction).
  Persist the list to disk: rejected, a file and its location for a once-per-restart cost.

## R8. Caches

- **Decision**: `CachingRadioBrowserClient` keeps, beside the station cache: the country list, the
  language list, and tag pages keyed by `(name, limit, offset)`, each for a fixed 6 hours; at most
  200 tag pages, oldest dropped first. Searches pass straight through. Failures are never stored
  (an exception stores nothing). The bounded, time-limited map already written for stations is
  extracted into one small generic class (`BoundedTtlCache`) and used for all of them.
- **Concurrent identical requests are not shared** (spec): two list requests at the same moment
  on a cold cache both reach the catalogue; the second result replaces the first.
- **Station lookups** for the API go through the existing 5-minute station cache, so `votes`,
  `clickCount` and `lastCheckOk` of a single station may be that old. A play's own refresh rules
  are unchanged (RADIO-001 R13).
- **Search results do not feed the station cache**: it would save one small request and mix two
  purposes; the address that plays still comes only from the play report or a fresh lookup.
- **No lock is held across a catalogue call**; the caches are concurrent maps and atomic
  references.

## R9. Cleaning what the catalogue sends

- **Decision**: `RadioStationMapper` (and the list mapping) apply these fixed bounds:

  | Value | Rule |
  |---|---|
  | station `name` | `StationTitle` rules: control characters removed, whitespace collapsed, 256 code points; nothing left gives an empty name (the station is still returned) |
  | `tags`, `languages` | split on `,`, trimmed, control characters removed, blanks and repeats dropped; an entry longer than 64 code points is **dropped, not cut** (a cut tag matches nothing when used as a filter); at most 30 tags and 10 languages, in the catalogue's order |
  | `favicon`, `homepage` | kept only if at most 2048 characters, parseable, scheme `http` or `https`, with a host; otherwise null. Never fetched |
  | `countryCode` | two ASCII letters, upper-cased; otherwise null |
  | `codec` | control characters removed, 32 code points; blank gives null |
  | `bitrate` | a positive whole number; 0, negative or not a number gives null |
  | `hls`, `lastCheckOk` | `1` or `true` is true; anything else false (the catalogue sends 0/1) |
  | `votes`, `clickCount` | non-negative whole numbers; anything else 0 |
  | country `name` | control characters removed, 100 code points; a country without a valid code is dropped |
  | language `name`, tag `name` | control characters removed; blank or longer than 64 code points drops the entry |
  | language `code` | 2 or 3 ASCII letters, lower-cased; otherwise null |

- **Evidence**: one live station carried 58 tags; the longest tag in the catalogue is 103
  characters; 447 of 585 languages have no code.
- **A record without a valid `stationuuid` is dropped**; it still counts in `received`.

## R10. Paging

- **Decision**: `nextOffset = offset + limit` when `received == limit` and `offset + limit <=
  10000`; otherwise null. The same for the tag list.
- **Rationale**: the hub knows how many records the catalogue returned; the client only knows how
  many survived cleaning. A full last page costs the client one extra request that returns an
  empty list and a null `nextOffset`.

## R11. The API description

- **Decision**: the API interfaces carry `@Tag(name = "Radio")`, `@Operation` with stable
  `operationId`s (`searchRadioStations`, `getRadioStation`, `listRadioCountries`,
  `listRadioLanguages`, `listRadioTags`), parameter constraints (`minimum`, `maximum`, `enum`,
  defaults) and every error response. DTO records are named without a prefix in Java only where
  no clash exists; each declares `@Schema(name = "Radio…")`. The hub's springdoc scans the whole
  `multiroom` package, so no hub change is needed.
- **The contract** is written by hand as [contracts/radio-rest-api.yaml](contracts/radio-rest-api.yaml);
  a `@WebMvcTest` asserts the shapes, and the live check compares `/api-docs` with it.

## R12. Metrics and logs

- **Decision**: `radio.catalogue.requests` gains the `endpoint` values `search`, `countries`,
  `languages`, `tags`. No new meter: the hub's own `http.server.requests` already times
  `/api/radio/**` by URI template. A background server-list refresh counts in `radio.discoveries`
  like any discovery.
- **Logs**: DEBUG for each API request (endpoint, records returned, answering server or `cache`,
  elapsed ms), never the filter text; WARN once for a 502 or 503 (endpoint, servers tried); ERROR
  with the stack trace for a 500.

## R13. Version and build gates

- **Decision**: the extension becomes 0.2.0 (a new feature; `Require-API-Version` stays
  `[0.1.33,0.2.0)`, no newer hub API is used). The JaCoCo 90% line gate is extended to the `rest`
  package, which holds the mapping from catalogue JSON to the API's shapes (constitution III).
- **Tests**: `@WebMvcTest` slices for the controllers with the advice and a mocked
  `RadioCatalogueService`; WireMock for the new catalogue calls with hand-written fixtures shaped
  like the live answers; `ApplicationContextRunner` for the wiring, including "no web application,
  no controllers" and the new setting's validation.

## R14. Live checks done before planning

- **Decision**: acceptance steps 6 and 7 were run on 2026-11-15 with the owner's permission,
  before the hub had the feature. Constitution XII places live contact at verification time; the
  deviation is recorded in the plan's Complexity Tracking.
- **Rationale**: the answers decide the design (exact-match flags, list sizes, 0/1 booleans, tag
  bounds). Learning them after the build would have meant correcting the spec and the code.
