# Search the catalogue through the hub

**Status:** Idea, not specified. Written 2026-11-12.

**Origin:** The decision of 2026-11-12 to put radio-browser.info in a hub extension (see
[AGENTS.md](../../AGENTS.md)): one search implementation for the CLI, MCP and the mobile app.

**Depends on:** [play-station-by-uuid.md](play-station-by-uuid.md) (the extension and its
radio-browser client).

**Effort:** Medium. A REST controller with its own OpenAPI, a response cache, filtering to codecs
the hub can play, tests. Client work (CLI commands, `hub` package methods, MCP tool, mobile screen)
is separate and goes into those repositories' backlogs once the API is fixed.

## Problem

- Playing a station by UUID is useless if nobody can find the UUID. Search, browse by country,
  genre (tag) and language are what the catalogue is for.
- If each client called radio-browser directly, search would be written twice (Kotlin and Go),
  each client would need its own mirror handling, and results could include stations the hub
  cannot play.

## Proposal

Endpoints under `/api/radio/**` (the extension's own namespace, like `/api/tts/**`; not part of
REST v2):

| Endpoint | What |
|---|---|
| `GET /api/radio/stations?name=&tag=&countrycode=&language=&codec=&bitrateMin=&order=&limit=&offset=` | Search. `hidebroken=true` always; `limit` always sent (radio-browser defaults to 100000), capped by the extension |
| `GET /api/radio/stations/{uuid}` | One station (`/json/stations/byuuid`) |
| `GET /api/radio/countries`, `/tags`, `/languages` | Lists for browse screens, ordered by station count |
| `POST /api/radio/stations/{uuid}/play` with a target | Convenience: the same as `POST /api/v2/play` with `radiobrowser://<uuid>`, so clients need not build the URI |

- **Response shape:** the extension's own `Station` DTO (`uuid`, `name`, `favicon`, `homepage`,
  `countrycode`, `language`, `tags`, `codec`, `bitrate`, `hls`, `lastcheckok`, `votes`,
  `clickcount`, `playable`), not radio-browser's raw JSON, so a change upstream does not break
  clients. `playable` says whether the hub can decode the station's codec.
- **Errors** as RFC 7807 problem details like the hub's REST v2; radio-browser unreachable answers
  503.
- **Cache** country, tag and language lists for hours, and search results briefly.
- **Untrusted text:** names and tags are community-edited; trim and bound them. Favicons are
  arbitrary URLs: return them as given and let the client decide whether to load them.

## Acceptance (live)

Run once by hand while the feature is verified against the real hub (`multiroom.lan`), never in
`mvn verify`; record commands, date and results in the feature's `verification.md`. Only the play
step makes sound; it follows the parent `AGENTS.md` (ask first, `office`, low volume, restore).

1. `GET /api/radio/stations?name=kiss&countrycode=UA&limit=10` returns at most 10 stations, each
   with a UUID, name, codec and bitrate, and only stations the hub can play (unless asked to
   include the rest).
2. `GET /api/radio/countries`, `/tags` and `/languages` return lists ordered by station count; a
   second call within the cache lifetime does not reach radio-browser (extension DEBUG log).
3. `GET /api/radio/stations/{uuid}` for a station from step 1 returns the same station; an unknown
   UUID answers 404 as a problem detail.
4. The endpoints appear in the hub's `/api-docs`.
5. `POST /api/radio/stations/{uuid}/play` to `office` plays the station; stopping the route leaves
   no ALSA device open and `audio.output.routes` for `office` at 0; volume and mute restored.
6. With every configured server unreachable, search answers 503 within the configured timeout and
   the rest of the hub keeps answering.

## Open questions

- **Filter or flag unplayable stations?** Hiding them is simpler for the user; flagging
  (`playable = false`) is honest about what exists. Default: hide, with a parameter to include.
- **Popular lists:** expose `topclick` / `topvote` for an empty-search home screen, or let clients
  order the search by `clickcount`?
- **Votes:** pass `/json/vote/{uuid}` through, or leave it out as not the hub's business?
- **OpenAPI:** the hub's `/api-docs` collects every controller. Check that the extension's
  endpoints appear there, so `sonora-cli` and `sonora-mobile` copies of the spec include them.
- **Localised country and language names** for the mobile app: radio-browser returns English
  names; is that acceptable?
