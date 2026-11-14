# Contract: configuration (`multiroom.radio.*`)

Set in the hub's `multiroom.yml` (or any Spring property source). Every property has a working
default, held in a field initialiser; the extension ships no `application.yml`. A value that breaks
a rule fails hub start-up with a message naming `multiroom.radio` and the property.

| Property | Type | Default | Rule |
|---|---|---|---|
| `enabled` | boolean | `true` | `false`: no beans at all; the hub lists the extension as disabled |
| `user-agent` | text | `sonora-radio/<version>` | Not blank, no CR or LF, at most 200 characters. Must not identify the owner or the home |
| `connect-timeout` | duration | `2s` | Positive |
| `read-timeout` | duration | `4s` | Positive |
| `resolve-timeout` | duration | `10s` | Positive, and at least `connect-timeout`. Bounds one whole resolution, discovery included |
| `max-servers-per-request` | integer | `3` | 1–10 |
| `servers` | list of URLs | empty | Empty: discover. Otherwise each an absolute `http`/`https` URL with a host, no path beyond `/`, no query or fragment; used in the order given |
| `discovery-host` | host name | `all.api.radio-browser.info` | A plain host name (no scheme, port or path) |
| `discovery-timeout` | duration | `3s` | Positive. How long a play waits for the DNS lookup at most (less when the discovery share below has less left). Discovery as a whole (DNS plus the `/json/servers` fallback) is also cut off at half of `resolve-timeout` |
| `server-list-ttl` | duration | `1h` | Positive |
| `station-cache-ttl` | duration | `5m` | Positive. How long a found station's lookup is reused; play reports are never cached |
| `station-cache-size` | integer | `100` | 1–10000. Oldest entry dropped first |
| `allow-hls` | boolean | `true` | `false` refuses stations marked HLS |

## Example

```yaml
multiroom:
  radio:
    enabled: true
    resolve-timeout: 10s
    # Pin servers during an outage, or for acceptance check 7:
    # servers:
    #   - https://unreachable.invalid
    #   - https://de1.api.radio-browser.info
```

## Notes

- The `radio` in `multiroom.radio` is the extension id: the POM's `extension.id`, the
  auto-configuration's `@ConditionalOnProperty` and this prefix are the same string.
- A pinned list never expires and is never discovered again; remove it to return to discovery.
- A pinned list is always tried from its first entry, in the order written; it is never rotated
  towards the server that last answered. An unreachable first entry therefore costs its timeout on
  every play.
