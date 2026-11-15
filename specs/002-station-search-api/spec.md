# Feature Specification: Search the catalogue through the hub

**Feature Branch**: `002-station-search-api`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "@docs/backlog/station-search-api.md" (backlog item written 2026-11-12,
reviewed against the code 2026-11-14). This is RADIO-002: read-only requests under `/api/radio/**`
that let every hub client search and browse the radio-browser.info catalogue through the hub. It
builds on RADIO-001 (play a station by its UUID).

## Clarifications

### Session 2026-11-15

- Q: Should a search by `tag` or `language` match exactly or match any text that contains the
  value? → A: Both match the whole value only. There is no `tagExact` parameter. (First answer was
  a `tagExact=false` whole-word mode; withdrawn in the review below.)
- Q: May a page come back shorter than `limit`, and how does a client know it has reached the
  end? → A: The hub does not filter the catalogue's page after it answers, so a page shorter than
  `limit` is the last page, as clients expect. Only records without a valid UUID are dropped, which
  is rare; a client that wants to be safe against it treats an empty page as the end too and
  otherwise adds `limit` to `offset`. (First answer, "only an empty page means the end", was
  needed only by the whole-word tag filter and is withdrawn. Replaced in the second review below:
  a result now carries `nextOffset`, so the client does not infer the end from a page's length.)
- Q: In what order do stations come back when a search has no `order`? → A: With no filter at all,
  most-clicked first (`clickCount`, highest first), the "trending" home list. With any filter
  (`name`, `tag`, `countryCode`, `language`, `codec`, `bitrateMin`), most-voted first (`votes`,
  highest first): all-time popularity, which ties less than the 24-hour click count. (Decided
  2026-11-15 in the review; the first answer was `clickCount` always. Changed again in the second
  review below: `votes` always, and a home screen asks for `order=clickCount` itself.)
- Q: Should this feature teach the hub which catalogue codec labels it cannot play? → A: Not in
  code, and the station shape has no `playable` flag until the hub knows an unplayable label. The
  live acceptance gains one read-only step that records the catalogue's codec labels and their
  station counts, as evidence for the follow-up backlog item.

### Session 2026-11-15 (review)

A critical review of the draft changed these points, all approved by the owner:

- Default order: `clickCount` with no filter, `votes` with any filter.
- The `playable` field is dropped (FR-018 withdrawn).
- `/api/radio/**` stays unversioned, with a written compatibility promise (Assumptions).
- `tagExact` and the post-answer whole-word filter are removed (they forced "only an empty page
  means the end" and contradicted the paging promise).
- `reverse` is replaced by `direction` (`asc` or `desc`), because `reverse` meant the opposite of
  the default for `votes` and `clickCount`.
- Unknown request parameters answer 400 in this namespace, so a typo such as `countrycode` never
  returns an unfiltered list with 200.
- The country, language and tag lists count only stations the catalogue does not mark as broken,
  like search.
- The search-result cache is removed; lists and single stations stay cached. Identical concurrent
  catalogue requests share one call.
- The limit and offset caps and the list cache lifetime become fixed values, not settings; the
  request time budget stays a setting. A per-attempt time bound makes failover fit in the budget.
- Live acceptance checks multi-language stations, tie order and list counts.

### Session 2026-11-15 (second review)

A second review checked the spec against the RADIO-001 code, the catalogue's documentation and the
catalogue server's source (`segler-alex/radiobrowser-api-rust`, read 2026-11-15; no live request
was made). Approved by the owner:

- Whole-value matching needs the catalogue's `tagExact=true` and `languageExact=true`; without
  them the catalogue matches any text that contains the value. The requirement now says so.
- The remembered server list is used after it expires while a new one is looked up, and looking
  servers up is bounded by the request's own budget. As RADIO-001 stands, an expired list is
  looked up inside the request and may take the whole 5 seconds.
- Search and tag results are an object with the list and `nextOffset`, not a bare list. The hub
  knows whether the catalogue returned a full page; a client guessing from the page's length does
  not.
- The default order is `votes` whether or not a filter is given. A default that changes when a
  filter is added is a surprise.
- The catalogue sorts by the one chosen value with no second key, so equal values page unstably in
  every order. Clients that page MUST drop stations they have already seen (by `uuid`).
- Identical concurrent requests no longer share one catalogue call: the reason that removed the
  search cache (one household) removes this too.
- Smaller points: list counts are close to, not equal to, what search returns; every error from
  these requests is a problem detail, including 500; `%` in a text filter is refused; countries
  and languages are asked with an explicit limit; the per-attempt bound is half the request budget
  instead of a second fixed value.

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
   it receives the matching stations, each with its UUID, a ready `uri`, name, codec, bitrate and
   HLS flag.
2. **Given** a search by country code, tag, language, codec or minimum bitrate, alone or combined,
   **When** it is sent, **Then** only stations matching every given filter are returned.
3. **Given** a search with `tag=jazz`, **When** it is sent, **Then** only stations tagged exactly
   "jazz" are returned; a station whose only matching tag is "acid jazz" or "popular" is not. A
   client finds the exact tag name through the tag list (Story 2).
4. **Given** no filters and no ordering, **When** a client asks for stations, **Then** it receives
   20 stations, the most-voted first; with `order=clickCount` it receives the most-clicked of the
   last 24 hours first.
5. **Given** a search with more matches than one page, **When** the client asks again with
   `offset` set to the answer's `nextOffset`, **Then** it receives the following stations; an
   answer whose `nextOffset` is empty is the last page.
6. **Given** a station from a search result, **When** the client passes its `uri` to the hub's play
   request, **Then** the station plays as RADIO-001 describes; the search itself reported no play
   to the catalogue.
7. **Given** a station the catalogue marks as broken, **When** a search runs, **Then** that station
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
   highest first; the count includes only stations the catalogue does not mark as broken, so it
   is close to what a search for that country returns (the catalogue keeps the count as a stored
   number and the hub remembers the list for hours, so it is not exact).
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

1. **Given** an invalid country code, ordering, direction, limit, offset, minimum bitrate or
   boolean, or a parameter the extension does not know, **When** the request is sent, **Then** it
   is refused as invalid, the error names the parameter, and the catalogue is not contacted.
2. **Given** the first catalogue server tried is unreachable or slow, **When** a search runs,
   **Then** it is given up after its own short bound, the next server is tried, and the search
   succeeds within the time budget.
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
7. **Given** the remembered list of catalogue servers has expired and looking servers up is slow,
   **When** a search runs, **Then** it uses the expired list and succeeds within the time budget,
   and a later request finds the new list.

---

### User Story 5 - The operator stays in control (Priority: P3)

The owner can tune the time budget of a request, see in the hub's metrics how often the catalogue is contacted, and find the new requests
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
4. **Given** a malformed setting (a time budget that is zero or negative), **When** the hub
   starts, **Then** start-up fails with a message naming the extension and the setting.

---

### Edge Cases

- **A search that matches nothing**: an empty list, not an error.
- **`limit` above 100, zero or negative; `offset` negative or above 10000**: refused as invalid,
  naming the allowed range. A silently reduced `limit` would make a client that adds its own
  `limit` to `offset` skip stations.
- **An `offset` past the end of the results**: an empty list.
- **A page with fewer stations than `limit`**: not a signal. The hub drops records without a valid
  UUID, so a page can be short without being the last; `nextOffset` says whether there is more,
  and it is worked out from what the catalogue returned before anything was dropped.
- **A next page that would start past the offset cap** (`offset` plus `limit` above 10000):
  `nextOffset` is empty; the search ends there.
- **Equal values in the chosen order** (many stations share a vote count, click count or bitrate):
  the catalogue sorts by that one value with no second key, so paging can repeat or skip stations
  among ties even when nothing changed. A client that pages drops stations whose `uuid` it has
  already shown. Skipped stations are accepted; live acceptance measures how often.
- **The remembered server list has expired**: it is still used for this request while a new one is
  looked up without making the request wait. Only a hub that has never had a list (the first
  request after a restart) waits for the look-up, inside the request's budget.
- **A blank filter** (`name=` with nothing, or only spaces): treated as not given.
- **A request parameter the extension does not know** (`countrycode`, `q`): refused as invalid,
  naming it, so a typo never returns an unfiltered list.
- **A filter value that is very long or contains control characters**: refused as invalid; nothing
  that long is a station name, tag or language.
- **A `%` in a text filter**: refused as invalid. The catalogue treats it as "any text", so
  `tag=%` would match every station that has a tag. An `_` is passed on and may match any one
  character; that only widens a match slightly and tags do contain it.
- **A `/` in the tag list's `name` filter**: refused as invalid. The catalogue takes that filter
  as part of the address, and whether it accepts a `/` there is not documented. A search's `tag`
  filter may contain one.
- **A country code in lower case** (`ua`): accepted and treated as `UA`. Three letters, digits or
  anything else: refused.
- **A station with no usable name, a very long name, HTML or control characters in its name or
  tags**: control characters are stripped and lengths bounded before it is returned; a station
  whose UUID is missing or malformed is dropped from the result.
- **A station with dozens of tags, or one enormous tag**: the number of tags and each tag's length
  are bounded.
- **A favicon or homepage that is not an `http`/`https` address, or is extremely long**: returned
  as empty. The hub never fetches these addresses.
- **A bitrate of 0**: the catalogue's way of saying "unknown"; returned as empty, not as 0.
- **A station the hub will refuse to play** (HLS while the operator disallows it): still returned,
  never removed, with its `hls` flag; the play request refuses it with RADIO-001's clear error.
- **A country entry in the catalogue without a valid two-letter code**: dropped from the country
  list. A language without a code is kept, with an empty code.
- **The same search repeated**: asks the catalogue again; only lists and single stations are
  remembered.
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
  `codec` and `bitrateMin`, and return only stations matching every filter given. All text
  matching ignores letter case:
  - `name` matches any part of the station name;
  - `language`, `codec` and `tag` match the whole value only. `tag` is compared with each of a
    station's tags in turn: "jazz" does not match "acid jazz" or "popular" for "pop". `language`
    takes the catalogue's language name as the language list gives it. A station with several
    languages must match a search for each of them (checked live, see Acceptance step 6);
  - the extension MUST ask the catalogue for exact matching of `tag` and `language`
    (`tagExact=true`, `languageExact=true`) on every search that carries them. Left out, the
    catalogue matches any tag or language that contains the value. `codec` and `countryCode` are
    always exact in the catalogue.
- **FR-006**: Search MUST never return stations the catalogue marks as broken.
- **FR-007**: Search MUST accept `limit` (default 20) and `offset` (default 0). `limit` MUST be
  between 1 and 100; `offset` MUST be between 0 and 10000. Both bounds are fixed, published in the
  API description, and not settings. A value outside its range MUST be refused as invalid, naming
  the range.
- **FR-008**: Search MUST accept `order`, one of `name`, `votes`, `clickCount`, `bitrate`, and
  `direction`, one of `asc`, `desc`. When `order` is not given, results are ordered by `votes`,
  with or without filters. When `direction` is not given, `votes` and `clickCount` are ordered
  highest first (`desc`) and `name` and `bitrate` lowest first (`asc`). There is no `reverse`
  parameter.
- **FR-009**: A search with no parameters MUST therefore return the 20 most-voted stations, and
  `order=clickCount` alone the 20 most-clicked of the last 24 hours (a "trending" list); there are
  no separate "top" requests.
- **FR-010**: `countryCode` MUST be two letters (ISO 3166-1 alpha-2), accepted in either case.
  `bitrateMin` MUST be a non-negative whole number. Text filters MUST be trimmed, ignored when
  blank, and refused when longer than a fixed bound or containing control characters or `%`.
- **FR-011**: A search result MUST be an object (`RadioStationPage`) with `stations`, the list,
  and `nextOffset`. The extension MUST NOT filter or reorder the catalogue's page, except to drop
  records without a valid UUID, and MUST NOT ask the catalogue again to fill a page. `nextOffset`
  is `offset` plus `limit` when the catalogue returned `limit` records (counted before any are
  dropped) and that value is within the offset cap; otherwise it is empty, which means the last
  page. A client pages by sending `nextOffset` as the next `offset` and never infers the end from
  a page's length. The order among stations with equal values is the catalogue's and is not
  stable, so a client that pages MUST drop stations whose `uuid` it has already received.

**One station**

- **FR-012**: The station lookup MUST validate the UUID before any network call, accept it in any
  letter case, and return the station whether or not the catalogue marks it as broken.
- **FR-013**: A well-formed UUID the catalogue does not know MUST answer 404, naming the UUID.

**Lists**

- **FR-014**: The country list MUST give each country's two-letter code, English name and station
  count; the language list each language's name, code (empty when the catalogue has none) and
  station count; the tag list each tag's name and station count. All three are ordered by station
  count, highest first, and count only stations the catalogue does not mark as broken (the
  extension asks the catalogue to hide broken stations in the lists, as in search). The counts
  are the catalogue's stored numbers and are remembered here for hours, so they are close to, not
  equal to, what a search returns. The country and language lists are returned whole, as plain
  lists; the extension asks the catalogue for them with a fixed limit of 2000, larger than either
  list (225 countries and 585 languages on 2026-11-15).
- **FR-015**: The tag list MUST accept an optional `name` filter and `limit` and `offset` with the
  same defaults, caps and validation as search, and MUST answer in the same form
  (`RadioTagPage`: `tags` and `nextOffset`, worked out as in search).
- **FR-016**: Entries without a usable identity (a country without a valid two-letter code, a tag
  or language without a name) MUST be dropped.

**The station as clients see it**

- **FR-017**: A station MUST be returned in the extension's own shape, never the catalogue's raw
  record: `uuid`, `uri`, `name`, `favicon`, `homepage`, `countryCode`, `languages` (list), `tags`
  (list), `codec`, `bitrate` (empty when unknown), `hls`, `lastCheckOk`, `votes`, `clickCount`.
  Field names are camelCase, like REST v2. There is no `playable` field: it would be true for
  nearly every station, and a field can be added later without breaking clients.
- **FR-018**: *(withdrawn: the `playable` flag was dropped in the review; the number is kept so
  references stay stable.)*
- **FR-019**: Stations the hub would refuse to play (HLS while disallowed) MUST be returned, never
  hidden; the play request reports the refusal.
- **FR-020**: Text from the catalogue MUST be stripped of control characters and bounded before it
  is returned: a station name to 256 characters (as in RADIO-001), each tag and language to a fixed
  length, and the number of tags and languages per station to a fixed count. A tag or language
  longer than the bound is dropped, not cut: a cut tag would match nothing when used as a filter.
- **FR-021**: `favicon` and `homepage` MUST be returned only when they are `http` or `https`
  addresses of bounded length, and empty otherwise. The extension MUST NOT fetch them.
- **FR-022**: Reading the catalogue's records MUST tolerate unknown, missing and malformed fields:
  a bad field becomes empty, a record without a valid UUID is dropped, and the request still
  succeeds.

**Errors**

- **FR-023**: Errors MUST be RFC 7807 problem details, like REST v2, and MUST affect only this
  extension's requests:
  - 400: a malformed UUID, country code, `order`, `direction`, `limit`, `offset`, `bitrateMin` or
    text filter, or a parameter the request does not define; the error names the parameter and no
    network call is made;
  - 404: a well-formed UUID the catalogue does not know;
  - 502: the catalogue answered with something unreadable, or refused the request;
  - 503: no catalogue server answered within the time budget, with a `Retry-After` header.
  The content type is `application/problem+json`. Every other error these requests can answer
  (a value of the wrong type such as `limit=abc`, a method that is not allowed, an unexpected
  failure as 500) MUST have the same form: the hub's own error handling covers only REST v2, so
  the extension handles them itself.
- **FR-024**: An invalid `order` or any other invalid parameter MUST be caught by the extension and
  never forwarded to become a catalogue error. Unknown parameters are refused (400) on these
  requests, so a misspelt filter cannot be mistaken for "no filter".

**Catalogue use**

- **FR-025**: One request, server discovery and failover included, MUST be bounded by a time budget
  of its own (default 5 seconds, a setting), separate from and shorter than the budget for playing
  a station. Each attempt on one server MUST also have a shorter bound, half the request's budget
  (2.5 seconds by default), so that a slow first server leaves time for the next.
- **FR-026**: Catalogue requests MUST reuse RADIO-001's server discovery, failover, pinned server
  list and user agent, and MUST always state an explicit result limit to the catalogue. RADIO-001
  already remembers the discovered server list and the last server that answered. Two things
  change, for plays as well as for these requests:
  - when the remembered list has expired, the request MUST use it as it is and the new look-up
    MUST NOT make that request wait (the look-up is still started only because a request came, so
    it is not a scheduled refresh); the new list replaces the old one when it arrives. A list
    whose servers all failed is not reused this way;
  - when there is no list at all, looking servers up MUST be bounded by what is left of the
    calling request's budget, not by a share of the play budget.
- **FR-027**: Country, language and tag lists MUST be remembered for 6 hours, keyed by the
  normalised parameters, with a bound on how many are kept, oldest dropped first. Station lookups
  reuse RADIO-001's short station memory. Search results are NOT remembered. Failures MUST NOT be
  remembered.
- **FR-028**: The catalogue MUST be contacted only because a client asked: no polling, crawling,
  prefetching or scheduled refresh.
- **FR-029**: A slow or unreachable catalogue MUST NOT delay hub start-up, playback or any request
  outside this extension, and one slow request MUST NOT block another.

**Operation**

- **FR-030**: The request time budget MUST be a setting under `multiroom.radio.*` with a working
  default; a value that is zero or negative MUST fail hub start-up with a message naming the
  extension and the setting. The limit and offset bounds and the list lifetime are fixed values;
  the per-attempt bound follows from the budget.
- **FR-031**: The extension MUST count catalogue requests by kind (`search`, `countries`,
  `languages`, `tags`, alongside RADIO-001's) and outcome, with no search text, station names or
  UUIDs in metric tags, and work unchanged when the hub has no metrics registry.
- **FR-032**: Logs MUST identify stations by UUID and MUST NOT contain unbounded text from the
  catalogue or from a client's filters.
- **FR-033**: The requests, their parameters, errors and data shapes MUST appear in the hub's
  published API description, with shape names prefixed `Radio` (`RadioStation`,
  `RadioStationPage`, `RadioCountry`, `RadioLanguage`, `RadioTag`, `RadioTagPage`,
  `RadioProblem`) so they cannot collide with another extension's.

### Key Entities

- **Station** (`RadioStation`): one catalogue station as clients see it: its UUID (the stable
  identifier), the `uri` that plays it, display details (name, icon and homepage addresses, country,
  languages, tags), technical details (codec, bitrate, HLS flag), catalogue signals (last check,
  votes, clicks in the last 24 hours). Everything but the UUID is untrusted community-edited text.
- **Country** (`RadioCountry`): two-letter code, English name, station count. Clients localise the
  name from the code.
- **Language** (`RadioLanguage`): name, ISO 639 code where known, station count.
- **Tag** (`RadioTag`): a free-text genre or label, with its station count.
- **Page** (`RadioStationPage`, `RadioTagPage`): one answer to a search or a tag listing: the
  list, and `nextOffset`, the `offset` of the next page or empty on the last one.
- **Search query**: the filters, ordering and paging of one search.
- **Problem** (`RadioProblem`): the error answer, in the same RFC 7807 form as REST v2.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can go from a typed name, or from a country, genre or language, to a playing
  station using only hub requests; no client contains code for catalogue servers, user agents or
  result clean-up.
- **SC-002**: 100% of stations returned by search or lookup carry a `uri` that the hub's play
  request accepts unchanged.
- **SC-003**: When the first catalogue server answers, a search or list is answered within 2
  seconds; a list asked for moments before is answered at once without contacting the catalogue.
  The first request after a hub restart may take longer, because it also looks the servers up,
  but stays within the time budget; an expired server list costs a request nothing.
- **SC-004**: With every catalogue server unreachable, every radio request answers "unavailable"
  within 5 seconds (default settings), and playback and the rest of the hub keep answering
  throughout.
- **SC-005**: 100% of invalid requests (UUID, country code, ordering, limit, offset, bitrate) are
  refused without the catalogue being contacted.
- **SC-006**: No single request can ask the catalogue for more than 100 results, and repeating a
  browse list within its lifetime costs the catalogue nothing.
- **SC-007**: A client that pages by following `nextOffset` until it is empty, dropping stations
  whose `uuid` it has already received, shows no station twice and reaches the end of every search
  and tag listing with one rule. Stations with equal values in the chosen order may still be
  skipped between pages (the catalogue's tie order is not stable).
- **SC-008**: Hub start-up is no slower with this feature than with RADIO-001 alone, including when
  radio-browser.info is unreachable.

## Acceptance (live)

Carried over from the backlog item. Run once by hand while the feature is verified against the real
hub (`multiroom.lan`), never in the automated build; record commands, date and results in
`verification.md`. Every step is a read-only request and makes no sound. Deploying the JAR and
restarting the hub need the owner's permission as always.

1. `GET /api/radio/stations?name=kiss&countryCode=UA&limit=10` returns at most 10 stations in
   `stations`, each with a UUID, `uri`, name, codec and bitrate, ordered by votes, highest first,
   and a `nextOffset` that is 10 or empty; following `nextOffset` reaches an answer where it is
   empty.
2. `GET /api/radio/stations` with no parameters returns 20 stations ordered by votes, highest
   first; with `order=clickCount` it returns 20 ordered by click count, highest first.
3. `GET /api/radio/countries`, `/languages` and `/tags?name=jazz` return lists ordered by station
   count, and the count for one small country is close to the number of stations a search for it
   returns (broken stations hidden in both; record both numbers); a second call within the lifetime does not reach radio-browser: the extension's catalogue
   request counter for that kind of request, read from `/actuator/metrics`, does not grow.
4. `GET /api/radio/stations/{uuid}` for a station from step 1 returns the same station; an unknown
   UUID answers 404 and a malformed one 400, both as problem details.
5. The requests and the `Radio*` shapes appear in the hub's `/api-docs` (the hub scans the whole
   `multiroom` package, so no hub change is expected).
6. Check the upstream facts this feature assumes and correct the spec if they differ: `tags` and
   `language` arrive as comma-separated text, a `bitrate` of 0 means unknown, the size of the full
   tag list and the sizes of the country and language lists, and the fields of the catalogue's
   country and language lists (`iso_3166_1` and `iso_639` according to its documentation). Also
   check, and correct the spec if they differ (the first two and the last were read in the
   catalogue server's source on 2026-11-15 and are expected to hold; the live check confirms the
   running servers behave the same):
   - `language=ukrainian` returns stations that list several languages (for example
     "ukrainian,russian"), not only single-language ones; if it does not, `language` needs another
     rule;
   - `tag=jazz` does not return a station whose only tag is "acid jazz";
   - the same search with `order=clickCount` and with `order=votes` (the default), asked twice
     and as two adjacent pages, shows how stable the order among equal values is (record the
     number of repeated or missing stations for each order);
   - the country, language and tag lists accept hiding broken stations, and their counts drop
     accordingly.
7. Record which codec labels the catalogue uses: one read-only request for the catalogue's codec
   list (`/json/codecs`, with the extension's user agent), saved with each label's station count
   in `verification.md`. The labels the hub cannot play (WMA, labels that include a video codec)
   are named there, and the backlog question "labels the hub cannot play" is updated from that
   evidence. Nothing in this feature's code depends on the result.

Steps 6 and 7 were run on 2026-11-15, before planning, straight against one catalogue server
(`verification.md`). Every assumption held: exact matching needs the two flags and keeps
multi-language stations; `tags` and `language` are comma-separated text; `bitrate` 0 occurs;
`hls` and `lastcheckok` arrive as 0 or 1; there are 225 countries, 585 languages (447 without a
code) and 11145 tags; the order among equal values did not change between requests or page sizes.
They are repeated only if planning changes what is asked of the catalogue.

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
  - *Out-of-range `limit` and `offset` are refused (400), not silently reduced*, so that a
    client that does not know the cap never advances `offset` by more than the hub served.
  - *The default ordering is `votes`, highest first, with or without filters* (decided by the
    owner, see Clarifications). One default is easier to predict than one that changes when a
    filter is added; a home screen that wants a "trending" list asks for `order=clickCount`. No
    order is stable among equal values, because the catalogue sorts by one value only, so clients
    drop repeats by `uuid`.
  - *Search and tag results are an object with `nextOffset`*, unlike REST v2's plain lists, which
    are not paged. A bare list could never gain a "more" marker without a new path, and the hub,
    not the client, knows whether the catalogue's page was full.
  - *Cache*: lists for 6 hours, fixed; searches are not cached (on one household hub a
    one-minute search cache would rarely hit and would add bounded-map code and tests; it can come
    back if a client is seen repeating searches).
  - *Fixed bounds*: `limit` at most 100 and `offset` at most 10000 are contract values, not
    settings, so the API description can state them; a configurable cap could not. Constitution
    VIII still lists "a result-limit cap outside its allowed range" as a configuration fault; the
    plan's Constitution Check records that there is no such setting.
  - *Strict parameters*: unknown parameters answer 400 here, unlike elsewhere in the hub, because
    this API is called by tools that guess parameter names and a silent unfiltered 200 hides the
    mistake.
  - *Bounds on untrusted text* (tag length, tags per station, address length, filter length) are
    fixed values chosen during planning, not settings.
- **No `playable` flag**: the hub decodes every codec label the extension maps, and unmapped labels
  are let through, so nearly every station would be playable. Teaching the codec decision about
  labels that are known not to work stays an open backlog question and is not part of this
  feature's code. Live acceptance step 7 collected the labels the catalogue really uses
  (`verification.md`): there is no WMA label; the candidates are the three labels that include
  `H.264` (59 stations), `MP4` and `FLV` (30), and `OGG` and `UNKNOWN` are untested. When it is answered, a `playable` field is added
  (non-breaking) and RADIO-001's refusal changes together with it.
- **Out of scope**: a play request; voting; a `playable` field and a `playableOnly` filter (only
  once a client needs them and the hub knows an unplayable label); favourites
  (`favourite-stations.md`); other filters the catalogue has (`bitrateMax`, `is_https`, several tags at once, word matching for `name` or `language`);
  totals in results (the catalogue does not report them); sharing one catalogue call between
  identical requests in flight.
- **Client work is separate**: `sonora radio …` commands and `hub` package methods in `sonora-cli`,
  the MCP tool in `sonora-mcp` and the browse and search screen in `sonora-mobile` go into those
  repositories' backlogs once this API is fixed, as does the question of whether their copies of
  the hub's API description include the radio requests.
- **Compatibility promise** (decided 2026-11-15): `/api/radio/**` stays unversioned, like
  `/api/tts/**`, and is outside REST v2. Within a released version of the extension, existing
  parameters, field names and error statuses do not change incompatibly; new optional parameters
  and new fields may be added, so clients must ignore fields they do not know. A breaking change
  would need a new path. The API is outside `api.Spec`, so the typed clients in `sonora-cli` and
  `sonora-mobile` will not see it unless their spec copies are fetched from a hub with the
  extension; each client repository decides its approach in its own backlog.
- **The API is unauthenticated on the home LAN**, like the rest of the hub.
- **Tests never contact radio-browser.info or its DNS** (constitution XII); the live checks above
  are the only contact, run once, by hand.
