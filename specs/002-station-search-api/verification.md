# Verification: RADIO-002 (search the catalogue through the hub)

## Upstream facts, checked before planning, 2026-11-15

Acceptance steps 6 and 7, run early with the owner's permission so that planning starts from
facts. They do not need the hub: 24 read-only `GET` requests to one catalogue server
(`https://de1.api.radio-browser.info`), `User-Agent: sonora-radio/0.1.0`, every answer 200 in
0.4–0.8 s. No play was reported and no vote cast. Other mirrors were not asked.

Every request had the form
`curl -sS -m 30 -A "sonora-radio/0.1.0" "https://de1.api.radio-browser.info/json/<path>"`.

| # | Path | Result |
|---|---|---|
| 1 | `stations/search?countrycode=UA&language=ukrainian&languageExact=true&hidebroken=true&limit=100&order=votes&reverse=true` | 100 stations, every one lists `ukrainian` as a whole language; 16 list several (`russian,ukrainian`, `english,ukrainian`, `english,russian,ukrainian`) |
| 2 | `stations/search?tag=jazz&tagExact=true&hidebroken=true&limit=100&order=votes&reverse=true` | 100 stations, every one has the tag `jazz` itself |
| 3 | the same without `tagExact` | 100 stations, 27 of them without the tag `jazz` (only `smooth jazz`, `christmas jazz` and the like) |
| 4 | `countries?hidebroken=true&order=stationcount&reverse=true&limit=1000` | 225 countries, fields `name`, `iso_3166_1`, `stationcount`; every code is two letters; ordered by count (US 6947, DE 5597, FR 2793, RU 2486) |
| 5 | the same without `hidebroken` | 225 countries; the count differs for 16 of them, 51600 stations in all against 51564 |
| 6 | `languages?hidebroken=true&order=stationcount&reverse=true&limit=5000` | 585 languages, fields `name`, `iso_639`, `stationcount`; 447 have no `iso_639` |
| 7 | `tags?hidebroken=true&order=stationcount&reverse=true&limit=100000` | 11145 tags (448 kB), fields `name`, `stationcount`; 5966 are used by one station; the longest name is 103 characters; top: pop 5119, music 3619, news 2685, rock 2611 |
| 8 | `tags/jazz?hidebroken=true&order=stationcount&reverse=true&limit=20` | 20 tags containing "jazz", ordered by count: jazz 1062, smooth jazz 235, vocal jazz 37, … acid jazz 13 |
| 9 | `codecs?hidebroken=true&order=stationcount&reverse=true&limit=1000` | see "Codec labels" below |
| 10 | `stations/search?countrycode=BY&hidebroken=true&limit=1000&order=votes&reverse=true` | 58 stations; the country list (4) says 58 |
| 11–17 | `stations/search?countrycode=UA&hidebroken=true&order=votes&reverse=true` with `limit=500` (twice) and `limit=100&offset=0,100,200,300,400` | 284 stations both times, in the same order; pages hold 100, 100, 84, 0, 0 and joined give exactly the same sequence: nothing repeated, nothing missing |
| 18–24 | the same with `order=clickcount` | the same: 284, identical order twice, pages join to the same sequence |

### What this settles

- **Exact matching** needs `tagExact=true` (3 against 2), and `languageExact=true` still returns
  stations with several languages (1). The spec's rule holds.
- **Station fields**: `tags` and `language` are comma-separated text with no spaces around the
  commas; `bitrate` is a whole number and 0 for 4 of 100 stations; `hls` and `lastcheckok` are the
  numbers 0 and 1, not booleans; `stationuuid` is lower case. One station carried 58 tags, so the
  bound on tags per station is needed.
- **Lists**: accept `hidebroken`, `order=stationcount`, `reverse` and `limit`. Hiding broken
  stations changes little (36 stations in all), because the catalogue holds few broken ones. The
  count for a small country equalled its search result (10).
- **Sizes**: 225 countries and 585 languages can be returned whole; 11145 tags cannot, so the tag
  list stays paged and filtered by `name`.
- **Ties**: among 284 stations there are 178 different vote counts (153 stations share one with
  another; the largest group is 12) but only 40 different click counts (267 share one; the largest
  group is 50). So `votes` does tie less. In this sample the order among equal values did not
  change between requests or between page sizes on one server. Not checked: another mirror, a
  larger result, a change in the catalogue between pages. Dropping repeats by `uuid` stays as a
  cheap guard.

### Codec labels (acceptance step 7)

Stations not marked broken, per label:

| Label | Stations | The hub today |
|---|---|---|
| `MP3` | 35923 | mapped, plays |
| `AAC+` | 7402 | mapped, plays |
| `AAC` | 7142 | mapped, plays |
| `UNKNOWN` | 1669 | not mapped, let through |
| `OGG` | 486 | not mapped, let through (the container; Vorbis or Opus inside) |
| `AAC,H.264` | 57 | not mapped, let through; a video stream |
| `MP4` | 17 | not mapped, let through |
| `FLV` | 13 | not mapped, let through |
| `FLAC` | 6 | mapped, plays |
| `UNKNOWN,H.264` | 1 | not mapped, let through; a video stream |
| `AAC+,H.264` | 1 | not mapped, let through; a video stream |

There is no `WMA` label and no `OPUS` label, so `CodecPolicy`'s `OPUS` mapping never matches a
catalogue station. Candidates for "the hub cannot play": the three labels with `H.264` (59
stations), `MP4` and `FLV` (30). Whether `OGG` and `UNKNOWN` stations play was not tested (it
needs audible plays).

## Still to do

Acceptance steps 1–5 against the hub (`multiroom.lan`), after the feature is built and deployed.
