# Quickstart: validate RADIO-001

How to prove the feature works: first offline, then once against the real hub. Results of part 2
go into `verification.md` with commands, date and outcome.

## Prerequisites

- JDK 17+, Maven, Git Bash.
- `multiroom-api` and `multiroom-extension-starter` 0.1.33 in `~/.m2`. If missing, in
  `D:\projects-sonora\sonora-multiroom`: `mvn -pl multiroom-api,multiroom-extension-starter -am install`.

## Part 1: offline (every change, no network to radio-browser)

```bash
cd /d/projects-sonora/sonora-radio
mvn verify > "$TEMP/radio-verify.log" 2>&1; echo "exit $?"
```

Expected: `exit 0`; JaCoCo line coverage ≥ 80% for the module and ≥ 90% for the `resolve`,
`catalogue` and `catalogue.servers` packages; the enforcer finds no `multiroom-core`
dependency and no root `application.yml`. The suite covers, with WireMock and an injected DNS
resolver:

- URI parsing (valid forms, upper case, `#label`, every refused form) with no request made;
- a known station resolving to its play-report address, title stripped and bounded;
- play-report failure falling back to `url_resolved`, after a fresh lookup (checked again for
  codec and HLS) when the station came from the cache;
- the station cache: reused within its lifetime, bounded in size, while the play is reported
  every time;
- unknown station, unsupported codec (stub registry), HLS switched off, non-HTTP address,
  unreadable JSON, each with its failure code and no play report for refused stations;
- failover past a refusing, a slow, a rate-limiting and a 5xx server, and past one that stalls in
  the middle of the body; total failure within the budget;
- discovery by DNS, fallback to `/json/servers`, pinned list order, cache expiry;
- `User-Agent` on every request;
- the auto-configuration: `enabled=false` contributes no beans; context start makes no request
  (WireMock records zero calls); each malformed property fails start-up naming it;
- metrics recorded with only closed-set tags; no-op without a registry.

Inspect the JAR:

```bash
unzip -p target/multiroom-radio-0.1.0.jar META-INF/MANIFEST.MF | grep -E 'Extension-|Require-API'
unzip -l target/multiroom-radio-0.1.0.jar | grep -E 'application\.(yml|properties)$'   # nothing
```

Expected: `Extension-Id: radio`, `Extension-Version: 0.1.0`, `Require-API-Version: [0.1.33,0.2.0)`.

## Part 2: live, once, on `multiroom.lan` (each step needs the owner's permission)

Follow the parent `AGENTS.md` production rules. Before anything audible, record the `office`
output's volume and mute and the master mute; keep the volume low; stop the test route first when
done, then restore.

1. **Deploy and restart** (permission): `mvn deploy` (the `remote` profile copies the JAR), then
   `ssh -o BatchMode=yes tiger@multiroom.lan 'sudo -n systemctl restart multiroom.service'`; wait
   about 45 s.
2. **Inventory** (spec acceptance 1): `curl -s http://multiroom.lan:8080/actuator/extensions` lists
   `radio` 0.1.0, not `REJECTED`. Repeat once with `multiroom.radio.enabled: false` in
   `/home/tiger/multiroom.yml` (permission; restore afterwards): listed as disabled, hub healthy.
3. **Play** (acceptance 2): find a known-good station's UUID (KissFM, Kraina FM or Radio NV) in the
   radio-browser web UI, then
   `curl -s -X POST http://multiroom.lan:8080/api/v2/play -H 'Content-Type: application/json' -d '{"uri":"radiobrowser://<uuid>","targetId":"office","targetType":"SINGLE_OUTPUT"}'`
   (no `displayName`, so the hub names the input). Audible; `GET /api/v2/inputs` shows the station's
   name. Stop the route. Then register a station with `POST /api/v2/inputs` without playing it:
   `/actuator/metrics/radio.resolutions` does not move until a route plays it; delete the input
   afterwards.
4. **Failures** (acceptance 3): the same request with a random well-formed UUID (400) and with
   `radiobrowser://not-a-uuid` (400); nothing plays; `GET /api/v2/inputs` unchanged.
5. **Log** (acceptance 4): the hub log (or Loki, `app=multiroom-core`) shows the answering server;
   with `logging.level.multiroom.radio=DEBUG` (permission) the `User-Agent` line shows
   `sonora-radio/0.1.0`. **Which address plays** (research R3): pick a station whose `url` is a
   playlist and differs from `url_resolved` (one `byuuid` request to a mirror, with permission:
   it counts no play), play it, and compare the address the hub opened (hub log) with the two. It
   must be the resolved one; if it is the raw `url`, stop and change the resolver as R3 says.
6. **HLS** (acceptance 5): a station with `hls = 1` plays for a few minutes, or the decision to set
   `allow-hls: false` by default is recorded with the evidence.
7. **Codecs** (acceptance 6): a station labelled `UNKNOWN` with a playable stream plays. If no
   mapped codec is unsupported at API 0.1.33, record that the refusal path is covered offline only.
8. **Failover** (acceptance 7): with `servers: [https://unreachable.invalid, https://<a real
   mirror>]` (permission; restore afterwards) a play still succeeds; the log shows the WARN for the
   first server.
9. **Clean-up** (acceptance 8):
   `ssh … 'grep -h "^state" /proc/asound/card*/pcm*p/sub*/status'` prints nothing;
   `/actuator/metrics/audio.output.routes?tag=output:office` reads 0; the `office` volume and mute
   are as recorded; `/actuator/metrics/radio.resolutions` shows the outcomes exercised.
