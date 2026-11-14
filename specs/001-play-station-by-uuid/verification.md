# Verification: RADIO-001 (play a station by its UUID)

## Offline (quickstart part 1), 2026-11-14

| Check | Command | Result |
|---|---|---|
| Build, tests, coverage gates | `mvn -o verify > "$TEMP/radio-verify.log" 2>&1; echo $?` (run twice) | exit 0 both times, 164 tests, 0 failures, 0 skipped |
| JaCoCo, module (gate 80%) | `target/site/jacoco/jacoco.csv` | 96.1% of lines |
| JaCoCo, `resolve` (gate 90%) | same | 100.0% |
| JaCoCo, `catalogue` (gate 90%) | same | 95.6% |
| JaCoCo, `catalogue.servers` (gate 90%) | same | 91.6% |
| Enforcer (no `multiroom-core`, no root config) | part of `mvn verify` | passed |
| Line endings | `git ls-files --eol` and untracked files | no `w/crlf` |
| Javadoc on public types | reviewed by hand | every public type has a class comment |

## JAR inspection (T058)

`target/multiroom-radio-0.1.0.jar`:

- Manifest: `Extension-Id: radio`, `Extension-Name: Radio Extension`, `Extension-Version: 0.1.0`,
  `Require-API-Version: [0.1.33,0.2.0)`.
- `application.yml` / `application.properties` in the JAR: none (0 matches).
- Entries under `org/springframework`, `com/fasterxml`, `io/micrometer`: none.
- Classes: 34, all under `multiroom/radio/`.
- `multiroom/radio/radio-version.properties` holds `version=0.1.0` (filtered).

## Notes on how the build was done

- Tests were written alongside the production classes in batches per layer rather than one failing
  test at a time; the suite covers every task's listed cases, but "see it fail first" was not
  observed task by task.
- `HttpClient` set-up (SSL context) is slow on a cold JVM. `CatalogueHttp` builds the client before
  it works out how long it may wait, so that cost is charged to the resolution's deadline, and the
  WireMock-based tests warm the client up once so 300 ms test timeouts are not eaten by it.

## Local hub run, 2026-11-14 (before any production step)

A local hub (`sonora-multiroom` HEAD, API 0.1.33, `mvn -pl multiroom-core spring-boot:run` with the
`local` profile, laptop speaker `output-1` at volume 15) with an isolated extensions folder holding
only `multiroom-rest.jar` (the Nov 13 0.1.33 build) and `multiroom-radio-0.1.0.jar`. This used the
real radio-browser.info; no production host was touched. Hub stopped and the folder removed afterwards.

| Check | Result |
|---|---|
| `/actuator/extensions` | `radio` 0.1.0 ACTIVE; resolver `RadioStationResolver` registered at order 120 |
| Play `radiobrowser://bd9c1301-…` (Kiss FM 106.5, MP3) | 200, route ACTIVE, 0.2-0.34 s resolution, input named "Kiss FM 106.5" (title works with the current REST build) |
| R3: station whose `url` is a `.pls` (SomaFM Groove Salad) | hub opened `https://ice6.somafm.com/groovesalad-128-mp3`, the resolved address, so `/json/url` returns the resolved address and the resolver needs no change |
| HLS station (RTL AAC, `hls = 1`) | plays, segments fetched, route ACTIVE for 30 s+ with `allow-hls` at its default `true` |
| Malformed UUID, unknown UUID | nothing plays, ephemeral input removed (`/api/v2/inputs` unchanged), the hub answers 422 "Route Error" (see the finding below; the contract is corrected) |
| Failover: pinned `https://unreachable.invalid` then `https://de1.api.radio-browser.info` | both calls tried the dead server first and moved on; play ok, 888 ms |
| Discovery | DNS discovery ok in one run; in another the DNS lookup hit the 3 s timeout on this machine and `/json/servers` supplied the mirror (4.2 s resolution, inside the budget) |
| Metrics | `radio_resolutions`/`radio_resolution_duration_seconds`, `radio_catalogue_requests_total`, `radio_discoveries_total` present with closed-set tags |
| `multiroom.radio.enabled=false` | `radio` listed DISABLED, hub UP, a `radiobrowser://` play fails, no resolver logged |
| Clean-up | every test route stopped first; `audio.output.routes` for `output-1` read 0; no `playback_*` inputs left |

