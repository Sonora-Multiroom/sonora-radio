# Feature Specification: Play a station by its UUID

**Feature Branch**: `001-play-station-by-uuid`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "@docs/backlog/play-station-by-uuid.md" (backlog item written 2026-11-12,
decisions agreed with the owner 2026-11-13, against `multiroom-api` 0.1.33). This is RADIO-001, the
first feature of the extension: its skeleton plus the `radiobrowser://` station scheme.

## User Scenarios & Testing *(mandatory)*

The actors are **hub clients** (`sonora-cli`, `sonora-mcp`, `sonora-mobile`, MQTT, Home Assistant,
or a person with `curl`) that ask the hub to play something, and the **hub operator** (the owner),
who installs and configures the extension on the Raspberry Pi.

### User Story 1 - Play a station by naming it (Priority: P1)

A client asks the hub to play `radiobrowser://<stationuuid>` on an output, exactly as it would ask
it to play a stream URL today. The hub looks the station up in the radio-browser.info catalogue,
plays its current stream, and shows the input under the station's name.

**Why this priority**: It is the whole feature. Without it, every client has to find stream URLs
itself, and a route that holds only a URL cannot recover when the station moves its stream.

**Independent Test**: With the extension installed, play the UUID of a known-good station on the
`office` output through the hub's existing play request; the station is heard, and the hub's list
of inputs shows the station's name, not the UUID.

**Acceptance Scenarios**:

1. **Given** the extension is installed and enabled, **When** a client asks the hub to play
   `radiobrowser://<uuid of a known-good station>` on an output, **Then** the station plays on that
   output and the play counts once towards the station's popularity in the catalogue.
2. **Given** a client did not supply a name for the input, **When** the station starts, **Then**
   clients see the input named after the station (for example "Radio ROKS"), not the UUID.
3. **Given** a client registers `radiobrowser://<uuid>` as an input without playing it, **When**
   nothing plays it yet, **Then** the catalogue is not contacted; it is contacted when a route
   starts playing that input.
4. **Given** a station UUID written in upper case or mixed case, **When** it is played, **Then** it
   is treated as the same station as its lower-case form.

---

### User Story 2 - A bad station fails clearly and leaves nothing behind (Priority: P1)

A client sends a UUID that is malformed, unknown to the catalogue, or names a station whose stream
the hub cannot decode. The request fails with an error that names the station (and the codec, when
that is the reason), nothing plays, and no stray input is left in the hub.

**Why this priority**: Clients (including an MCP assistant) will send wrong UUIDs. A silent failure,
a hang, or a leftover input on the owner's real audio system is worse than no feature.

**Independent Test**: Send a malformed UUID, a well-formed but unknown UUID, and the UUID of a
station whose codec the hub cannot decode; each answers with a clear error, nothing is heard, and
the hub's input list is unchanged.

**Acceptance Scenarios**:

1. **Given** a malformed UUID, **When** it is played, **Then** the request fails naming the value,
   and the catalogue is not contacted.
2. **Given** a well-formed UUID that the catalogue does not know, **When** it is played, **Then**
   the request fails with an error naming the station UUID, and nothing plays.
3. **Given** a station whose codec is known to the catalogue and not decodable by the hub, **When**
   it is played, **Then** the request fails with an error naming that codec.
4. **Given** a station whose codec is unlabelled, labelled `UNKNOWN` or labelled `OGG`, **When** it
   is played, **Then** the hub attempts to play it (the stream itself decides) rather than refusing
   it up front.
5. **Given** the catalogue returns a stream address that is not `http` or `https`, **When** it is
   played, **Then** the request fails and the hub opens nothing at that address.
6. **Given** any of the failures above, **When** the request has failed, **Then** no input created
   by that request remains in the hub.

---

### User Story 3 - Playback survives catalogue and station trouble (Priority: P2)

radio-browser.info is a set of volunteer-run mirror servers, any of which can be down, and stations
move their stream addresses. A play succeeds as long as one mirror answers, and a station whose
stream address was refused is looked up again rather than abandoned.

**Why this priority**: Mirrors and stations change without notice; without this the feature works
on the day it is tested and breaks soon after. It builds on Story 1.

