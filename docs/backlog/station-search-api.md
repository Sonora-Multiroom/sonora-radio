# Search the catalogue through the hub

**Status:** Idea, not specified. Written 2026-11-12, reviewed against the code 2026-11-14.

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
| `GET /api/radio/stations?name=&tag=&countryCode=&language=&codec=&bitrateMin=&order=&reverse=&limit=&offset=` | Search (`/json/stations/search`, `hidebroken=true` always) |
| `GET /api/radio/stations/{uuid}` | One station (`/json/stations/byuuid`, broken stations included, so a saved station can still be shown) |
| `GET /api/radio/countries`, `/languages` | Lists for browse screens, ordered by station count |
| `GET /api/radio/tags?name=&limit=&offset=` | Tags ordered by station count; `name` filters, because the full list is long |

There is **no play endpoint**. A station carries a ready `uri` (`radiobrowser://<uuid>`) that the
client passes to `POST /api/v2/play`. A play endpoint here would re-implement the hub's
`PlaybackControllerImpl` (ephemeral input, volume set and restore, join mode, route volume,
ducking, cleanup on failure) against `multiroom-api`, drift with every hub play feature, and answer
in a third response shape, because the hub's `PlaybackResponse` lives in `multiroom-rest`.

### Search parameters

- **`limit`**: default 20, capped by configuration (default cap 100, validated at start-up);
  always sent upstream (radio-browser defaults to 100000). **`offset`**: capped too (default
  10000), so nothing pages through the whole catalogue.
- **`order`**: one of a fixed list (`name`, `votes`, `clickCount`, `bitrate`), mapped to
  radio-browser's names by the extension; anything else is 400 here, never an upstream error.
  **`reverse`**: boolean. `votes` and `clickCount` default to descending.
- **No filters at all** returns the most-clicked stations (`clickCount`, descending). That is the
  home screen's "popular" list; no separate `topclick` / `topvote` endpoints.
- `countryCode` is ISO 3166-1 alpha-2, validated before any network call.

### Response shapes

Names are camelCase like REST v2. Schema names carry a `Radio` prefix: springdoc keys schemas by
simple class name across the hub's shared classpath (the reason `sonora-tts` has
`TtsErrorResponse`), and `catalogue.Station` already exists here.

- **`RadioStation`**: `uuid`, `uri`, `name`, `favicon`, `homepage`, `countryCode`, `languages`
  (array), `tags` (array), `codec`, `bitrate` (null when unknown), `hls`, `lastCheckOk` (boolean),
  `votes`, `clickCount`, `playable`. Not radio-browser's raw JSON, so a change upstream does not
  break clients.
- **`playable`** is `false` only when the hub is known to refuse the station: the same decision
  the resolver makes (`CodecPolicy`, and HLS when `allow-hls=false`). It is not a promise that the
  stream works. Today the hub decodes every codec the policy maps (MP3, AAC, AAC+, OPUS, FLAC) and
  unmapped labels are let through, so in practice every station is `playable`; the field starts to
  matter only once the policy knows labels the hub cannot play (open question).
- **Unplayable stations are flagged, not hidden.** Hiding them after the upstream query would
  return short pages, and a short page would no longer mean the last one.
- **Search result**: a JSON array of `RadioStation`, like REST v2's lists. A page shorter than
  `limit` is the last page.
- **`RadioCountry`**: `code` (ISO 3166-1 alpha-2), `name` (English), `stationCount`.
  **`RadioLanguage`**: `code` (ISO 639, may be null), `name`, `stationCount`. **`RadioTag`**:
  `name`, `stationCount`. Clients localise country and language names from the codes.

### Errors

RFC 7807 problem details in the extension's own record (schema `RadioProblem`; the hub's
`ErrorResponseV2` is in `multiroom-rest` and cannot be used), from a `@RestControllerAdvice` scoped
to this module's API interfaces.

| Status | When |
|---|---|
| 400 | A malformed UUID, country code, `order`, `limit` or `offset`; no network call is made |
| 404 | A well-formed UUID the catalogue does not know |
| 502 | The catalogue answered with something unreadable, or refused the request |
| 503 | No catalogue server answered within the budget |

### Time budget and cache

- **Budget:** a new `multiroom.radio.request-timeout` (default 5 s) bounds one REST request,
  discovery and failover included. `resolve-timeout` (10 s) stays for plays; it is too long for a
  search screen.
- **Lists** (countries, languages, tags per filter): cached for hours.
- **Search results:** cached briefly (about a minute), keyed by the normalised parameters, with a
  size bound, oldest dropped first, like the station cache.
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

1. `GET /api/radio/stations?name=kiss&countryCode=UA&limit=10` returns at most 10 stations, each
   with a UUID, `uri`, name, codec, bitrate and `playable`.
2. `GET /api/radio/stations` with no parameters returns 20 stations ordered by click count,
   highest first.
3. `GET /api/radio/countries`, `/languages` and `/tags?name=jazz` return lists ordered by station
   count; a second call within the cache lifetime does not reach radio-browser: the extension's
   catalogue request counter for that endpoint, read from `/actuator/metrics`, does not grow.
4. `GET /api/radio/stations/{uuid}` for a station from step 1 returns the same station; an unknown
   UUID answers 404 and a malformed one 400, both as problem details.
5. The endpoints and the `Radio*` schemas appear in the hub's `/api-docs` (the hub scans the whole
   `multiroom` package, so no hub change is expected).
6. Check the upstream facts this item assumes and correct it if they differ: `tags` and `language`
   arrive as comma-separated strings, `bitrate` 0 means unknown, the size of the full tag list,
   and the fields of `/json/countries` and `/json/languages`.

Not live: "every configured server unreachable answers 503 within `request-timeout`, and the rest
of the hub keeps answering" is a WireMock test in `mvn verify`. On production it would need pinned
dead servers and two restarts for something a test proves exactly.

The play itself is RADIO-001's acceptance; this feature only checks that `uri` from step 1 has the
form RADIO-001 accepts.

## Open questions

- **Labels the hub cannot play:** should `CodecPolicy` learn radio-browser labels that are known
  not to work (for example WMA, or video mixes such as `AAC,H.264`), so that `playable` and the
  resolver's refusal mean something? Needs a look at which labels the catalogue really uses.
- **A parameter to hide unplayable stations** (`playableOnly=true`, accepting short pages): only if
  a client asks for it once `playable` is ever false.
- **Cache lifetimes:** hours for lists and a minute for searches are guesses; settle the defaults
  and whether they are configurable in the spec.
- **Client spec copies:** `sonora-cli/api/openapi.json` and `sonora-mobile/api/openapi.json` will
  contain `/api/radio/**` only if fetched from a hub with the extension loaded. Decide in those
  repositories whether the radio paths belong in the shared copy.
