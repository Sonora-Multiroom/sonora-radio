# Data Model: Play a station by its UUID (RADIO-001)

Nothing is persisted. These are the values that pass through one resolution, and the
configuration and cached state that shape it. Field names are the extension's own; radio-browser's
JSON names appear only in the "from" column.

## StationReference

The parsed form of the URI a client sent. Built by the resolver before any network call.

| Field | Type | Rule |
|---|---|---|
| `uuid` | UUID | Required. The URI's authority, parsed with a strict 8-4-4-4-12 hex pattern (case-insensitive), then held in canonical lower case |
| `label` | text, optional | Whatever follows `#`. Ignored after parsing; never logged |

Accepted: `radiobrowser://<uuid>`, `radiobrowser://<uuid>/`, `radiobrowser://<uuid>#<label>`,
any case in the scheme and the UUID. Refused (`INVALID_INPUT`, no network call): an empty
authority, a non-UUID authority, a path other than `/`, a query, user info or a port. See
[contracts/station-uri.md](contracts/station-uri.md).

## Station (from `/json/stations/byuuid`, untrusted)

| Field | From | Type | Handling |
|---|---|---|---|
| `uuid` | `stationuuid` | UUID | Must equal the requested UUID; an array with no matching entry is "unknown station" |
| `name` | `name` | text | Becomes the title (see StationTitle); missing → no title |
| `streamUrl` | `url_resolved` (else `url`) | text | Fallback address when the play report fails; validated as StreamAddress |
| `codecLabel` | `codec` | text | Trimmed, upper-cased for the codec policy; missing → empty |
| `hls` | `hls` | boolean | `1`/`true` → HLS; anything else → not HLS |

Unknown fields are ignored; a field of the wrong type counts as missing. A body that is not a JSON
array is an upstream protocol error.

## PlayReport (from `/json/url/{uuid}`, untrusted)

| Field | From | Type | Handling |
|---|---|---|---|
| `ok` | `ok` | boolean | `false` → treated as a failed report (fallback to `Station.streamUrl`) |
| `url` | `url` | text | The address to play when present and valid |

## StreamAddress

The URI handed to the hub. Rules: parses as an absolute URI; scheme `http` or `https`
(case-insensitive); has a host. Anything else → `UNSUPPORTED_RESOURCE` naming the station UUID and
the scheme (not the full address).

## StationTitle

`name` with control characters, line and paragraph separators removed, whitespace runs collapsed
to one space, trimmed, and cut at 256 code points (never inside a surrogate pair). Blank →
`null`. The hub shortens it further to its own display limit.

## CodecDecision

| Label (normalised) | Mapped codec | Outcome |
|---|---|---|
| `MP3` | `mp3` | refused only if the hub's registry says unsupported |
| `AAC`, `AAC+` | `aac` | same |
| `OPUS` | `opus` | same |
| `FLAC` | `flac` | same |
| `OGG`, `UNKNOWN`, empty, anything else | — | allowed; the decoder detects the format from the stream |

No codec registry in the host → allowed.

## ResolvedInputEndpoint (hub API, produced)

| Field | Value |
|---|---|
| `originalInput` | the input as given (re-lookups start from it) |
| `effectiveUri` | the StreamAddress |
| `headers` | empty |
| `expiresAt` | `null` |
| `refreshMode` | `RE_RESOLVE_ORIGINAL_INPUT` |
| `resolverId` | `radiobrowser` |
| `title` | StationTitle, or `null` |
| `creator` | `null` |

## CatalogueServer

| Field | Type | Rule |
|---|---|---|
| `baseUri` | URI | `https://<name>` when discovered; as configured when pinned |
| `tag` | text | The host name (with port when not default), used in logs and metric tags |

## ServerList (cached state)

| Field | Type | Notes |
|---|---|---|
| `servers` | ordered list of CatalogueServer | Pinned order, or shuffled discovery |
| `discoveredAt` | instant | `null` for a pinned list (never expires) |
| `next` | index | Where the next request starts: the server that last answered |

States: **empty** (nothing discovered yet; the first play discovers) → **fresh** (used as is) →
**stale** (older than `server-list-ttl`, or every server failed on the last play: discovered again
on the next play) → **fresh**. Held in an `AtomicReference`; replaced whole, never locked across a
network call. A pinned list is always fresh.

## RadioProperties (`multiroom.radio.*`)

Full table with defaults and validation in [contracts/configuration.md](contracts/configuration.md).

## ResolutionOutcome (metric and log value)

`ok`, `fallback`, `invalid_uuid`, `unknown_station`, `unsupported_codec`, `hls_refused`,
`unsupported_address`, `upstream_unavailable`, `upstream_protocol_error`. Fixed set; see
[contracts/observability.md](contracts/observability.md).