**Independent Test**: Configure a server list whose first entry is unreachable; a play still
succeeds through the next server, within the time limit, and the log says which server answered.

**Acceptance Scenarios**:

1. **Given** the first catalogue server tried is unreachable or slow, **When** a station is played,
   **Then** the next server is tried and the play succeeds, within the bounded time below.
2. **Given** every catalogue server is unreachable, **When** a station is played, **Then** the
   request fails with an error naming the station within a bounded time, and the hub stays
   responsive for everything else.
3. **Given** the catalogue's play-counting lookup fails but the plain station lookup succeeds,
   **When** a station is played, **Then** it still plays, using the station's already-resolved
   stream address.
4. **Given** a station is playing and the hub reports its stream address was refused, **When** the
   hub asks for the input again, **Then** the station is looked up again by its UUID and the
   current stream address is used.

---

### User Story 4 - The operator stays in control (Priority: P3)

The owner installs the extension as a drop-in, can switch it off, can pin the catalogue servers it
uses, can tune its time limits, and can see from the hub's inventory, log and metrics what it is
doing, without the extension ever affecting hub start-up.

**Why this priority**: Needed to run the feature safely in production, but delivers no playback on
its own.

**Independent Test**: Deploy the extension, restart the hub, and confirm the hub's extension
inventory lists it with its version and not rejected; set the switch-off flag, restart, and confirm
the hub starts and lists the extension as disabled.

**Acceptance Scenarios**:

1. **Given** the extension is installed, **When** the hub starts, **Then** its extension inventory
   lists the extension with its version, and it is not rejected.
2. **Given** the extension is switched off in configuration, **When** the hub starts, **Then** the
   hub starts normally, contributes nothing from the extension, and lists it as disabled.
3. **Given** radio-browser.info is unreachable when the hub starts, **When** the hub starts,
   **Then** start-up is neither delayed nor failed by the extension.
4. **Given** a malformed configuration (a pinned server that is not a valid address, a time limit
   that is zero or negative), **When** the hub starts, **Then** start-up fails with a message that
   names the extension and the faulty setting.
5. **Given** stations are played, **When** the operator reads the hub's metrics, **Then** they see
   resolution counts by outcome and by catalogue server, with no station names or UUIDs in them.

---

### Edge Cases

- **HLS stations** (the catalogue marks them `hls`): allowed. If the live check shows they do not
  play reliably, they are refused at play time with a clear reason, and the decision and evidence
  are recorded (hub backlog `hls-link-refresh.md`) rather than holding up the feature.
- **A station the catalogue marks as failing its last check**: still attempted; the catalogue's
  check may be stale, and the stream itself decides.
- **A human-readable label after the UUID** (`radiobrowser://<uuid>#radio-roks`): accepted and
  ignored, so a URI can be made readable in logs and configuration; the station is identified by
  the UUID alone.
- **A station name that is very long, or contains HTML or control characters**: stripped of control
  characters and bounded by a safety limit before it is offered as the input's title; the hub
  shortens it to its own display length. Logs identify the station by UUID, not by name.
- **A reconnect after a network blip, or repeated plays of the same station**: every play and
  every re-lookup goes through the catalogue's play-counting lookup; the catalogue itself counts at
  most one play per address per station per day, so reconnects do not inflate popularity.
- **A stream host that vanishes while playing** (not a refused address): the hub does not yet look
  the station up again for this kind of failure (hub backlog `re-resolve-on-reconnect-failure.md`),
  so the route ends. Out of scope here.
- **A station in `multiroom.yml`**: the hub's configuration validator refuses the scheme today;
  covered by backlog `configured-station-inputs.md` and the hub's `resolver-schemes-in-config.md`.
  Out of scope here.
- **The extension not installed**: the hub refuses the scheme as unsupported, as it does today.

## Requirements *(mandatory)*

### Functional Requirements

**Station scheme**

- **FR-001**: The extension MUST accept `radiobrowser://<stationuuid>` wherever the hub accepts an
  input URI at runtime (the existing play and register-input requests), with no change to the
  hub's REST API.
- **FR-002**: The extension MUST validate the station identifier as a UUID before any network call,
  and treat it in its canonical lower-case form.
