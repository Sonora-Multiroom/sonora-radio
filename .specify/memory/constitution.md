<!--
Sync Impact Report:
- Version: (unfilled template) → 1.0.0 (initial constitution for this repository)
- Derived from: sonora-tts constitution 1.0.3
  (D:\projects-sonora\sonora-tts\.specify\memory\constitution.md), same structure
- Principles carried over, adapted to radio: I (Clean Code), II (Java 17 & Spring Boot supplied by
  the host), III (Test-First), IV (SOLID), V (Code Quality & Reviews, two-part merge gate),
  VI (Git Workflow), VII (Cross-Platform), VIII (Extension Boundary)
  * TTS specifics removed: providers, API keys, Piper / local engines, audio cache
  * Radio equivalents: the radio-browser client and mirror failover behind one interface (IV),
    platform-neutral DNS discovery (VII), the resolver may return only http/https (VIII),
    configuration faults are malformed server lists, timeouts and limits rather than missing keys
- Added principles: IX (Good citizen of a free public service), X (The catalogue is untrusted
  input), XI (The hub keeps working when the catalogue does not), XII (Tests never touch the real
  service, and never make sound uninvited)
- Added sections: Upstream Contract (sonora-multiroom), Development Workflow, Governance
- Removed sections: none (template placeholders only)
- Templates requiring updates: none (plan/spec/tasks templates read the constitution at runtime)
- Deferred TODOs: none
-->

# Sonora Radio Constitution

This repository builds **one Multiroom Audio Hub extension**: a drop-in JAR
(`ai.multiroom:multiroom-radio`, package `multiroom.radio`, extension id `radio`) that lets a
running `multiroom-core` play internet radio stations from the
[radio-browser.info](https://www.radio-browser.info/) catalogue by station UUID (a
`radiobrowser://<uuid>` input resolver), and lets clients search that catalogue under
`/api/radio/**`. The host, the API it compiles against and the parent POM live in a separate
repository (`sonora-multiroom`); the catalogue is a free service run by volunteers. Everything below
follows from those two facts.

## Core Principles

### I. Clean Code Practices

- **Meaningful Names**: Variables, methods and classes MUST have clear, self-explanatory names
  that reveal intent
- **Single Responsibility**: Each class and method MUST do one thing and do it well
- **Small Functions**: Methods MUST be small (ideally <20 lines) and operate at one level of
  abstraction
- **DRY**: Duplication MUST be eliminated through abstraction
- **Comments When Necessary**: Code should be self-documenting; comments MUST explain "why", not
  "what"
- **Error Handling**: No empty catch blocks; specific exceptions over generic ones

**Rationale**: Clean code reduces technical debt and makes onboarding faster. Poor quality
compounds and becomes exponentially harder to maintain.

### II. Java 17 & Spring Boot Standard

- **Java Version**: Java 17 LTS, matching the host application
- **Spring Boot**: 3.x, **supplied by the host at runtime and never bundled**: every Spring
  dependency in this repository is `provided` scope (see VIII)
- **Modern Java Features**: Records, sealed classes, pattern matching and text blocks are
  encouraged
- **Dependency Injection**: Constructor injection through the host's single application context;
  no manual object graphs and no second context
- **Spring Best Practices**: Contribute through `@AutoConfiguration`, `@ConfigurationProperties`
  and conditional beans, the way a starter does
