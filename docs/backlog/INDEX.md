# Backlog index

Every item in `docs/backlog/`, highest priority first. Each one is a proposal, not a requirement
(see the Backlog section of [AGENTS.md](../../AGENTS.md)). When you add, ship or delete an item,
update this list.

| Priority | Meaning |
|---|---|
| **P1** | A crash, data loss or other user-visible fault. Do it next. |
| **P2** | Wrong or confusing behaviour, or something production needs now. |
| **P3** | A worthwhile feature or cleanup with no pressure on it. |
| **P4** | Only worth doing once a concrete need appears. |

| Priority | Item | Kind | Effort | Why this priority |
|---|---|---|---|---|
| P4 | [A `playable` flag on stations](playable-station-flag.md) | Feature | Small to medium | Needs audible tests to learn which codec labels the hub cannot play; only worth it once a client asks |
| P4 | [Favourite stations](favourite-stations.md) | Feature | Medium | Clients can keep favourites themselves until the hub has a persistence layer |
| P4 | [Stations as configured inputs in `multiroom.yml`](configured-station-inputs.md) | Feature | Small (here) | No longer blocked (the hub accepts resolver schemes since HUB-039); what remains is documenting and verifying it. Runtime play covers the need until stations must survive a restart |