- **FR-003**: The extension MUST accept and ignore an optional label after the UUID, introduced by
  `#`.
- **FR-004**: The extension MUST claim only the `radiobrowser` scheme and leave every other URI to
  the hub's other resolvers.

**Resolving a station**

- **FR-005**: To resolve a station the extension MUST use the catalogue's play-counting station URL
  lookup, so that every play (re-lookups included) is reported to the catalogue; it MUST NOT report
  a play for anything other than resolving a station for playback.
- **FR-006**: If the play report fails, the extension MUST play the stream address from the station
  lookup it already made.
- **FR-007**: The extension MUST hand the hub only an `http` or `https` stream address and refuse
  any other scheme with an error naming the station UUID.
- **FR-008**: The extension MUST offer the station's name to the hub as the resolved input's title,
  stripped of control characters and bounded by a generous safety limit, so that clients that
  supplied no input name see the station's name. It MUST NOT shorten the name to a display length:
  the hub decides how long an input name may be.
- **FR-009**: The extension MUST ask the hub to look the station up again by its UUID when the hub
  reports the stream address was refused, rather than retrying the old address.
- **FR-010**: The extension MUST refuse a station, with an error naming its codec, only when the
  catalogue's codec label maps to a known codec that the hub reports it cannot decode. Labels map
  as `MP3` → mp3, `AAC` and `AAC+` → aac, `OPUS` → opus, `FLAC` → flac (case-insensitive); `OGG`,
  `UNKNOWN`, an empty label and any unmapped label MUST be let through.
- **FR-011**: The extension MUST allow HLS stations, unless the live acceptance check shows they do
  not play reliably, in which case it MUST refuse them with a clear reason.
- **FR-012**: An unknown station, a malformed UUID, a refused codec, a non-HTTP address and an
  unreachable catalogue MUST each fail the play with an error naming the station UUID (or the
  malformed value), and MUST leave nothing playing.

**Catalogue servers**

- **FR-013**: The extension MUST discover catalogue servers itself (the catalogue's published
  discovery name, then its server list as a fallback), shuffle them, and fail over to the next on
  an error or timeout. No server host name is hard-coded.
- **FR-014**: Discovery MUST be lazy: nothing contacts the catalogue or its DNS while the hub
  starts; the first play triggers it.
- **FR-015**: The operator MUST be able to pin an explicit server list in configuration, which
  replaces discovery.
- **FR-016**: Every catalogue request MUST carry the user agent `sonora-radio/<extension version>`,
  overridable in configuration, and MUST NOT carry anything identifying the owner, the home or its
  host names.
- **FR-017**: Every catalogue request MUST have a short connect and read time limit, and one play
  MUST try a bounded number of servers within a bounded total time, without blocking other
  routes.

**Operation**

- **FR-018**: The extension MUST load as a drop-in extension with id `radio`, requiring hub API
  0.1.33, and appear in the hub's extension inventory with its own version (starting at 0.1.0).
- **FR-019**: Setting `multiroom.radio.enabled=false` MUST leave the hub starting normally with
  nothing contributed by the extension.
- **FR-020**: All settings MUST live under `multiroom.radio.*` with working defaults (enabled, user
  agent, time limits, pinned server list); a malformed setting MUST fail hub start-up with a
  message naming the extension and the setting.
- **FR-021**: The extension MUST record resolution counts by outcome and by catalogue server, with
  no station names or UUIDs in metric tags, and work unchanged when the hub has no metrics
  registry.
- **FR-022**: The extension MUST log which catalogue server answered each resolution, and at debug
  level the user agent it sent; logs identify stations by UUID, never by unbounded catalogue text.

### Key Entities

- **Station reference**: the `radiobrowser://<stationuuid>[#label]` URI a client sends. Its only
  meaningful part is the station UUID, the one identifier that is stable across catalogue servers
  and edits.
- **Station** (from the catalogue, untrusted): UUID, name, stream address (as the catalogue
  resolved it), codec label, HLS flag. Only these are used by this feature.
