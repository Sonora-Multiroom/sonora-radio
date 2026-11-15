# Quickstart: validate RADIO-002

How to prove the feature works: offline first, then once against the real hub. Results go into
[verification.md](verification.md) with commands, date and outcome. The wire format is
[contracts/radio-rest-api.yaml](contracts/radio-rest-api.yaml).

## Prerequisites

- JDK 17+, Maven, Git Bash.
- `multiroom-api` and `multiroom-extension-starter` 0.1.33 in `~/.m2`. If missing, in
  `D:\projects-sonora\sonora-multiroom`: `mvn -pl multiroom-api,multiroom-extension-starter -am install`.

## Part 1: offline (every change, no network to radio-browser)

```bash
cd /d/projects-sonora/sonora-radio
mvn verify > "$TEMP/radio-verify.log" 2>&1; echo "exit $?"
```

Expected: `exit 0`; JaCoCo line coverage at least 80% for the module and 90% for `resolve`,
`catalogue`, `catalogue.servers`, `text`, `rest` and each `rest` sub-package; RADIO-001's tests
still pass. The suite covers, with
`@WebMvcTest`, WireMock and an injected DNS resolver:

- every parameter rule: each invalid value, an unknown parameter and a repeated one answer 400
  naming the parameter, with no catalogue request recorded;
- the search request sent to the catalogue for each filter, order and direction, including
  `tagExact=true`, `languageExact=true`, `hidebroken=true` and the explicit `limit`;
- `nextOffset`: a full page, a short page, an empty page, a full page with a dropped record, and
  the offset cap;
- cleaning: control characters, over-long names, tags and addresses, 58 tags on one station,
  non-HTTP favicon, bitrate 0, `hls` and `lastcheckok` as 0/1, a record without a UUID, fields of
  the wrong type;
- one station: found, broken but found, unknown (404), malformed UUID (400), upper-case UUID;
- lists: mapping, dropped entries, the second request within 6 hours makes no catalogue request,
  a failure is not remembered, at most 200 tag pages are kept;
- errors as problem details with `application/problem+json`: 400, 404, 502, 503 with
  `Retry-After`, 405 for `POST`, 404 for an unknown path, 500;
- time: a slow first server is given up at half the budget and the second answers; every server
  down answers 503 within `request-timeout`; an expired server list is used without waiting and
  refreshed in the background; a failed refresh is not retried for 60 s; discovery with no list
  uses at most half of what the caller has left;
- wiring: `enabled=false` and a non-web context contribute no controllers; context start makes no
  request; a non-positive `request-timeout` fails start-up naming it;
- the play path is unchanged (RADIO-001's resolver tests).

Inspect the JAR as in RADIO-001's quickstart; expected `Extension-Version: 0.2.0`,
`Require-API-Version: [0.1.33,0.2.0)`, no Spring, Jackson or Swagger classes inside.

## Part 2: live, once, on `multiroom.lan`

Every request in steps 2–7 is a read-only `GET` and makes no sound; step 8 is audible and needs
its own permission. The deploy and the restart each need the owner's permission, and the restart
stops whatever is playing. `H=http://multiroom.lan:8080`. Add
`-w '\n%{time_total}s\n'` to the `curl` of steps 2–4 and record the times: with the first server
answering, each is expected under 2 s, and a remembered list near 0.

1. **Deploy and restart** (permission): `mvn deploy`, then
   `ssh -o BatchMode=yes tiger@multiroom.lan 'sudo -n systemctl restart multiroom.service'`; wait
   about 45 s. `curl -s $H/actuator/extensions` lists `radio` 0.2.0, not `REJECTED`.
2. **Search** (acceptance 1): `curl -s "$H/api/radio/stations?name=kiss&countryCode=UA&limit=10"`:
   at most 10 entries in `stations`, each with `uuid`, `uri`, `name`, `codec`, `bitrate`; votes
   never rise down the list; `nextOffset` is 10 or null. Follow `nextOffset` until it is null.
   Each `uri` is `radiobrowser://` followed by the lower-case `uuid`.
3. **Popular and trending** (acceptance 2): `curl -s "$H/api/radio/stations"` returns 20 stations
   by votes; `curl -s "$H/api/radio/stations?order=clickCount"` returns 20 by click count.
4. **Lists and their cache** (acceptance 3): read the counter
   (`curl -s "$H/actuator/metrics/radio.catalogue.requests?tag=endpoint:countries"`), call
   `curl -s "$H/api/radio/countries"` twice, read the counter again: it grew by one. The same for
   `languages` and for `tags?name=jazz` (`endpoint:tags`). Each list is ordered by `stationCount`.
   Compare one small country's `stationCount` with the number of stations
   `"$H/api/radio/stations?countryCode=<code>&limit=100"` returns, and record both.
5. **One station and errors** (acceptance 4): `curl -s "$H/api/radio/stations/<uuid from step 2>"`
   returns the same station. `curl -si "$H/api/radio/stations/00000000-0000-4000-8000-000000000000"`
   answers 404 and `curl -si "$H/api/radio/stations/not-a-uuid"` answers 400, both
   `application/problem+json`. Also `curl -si "$H/api/radio/stations?countrycode=UA"` (unknown
   parameter) and `"$H/api/radio/stations?limit=101"` answer 400 naming the parameter.
6. **API description** (acceptance 5):
   `curl -s $H/api-docs | grep -o '"/api/radio/[^"]*"\|"Radio[A-Za-z]*"' | sort -u` lists the five
   paths and `RadioStation`, `RadioStationPage`, `RadioCountry`, `RadioLanguage`, `RadioTag`,
   `RadioTagPage`, `RadioProblem`.
7. **Nothing else changed**: `curl -s $H/api/v2/outputs` answers as before;
   `/actuator/metrics/radio.resolutions` did not move (no play was reported).
8. **One play** (audible; permission; constitution VII, because this feature changes the play
   path). Skip it only if RADIO-001's live verification ran on this build, and record which.
   Follow the parent `AGENTS.md` and RADIO-001's quickstart part 2, steps 3 and 9: record the
   `office` volume, mute and enabled flag and the master mute, keep the volume low, then
   `curl -s -X POST $H/api/v2/play -H 'Content-Type: application/json' -d '{"uri":"<uri from step 2>","targetId":"office","targetType":"SINGLE_OUTPUT"}'`
   with a known-good station (KissFM). It plays; stop that route first, restore what was
   recorded, and confirm no ALSA playback device is open and
   `/actuator/metrics/audio.output.routes?tag=output:office` reads 0.

Acceptance steps 6 and 7 (upstream facts, codec labels) are already recorded in
`verification.md`. The full play acceptance stays RADIO-001's live verification; if it runs in the
same session, use a `uri` from step 2 and step 8 is covered by it.

Not live: "every server unreachable answers 503 within the budget while the hub keeps answering"
is proved in part 1.
