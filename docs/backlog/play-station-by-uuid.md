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
- **The display name** comes from the hub: the resolver puts the station's name (bounded length) in
  `ResolvedInputEndpoint.title` (API 0.1.28), and core's `ResolvedInputNamer` names an input whose
  name the caller did not supply, so clients show "Radio ROKS", not a UUID.
- **Refresh mode** `EndpointRefreshMode.RE_RESOLVE_ORIGINAL_INPUT`, as the SoundCloud and YouTube
  resolvers use, so the hub resolves the station again when its stream URL is refused.
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
5. An HLS station (`hls = 1`) plays on `office` for a few minutes, or, if it does not, the
   decision to refuse HLS is recorded with the evidence.
6. A station whose codec is known and unsupported is refused with an error naming the codec, and
   one labelled `UNKNOWN` (with a playable stream) plays.
7. With the configured server list pointing at an unreachable host first, the play still succeeds
   through the next server.
8. Afterwards: no ALSA playback device is open, `audio.output.routes` for `office` reads 0, and
   the `office` volume and mute are back as recorded.

## Decisions (2026-11-13, agreed with the owner)

- **Scheme:** `radiobrowser://<stationuuid>`. It names the source; `radio://` stays free for
  something broader. The UUID is the only stable station identifier radio-browser has (see below).
- **Codecs:** refuse a station at resolve time, with an `InputResolutionException` naming the codec,
  only when its `codec` is known **and** the hub cannot decode it. radio-browser's labels are
  free-form, so map them first: `MP3` → `mp3`, `AAC` and `AAC+` → `aac`, `OPUS` → `opus`, `FLAC` →
  `flac`; let `OGG`, `UNKNOWN` and an empty codec through, since the decoder detects the format from
  the stream itself. Check the mapped name with `SupportedCodecRegistry.isSupported` (today `mp3`,
  `aac`, `opus`, `webm`, `ogg_vorbis`, `wav`, `flac`).
- **HLS stations** (`hls = 1`): allowed. The live checks include one HLS station; if it does not
  play reliably, refuse HLS with a clear reason rather than hold up the feature, and record why
  (hub backlog `hls-link-refresh.md`).
- **Clicks:** every resolve goes through `/json/url/{stationuuid}`, re-resolves included.
  radio-browser counts one click per IP per station per day, so reconnects do not inflate it.
- **API version:** `multiroom.require_api_version` 0.1.33, the version production runs (strictly
  0.1.28 is enough, for `ResolvedInputEndpoint.title`).
- **Extension version:** starts at 0.1.0.

## Findings from the hub (2026-11-13)

- **Runtime path: no hub change needed** (read in `sonora-multiroom` master,
  API 0.1.33). `POST /api/v2/play` checks only that `uri` is not blank (`PlaybackRequest`);
  `DeviceRegistryServiceImpl.registerInput` checks no scheme; route creation calls
  `InputHandlerRegistry.resolveInput`, and `DefaultInputResolutionService` offers the URI to each
  resolver's `supports` in order, falling back to passthrough (which then fails with
  `UnsupportedSchemeException` when the extension is not loaded). `POST /api/v2/inputs` takes the
  same path when a route starts. The REST layer reports such an input as not pauseable, which is
  right for live radio.
- **`multiroom.yml` still refuses the scheme:** `ConfigurationValidator` allows only `file`,
  `http`, `https`, `alsa`, `direct-sound`, `wasapi` and `signal`. A configured station input would
  need a hub change, proposed as `sonora-multiroom/docs/backlog/resolver-schemes-in-config.md` and
  followed up here in [configured-station-inputs.md](configured-station-inputs.md); not needed for
  this item.
- **When the hub resolves:** at route creation, not at registration, so an input registered with
  `POST /api/v2/inputs` costs no radio-browser call until it plays. It resolves again on expiry and
  on a refused link (HUB-038); after other reconnect failures it does not yet (hub backlog
  `re-resolve-on-reconnect-failure.md`), so a station whose stream host vanished ends its route.
- **Station identifiers in radio-browser:** `stationuuid` is the only stable one, the same on every
  mirror. `changeuuid` changes with every edit of the station; the old numeric `id` is deprecated
  and differs between servers; there is no slug, and names are neither unique nor stable.

## Open questions

- **A human-readable hint in the URI?** A bare UUID is unreadable in logs and in `multiroom.yml`.
  Option: accept and ignore a trailing label, e.g. `radiobrowser://<uuid>#radio-roks`, which the
  resolver strips. Decide in the spec; the resolved title already names the input in clients.
