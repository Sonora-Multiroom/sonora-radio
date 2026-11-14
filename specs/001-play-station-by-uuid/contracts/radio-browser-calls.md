# Contract: calls this feature makes to radio-browser.info

The extension's outbound surface. Every request carries `User-Agent` (see
[configuration.md](configuration.md)) and `Accept: application/json`, and is bounded by the
per-request timeouts and the per-resolution budget. WireMock fixtures under
`src/test/resources/radiobrowser/` follow these shapes; tests never reach the real service.

## DNS discovery (no HTTP)

1. Forward lookup of `discovery-host` → addresses.
2. Reverse lookup of each address → host name; keep names ending in `.api.radio-browser.info`.
3. Shuffle; each becomes `https://<name>`.

## `GET /json/servers` (discovery fallback)

Called on `https://<discovery-host>` only when DNS gave no usable names. DNS and this request
together may use at most half of `resolve-timeout`. A DNS lookup that answers after its wait timed
out is not thrown away: its names become the server list for the next play.

```json
[ { "ip": "…", "name": "de1.api.radio-browser.info" }, … ]
```

Used: `name` (kept only if it ends in `.api.radio-browser.info`).

## `GET /json/stations/byuuid?uuids=<uuid>&hidebroken=false`

Lookup; never reports a play.

```json
[ { "stationuuid": "…", "name": "…", "url": "…", "url_resolved": "…",
    "codec": "MP3", "hls": 0, "lastcheckok": 1, … } ]
```

Used: `stationuuid`, `name`, `url_resolved` (else `url`), `codec`, `hls`. `[]` → unknown station.
`hidebroken=false` so a station failing its last check is still found (spec edge case).

## `GET /json/url/<uuid>`

Reports the play (one click per client address, station and day) and returns the address.

```json
{ "ok": true, "message": "retrieved station url", "stationuuid": "…", "name": "…", "url": "…" }
```

Used: `ok`, `url`. Called only after the station passed the codec and HLS checks.

- `ok` counts as failed only when it is an explicit `false` (boolean, or the string `"false"`);
  missing or of another type, with a usable `url`, is a success.
- A missing or blank `url` is a failed report (fallback).
- A `url` that is present but not `http(s)` fails the play (`unsupported_address`); it is not a
  fallback.

## Time limits

Each attempt (connecting, response headers and response body together) is awaited for at most the
smaller of `connect-timeout + read-timeout` and what is left of `resolve-timeout`, then cancelled
and counted as `timeout`. A server that sends headers and then stalls is a timeout like any other.

## Failover classification

| Result | Next server? |
|---|---|
| Connect error, timeout, HTTP 5xx, HTTP 429 | yes, while servers and budget remain |
| HTTP 4xx other than 429 | no (another mirror would answer the same); `upstream_protocol_error` for `station`, fallback for `play` |
| 2xx with a body that is not the expected JSON shape | no; `upstream_protocol_error` for `station`, fallback for `play` |

"Fallback for `play`" means the station lookup's address is played. When that lookup came from the
station cache, the station is first looked up again (`byuuid`, same failover rules): not found
fails the play as an unknown station, unreachable as `upstream_unavailable`.

## Never called by this feature

Search, lists (countries, tags, languages), votes, `/json/stations/byurl`, favicons, homepages.
