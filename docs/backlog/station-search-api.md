# Search the catalogue through the hub

**Status:** Specified in `specs/002-station-search-api/` (2026-11-15), where it was revised after
two critical reviews; the spec wins where the two differ. Written 2026-11-12, reviewed against the
code 2026-11-14 and against the catalogue server's source 2026-11-15.

**Origin:** The decision of 2026-11-12 to put radio-browser.info in a hub extension (see
[AGENTS.md](../../AGENTS.md)): one search implementation for the CLI, MCP and the mobile app.

**Depends on:** RADIO-001 (implemented, live verification pending: the extension and its
radio-browser client, `catalogue` package).

**Effort:** Medium. A REST controller with its own OpenAPI and scoped error handler, new catalogue
calls (search, lists), a response cache, tests. Client work (CLI commands, `hub` package methods,
MCP tool, mobile screen) is separate and goes into those repositories' backlogs once the API is
fixed.

## Problem

- Playing a station by UUID is useless if nobody can find the UUID. Search, browse by country,
  genre (tag) and language are what the catalogue is for.
- If each client called radio-browser directly, search would be written twice (Kotlin and Go),
  each client would need its own mirror handling, and results could include stations the hub
  cannot play.

## Proposal

Endpoints under `/api/radio/**` (the extension's own namespace, like `/api/tts/**`; not part of
REST v2). They answer 404 on a hub without the extension, or with `multiroom.radio.enabled=false`;
clients must treat that as "radio not available".

| Endpoint | What |
|---|---|
| `GET /api/radio/stations?name=&tag=&countryCode=&language=&codec=&bitrateMin=&order=&direction=&limit=&offset=` | Search (`/json/stations/search`, `hidebroken=true` always) |
| `GET /api/radio/stations/{uuid}` | One station (`/json/stations/byuuid`, broken stations included, so a saved station can still be shown) |
| `GET /api/radio/countries`, `/languages` | Lists for browse screens, ordered by station count, broken stations not counted |
| `GET /api/radio/tags?name=&limit=&offset=` | Tags ordered by station count; `name` filters, because the full list is long |

Unknown parameters answer 400 (a typo such as `countrycode` must not return an unfiltered list).

There is **no play endpoint**. A station carries a ready `uri` (`radiobrowser://<uuid>`) that the
client passes to `POST /api/v2/play`. A play endpoint here would re-implement the hub's
`PlaybackControllerImpl` (ephemeral input, volume set and restore, join mode, route volume,
ducking, cleanup on failure) against `multiroom-api`, drift with every hub play feature, and answer
in a third response shape, because the hub's `PlaybackResponse` lives in `multiroom-rest`.

### Search parameters

- **`limit`**: default 20, at most 100 (a fixed contract value, not a setting); always sent
  upstream (radio-browser defaults to 100000). **`offset`**: at most 10000, so nothing pages
  through the whole catalogue. Out-of-range values are 400, never silently reduced.
- **`order`**: one of a fixed list (`name`, `votes`, `clickCount`, `bitrate`), mapped to
  radio-browser's names by the extension; anything else is 400 here, never an upstream error.
  **`direction`**: `asc` or `desc`; defaults to `desc` for `votes` and `clickCount`, `asc` for
  `name` and `bitrate`. (No `reverse`: upstream's flag means the opposite of these defaults.)
- **`tag`, `language`, `codec`** match the whole value only; there is no substring or whole-word
  mode, so the extension never filters the catalogue's page. Upstream matches substrings unless
  told otherwise, so the extension always sends `tagExact=true` and `languageExact=true`. Find the
  exact tag through `/tags?name=`.
- **Ties**: upstream sorts by the one chosen column with no second key, so stations with equal
  values may repeat or be skipped between pages in every order. A client that pages drops stations
  whose `uuid` it has already received.
- **No `order`** means `votes`, descending, with or without filters. No filters at all is the
  "popular" list; a home screen that wants "trending" sends `order=clickCount`. No separate
  `topclick` / `topvote` endpoints.
- `countryCode` is ISO 3166-1 alpha-2, validated before any network call. A `%` in a text filter
  is 400 (upstream treats it as a wildcard).

### Response shapes

Names are camelCase like REST v2. Schema names carry a `Radio` prefix: springdoc keys schemas by
simple class name across the hub's shared classpath (the reason `sonora-tts` has
`TtsErrorResponse`), and `catalogue.Station` already exists here.

