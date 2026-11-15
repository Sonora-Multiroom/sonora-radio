# A `playable` flag on stations

**Status:** Idea, not designed. Split out of the catalogue search item when RADIO-002 shipped
(2026-11-16).

**Origin:** RADIO-002 returns every station and no `playable` field, because the codec policy
maps nearly every label to something the hub can decode and lets unmapped labels through.

**Depends on:** RADIO-002 (shipped). Audible tests on `multiroom.lan`, which need the owner's
permission.

**Effort:** Small to medium. A policy change in `CodecPolicy`, a new field on `RadioStation` (a
non-breaking addition), and a parameter on search.

## Problem

- A client cannot tell from a search result which stations will fail to start. The play request
  answers 422 and that is the first it hears of it.
- Hiding unplayable stations after the upstream query would return short pages, and a short page
  means the last one (see RADIO-002's `nextOffset` rule).
- The labels recorded on 2026-11-15 (`specs/002-station-search-api/verification.md`): no `WMA` and
  no `OPUS` label exists, so the `OPUS` mapping never matches. Candidates to refuse are
  `AAC,H.264`, `AAC+,H.264`, `UNKNOWN,H.264` (59 stations), `MP4` (17) and `FLV` (13). Whether
  `OGG` (486) and `UNKNOWN` (1669) stations play is not known.

## Proposal

1. Play a sample of each doubtful label on `office` and record which ones the hub plays.
2. Teach `CodecPolicy` the labels that fail, so the resolver refuses them and the refusal means
   something.
3. Add `playable` to `RadioStation`, and `playableOnly=true` to search (accepting short pages, so
   `nextOffset` stays the only end-of-list signal).

## Open questions

- Is it worth doing before a client asks for it? Until then `hls` and `codec` are in the shape.
