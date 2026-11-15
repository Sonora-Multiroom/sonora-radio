# Feature Specification: Search the catalogue through the hub

**Feature Branch**: `002-station-search-api`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "@docs/backlog/station-search-api.md" (backlog item written 2026-11-12,
reviewed against the code 2026-11-14). This is RADIO-002: read-only requests under `/api/radio/**`
that let every hub client search and browse the radio-browser.info catalogue through the hub. It
builds on RADIO-001 (play a station by its UUID).

## User Scenarios & Testing *(mandatory)*

The actors are **hub clients** (`sonora-cli`, `sonora-mcp`, `sonora-mobile`, Home Assistant, or a
person with `curl`) that want to find a station to play, the **people using those clients**, and the
**hub operator** (the owner), who runs the extension on the Raspberry Pi.

### User Story 1 - Find a station and play it (Priority: P1)

A person types part of a station's name, or picks a country, a genre or a language, and the client
asks the hub for matching stations. Each result carries what a list needs to show (name, country,
codec, bitrate, icon address) and a ready-made address that the client hands to the hub's existing
play request. Asking with no filters at all returns the most popular stations, which is what a home
screen shows first.

**Why this priority**: It is the whole feature. RADIO-001 plays a station by its UUID, and a UUID
is useless if nobody can find it.

**Independent Test**: Ask the hub for stations named "kiss" in Ukraine, limited to 10; the answer
holds at most 10 stations, and the `uri` of any of them is accepted by the hub's play request
without the client changing it.

**Acceptance Scenarios**:

1. **Given** the extension is installed and enabled, **When** a client searches by name, **Then**
   it receives the matching stations, each with its UUID, a ready `uri`, name, codec, bitrate and a
   `playable` flag.
2. **Given** a search by country code, tag, language, codec or minimum bitrate, alone or combined,
   **When** it is sent, **Then** only stations matching every given filter are returned.
3. **Given** no filters and no ordering, **When** a client asks for stations, **Then** it receives
   20 stations, the most-clicked first.
4. **Given** a search with more matches than one page, **When** the client asks for the next page
   with `offset`, **Then** it receives the following stations, and a page shorter than the
   requested `limit` tells it that this was the last page.
5. **Given** a station from a search result, **When** the client passes its `uri` to the hub's play
   request, **Then** the station plays as RADIO-001 describes; the search itself reported no play
   to the catalogue.
6. **Given** a station the catalogue marks as broken, **When** a search runs, **Then** that station
   is not in the results.

---

### User Story 2 - Browse by country, language and genre (Priority: P2)

A person who does not know a station's name opens a browse screen. The client asks the hub for the
list of countries, of languages, or of tags (genres), each with the number of stations it has, the
largest first. Picking an entry leads to a search with that filter (Story 1).

**Why this priority**: Browsing is the second way into the catalogue. Search works without it, so
it comes after Story 1.

**Independent Test**: Ask for the countries, the languages, and the tags containing "jazz"; each
answer is ordered by station count, and using one entry's code or name as a search filter returns
stations.

**Acceptance Scenarios**:

1. **Given** the extension is enabled, **When** a client asks for countries, **Then** it receives
   each country's two-letter code, English name and station count, ordered by station count,
   highest first.
2. **Given** the extension is enabled, **When** a client asks for languages, **Then** it receives
   each language's name, its code where the catalogue has one, and its station count, ordered by
   station count.
3. **Given** the tag list is long, **When** a client asks for tags with a `name` filter and a
   `limit`, **Then** it receives at most that many tags whose name matches, ordered by station
   count.
4. **Given** a list was fetched a moment ago, **When** the same list is asked for again, **Then**
   it is answered without contacting the catalogue.

---

### User Story 3 - Show one station again (Priority: P2)

A client that remembered a station (a favourite, the station a route is playing, a link someone
shared) asks the hub for that one station by its UUID, to show its name, icon and details.

**Why this priority**: Clients keep UUIDs, not station details, and need a way back from one to the
other. Search is usable without it.

**Independent Test**: Ask for a station by a UUID taken from a search result; the same station
comes back. Ask for an unknown UUID and for a malformed one; each answers with a clear error.

**Acceptance Scenarios**:

1. **Given** a station UUID from a search result, **When** a client asks for that station,
   **Then** it receives the same station in the same shape.
2. **Given** a station the catalogue now marks as broken, **When** a client asks for it by UUID,
   **Then** it is still returned, with `lastCheckOk` false, so a saved station can be shown.