- **`RadioStation`**: `uuid`, `uri`, `name`, `favicon`, `homepage`, `countryCode`, `languages`
  (array), `tags` (array), `codec`, `bitrate` (null when unknown), `hls`, `lastCheckOk` (boolean),
  `votes`, `clickCount`. Not radio-browser's raw JSON, so a change upstream does not break
  clients.
- **No `playable` field (for now).** The hub decodes every codec the policy maps (MP3, AAC, AAC+,
  OPUS, FLAC) and lets unmapped labels through, so nearly every station would be playable. Only
  HLS with `allow-hls=false` is refused, and the play request reports that; `hls` is in the shape.
  Add `playable` (a non-breaking change) once the policy knows labels the hub cannot play.
- **Stations the hub would refuse are returned, not hidden.** Hiding them after the upstream query
  would return short pages, and a short page means the last one.
- **Search result**: an object, `RadioStationPage`: `stations` (array of `RadioStation`) and
  `nextOffset` (the next page's `offset`, null on the last page). The hub sets `nextOffset` when
  upstream returned a full page, counted before invalid records are dropped, so a client never
  infers the end from a page's length. REST v2's lists are bare arrays, but they are not paged.
  The tag list answers the same way (`RadioTagPage`: `tags`, `nextOffset`).
- **`RadioCountry`**: `code` (ISO 3166-1 alpha-2), `name` (English), `stationCount`.
  **`RadioLanguage`**: `code` (ISO 639, may be null), `name`, `stationCount`. **`RadioTag`**:
  `name`, `stationCount`. Clients localise country and language names from the codes. Countries
  and languages are returned whole, as bare arrays.

### Errors

RFC 7807 problem details in the extension's own record (schema `RadioProblem`; the hub's
`ErrorResponseV2` is in `multiroom-rest` and cannot be used), from a `@RestControllerAdvice` scoped
to this module's API interfaces.

| Status | When |
|---|---|
| 400 | A malformed UUID, country code, `order`, `direction`, `limit`, `offset` or `bitrateMin`, or an unknown parameter; no network call is made |
| 404 | A well-formed UUID the catalogue does not know |
| 502 | The catalogue answered with something unreadable, or refused the request |
| 503 | No catalogue server answered within the budget (with `Retry-After`) |

Content type `application/problem+json`, also for a wrongly typed value (`limit=abc`), a method
that is not allowed and an unexpected 500: the hub's `V2ExceptionHandler` covers only its own
package, so the extension's advice must handle them.

### Time budget and cache

- **Budget:** a new `multiroom.radio.request-timeout` (default 5 s) bounds one REST request,
  discovery and failover included. `resolve-timeout` (10 s) stays for plays; it is too long for a
  search screen. Each attempt on one server is bounded by half the budget so failover fits.
- **Server list:** RADIO-001 remembers the discovered list (1 hour) and the last good server, but
  looks an expired list up inside the request, for up to half of `resolve-timeout` (5 s, the whole
  search budget), and DNS on the home network is slow. Change `DiscoveredServerSource`: an expired
  list is still used while the new look-up runs off the request, and a look-up with no list at all
  is bounded by the caller's remaining budget.
- **Lists** (countries, languages, tags per filter): cached for 6 hours (fixed), asked upstream
  with `hidebroken=true` (upstream then reports its stored working-station count) so counts are
  close to what search returns. Countries and languages are asked with an explicit fixed limit.
- **Search results:** not cached. On one household hub a one-minute cache would rarely hit; it can
  return if a client is seen repeating searches. For the same reason, identical requests in flight
  do not share an upstream call.
- **Station lookups** reuse `CachingRadioBrowserClient`; the `Station` record grows to carry the
  fields above. A search result never supplies the address that plays: that still comes from the
  play report (RADIO-001, research R3).
- New `endpoint` metric values for the new calls (`search`, `countries`, `languages`, `tags`).

### Untrusted text

