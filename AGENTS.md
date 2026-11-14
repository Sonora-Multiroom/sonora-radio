# sonora-radio Agent Knowledge

## What this repository is

**One Multiroom Audio Hub extension**: a drop-in JAR that lets the hub play internet radio stations
from the [radio-browser.info](https://www.radio-browser.info/) catalogue by their station UUID
(`radiobrowser://<uuid>`, RADIO-001, implemented and verified offline; live verification is
pending the owner's permission), and, still planned, lets clients search that catalogue through the
hub.

- **Language**: Java 17 LTS
- **Framework**: Spring Boot, **supplied by the host, never bundled** (every Spring dependency is
  `provided`)
- **Artifact**: `ai.multiroom:multiroom-radio`, package `multiroom.radio`, extension id `radio`
- **Build**: Maven (`mvn`), a single module, parent `multiroom-extension-starter`
- **Feature prefix**: `RADIO-NNN` (see the parent `D:\projects-sonora\AGENTS.md`)

The layout, the build and the extension contract follow the sibling `sonora-tts`, the first
out-of-tree extension. Copy its structure (POM, auto-configuration entry point, scoped exception
handler, metrics interface) rather than inventing a new one.

### Package layout (`src/main/java/multiroom/radio/`)

- `RadioAutoConfiguration`, `RadioVersion`: entry point and the version read from the filtered
  `multiroom/radio/radio-version.properties`.
- `config/RadioProperties`: `multiroom.radio.*`, validated, defaults in field initialisers.
- `resolve/`: `RadioStationResolver` (the `InputEndpointResolver`), `StationReference` (URI
  parsing), `CodecPolicy`, `StationTitle`, `StreamAddress`. Depends on `catalogue` only through
  `RadioBrowserClient`.
- `catalogue/`: `RadioBrowserClient` and its `HttpRadioBrowserClient` (failover, tolerant JSON
  mapping), `CachingRadioBrowserClient` (short station cache), `CatalogueHttp` (the one transport),
  `Deadline`; `catalogue/servers/`: pinned or DNS/`/json/servers` discovered mirror list.
- `metrics/`: `RadioMetrics` with Micrometer and no-op implementations.

Decisions worth knowing before changing the resolver (reasoning in
`specs/001-play-station-by-uuid/research.md`):

- **Core owns the display length of titles** (R4). The resolver only strips control characters and
  bounds the station name at 256 code points; do not shorten it to the hub's display limit here.
- **`/json/url` is called only after the codec and HLS checks** (R3), so a refused station is never
  counted as played. If the play report fails, the address from the lookup is played, looked up
  afresh when the lookup came from the cache.
- **Only `InputResolutionException` may leave `resolve`**: the hub removes the ephemeral input it
  created only for that exception.
- **Every network wait is bounded by awaiting the attempt against a `Deadline`** (R8), including a
  body that stalls after the headers; DNS runs off the calling thread.

### Why an extension, and not core or a client

Decided 2026-11-12. A station is only a stream URL, which the hub already plays, so core needs
nothing. Putting the catalogue in the hub rather than in one client means:

- every client (`sonora-cli`, `sonora-mcp`, `sonora-mobile`, MQTT, Home Assistant) gets search and
  playback from one implementation, instead of a Kotlin one and a Go one;
- a route that knows the **station UUID** can look up the current stream URL again when a station
  moves its stream, which a client holding only a URL cannot do;
- mirror selection, the `User-Agent`, click reporting and caching live in one place.

Clients stay thin: `sonora-cli` gets `sonora radio …` commands and methods in its `hub` package
(then `sonora-mcp` picks them up with a bump); `sonora-mobile` gets a browse and search screen. Those
are changes in their own repositories and backlogs.

## Upstream: sonora-multiroom (`D:\projects-sonora\sonora-multiroom`)

The host, the contract and the parent POM live in the hub repository. **Read it to learn how the
hub behaves. Never edit it from here.**

| What | Where | Role here |
|---|---|---|
| `multiroom-api` | `sonora-multiroom/multiroom-api/` | The only dependency (`provided`). `InputEndpointResolver`, `ResolvedInputEndpoint`, `InputResolutionException`, `SupportedCodecRegistry` (package `multiroom.api.io.resolve`), plus `RouteService` and the device services |
| `multiroom-extension-starter` | `sonora-multiroom/multiroom-extension-starter/pom.xml` | Parent POM: shading, JAR manifest, enforcer gates, `local`/`remote` deploy profiles |
| `multiroom-core` | `sonora-multiroom/multiroom-core/` | The runtime host. Read it; never depend on it (the enforcer fails the build) |
| `multiroom-resolvers` | `sonora-multiroom/multiroom-resolvers/` | In-tree resolvers (SoundCloud, YouTube): the closest model for a resolver that calls a web API |
| Extension guides | `sonora-multiroom/docs/extensions/` | The loading contract: `extension-development-guide.md`, `extension-manifest-format.md`, `extracting-an-extension-to-its-own-repo.md` |
| Sibling extension | `D:\projects-sonora\sonora-tts` | A working out-of-tree extension with its own resolver (`TtsInputResolver`, scheme `tts://`) and REST API (`/api/tts/**`) |

**Rules across the repository boundary** (as in `sonora-tts`):

1. A change to `multiroom-api` is a `sonora-multiroom` change: its own branch there, full-reactor
   `mvn verify` there, version bump, then `mvn -pl multiroom-api,multiroom-extension-starter -am
   install`, and only then raise `multiroom.require_api_version` here. Never copy hub classes.
2. Artifacts resolve from the **local `~/.m2` only**. `Could not resolve ai.multiroom:*` means
   "install it upstream", never "add a `<repository>`".
3. After the hub is rebuilt, rebuild and redeploy this JAR: an incompatible API shows up only at
   runtime (`Require-API-Version`).
4. A hub-side need (for example accepting `radiobrowser://` in `multiroom.yml`) is proposed in the
   `sonora-multiroom` backlog, not worked around here.

## The extension contract (what the host enforces)

From the hub's feature 019 (shared classloader); `sonora-tts/AGENTS.md` has the full gotchas list.

- **`multiroom-api` only**, `provided` scope.
- **The entry point is**
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  naming `multiroom.radio.RadioAutoConfiguration`.
- **No root `application.yml` / `application.properties`** in the JAR. Defaults live in
  `@ConfigurationProperties` field initialisers.
- **No network work during bean construction**: radio-browser being unreachable at boot must not
  stop the hub starting. Server discovery happens lazily, on the first request.
- **`multiroom.radio.enabled=false`** leaves the hub starting normally with no beans contributed.
- **`extension.id` is `radio` everywhere**: the POM property, `@ConditionalOnProperty` and the
  configuration prefix are the same string.
- **The parent POM needs an empty `<relativePath/>`** so it resolves from `~/.m2`.
- **Scope the `@RestControllerAdvice`** to this module's API interfaces (`assignableTypes`), or it
  takes over other extensions' error responses.
- **A resolver must return a URI core already plays** (`http`/`https`, or `file`). It only rewrites
  a URI; core needs an `InputHandler` for the result, and an extension cannot add one.

## radio-browser.info: facts the design relies on

Checked against `https://api.radio-browser.info/` and `https://de1.api.radio-browser.info/` on
2026-11-12.

- **No fixed server.** Resolve `all.api.radio-browser.info` (A records, then reverse lookups for
  names such as `de1.api.radio-browser.info`), shuffle, use the first, and move to the next on
  failure. `/json/servers` exists for clients that cannot do DNS. HTTPS preferred.
- **A descriptive `User-Agent`** is asked for (`sonora-radio/<version>`). Do not put the owner's
  name, e-mail or host names in it.
- **Always pass `limit`**: list and search endpoints default to `limit=100000`.
- **Use `stationuuid`**, never the old numeric `id`, and `countrycode` (ISO 3166-1 alpha-2) rather
  than the free-text `country`.
- Search: `/json/stations/search` with `name`, `tag`, `tagList`, `language`, `countrycode`, `codec`,
  `bitrateMin`/`bitrateMax`, `is_https`, `order`, `reverse`, `offset`, `limit`, `hidebroken`.
  Lookup: `/json/stations/byuuid?uuids=a,b`. Lists: `/json/countries`, `/json/tags`,
  `/json/languages`, `/json/codecs`.
- Station fields used here: `stationuuid`, `name`, `url_resolved` (playlists and redirects already
  followed), `hls`, `codec`, `bitrate`, `favicon`, `homepage`, `tags`, `countrycode`, `language`,
  `lastcheckok`, `votes`, `clickcount` (last 24 h).
- `/json/url/{stationuuid}` returns the station's URL **and counts a click** (once per IP per station
  per day); the service asks clients to call it for every play. `/json/vote/{stationuuid}`: one vote
  per IP per station per 10 minutes.
- The catalogue is community-edited: names, tags and favicons are untrusted input (length, HTML,
  odd URLs). Never put them in metric tags or log them unbounded.

## Production

`multiroom.lan` is the owner's real home audio system; the rules are in the parent
`D:\projects-sonora\AGENTS.md` (read-only SSH allowed; deploys, restarts and anything audible need
permission each time). A bare `mvn deploy` will upload to production once the `remote` profile is
inherited from the starter: use `-Plocal` while developing.

## Specification and backlog

- **Spec Kit** 1.4.0 (integration `claude`, scripts `ps`), set up 2026-11-13 with the `agent-context`,
  `archive`, `git` and `bug` extensions. Read `.specify/memory/constitution.md` (v1.0.0, ratified
  2026-11-13) before planning.
  Lookup order: `specs/NNN-*/` → `.specify/memory/` → `.specify/archive/`. RADIO-001 (`specs/001-play-station-by-uuid/`) is the one
  feature so far; the backlog holds the rest.
- **Backlog** in `docs/backlog/`: one Markdown file per proposal with a header block (`Status`,
  `Origin`, `Depends on`, `Effort`) and Problem, Proposal, Acceptance (live) and Open questions
  sections. Acceptance (live) lists the one-time manual checks against the real hub and
  radio-browser.info that the feature's spec carries over and `verification.md` records;
  `docs/backlog/INDEX.md` lists every item with a priority P1–P4. Delete an item once it ships, or
  update it if part remains. Work for another repository goes into that repository's backlog.
- Do not cite `FR-`, `SC-` or `US` numbers in code; `RADIO-NNN` and `HUB-NNN` are fine.
