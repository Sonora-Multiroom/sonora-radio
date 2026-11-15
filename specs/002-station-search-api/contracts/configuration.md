# Contract: configuration added by RADIO-002

Adds to RADIO-001's `contracts/configuration.md`; every property there keeps its meaning.

| Property | Type | Default | Rule |
|---|---|---|---|
| `multiroom.radio.request-timeout` | duration | `5s` | Positive. Bounds one `/api/radio/**` request, server discovery and failover included. Each attempt on one server gets at most half of it |

A value that is zero, negative or unparseable fails hub start-up with a message naming
`multiroom.radio.request-timeout`.

## Changed meaning

| Property | Change |
|---|---|
| `server-list-ttl` | After this time the list is still used while it is looked up again in the background; it no longer makes a request wait |
| `discovery-timeout` | Still the longest wait for the DNS lookup. Discovery inside a request is now cut off at half of the time that request has left (was: half of `resolve-timeout`); for a play with the default settings that is the same 5 s |
| `connect-timeout`, `read-timeout` | Also apply to API requests, where the attempt limit above is usually the smaller bound |
| `station-cache-ttl`, `station-cache-size` | Also bound how old `GET /api/radio/stations/{uuid}` may be |

## Not settings

The page size limits (20 by default, at most 100), the largest `offset` (10000), the list lifetime
(6 hours) and the bounds on catalogue text are fixed values published in the API description.
`multiroom.radio.enabled=false` removes the API together with everything else; there is no
separate switch for it.

## Example

```yaml
multiroom:
  radio:
    request-timeout: 5s
```
