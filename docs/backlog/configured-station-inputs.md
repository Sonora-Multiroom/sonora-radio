# Stations as configured inputs in `multiroom.yml`

**Status:** Idea, not designed. Written 2026-11-13. Blocked on the hub item below.

**Origin:** Checking how the hub handles `radiobrowser://` (2026-11-13): the runtime path works, but
`multiroom.yml` refuses any scheme outside core's own handlers, so a station cannot be a permanent,
configured input.

**Depends on:** [play-station-by-uuid.md](play-station-by-uuid.md) (the resolver), and the hub's
`sonora-multiroom/docs/backlog/resolver-schemes-in-config.md` (accept a scheme a loaded resolver
supports). Overlaps [favourite-stations.md](favourite-stations.md): both keep stations across
restarts; this one by editing configuration, that one at runtime.

**Effort:** Small here once the hub item ships: documentation, an example, and live checks. The
work is in the hub.

## Problem

- A station played through `radiobrowser://` is a runtime input and disappears on a hub restart.
- The house's regular stations (the ones already in `multiroom.yml` as plain `https` URLs) break
  when a station moves its stream; written as `radiobrowser://<uuid>` they would follow the move.

## Proposal

- Once the hub accepts resolver schemes in configuration, document in `docs/configuration.md` how to
  write a station as a configured input:

  ```yaml
  - id: radio-roks
    display-name: Radio ROKS
    uri: radiobrowser://<stationuuid>
  ```

- Nothing in the extension should need to change: the resolver already handles any input with the
  scheme, runtime or configured. Confirm it, and that start-up makes no radio-browser call for a
  configured station (resolution happens only when a route starts).

## Acceptance (live)

Run once by hand against the real hub, recorded in the feature's `verification.md`. Editing
`multiroom.yml` and restarting the hub change production and drop every playing route: ask first,
and put the file back as it was afterwards.

1. With a `radiobrowser://` input in `multiroom.yml`, the hub starts, and the input is listed with
   its configured name; the start-up log shows no radio-browser request.
2. Playing it on `office` works (audible step: ask, low volume, restore, check no ALSA device left
   open).
3. With the extension disabled (`multiroom.radio.enabled=false`), the hub behaves as the hub item
   decides (start-up refused, or the input disabled with a WARN), and the message names the
   extension.

## Open questions

- Should the existing `https` station inputs in production's `multiroom.yml` be migrated to
  `radiobrowser://`? Only for stations whose UUID is known and stable.
