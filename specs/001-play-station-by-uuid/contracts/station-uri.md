# Contract: the `radiobrowser://` station URI

What a hub client may send, and what it gets back. Clients use the hub's existing REST v2 requests;
this feature adds no endpoint.

## Grammar

```text
station-uri = "radiobrowser://" uuid [ "/" ] [ "#" label ]
uuid        = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG   ; any case
label       = *( any URI fragment character )                            ; ignored
```

The scheme is matched case-insensitively. The UUID is radio-browser's `stationuuid`; it is
compared and sent in lower case.

## Where it is accepted

| Request | Behaviour |
|---|---|
| `POST /api/v2/play` with `"uri": "radiobrowser://<uuid>"` | The hub registers an ephemeral input, starts the route, and this extension resolves the station while the route starts |
| `POST /api/v2/inputs` with the URI | Registered without contacting the catalogue; resolved when a route first plays it |
| `multiroom.yml` inputs | Accepted by a hub with HUB-039 once this extension is loaded (the hub asks the resolver at start-up whether it handles the scheme, which contacts nothing) and resolved when a route plays it. **Not tested or promised by this feature** (backlog `configured-station-inputs.md`) |

With the extension absent or disabled, the hub's passthrough fails the play as an unsupported
scheme, exactly as for any unknown scheme today.

## Results

| Case | HTTP (hub REST v2) | Problem detail mentions |
|---|---|---|
| Station plays | 2xx, as for any play | — ; the input is named after the station when the client gave no name |
| Malformed URI or UUID | 400 | the value (truncated to 64 characters) |
| Unknown station | 400 | `radiobrowser://<uuid>` and "not found in radio-browser" |
| Codec known and not decodable | 422 | the UUID and the codec label |
| HLS switched off and station is HLS | 422 | the UUID and "HLS" |
| Catalogue gives a non-HTTP stream address | 422 | the UUID and the address's scheme |
| No catalogue server answered within the time budget | 503 | the UUID and "radio-browser unavailable" |
| Catalogue answered with unreadable data | 502 | the UUID |

In every failing case the hub removes the ephemeral input it created, and nothing plays.

## While playing

The input is not pauseable (live stream). When the hub reports the stream address refused or
expired, it asks this extension again, which looks the station up by its UUID and reports the play
again (the catalogue counts at most one play per address, station and day). A catalogue outage
during that re-lookup is retried by the hub with its backoff; an unknown station ends the route.

## Examples

```text
radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81
radiobrowser://960E57C5-0601-11E8-AE97-52543BE04C81          same station
radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81#kissfm   same station, readable
radiobrowser://kissfm                                         400, not a UUID
radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81?x=1       400, query not allowed
```

(The UUIDs above are illustrative.)
