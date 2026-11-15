# Data Model: Search the catalogue through the hub (RADIO-002)

Nothing is persisted. These are the values that pass through one API request, what the extension
remembers in memory, and the fixed bounds. The API's field names are the extension's own;
radio-browser's JSON names appear only in the "from" columns. Cleaning rules are in
[research.md](research.md) R9; the wire format is [contracts/radio-rest-api.yaml](contracts/radio-rest-api.yaml).

## StationQuery (one search, after validation)

Built by `StationQueryParser` before any network call.

| Field | From parameter | Type | Rule |
|---|---|---|---|
| `name` | `name` | text, optional | trimmed; blank is absent; at most 100 code points; no control characters, no `%` |
| `tag` | `tag` | text, optional | as `name`; lower-cased |
| `language` | `language` | text, optional | as `name`; lower-cased |
| `countryCode` | `countryCode` | text, optional | exactly two ASCII letters; upper-cased |
| `codec` | `codec` | text, optional | as `name`, at most 32 code points |
| `bitrateMin` | `bitrateMin` | integer, optional | 0–1000000 |
| `order` | `order` | `NAME`, `VOTES`, `CLICK_COUNT`, `BITRATE` | default `VOTES` |
| `direction` | `direction` | `ASC`, `DESC` | default `DESC` for `VOTES` and `CLICK_COUNT`, `ASC` for `NAME` and `BITRATE` |
| `limit` | `limit` | integer | default 20; 1–100 |
| `offset` | `offset` | integer | default 0; 0–10000 |

## TagQuery (one tag listing)

| Field | From parameter | Type | Rule |
|---|---|---|---|
| `name` | `name` | text, optional | as `StationQuery.name`, lower-cased, and no `/` |
| `limit`, `offset` | `limit`, `offset` | integer | as in `StationQuery` |

`TagQuery` is also the key of a remembered tag page.

## Station (catalogue side, untrusted; grows from RADIO-001)

| Field | From | Type | Used by |
|---|---|---|---|
| `uuid` | `stationuuid` | UUID | both; a record without a valid one is dropped |
| `name` | `name` | text or null | both |
| `streamUrl` | `url_resolved` (else `url`) | text or null | plays only; never returned by the API |
| `codecLabel` | `codec` | text, never null | both |
| `hls` | `hls` | boolean | both |
| `favicon`, `homepage` | `favicon`, `homepage` | text or null | API |
| `countryCode` | `countrycode` | text or null | API |
| `languages` | `language` | text or null (comma-separated) | API |
| `tags` | `tags` | text or null (comma-separated) | API |
| `bitrate` | `bitrate` | integer (0 when missing) | API |
| `lastCheckOk` | `lastcheckok` | boolean | API |
| `votes`, `clickCount` | `votes`, `clickcount` | integer (0 when missing) | API |

A field of the wrong type counts as missing. Unknown fields are ignored.

## StationPage, TagPage (catalogue side)

| Field | Meaning |
|---|---|
| `stations` / `tags` | the records kept |
| `received` | how many records the catalogue's answer held, before any was dropped |

## What the API returns

| Shape | Fields |
|---|---|
| `RadioStation` | `uuid` (lower case), `uri` (`radiobrowser://<uuid>`), `name`, `favicon`, `homepage`, `countryCode`, `languages` (list), `tags` (list), `codec`, `bitrate`, `hls`, `lastCheckOk`, `votes`, `clickCount`. `name`, `favicon`, `homepage`, `countryCode`, `codec` and `bitrate` may be null; the lists may be empty, never null |
| `RadioStationPage` | `stations` (list of `RadioStation`), `nextOffset` (integer or null) |
| `RadioCountry` | `code`, `name`, `stationCount` |
| `RadioLanguage` | `name`, `code` (may be null), `stationCount` |
| `RadioTag` | `name`, `stationCount` |
| `RadioTagPage` | `tags` (list of `RadioTag`), `nextOffset` (integer or null) |
| `RadioProblem` | `type`, `title`, `detail`, `status`, `instance`, `timestamp`, `parameter` (only on a 400 about one parameter) |

Null fields are written as `null`, not left out, so every `RadioStation` has the same keys.
`RadioProblem` omits `parameter` when it does not apply.

`nextOffset` is `offset + limit` when `received == limit` and that sum is at most 10000; otherwise
null.

Country (`/json/countries`): `code` from `iso_3166_1`, `name` from `name`, `stationCount` from
`stationcount`. Language (`/json/languages`): `name`, `code` from `iso_639`, `stationCount`. Tag
(`/json/tags`): `name`, `stationCount`. Entries are returned in the catalogue's order (station
count, highest first); the extension does not sort them again.

## Remembered in memory

| What | Key | Lifetime | Bound |
|---|---|---|---|
| Station lookups (RADIO-001) | UUID | `station-cache-ttl` (5 min) | `station-cache-size` (100), oldest dropped first |
| Country list | none (one entry) | 6 h | one entry |
| Language list | none (one entry) | 6 h | one entry |
| Tag pages | `TagQuery` | 6 h | 200 pages, oldest dropped first |
| Server list (RADIO-001) | none | `server-list-ttl` (1 h); after that still used while a refresh runs | one list |
| Search results | not remembered | | |

Only successful answers are stored. Everything is lost on a hub restart.

## Server list states (changed from RADIO-001)

| State | A request gets | What else happens |
|---|---|---|
| none yet | discovery inside the request, bounded by half of what the caller has left | the result is stored |
| fresh | the list, starting at the server that last answered | nothing |
| expired | the same list, at once | one refresh starts in the background; on failure the list stays and no refresh is tried for 60 s |
| all servers failed | discovery inside the request, as for "none yet" | the result replaces the list |

## Errors

| Exception | Raised by | Answer |
|---|---|---|
| `InvalidParameterException(parameter, reason)` | the parsers, `RequestParameters`, `StationUuid` | 400 |
| `StationNotFoundException(uuid)` | `RadioCatalogueService` | 404 |
| `CatalogueProtocolException` (RADIO-001) | the catalogue client | 502 |
| `CatalogueUnavailableException` (RADIO-001) | the catalogue client, the server source | 503 with `Retry-After: 30` |

## Fixed values

| Value | Is |
|---|---|
| `limit` default / maximum | 20 / 100 |
| `offset` maximum | 10000 |
| Text filter length | 100 code points (`codec`: 32) |
| `bitrateMin` maximum | 1000000 |
| Limit sent for countries and languages | 2000 |
| List lifetime | 6 hours |
| Remembered tag pages | 200 |
| Tags / languages per station | 30 / 10 |
| Tag and language length | 64 code points |
| `favicon` / `homepage` length | 2048 characters |
| Attempt limit | half of `request-timeout` |
| `Retry-After` on 503 | 30 seconds |
| Pause after a failed background refresh | 60 seconds |
