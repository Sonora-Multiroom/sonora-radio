# Stations as configured inputs in `multiroom.yml`

**Status:** Idea, not designed. Written 2026-11-13; updated 2026-11-14: no longer blocked, the hub
side shipped as HUB-039.

**Origin:** Checking how the hub handles `radiobrowser://` (2026-11-13): the runtime path worked,
but `multiroom.yml` refused any scheme outside core's own handlers. HUB-039 (no API change, in
`sonora-multiroom` master as read on 2026-11-14) now accepts a configured input whose scheme a loaded
resolver handles, and skips one that nothing handles with a single WARN.

**Depends on:** RADIO-001 (shipped: the resolver), and
HUB-039 being deployed on `multiroom.lan` (its production check was still open on 2026-11-14).
Overlaps [favourite-stations.md](favourite-stations.md): both keep stations across restarts; this
one by editing configuration, that one at runtime.

**Effort:** Small: documentation, an example, and live checks. No code is expected here.

## Problem

- A station played through `radiobrowser://` is a runtime input and disappears on a hub restart.
- The house's regular stations (the ones already in `multiroom.yml` as plain `https` URLs) break
  when a station moves its stream; written as `radiobrowser://<uuid>` they would follow the move.
- RADIO-001 does not test or promise configured stations. With HUB-039 they are accepted anyway
  once the extension is loaded, so today the behaviour exists but is unverified.

## Proposal

- Document in `docs/configuration.md` how to write a station as a configured input:

  ```yaml
  - id: radio-roks
    display-name: Radio ROKS
    uri: radiobrowser://<stationuuid>
  ```

- Nothing in the extension should need to change: the resolver handles any input with the scheme,
  runtime or configured, and RADIO-001 already requires its scheme check to be cheap, never to
  throw and to contact nothing (the hub calls it at start-up for every configured input). Confirm
  it, and that start-up makes no radio-browser call for a configured station (resolution happens
  only when a route starts).

## Acceptance (live)

Run once by hand against the real hub, recorded in the feature's `verification.md`. Editing
`multiroom.yml` and restarting the hub change production and drop every playing route: ask first,
and put the file back as it was afterwards.

1. With a `radiobrowser://` input in `multiroom.yml`, the hub starts, and the input is listed with
   its configured name; the start-up log shows no radio-browser request.
2. Playing it on `office` works (audible step: ask, low volume, restore, check no ALSA device left
   open).
3. With the extension disabled (`multiroom.radio.enabled=false`), the hub starts, skips the input
   and logs one WARN naming the input and the scheme (HUB-039's behaviour).

## Open questions

- Should the existing `https` station inputs in production's `multiroom.yml` be migrated to
  `radiobrowser://`? Only for stations whose UUID is known and stable.
- A configured input has a `display-name`, so the hub does not name it after the station. Is that
  what is wanted, or should a configured station without a name take the station's?