3. **Given** a well-formed UUID the catalogue does not know, **When** a client asks for it,
   **Then** the answer is "not found" and names the UUID.
4. **Given** a malformed UUID, **When** a client asks for it, **Then** the request is refused as
   invalid and the catalogue is not contacted.
5. **Given** a UUID in upper or mixed case, **When** a client asks for it, **Then** it is treated
   as the same station as its lower-case form.

---

### User Story 4 - Bad requests and catalogue trouble fail quickly and clearly (Priority: P2)

A client sends a parameter the hub cannot accept, or radio-browser.info is slow or down. The client
gets an error it can tell apart (fix the request, not found, catalogue trouble) within a few
seconds, and the rest of the hub is unaffected.

**Why this priority**: A search screen that hangs for ten seconds, or a hub that slows down because
a volunteer-run service is having a bad day, is worse than no search.

**Independent Test**: Send an invalid country code, an unknown ordering and a limit above the cap;
each is refused as invalid with no catalogue contact. With every catalogue server unreachable, a
search answers "unavailable" within the configured time while other hub requests keep answering.

**Acceptance Scenarios**:

1. **Given** an invalid country code, ordering, limit, offset, minimum bitrate or boolean,
   **When** the request is sent, **Then** it is refused as invalid, the error names the parameter,
   and the catalogue is not contacted.
2. **Given** the first catalogue server tried is unreachable or slow, **When** a search runs,
   **Then** the next server is tried and the search succeeds within the time budget.
3. **Given** no catalogue server answers within the time budget, **When** a search, a list or a
   station lookup is requested, **Then** the answer is "catalogue unavailable", within the budget.
4. **Given** the catalogue answers with something unreadable or refuses the request, **When** any
   of these requests is made, **Then** the answer is "bad answer from the catalogue", distinct
   from "unavailable".
5. **Given** the catalogue is unreachable, **When** other hub requests are made (playback, outputs,
   other extensions), **Then** they answer as usual.
6. **Given** a station record in the catalogue with missing, malformed or unexpected fields,
   **When** it appears in a result, **Then** the request still succeeds; the bad fields are left
   empty or the one record is dropped.

---

### User Story 5 - The operator stays in control (Priority: P3)

The owner can cap how much one request may ask for, tune the time budget and how long answers are
remembered, see in the hub's metrics how often the catalogue is contacted, and find the new requests
in the hub's API description. With the extension switched off, the requests do not exist.

**Why this priority**: Needed to run the feature responsibly against a free service, but delivers no
search on its own.

**Independent Test**: Read the hub's API description and find the radio requests and their data
shapes; repeat a list request and see in the metrics that the catalogue was contacted once; switch
the extension off, restart, and see the requests answer "not found" while the hub works.

**Acceptance Scenarios**:

1. **Given** the extension is enabled, **When** the operator reads the hub's API description,
   **Then** the radio requests, their parameters, their data shapes and their errors are there.
2. **Given** searches and lists are requested, **When** the operator reads the hub's metrics,
   **Then** they see catalogue request counts per kind of request and outcome, with no station
   names, search text or UUIDs in them.
3. **Given** the extension is switched off or not installed, **When** a client calls a radio
   request, **Then** it answers "not found", which clients read as "radio is not available".
4. **Given** a malformed setting (a limit cap outside its allowed range, a time budget or cache
   lifetime that is zero or negative), **When** the hub starts, **Then** start-up fails with a
   message naming the extension and the setting.

---

### Edge Cases

- **A search that matches nothing**: an empty list, not an error.
- **`limit` above the configured cap, zero or negative; `offset` negative or above its cap**:
  refused as invalid, naming the allowed range. A silently reduced `limit` would make a full page
  look like the last one.
- **An `offset` past the end of the results**: an empty list.
- **A blank filter** (`name=` with nothing, or only spaces): treated as not given.
- **A filter value that is very long or contains control characters**: refused as invalid; nothing
  that long is a station name, tag or language.
- **A country code in lower case** (`ua`): accepted and treated as `UA`. Three letters, digits or
  anything else: refused.
- **A request parameter the hub does not know**: ignored, as elsewhere in the hub.
- **A station with no usable name, a very long name, HTML or control characters in its name or
  tags**: control characters are stripped and lengths bounded before it is returned; a station
  whose UUID is missing or malformed is dropped from the result.
- **A station with dozens of tags, or one enormous tag**: the number of tags and each tag's length
  are bounded.
- **A favicon or homepage that is not an `http`/`https` address, or is extremely long**: returned
  as empty. The hub never fetches these addresses.
