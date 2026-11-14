# sonora-radio

A Sonora Multiroom hub extension (`ai.multiroom:multiroom-radio`, extension id `radio`) that plays
internet radio from the [radio-browser.info](https://www.radio-browser.info/) catalogue. Search
through the hub is planned; see [docs/backlog/INDEX.md](docs/backlog/INDEX.md) and
[AGENTS.md](AGENTS.md).

## What it does (RADIO-001)

The hub accepts `radiobrowser://<station uuid>` wherever it accepts an input address, for example
`POST /api/v2/play`. The extension looks the station up, checks that the hub can decode it, reports
the play to the catalogue and hands the hub the station's current `http(s)` stream address, with the
station's name as the input's title. If the catalogue is slow or down it fails over between
mirrors; if reporting the play fails it plays the address from the lookup.

```text
radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81            plays the station
radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81#kissfm     same station, readable label
radiobrowser://kissfm                                          refused: not a UUID
```

The scheme and the UUID are case-insensitive; a path, query, user info or port is refused. On
`POST /api/v2/play` the hub answers every failure with 422 "Route Error" and removes the input it
created; the reason (malformed or unknown station, a codec the hub cannot decode, HLS switched off, a
non-HTTP address, no catalogue server in time, an unreadable answer) is in the hub log. See
[the URI contract](specs/001-play-station-by-uuid/contracts/station-uri.md).

## Configuration

Set under `multiroom.radio` in the hub's `multiroom.yml`. Every property has a default; a bad value
fails hub start-up naming the property. The full table, with rules, is in
[the configuration contract](specs/001-play-station-by-uuid/contracts/configuration.md).

| Property | Default | |
|---|---|---|
| `enabled` | `true` | `false` contributes nothing |
| `user-agent` | `sonora-radio/<version>` | sent to the catalogue |
| `connect-timeout` / `read-timeout` | `2s` / `4s` | per request |
| `resolve-timeout` | `10s` | one whole resolution, discovery included |
| `max-servers-per-request` | `3` | mirrors tried per request |
| `servers` | empty | pin mirrors (tried in order); empty means discover |
| `discovery-host`, `discovery-timeout`, `server-list-ttl` | `all.api.radio-browser.info`, `3s`, `1h` | mirror discovery |
| `station-cache-ttl` / `station-cache-size` | `5m` / `100` | lookups are cached, play reports never |
| `allow-hls` | `true` | `false` refuses HLS stations |

Metrics (`radio.resolutions`, `radio.resolution.duration`, `radio.catalogue.requests`,
`radio.discoveries`) and logs are described in
[the observability contract](specs/001-play-station-by-uuid/contracts/observability.md).

## Build and deploy

Needs `multiroom-api` and `multiroom-extension-starter` 0.1.33 in `~/.m2` (in `sonora-multiroom`:
`mvn -pl multiroom-api,multiroom-extension-starter -am install`).

```bash
mvn verify                 # tests with WireMock and injected DNS; JaCoCo gates (80% module, 90% resolve/catalogue)
mvn deploy -Plocal         # copies the JAR to the sibling hub checkout's extensions folder
```

**A bare `mvn deploy` uploads to production** (`multiroom.lan`) through the starter's `remote`
profile; use `-Plocal` while developing. After the hub is rebuilt, rebuild and redeploy this JAR: an
incompatible API shows up only at runtime. Validation steps, offline and live, are in
[quickstart.md](specs/001-play-station-by-uuid/quickstart.md).
