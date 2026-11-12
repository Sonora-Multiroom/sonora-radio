# Play a station by its UUID

**Status:** Idea, not specified. Written 2026-11-12, against `multiroom-api` 0.1.33.

**Origin:** The owner wants the radio-browser.info catalogue in Sonora Multiroom. On 2026-11-12 the
choice was an out-of-tree hub extension rather than core or a client (reasons in
[AGENTS.md](../../AGENTS.md)).

**Depends on:** nothing. The resolver SPI (`multiroom.api.io.resolve.InputEndpointResolver`) is
already public, and `sonora-tts` proves a custom scheme through it (`tts://`).

**Effort:** Medium. A new repository's skeleton (POM on `multiroom-extension-starter`,
auto-configuration, properties), a small radio-browser HTTP client with server discovery, one
resolver, tests, and a first deploy.

## Problem

- To play a station today a client must find its stream URL somewhere and send it to
  `POST /api/v2/play`. The hub then knows only the URL: if the station moves its stream, the input
  breaks and nothing can repair it.
- Each client that wanted radio-browser would have to implement mirror discovery, the `User-Agent`
  and click reporting itself.

## Proposal

The extension, with one feature: a URI scheme that names a station.

- **`radiobrowser://<stationuuid>`** is accepted wherever the hub takes an input URI at runtime
  (`POST /api/v2/play`, `POST /api/v2/inputs`). No REST v2 change.
- **A `RadioStationResolver`** (`InputEndpointResolver`) supports exactly that scheme. On `resolve`
  it calls `/json/url/{stationuuid}` (which also counts the play for the catalogue's popularity)
  and returns the station's `http(s)` URL, which core plays as any stream. Falls back to
  `/json/stations/byuuid` and `url_resolved` if the click call fails.
- **A `RadioBrowserClient`**: lazy server discovery (DNS `all.api.radio-browser.info`, shuffled,
  fail over to the next server; `/json/servers` as fallback), `User-Agent: sonora-radio/<version>`,
  short connect and read timeouts, never called during bean construction.
- **The display name** of the resulting input is the station's name (bounded length), so clients
  show "Radio ROKS", not a UUID.
- **Configuration** under `multiroom.radio.*`: `enabled`, `user-agent` override, timeouts, optional
  fixed server list (for tests and outages).
- **Metrics**: resolutions by outcome and by server, behind a `RadioMetrics` interface as in
  `sonora-tts`. No station names or UUIDs in tags.

## Acceptance (live)

Run once by hand while the feature is verified against the real hub (`multiroom.lan`), never in
`mvn verify`; record commands, date and results in the feature's `verification.md`. Audible steps
follow the parent `AGENTS.md`: ask first, record and restore the `office` output's state, low
volume, stop the test route first, a known-good station (KissFM, Kraina FM, Radio NV).

1. The deployed JAR is listed in `/actuator/extensions` with its version and is not `REJECTED`;
   with `multiroom.radio.enabled=false` the hub starts and lists it as disabled.
2. `POST /api/v2/play` with `radiobrowser://<uuid of a known-good station>` to `office` plays
   audibly; the route's input shows the station's name, not the UUID.
3. An unknown but well-formed UUID and a malformed one each fail with a clear error naming the
   station, and nothing plays; no input is left behind in `GET /api/v2/inputs`.
4. The hub log shows which radio-browser server answered, and the request carried the
   `sonora-radio/<version>` `User-Agent` (from the extension's own DEBUG log).
5. With the configured server list pointing at an unreachable host first, the play still succeeds
   through the next server.
6. Afterwards: no ALSA playback device is open, `audio.output.routes` for `office` reads 0, and
   the `office` volume and mute are back as recorded.

## Open questions

- **Does the runtime path accept an unknown scheme?** `multiroom-core`'s `ConfigurationValidator`
  allows only `file`, `http`, `https`, `alsa`, `direct-sound`, `wasapi` and `signal` in
  `multiroom.yml`, so a `radiobrowser://` input cannot be configured there today. TTS registers its
  `tts://` inputs through `multiroom-api`, not REST. Check that `POST /api/v2/play` passes the URI
  to the resolvers untouched; if not, that is a hub change (`sonora-multiroom` backlog). Allowing
  resolver schemes in `multiroom.yml` would also be a hub change.
- **When does the hub resolve**: once at registration, or at every route start and reconnect? The
  hub backlog has `resolve-before-register.md` and `re-resolve-on-reconnect-failure.md`. A station
  that moved its stream is only repaired if the hub resolves again after a failure.
- **Should a resolve count a click every time?** One play can resolve several times (reconnects).
  The service counts one click per IP per station per day anyway, so probably harmless; confirm.
- **HLS stations** (`hls = 1`): does the hub play them reliably (see hub backlog
  `hls-link-refresh.md`), or should they be refused with a clear reason?
- **Codecs:** refuse a station whose `codec` the hub cannot decode (`SupportedCodecRegistry`) at
  resolve time with an `InputResolutionException` naming the codec, rather than letting the
  pipeline fail later.
- **Name of the scheme:** `radiobrowser://` (names the source) or `radio://` (shorter, but implies
  any radio).
