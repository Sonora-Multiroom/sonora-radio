# Favourite stations

**Status:** Idea, not designed. Written 2026-11-12.

**Origin:** The decision of 2026-11-12 to put radio-browser.info in a hub extension (see
[AGENTS.md](../../AGENTS.md)). Favourites shared by every client of the house are one of the
reasons for doing it in the hub.

**Depends on:** [play-station-by-uuid.md](play-station-by-uuid.md). Best done on top of the hub's
`STORED` input origin, proposed in `sonora-multiroom/docs/backlog/persistent-input-store.md`.

**Effort:** Medium. Small with the hub's store in place; larger if the extension must keep its own
file.

## Problem

- The hub persists nothing between restarts, so a station played from the catalogue is a runtime
  input and is gone after a restart.
- Favourites kept by one client (the phone) are not seen by the others (CLI, MCP, voice), so "play
  my usual radio in the kitchen" cannot work everywhere.

## Proposal

Two possible routes; pick one when the hub's persistence plans are clearer.

- **A. On the hub's `STORED` origin** (preferred): a favourite is a stored input with URI
  `radiobrowser://<uuid>` and the station's name. It appears in `GET /api/v2/inputs` like any other
  source, survives restarts, and every client sees it with no radio-specific code.
- **B. The extension's own store**: a small JSON file beside the hub's data, with
  `GET/PUT/DELETE /api/radio/favourites`. Works before the hub has persistence, but adds a second
  place where state lives, to be migrated later.

## Acceptance (live)

Run once by hand against the real hub, recorded in the feature's `verification.md`. Restarting the
hub drops every playing route, so ask before step 2.

1. A station added as a favourite appears to every client (`GET /api/v2/inputs` or
   `/api/radio/favourites`, depending on the route chosen) with its name.
2. After a hub restart (with permission) the favourite is still there and plays on `office`
   (audible step: ask, low volume, restore).
3. Removing it removes it for every client.

## Open questions

- Is the hub's `STORED` origin coming soon enough to wait for it?
- Should a favourite keep a snapshot (name, favicon) for when radio-browser is unreachable?
- Ordering and grouping of favourites: needed, or does the client sort?