- **A bitrate of 0**: the catalogue's way of saying "unknown"; returned as empty, not as 0.
- **An unplayable station in a result**: flagged with `playable: false`, never removed, so page
  sizes stay honest.
- **A country entry in the catalogue without a valid two-letter code**: dropped from the country
  list. A language without a code is kept, with an empty code.
- **The same search repeated within a minute**: answered from memory; a station added upstream in
  that minute appears a little later.
- **A failed catalogue request**: never remembered; the next request tries again.
- **Paging while the catalogue changes**: pages are separate requests, so a station can appear on
  two pages or be skipped. Accepted.
- **Many clients searching at once with a slow catalogue**: each request is bounded by its own time
  budget and none blocks playback or another request.

## Requirements *(mandatory)*

### Functional Requirements

**Requests offered**

- **FR-001**: The extension MUST offer these read-only requests in its own namespace, separate from
  the hub's REST v2:
  - `GET /api/radio/stations`: search;
  - `GET /api/radio/stations/{uuid}`: one station;
  - `GET /api/radio/countries` and `GET /api/radio/languages`: lists for browse screens;
  - `GET /api/radio/tags`: tags, filterable by name.
- **FR-002**: The extension MUST NOT offer a play request. A station carries a `uri` of the form
  `radiobrowser://<uuid>` (UUID in lower case) that the hub's existing play request accepts
  unchanged.
- **FR-003**: The extension MUST NOT offer voting, and MUST NOT report a play to the catalogue for
  a search, a list or a station lookup.
- **FR-004**: With the extension not installed, or with `multiroom.radio.enabled=false`, these
  requests MUST answer 404.

**Search**

- **FR-005**: Search MUST accept the optional filters `name`, `tag`, `countryCode`, `language`,
  `codec` and `bitrateMin`, and return only stations matching every filter given. Text filters
  match as the catalogue matches them.
- **FR-006**: Search MUST never return stations the catalogue marks as broken.
- **FR-007**: Search MUST accept `limit` (default 20) and `offset` (default 0). `limit` MUST be
  between 1 and a configured cap (default 100); `offset` MUST be between 0 and a configured cap
  (default 10000). A value outside its range MUST be refused as invalid, naming the range.
- **FR-008**: Search MUST accept `order`, one of `name`, `votes`, `clickCount`, `bitrate`, and
  `reverse` (boolean). When `order` is not given, results are ordered by `clickCount`. When
  `reverse` is not given, `votes` and `clickCount` are ordered highest first and `name` and
  `bitrate` lowest first.
- **FR-009**: A search with no parameters MUST therefore return the 20 most-clicked stations; there
  are no separate "top" requests.
- **FR-010**: `countryCode` MUST be two letters (ISO 3166-1 alpha-2), accepted in either case.
  `bitrateMin` MUST be a non-negative whole number. Text filters MUST be trimmed, ignored when
  blank, and refused when longer than a fixed bound or containing control characters.
- **FR-011**: A search result MUST be a plain list of stations. A page shorter than the requested
  `limit` is the last page; the extension MUST NOT shorten a page for any other reason.

**One station**

- **FR-012**: The station lookup MUST validate the UUID before any network call, accept it in any
  letter case, and return the station whether or not the catalogue marks it as broken.
- **FR-013**: A well-formed UUID the catalogue does not know MUST answer 404, naming the UUID.

**Lists**

- **FR-014**: The country list MUST give each country's two-letter code, English name and station
  count; the language list each language's name, code (empty when the catalogue has none) and
  station count; the tag list each tag's name and station count. All three are ordered by station
  count, highest first.
- **FR-015**: The tag list MUST accept an optional `name` filter and `limit` and `offset` with the
  same defaults, caps and validation as search.
- **FR-016**: Entries without a usable identity (a country without a valid two-letter code, a tag
  or language without a name) MUST be dropped.

**The station as clients see it**

- **FR-017**: A station MUST be returned in the extension's own shape, never the catalogue's raw
  record: `uuid`, `uri`, `name`, `favicon`, `homepage`, `countryCode`, `languages` (list), `tags`
  (list), `codec`, `bitrate` (empty when unknown), `hls`, `lastCheckOk`, `votes`, `clickCount`,
  `playable`. Field names are camelCase, like REST v2.
- **FR-018**: `playable` MUST be false only when the hub is known to refuse the station, by the
  same decision RADIO-001 makes at play time (a codec the hub reports it cannot decode; HLS when
  the operator has disallowed it). It is not a promise that the stream works.
