# Contract: calls this feature adds to radio-browser.info

Adds to RADIO-001's `contracts/radio-browser-calls.md`, whose rules still hold: every request
carries the `User-Agent` and `Accept: application/json`, goes to the servers of the server source
in order, and follows the same failover classification (connect error, timeout, 5xx and 429 move
on; another 4xx or an unreadable body does not). Values from a client are percent-encoded as
UTF-8. Fixtures under `src/test/resources/radiobrowser/` follow the shapes below, which were seen
live on 2026-11-15 ([verification.md](../verification.md)); tests never reach the real service.

## `GET /json/stations/search` (metric endpoint `search`)

Always sent: `hidebroken=true`, `limit=<1–100>`, `offset=<0–10000>`, `order=<…>`, `reverse=<…>`.

| API parameter | Sent as |
|---|---|
| `name` | `name=<value>` (the catalogue matches any part) |
| `tag` | `tag=<lower case>&tagExact=true` |
| `language` | `language=<lower case>&languageExact=true` |
| `countryCode` | `countrycode=<UPPER CASE>` |
| `codec` | `codec=<value>` (the catalogue compares the whole label, ignoring case) |
| `bitrateMin` | `bitrateMin=<n>` |
| `order` | `name` → `name`, `votes` → `votes`, `clickCount` → `clickcount`, `bitrate` → `bitrate` |
| `direction` | `desc` → `reverse=true`, `asc` → `reverse=false` |

Without `tagExact` / `languageExact` the catalogue matches any tag or language that contains the
value (27 of 100 results for "jazz" had no `jazz` tag), so they are never left out.

```json
[ { "stationuuid": "…", "name": "…", "url": "…", "url_resolved": "…", "homepage": "…",
    "favicon": "…", "tags": "jazz,smooth jazz", "countrycode": "UA", "language": "russian,ukrainian",
    "codec": "MP3", "bitrate": 128, "hls": 0, "lastcheckok": 1, "votes": 42, "clickcount": 7, … } ]
```

Used: `stationuuid`, `name`, `url_resolved` (else `url`), `homepage`, `favicon`, `tags`,
`countrycode`, `language`, `codec`, `bitrate`, `hls`, `lastcheckok`, `votes`, `clickcount`. `tags`
and `language` are comma-separated text; `hls` and `lastcheckok` are 0 or 1; `bitrate` 0 means
unknown. `[]` is an empty page. A body that is not a JSON array is a protocol error. An element
that is not an object, or has no valid `stationuuid`, is dropped but counted as received.

## `GET /json/stations/byuuid?uuids=<uuid>&hidebroken=false` (endpoint `station`)

Unchanged from RADIO-001; the additional fields listed above are now read from its answer too.

## `GET /json/countries?hidebroken=true&order=stationcount&reverse=true&limit=2000` (endpoint `countries`)

```json
[ { "name": "The United States Of America", "iso_3166_1": "US", "stationcount": 6947 }, … ]
```

225 entries on 2026-11-15. With `hidebroken=true` the catalogue reports its stored count of
working stations.

## `GET /json/languages?hidebroken=true&order=stationcount&reverse=true&limit=2000` (endpoint `languages`)

```json
[ { "name": "english", "iso_639": "en", "stationcount": 11283 },
  { "name": "american english", "iso_639": null, "stationcount": 12 }, … ]
```

585 entries on 2026-11-15, 447 without `iso_639`.

## `GET /json/tags[/<filter>]?hidebroken=true&order=stationcount&reverse=true&limit=<n>&offset=<n>` (endpoint `tags`)

`<filter>` is the API's `name`, lower-cased and percent-encoded as one path segment; left out,
with its slash, when no `name` was given. The catalogue returns tags that contain it.

```json
[ { "name": "jazz", "stationcount": 1062 }, { "name": "smooth jazz", "stationcount": 235 }, … ]
```

11145 tags in all on 2026-11-15, which is why this list is never fetched whole.

## Time limits

One API request has `request-timeout` (5 s) for everything, server discovery included. Each
attempt on one server is awaited for at most half of that, or `connect-timeout + read-timeout`, or
what is left, whichever is smallest, then cancelled and counted as `timeout`.

## Server discovery (changed for plays too)

- A server list older than `server-list-ttl` is used as it is; one background refresh (DNS, then
  `/json/servers`) is started and replaces the list when it succeeds. It is given up after half of
  `resolve-timeout`. After a failed refresh none is started for 60 s.
- With no list, or after every server of the list failed, discovery runs inside the request and
  may use at most half of the time the caller has left.

## Never called by this feature

`/json/url/<uuid>` (it counts a play), votes, `/json/stations/byurl`, `/json/codecs`, favicons,
homepages. Nothing is requested unless a client asked: no polling, prefetching or scheduled
refresh.