Names and tags are community-edited: strip control characters and bound them (name 256 code
points like the resolver; each tag and the number of tags bounded too). `favicon` and `homepage`
are returned only when they are `http` or `https` URLs of bounded length, otherwise null; the
extension never fetches them and the client decides whether to load them.

### Votes

Left out. Every vote would come from the one household IP, and nothing here needs them.

## Acceptance (live)

Run once by hand while the feature is verified against the real hub (`multiroom.lan`), never in
`mvn verify`; record commands, date and results in the feature's `verification.md`. Every step is
a read-only request and makes no sound. Deploying the JAR and restarting the hub need permission
as always.

1. `GET /api/radio/stations?name=kiss&countryCode=UA&limit=10` returns at most 10 stations in
   `stations`, each with a UUID, `uri`, name, codec and bitrate, ordered by votes, highest first,
   and a `nextOffset` that is 10 or null; following it reaches a page where it is null.
2. `GET /api/radio/stations` with no parameters returns 20 stations ordered by votes, highest
   first; with `order=clickCount`, 20 ordered by click count.
3. `GET /api/radio/countries`, `/languages` and `/tags?name=jazz` return lists ordered by station
   count, and one small country's count is close to what a search for it returns; a second call
   within the cache lifetime does not reach radio-browser: the extension's
   catalogue request counter for that endpoint, read from `/actuator/metrics`, does not grow.
4. `GET /api/radio/stations/{uuid}` for a station from step 1 returns the same station; an unknown
   UUID answers 404 and a malformed one 400, both as problem details.
5. The endpoints and the `Radio*` schemas appear in the hub's `/api-docs` (the hub scans the whole
   `multiroom` package, so no hub change is expected).
6. Check the upstream facts this item assumes and correct it if they differ: `tags` and `language`
   arrive as comma-separated strings, `bitrate` 0 means unknown, the size of the full tag list,
   the sizes of the country and language lists, and the fields of `/json/countries` and
   `/json/languages` (`iso_3166_1`, `iso_639` per the documentation). Also (the exact-match and
   `hidebroken` points were read in the server's source on 2026-11-15; confirm the running servers
   agree): `language=ukrainian` returns
   multi-language stations; `tag=jazz` does not return a station tagged only "acid jazz"; how
   stable the order among equal click counts and equal votes is across repeated calls and adjacent
   pages (record repeats and gaps for each); the
   lists accept `hidebroken` and their counts match search.

Step 6 and the codec labels were checked on 2026-11-15, before planning; every assumption held
(results in `specs/002-station-search-api/verification.md`).

Not live: "every configured server unreachable answers 503 within `request-timeout`, and the rest
of the hub keeps answering" is a WireMock test in `mvn verify`. On production it would need pinned
dead servers and two restarts for something a test proves exactly.

The play itself is RADIO-001's acceptance; this feature only checks that `uri` from step 1 has the
form RADIO-001 accepts.

## Open questions

- **Labels the hub cannot play:** should `CodecPolicy` learn radio-browser labels that are known
  not to work, so that a `playable` field and the resolver's refusal can mean something? The
  labels were recorded on 2026-11-15 (`specs/002-station-search-api/verification.md`): no `WMA`
  and no `OPUS` label exists (so the `OPUS` mapping never matches); candidates to refuse are
  `AAC,H.264`, `AAC+,H.264`, `UNKNOWN,H.264` (59 stations), `MP4` (17) and `FLV` (13). Whether
  `OGG` (486) and `UNKNOWN` (1669) stations play needs audible tests.
- **A `playable` field and a parameter to hide unplayable stations** (`playableOnly=true`,
  accepting short pages): only once the hub knows an unplayable label and a client asks.
- **Compatibility promise (decided):** `/api/radio/**` stays unversioned, like `/api/tts/**`.
  Within a released version, existing parameters, fields and statuses do not change
  incompatibly; additions are allowed, so clients ignore unknown fields. A breaking change needs a
  new path.
- **Client spec copies:** `sonora-cli/api/openapi.json` and `sonora-mobile/api/openapi.json` will
  contain `/api/radio/**` only if fetched from a hub with the extension loaded. Decide in those
  repositories whether the radio paths belong in the shared copy.
