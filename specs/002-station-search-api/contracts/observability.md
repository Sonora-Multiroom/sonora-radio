# Contract: metrics and logs added by RADIO-002

Adds to RADIO-001's `contracts/observability.md`. No new meter.

## Meters

| Name | Change |
|---|---|
| `radio.catalogue.requests` | the `endpoint` tag gains `search`, `countries`, `languages`, `tags`. A request answered from the hub's memory counts nothing |
| `radio.discoveries` | also counts a background refresh of an expired server list |

The time and status of the API requests themselves are in the hub's own `http.server.requests`
(tag `uri`, for example `/api/radio/stations/{uuid}`).

Search text, tag and language names, station names and UUIDs never appear in tags.

## Reading the cache check (acceptance step 3)

```bash
curl -s 'http://multiroom.lan:8080/actuator/metrics/radio.catalogue.requests?tag=endpoint:countries'
```

The `COUNT` value does not grow when the list is asked for again within 6 hours.

## Logs (logger `multiroom.radio`)

| Level | When | Content |
|---|---|---|
| DEBUG | each API request answered | endpoint, number of records returned, answering server or `cache`, elapsed ms |
| WARN | an API request answers 502 or 503 | endpoint, servers tried |
| ERROR | an API request answers 500 | the exception |
| INFO | a background refresh replaces the server list | source, number of servers |

Filter text sent by a client and text from the catalogue are never logged by the API; a single
station is identified by its UUID.