- **Resolved input**: what the extension hands the hub: an `http(s)` stream address, a display
  title (the station's bounded name), and the instruction to look the station up again when its
  address is refused.
- **Catalogue server**: one radio-browser.info mirror, discovered or pinned; tried in shuffled
  order with failover.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client plays any known-good catalogue station with a single existing hub request,
  knowing only its UUID; no client needs code for mirror selection, user agent or play reporting.
- **SC-002**: A station resolves, and its stream starts, no more than 2 seconds later than the same
  station played by its stream address directly, when the first catalogue server answers.
- **SC-003**: With every catalogue server unreachable, a play fails with an error within 15 seconds
  (default settings), and other routes keep playing throughout.
- **SC-004**: 100% of failed plays (malformed UUID, unknown station, refused codec, unreachable
  catalogue) leave no input behind and nothing playing.
- **SC-005**: Hub start-up time is unchanged (within normal variance) with the extension installed,
  including when radio-browser.info is unreachable.
- **SC-006**: A play succeeds while at least one catalogue server answers, whichever position it has
  in the server order.
- **SC-007**: Clients show the station's name for 100% of station inputs whose name the client did
  not supply.

## Acceptance (live)

Carried over from the backlog item. Run once by hand while the feature is verified against the
real hub (`multiroom.lan`), never in the automated build; record commands, date and results in
`verification.md`. Audible steps follow the parent `AGENTS.md`: ask first, record and restore the
`office` output's state, low volume, stop the test route first, a known-good station (KissFM,
Kraina FM, Radio NV).

1. The deployed extension is listed in `/actuator/extensions` with its version and is not
   `REJECTED`; with `multiroom.radio.enabled=false` the hub starts and lists it as disabled.
2. `POST /api/v2/play` with `radiobrowser://<uuid of a known-good station>` to `office` plays
   audibly; the route's input shows the station's name, not the UUID.
3. An unknown but well-formed UUID and a malformed one each fail with a clear error naming the
   station, and nothing plays; no input is left behind in `GET /api/v2/inputs`.
4. The hub log shows which radio-browser server answered, and the request carried the
   `sonora-radio/<version>` user agent (from the extension's own debug log).
5. An HLS station plays on `office` for a few minutes, or, if it does not, the decision to refuse
   HLS is recorded with the evidence.
6. A station whose codec is known and unsupported is refused with an error naming the codec, and
   one labelled `UNKNOWN` (with a playable stream) plays.
7. With the configured server list pointing at an unreachable host first, the play still succeeds
   through the next server.
8. Afterwards: no ALSA playback device is open, `audio.output.routes` for `office` reads 0, and the
   `office` volume and mute are back as recorded.

## Assumptions

- **No hub change is needed** for runtime play: the hub's play and register-input requests accept
  any non-blank URI, offer it to installed resolvers in order, and resolve it when a route starts
  (read in `sonora-multiroom` master, API 0.1.33). The hub reports such an input as not pauseable,
  which suits live radio.
- **Input naming comes from the hub**: since API 0.1.28 a resolver can supply a title, and the hub
  names an input with it when the client gave none.
- **Codec support is the hub's own list** (today mp3, aac, opus, webm, ogg_vorbis, wav, flac); the
  extension asks the hub rather than keeping a copy.
- **Default limits** (tunable): about 2 s to connect and 4 s to read per request, at most 3 servers
  per play; together these keep a failing play under the 15 s in SC-003.
- **Name length is the hub's call**: the hub cuts a resolved title to its own display limit (64
  characters at API 0.1.33, which the owner may raise). The extension's safety limit (256
  characters) only bounds untrusted text and is well above any display limit.
- **The label after `#`** is decided here (the backlog left it open): accepted and ignored. The
  resolved station name, not the label, names the input.
- **Out of scope**: catalogue search and browse (`station-search-api.md`), favourites
  (`favourite-stations.md`), stations in `multiroom.yml` (`configured-station-inputs.md`), voting,
  caching of station lookups for play (every play must be reported), and re-lookup after
  non-refusal reconnect failures (a hub backlog item).
- **Clients** (`sonora-cli`, `sonora-mcp`, `sonora-mobile`) need no change to use this feature; any
  convenience commands are their own backlog items.
- **Tests** never contact radio-browser.info or its DNS (constitution XII); the live checks above
  are the only contact, run once, by hand.
