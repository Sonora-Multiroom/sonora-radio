# Contract: metrics and logs

Metrics go to the hub's own `MeterRegistry` (scraped by Prometheus with dots as underscores). With
no registry, or no Micrometer on the classpath, nothing is recorded and nothing else changes.

## Meters

| Name | Type | Tags | Meaning |
|---|---|---|---|
| `radio.resolutions` | counter | `outcome`, `server` | One per `resolve` call (first plays and re-lookups) |
| `radio.resolution.duration` | timer | `outcome` | Wall time of one `resolve`, discovery included |
| `radio.catalogue.requests` | counter | `server`, `endpoint`, `result` | One per HTTP request to a catalogue server |
| `radio.discoveries` | counter | `source`, `result` | One per server discovery |

## Tag values (closed sets)

| Tag | Values |
|---|---|
| `outcome` | `ok`, `fallback`, `invalid_uuid`, `unknown_station`, `unsupported_codec`, `hls_refused`, `unsupported_address`, `upstream_unavailable`, `upstream_protocol_error` |
| `server` | the answering server's host name (with port if not default), or `none` when no server answered or none was asked |
| `endpoint` | `station`, `play`, `servers` |
| `result` (requests) | `ok`, `timeout`, `connect_error`, `http_4xx`, `http_5xx`, `bad_body` |
| `source` | `dns`, `server_list`, `pinned` |
| `result` (discoveries) | `ok`, `empty`, `timeout`, `error` |

Station names, UUIDs, codec labels and stream addresses never appear in tags.

## Logs (logger `multiroom.radio`)

| Level | When | Content |
|---|---|---|
| INFO | each resolution | station UUID, outcome, answering server, elapsed ms |
| WARN | a server fails and the next is tried | server, endpoint, result |
| WARN | a resolution fails because no server answered | station UUID, servers tried |
| INFO | discovery completes | source, number of servers |
| DEBUG | each request | full request URL and the `User-Agent` sent |

Station names and labels after `#` are never logged; catalogue text that must be logged (an
unexpected codec label) is bounded to 32 characters with control characters removed.
