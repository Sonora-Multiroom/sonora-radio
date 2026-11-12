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

Called on `https://<discovery-host>` only when DNS gave no usable names.

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

## Failover classification

| Result | Next server? |
|---|---|
| Connect error, timeout, HTTP 5xx, HTTP 429 | yes, while servers and budget remain |
| HTTP 4xx other than 429 | no (another mirror would answer the same) |
| 2xx with a body that is not the expected JSON shape | no; `upstream_protocol_error` for `station`, fallback for `play` |

## Never called by this feature

Search, lists (countries, tags, languages), votes, `/json/stations/byurl`, favicons, homepages.