- **HTTP**: Use an HTTP client the host or the JDK already supplies (`java.net.http.HttpClient`,
  Spring's `RestClient`); never bundle a second HTTP stack

**Rationale**: The extension runs inside someone else's JVM and someone else's Spring context.
Matching the host's stack exactly is what makes that possible.

### III. Test-First Development (TDD)

- **Red-Green-Refactor**: Write a failing test, implement the minimum to pass, then refactor
- **Tests Before Code**: No production code without a failing test first
- **Coverage**: Minimum 80%; the resolver, mirror discovery and failover, timeout handling and the
  mapping from radio-browser JSON to the extension's DTOs require more
- **Frameworks**: JUnit 5, Mockito, Spring Boot Test; `@WebMvcTest` slices for controllers;
  WireMock for radio-browser HTTP
- **No Live Service In Tests**: A test MUST NOT call radio-browser.info or resolve its DNS names
  (see XII)
- **Fast Tests**: Unit tests run in milliseconds; a slow test signals a design problem. Timeout
  tests use short configured timeouts, never real waits of seconds

**Rationale**: TDD is the only cheap correctness signal available here, because this repository
cannot run the host's integration suite and must not lean on the real catalogue.

### IV. SOLID Principles

Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation and Dependency
Inversion apply as upstream. In particular:

- **The catalogue sits behind one interface** (the radio-browser client): the resolver and the
  REST controllers depend on it, never on HTTP details, so both are testable without a server
- **Server discovery is its own component** behind an interface (DNS, `/json/servers`, a pinned
  list from configuration), so failover can be tested and the discovery source swapped without
  touching callers
- **Metrics sit behind a `RadioMetrics` interface**, as in `sonora-tts`, so a missing meter
  registry never changes behaviour

### V. Code Quality & Reviews

Quality gates MUST be enforced at every stage:

- **Code Reviews**: All code MUST be reviewed before merge
- **Style**: New and changed code MUST match the surrounding style (4-space indentation, the
  existing import ordering and naming conventions), verified in review. No automated formatter is
  mandated
- **Merge Gate (two parts, both required)**:
  1. `mvn verify` in this repository MUST be green
  2. A **smoke run against a live `multiroom-core`** MUST pass: deploy the built JAR to the host's
     `extensions/` directory, start the application, and confirm the extension appears in
     `GET /actuator/extensions` with the expected version and **is not `REJECTED`**. When the
     live core is production (`multiroom.lan`), the deploy and the restart each need the owner's
     permission (see the Upstream Contract)
- **Why both**: this repository has no reactor and no integration suite that boots the host. A
  green local build proves only that the module compiles against the API it was handed; it cannot
  prove the JAR loads. Until a real core has loaded it, it is not verified
- **Static Analysis (recommended)**: SpotBugs SHOULD be run on changed code, with the upstream
  noise filter (`EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `CT_CONSTRUCTOR_THROW`,
  `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`). Not a gate, but findings on changed code MUST be
  fixed or justified
- **Documentation**: Public types MUST have Javadoc; architectural decisions MUST be documented

### VI. Git Workflow & Version Control

- **Feature Branches**: `[###-feature-name]`, matching the directory under `specs/`; features are
  referred to in prose, commits and code comments as `RADIO-NNN`
- **Commit Messages**: Conventional Commits (`type(scope): description`), explaining the "why"
- **Atomic Commits**: One logical change per commit
- **No Direct Commits** to `main` once the repository has its first commit
- **Pull Request Required** for all changes
- **Tag Releases** with the semantic version of the extension
- **Version Independence**: this extension carries its own version, independent of the host's. The
  API version it requires is pinned separately (see the Upstream Contract below)

### VII. Cross-Platform Compatibility

The extension MUST run wherever the host runs: Windows, Linux and Raspberry Pi OS (ARM64).

- **Platform Agnostic Code**: No platform-specific branches
- **DNS Discovery In The JDK**: Mirror discovery MUST use the JDK's resolver (or an injected
  abstraction over it), never a shelled-out `dig`/`nslookup`. Reverse lookups behave differently
  on Windows and on the Pi's resolver; when one does not yield a host name, fall back to
  `/json/servers` rather than calling HTTPS by bare IP (the certificate names the host, not the IP)
- **Path Handling**: `java.nio.file.Path` everywhere; never a hardcoded separator. Any file the
  extension keeps (for example a later favourites store) is located by configuration, not a
  constant
- **Line Endings**: `.gitattributes` required
- **Case Sensitivity**: Assume case-sensitive filesystems; station UUIDs are compared in their
  canonical lower-case form
- **Dependencies**: Cross-platform and ARM64-safe; no native libraries without an abstraction
- **Testing**: The suite MUST pass on Windows (the development platform). **Any change that
  affects playback MUST additionally be verified on the production Raspberry Pi**, which is the
  only end-to-end channel this repository has; there is no in-repo test that boots the host

### VIII. Extension Boundary (the rules the host enforces)

These are contracts of the host's extension loader: the terms on which this JAR is admitted.
**Nothing in this repository re-checks them beyond the inherited build gates**:

- **Depend on `multiroom-api` only** among multiroom artifacts, at `provided` scope. A dependency
  on `multiroom-core` is an architectural violation, and the inherited `bannedDependencies`
  enforcer rule fails the build. Other libraries the host supplies on the shared classpath
  (Spring, Jackson, SLF4J, Micrometer) are `provided` too, and never bundled
- **Bundle nothing framework-shaped**: Spring, Jackson, SLF4J/Logback and the API itself are
  supplied by the host. Shading them risks a class-name collision with another independently built
  extension
- **Ship no root `application.yml` or `application.properties`**: extension JARs share the host's
  classpath, so a root config file shadows the host's own. All defaults belong in
  `@ConfigurationProperties` field initialisers. The inherited build gate fails on this
- **The entry point** is `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  naming `multiroom.radio.RadioAutoConfiguration`; the parent POM is declared with an empty
  `<relativePath/>` so it resolves from `~/.m2`
- **`radio` everywhere**: the `extension.id` POM property, `@ConditionalOnProperty` and the
  configuration prefix `multiroom.radio` are the same string
- **No network work during bean construction**: the radio-browser client is constructed, not
  contacted; mirror discovery happens lazily on the first request. radio-browser being unreachable
  at boot is a runtime state, not an initialisation failure, and MUST NOT abort host start-up
- **Configuration faults DO abort start-up**: a pinned server list with a malformed URL, a
  non-positive timeout, a result-limit cap outside its allowed range, an unparseable
  `multiroom.radio` block. These are operator-fixable, so fail fast with a message naming the
  extension, rather than failing the first play hours later
- **The resolver returns only what core already plays**: an `http` or `https` URI. It only
  rewrites a URI; core needs an `InputHandler` for the result, and an extension cannot add one
- **Scope the `@RestControllerAdvice`** to this module's API interfaces (`assignableTypes`), or it
  takes over other extensions' error responses. Errors are RFC 7807 problem details, like the
  hub's REST v2
- **Start-up cost stays negligible**: the host budgets seconds for its entire start-up with every
  extension loaded. A measurable jump caused by this module means something is being done eagerly
  that should not be
- **Switchable off**: `multiroom.radio.enabled=false` MUST leave the host starting normally with
  no beans contributed

**Rationale**: every one of these rules exists because the extension shares a classloader, a
context and a process with code it does not own. Breaking one does not fail this repository's
build; it fails someone's audio system at start-up.

### IX. Good Citizen of a Free Public Service

radio-browser.info is a free API run by volunteers. This extension MUST cost it as little as
possible and MUST NOT expose the household that uses it.

- **Descriptive User-Agent**: every request MUST send `User-Agent: sonora-radio/<version>`, with
  the version taken from the build. It MUST NOT contain anything that identifies the owner, the
  home or its host names (no names, e-mail addresses, `*.lan` names or IP addresses)
- **No fixed server**: discover mirrors (DNS `all.api.radio-browser.info`, then `/json/servers` as
  the fallback), shuffle them, and fail over to the next on failure. No mirror host name is
  hard-coded in code; the discovery name is a configuration default, and configuration MAY pin an
  explicit server list (for outages and tests). HTTPS is preferred
- **Explicit, capped `limit`**: every list or search request MUST send an explicit `limit` (the
  service defaults to 100000), capped by the extension whatever the client asks for. Nothing pages
  through the whole catalogue
- **Cache, never crawl**: lists (countries, tags, languages) and station lookups are cached; no
  polling, no background crawling, no prefetching, no scheduled refresh. A request reaches
  radio-browser only because a client or a play asked for it
- **Clicks only for real plays**: `/json/url/{stationuuid}` counts a click, which the service asks
  clients to report for every play. It MUST be called only on the play path (resolving a
  `radiobrowser://` input), never for search, browse or a station lookup. Votes are never cast
  automatically

**Rationale**: the catalogue is useful only while its volunteers can afford to run it. A hub that
crawls, polls or misreports popularity is a bad neighbour, and a User-Agent with the owner's details
leaks a private household to a third party.

### X. The Catalogue Is Untrusted Input

- **Community-edited text**: station names, tags, homepages and favicons are edited by anyone.
  Their length MUST be bounded (truncated to fixed limits) and control characters stripped before
  they are returned or logged. They MUST NOT appear in metric tags, file names or log keys, and
  MUST NOT be logged unbounded; log the station UUID instead
- **UUIDs are validated**: a station UUID from a client is parsed as a UUID before it is used in
  any request to radio-browser; anything else is rejected without a network call
- **Stream URLs are only handed to the hub**: a stream URL from the catalogue MUST be `http` or
  `https` and is passed to the hub to play, nothing else. Any other scheme (`file`, `jar`, a
  device scheme) is refused with an `InputResolutionException`
- **No fetching on the catalogue's behalf**: the extension never fetches favicons or homepages;
  it returns them as given and the client decides whether to load them
- **Own DTOs only**: the REST API returns the extension's own DTOs, never radio-browser's raw JSON,
  and the mapping tolerates unknown, missing and malformed fields, so an upstream change cannot
  break hub clients

**Rationale**: anything the catalogue returns could have been written by anyone. Bounding it keeps
logs, metrics and clients safe, and refusing non-HTTP URLs stops a catalogue entry from turning the
hub into a reader of its own files or devices.

### XI. The Hub Keeps Working When the Catalogue Does Not

- **Isolation**: radio-browser being slow or down MUST NOT affect hub start-up or anything outside
  this extension. No network during bean construction (VIII)
- **Bounded calls**: every call has short connect and read timeouts from configuration; failover
  tries a bounded number of servers within a bounded total time; there are no unbounded retries
- **Clear errors, never hangs**: when the catalogue cannot answer, search and browse answer 503 as
  a problem detail, and a play fails with an `InputResolutionException` naming the station UUID
- **The playback path**: a resolver call runs on the hub's playback path, so it MUST be bounded in
  time and MUST NOT block other routes (no shared lock held across a network call, no
  single-threaded queue that one slow station can stall)

**Rationale**: the hub is the owner's real home audio system. A third-party outage may cost the
radio features, never the speakers, the other inputs or start-up.

### XII. Tests Never Touch the Real Service, and Never Make Sound Uninvited

- **`mvn verify` is offline from radio-browser**: unit and integration tests use WireMock with
  recorded responses committed under `src/test/resources`, and an injected discovery source in
  place of real DNS. They cover mirror failover, timeouts and malformed data
- **Live contact is a one-time acceptance check**: radio-browser.info is contacted only while a
  feature is verified against the real hub. The feature's spec (or the backlog item it came from)
  names the live checks as acceptance criteria; they run once, by hand, against `multiroom.lan`,
  and their commands, date and result are recorded in the feature's `verification.md`. They are
  never automated, never scheduled and never part of the build. Refreshing a recorded fixture from
  the live service is live contact too and follows the same rule
- **Audible checks follow the parent `AGENTS.md`**: ask first; record and restore state (volume,
  mute, enabled, master mute); keep volumes low; use the `office` output; stop the test route
  first, then restore; use a known-good station; afterwards confirm no ALSA playback device is open
  and `audio.output.routes` for `office` reads 0

**Rationale**: a build that depends on a volunteer service is flaky and unkind to it, and a test
that plays aloud uninvited intrudes on a real home.

## Upstream Contract

- The host repository is `sonora-multiroom`, available locally at
  `D:\projects-sonora\sonora-multiroom`. It is **read-only from here**: an API change is made
  there, on its own branch, verified there with its full-reactor `mvn verify` and released there.
  A hub-side need (for example accepting `radiobrowser://` in `multiroom.yml`) is proposed in the
  `sonora-multiroom` backlog, never worked around here, and hub classes are never copied
- `ai.multiroom:multiroom-api` and `ai.multiroom:multiroom-extension-starter` are resolved from the
  **local Maven repository** (`~/.m2`). Nothing is published to a remote repository. A missing
  artifact means "install it upstream", never "add a `<repository>`"
- The required API version is **pinned explicitly** in `pom.xml`
  (`multiroom.require_api_version`), because the inherited default would otherwise track this
  extension's own version. That property is what the JAR manifest advertises as
  `Require-API-Version`, and the host's compatibility check reads it. It is raised only when the
  extension uses the newer API
- After any upstream API change: rebuild and redeploy this extension. Incompatibility surfaces only
  at runtime, and a stale JAR beside a rebuilt host is a known, silent failure mode
- `multiroom.lan` is **production**, the owner's real home audio system. Read-only SSH inspection
  (the deployed `multiroom.yml`, logs, `systemctl status`) is pre-authorised; deploys, restarts,
  service control and anything audible need permission each time. A bare `mvn deploy` uploads to
  production once the `remote` profile is inherited: use `-Plocal` while developing

## Development Workflow

1. Work from a spec under `specs/[###-feature-name]/` on a matching branch; the spec carries over
   the live acceptance checks from its backlog item (XII)
2. Write the failing test first (III), against WireMock fixtures, never the live service
3. Implement, keeping the extension boundary (VIII) and the radio-browser rules (IX–XI) in view;
   they are cheapest to satisfy before the code exists
4. `mvn verify` locally
5. Deploy the JAR to a live core and confirm the inventory reports it correctly (V)
6. For anything touching playback: verify on the production Raspberry Pi (VII), with permission
7. Run the live acceptance checks once, by hand, and record them in `verification.md` (XII)
8. Review, then merge

## Governance

- This constitution supersedes ad-hoc practice in this repository
- Amendments require a documented rationale in the Sync Impact Report and a version bump: MAJOR
  for removing or redefining a MUST, MINOR for a new principle or materially expanded guidance,
  PATCH for clarifications
- Every plan's Constitution Check and every review MUST confirm compliance with these principles;
  a deviation is justified in the plan's Complexity Tracking or it is not merged
- Where this constitution is silent, the upstream `sonora-multiroom` constitution applies, and
  `sonora-tts` is the model for how an out-of-tree extension applies it
- Where the two conflict on anything inside this repository, **this one wins**: it was written
  knowing there is no reactor here and that the catalogue belongs to someone else

**Version**: 1.0.0 | **Ratified**: 2026-11-13 | **Last Amended**: 2026-11-13