- **FR-019**: Unplayable stations MUST be flagged, never hidden.
- **FR-020**: Text from the catalogue MUST be stripped of control characters and bounded before it
  is returned: a station name to 256 characters (as in RADIO-001), each tag and language to a fixed
  length, and the number of tags and languages per station to a fixed count.
- **FR-021**: `favicon` and `homepage` MUST be returned only when they are `http` or `https`
  addresses of bounded length, and empty otherwise. The extension MUST NOT fetch them.
- **FR-022**: Reading the catalogue's records MUST tolerate unknown, missing and malformed fields:
  a bad field becomes empty, a record without a valid UUID is dropped, and the request still
  succeeds.

**Errors**

- **FR-023**: Errors MUST be RFC 7807 problem details, like REST v2, and MUST affect only this
  extension's requests:
  - 400: a malformed UUID, country code, `order`, `limit`, `offset`, `bitrateMin`, boolean or text
    filter; the error names the parameter and no network call is made;
  - 404: a well-formed UUID the catalogue does not know;
  - 502: the catalogue answered with something unreadable, or refused the request;
  - 503: no catalogue server answered within the time budget.
- **FR-024**: An invalid `order` or any other invalid parameter MUST be caught by the extension and
  never forwarded to become a catalogue error.

**Catalogue use**

- **FR-025**: One request, server discovery and failover included, MUST be bounded by a time budget
  of its own (default 5 seconds), separate from and shorter than the budget for playing a station.
- **FR-026**: Catalogue requests MUST reuse RADIO-001's server discovery, failover, pinned server
  list and user agent, and MUST always state an explicit result limit to the catalogue.
- **FR-027**: Country, language and tag lists MUST be remembered for hours (default 6), and search
  results for about a minute (default 60 seconds), keyed by the normalised parameters, with a
  bound on how many are kept, oldest dropped first. Station lookups reuse RADIO-001's short station
  memory. Failures MUST NOT be remembered.
- **FR-028**: The catalogue MUST be contacted only because a client asked: no polling, crawling,
  prefetching or scheduled refresh.
- **FR-029**: A slow or unreachable catalogue MUST NOT delay hub start-up, playback or any request
  outside this extension, and one slow request MUST NOT block another.

**Operation**

- **FR-030**: The limit cap, the offset cap, the request time budget and the two memory lifetimes
  MUST be settings under `multiroom.radio.*` with working defaults; a value outside its allowed
  range MUST fail hub start-up with a message naming the extension and the setting.
- **FR-031**: The extension MUST count catalogue requests by kind (`search`, `countries`,
  `languages`, `tags`, alongside RADIO-001's) and outcome, with no search text, station names or
  UUIDs in metric tags, and work unchanged when the hub has no metrics registry.
- **FR-032**: Logs MUST identify stations by UUID and MUST NOT contain unbounded text from the
  catalogue or from a client's filters.
- **FR-033**: The requests, their parameters, errors and data shapes MUST appear in the hub's
  published API description, with shape names prefixed `Radio` (`RadioStation`, `RadioCountry`,
  `RadioLanguage`, `RadioTag`, `RadioProblem`) so they cannot collide with another extension's.

### Key Entities

- **Station** (`RadioStation`): one catalogue station as clients see it: its UUID (the stable
  identifier), the `uri` that plays it, display details (name, icon and homepage addresses, country,
  languages, tags), technical details (codec, bitrate, HLS flag), catalogue signals (last check,
  votes, clicks in the last 24 hours) and the `playable` flag. Everything but the UUID is untrusted
  community-edited text.
- **Country** (`RadioCountry`): two-letter code, English name, station count. Clients localise the
  name from the code.
- **Language** (`RadioLanguage`): name, ISO 639 code where known, station count.
- **Tag** (`RadioTag`): a free-text genre or label, with its station count.
- **Search query**: the filters, ordering and paging of one search; in normalised form it is also
  what a remembered result is filed under.
- **Problem** (`RadioProblem`): the error answer, in the same RFC 7807 form as REST v2.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can go from a typed name, or from a country, genre or language, to a playing
  station using only hub requests; no client contains code for catalogue servers, user agents or
  result clean-up.
- **SC-002**: 100% of stations returned by search or lookup carry a `uri` that the hub's play
  request accepts unchanged.
- **SC-003**: When the first catalogue server answers, a search or list is answered within 2
  seconds; when it was asked moments before, it is answered at once without contacting the
  catalogue.
- **SC-004**: With every catalogue server unreachable, every radio request answers "unavailable"
  within 5 seconds (default settings), and playback and the rest of the hub keep answering
  throughout.
- **SC-005**: 100% of invalid requests (UUID, country code, ordering, limit, offset, bitrate) are
  refused without the catalogue being contacted.
- **SC-006**: No single request can ask the catalogue for more than the configured cap of results
  (100 by default), and repeating a browse list within its lifetime costs the catalogue nothing.
- **SC-007**: A full page always means "there may be more" and a short page always means "the
  end", for every search and tag listing.
- **SC-008**: Hub start-up is no slower with this feature than with RADIO-001 alone, including when
  radio-browser.info is unreachable.

## Acceptance (live)

Carried over from the backlog item. Run once by hand while the feature is verified against the real
hub (`multiroom.lan`), never in the automated build; record commands, date and results in
`verification.md`. Every step is a read-only request and makes no sound. Deploying the JAR and
restarting the hub need the owner's permission as always.

1. `GET /api/radio/stations?name=kiss&countryCode=UA&limit=10` returns at most 10 stations, each
   with a UUID, `uri`, name, codec, bitrate and `playable`.
2. `GET /api/radio/stations` with no parameters returns 20 stations ordered by click count, highest
   first.
3. `GET /api/radio/countries`, `/languages` and `/tags?name=jazz` return lists ordered by station
   count; a second call within the lifetime does not reach radio-browser: the extension's catalogue
   request counter for that kind of request, read from `/actuator/metrics`, does not grow.
4. `GET /api/radio/stations/{uuid}` for a station from step 1 returns the same station; an unknown
   UUID answers 404 and a malformed one 400, both as problem details.
5. The requests and the `Radio*` shapes appear in the hub's `/api-docs` (the hub scans the whole
   `multiroom` package, so no hub change is expected).