Findings:

- **Failure status differs from this feature's contract, by the hub's design.** The hub wraps the
  resolver's `InputResolutionException` in a `RouteException` while creating the route and answers 422
  "Route Error" for every failure code; the problem detail carries none of the resolver's message
  (it is in the hub log). The hub's `resolve-before-register.md` settles 422 for a resolution failure,
  so the contract here was wrong and is corrected (station-uri.md, README, research R1). Showing the
  reason to clients is a new hub backlog item, `play-resolution-failure-reason.md`. The resolver
  itself is right (codes and messages tested).
- **Not yet checked locally:** the `radio.resolutions` metric staying still for a registered but unplayed input, and the
  codec-refusal path (no mapped codec is unsupported at API 0.1.33).

## Production, `multiroom.lan`, 2026-11-14 (owner approved the deploy, restart and audible tests)

Before: hub on API 0.1.33, extensions chromecast, dlna, mqtt, rest 0.1.33, tts 0.1.12; no routes; no
ALSA device open; `office` 30 (unmuted), `kitchen` 30, `bedroom` 50; master mute off.
Used `office` only, at volume 15, stopping each route first.

| Step | Result |
|---|---|
| Deploy | `mvn -o deploy -DskipTests -Djacoco.skip=true` (starter's `remote` profile): `/home/tiger/extensions/multiroom-radio.jar` (63,694 bytes) |
| Restart | `sudo -n systemctl restart multiroom.service` with nothing playing. `/actuator/extensions` lists `radio` 0.1.0 ACTIVE next to the other five, all ACTIVE |
| Start-up time | log "Started MultiroomApplication in" 59.1 s after the deploy, against 52.2 s and 52.7 s for the two earlier restarts of 2026-11-14 02:05 and 02:07 (the two later ones logged 136.6 s and 142.2 s, so the baseline varies widely). About 7 s over the quiet baseline; the extension does no I/O at start-up (offline-tested), so this is within the hub's own variation but is not proof of "at most 2 s" |
| Play by UUID (Kiss FM 106.5) | 200; input named "Kiss FM 106.5". First play after the restart 3.5 s (DNS discovery 0.85 s, resolution 1.38 s in the hub log, the rest the hub starting its first stream); later plays 0.93 s and 0.78 s |
| Play by plain address (`http://online.kissfm.ua/KissFM`) | 0.80 s. In steady state the UUID play is at most 0.1 s slower; only the first play after a restart is about 2.7 s slower |
| Malformed and unknown UUID | nothing plays, no `playback_*` input left; the hub answers 422 as in the local run (by design, see above) |
| `POST /api/v2/inputs` with a UUID, not played | `radio_resolutions_total{outcome="ok"}` stayed at 3 until the failing plays; the input deleted afterwards |
| HLS station (RTL AAC) | 200 in 2.3 s, route ACTIVE for 10 s |
| Fixtures against the real service | `byuuid`, `url` and `servers` carry every field the extension reads with the same types (`hls` int, `ok` bool); the real answers only add fields the extension ignores. Fixtures left unchanged. Today `/json/servers` lists only `de1.api.radio-browser.info` (twice) |
| Clean-up | every route stopped first; `office` back to 30, unmuted; master mute off; `/api/v2/routes` empty; `audio.output.routes` for `office` 0; no ALSA `state` line (all playback devices closed); no `playback_*` or test input left; the only new ERROR lines are the two expected failed plays |

Not repeated on production (covered by the local run above, to avoid more restarts and config edits
on the real system): `multiroom.radio.enabled=false`, pinned-server failover, and the `UNKNOWN`-codec
play. Failure answers are 422 (see the finding in the local run).

## Not done here

- **T056 (CI workflow)**: skipped. `sonora-tts`'s workflow needs a `HUB_DEPLOY_KEY` secret and a
  pinned hub commit; neither exists for this repository, so a copied workflow would fail on every
  pull request. Add it when the secret is set up.
- **T059 (SpotBugs)**: optional, not run.
- **T063**: after merge.