6. Check the upstream facts this feature assumes and correct the spec if they differ: `tags` and
   `language` arrive as comma-separated text, a `bitrate` of 0 means unknown, the size of the full
   tag list, and the fields of the catalogue's country and language lists.

Not live: "every configured server unreachable answers 503 within the time budget, and the rest of
the hub keeps answering" is proved by an automated test against a stand-in catalogue. On production
it would need pinned dead servers and two restarts for something a test proves exactly.

Playing a station is RADIO-001's acceptance; this feature only checks that the `uri` from step 1
has the form RADIO-001 accepts.

## Assumptions

- **RADIO-001 is the base**: its catalogue client, server discovery, failover, user agent, station
  memory and codec decision are implemented and are reused, not rebuilt. Its live verification is
  still pending; this feature's live checks can run in the same session.
- **No hub change is needed**: the hub serves an extension's requests and includes them in its API
  description, as it does for `/api/tts/**`.
- **Decided here, where the backlog item was silent or open**:
  - *Out-of-range `limit` and `offset` are refused (400), not silently reduced*, so that "a short
    page is the last page" stays true for a client that does not know the cap.
  - *The default ordering is `clickCount`, highest first, with or without filters.* The backlog
    item fixed this only for the no-filter case; one rule is simpler for clients, and popularity
    is the more useful order for a name or genre search.
  - *Cache lifetimes*: 6 hours for lists and 60 seconds for searches, both configurable.
  - *Bounds on untrusted text* (tag length, tags per station, address length, filter length) are
    fixed values chosen during planning, not settings.
- **`playable` is true for every station today**: the hub decodes every codec label the extension
  maps, and unmapped labels are let through. Teaching the codec decision about labels that are
  known not to work (WMA, video mixes) stays an open backlog question and is not part of this
  feature; when it is answered, `playable` and RADIO-001's refusal change together.
- **Out of scope**: a play request; voting; a `playableOnly` filter (only if a client asks once
  `playable` is ever false); favourites (`favourite-stations.md`); other filters the catalogue has
  (`bitrateMax`, `is_https`, exact-match variants, several tags at once); totals or "has more"
  markers in results.
- **Client work is separate**: `sonora radio …` commands and `hub` package methods in `sonora-cli`,
  the MCP tool in `sonora-mcp` and the browse and search screen in `sonora-mobile` go into those
  repositories' backlogs once this API is fixed, as does the question of whether their copies of
  the hub's API description include the radio requests.
- **The API is unauthenticated on the home LAN**, like the rest of the hub.
- **Tests never contact radio-browser.info or its DNS** (constitution XII); the live checks above
  are the only contact, run once, by hand.
