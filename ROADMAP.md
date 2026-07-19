# Roadmap

> Forward-looking **commitments** only — the phased build plan and concrete TODOs being
> tracked now. Companion file [`BACKLOG.md`](BACKLOG.md) holds **wishes** (Ideas +
> brainstorm parking lot) and **history** (shipped milestones). Keeping commitments and
> wishes apart stops commitments from looking negotiable and ideas from looking promised.

The **Inventory** Android client (Kotlin/Compose). Authoritative spec lives in
[`inventory-laravel/docs/specs/`](https://github.com/spdotdev/inventory-laravel/tree/main/docs/specs); this file tracks build work.

Markers: 🟡 TBD · 🔲 TODO · 🛠 in progress · ✅ done (shipped work moves to `BACKLOG.md`).

---

## Phased plan

| Phase | Status | Scope |
|---|---|---|
| 0 — Project skeleton | ✅ shipped 2026-06-23 | Single-activity Compose app + Hilt + Retrofit + Frost theme (palette in `docs/design/`). |
| 1 — Auth | ✅ shipped 2026-06-23 | Email/password + native Google Sign-In; secure token storage; auth interceptor + 401 recovery. |
| 2 — Households | ✅ shipped 2026-06-23 | List / switcher / create / join-by-code / leave; invite (copy link + QR). |
| 3 — Inventory | ✅ shipped 2026-06-23 | Storage overview → shelves (tab strip + swipe) → products; add/remove/move; global search. |
| 4 — Settings + polish | ✅ shipped 2026-06-24 | Theme (System/Light/Dark), household mgmt, account/sign out; empty/error/offline states. |
| 5 — Phase 2 | ✅ shipped 2026-07-10 | **Unlocked 2026-07-10** (user decision): barcode scanning ✅, low-stock tile ✅, filter/sort ✅ (backlog sweep), household color/icon theme ✅, live-updates client ✅ (server Reverb config completed + verified 2026-07-10). |
| 6 — Storage architecture editing UI | ✅ shipped 2026-07-15 (v0.1.9 prerelease) | Nav rework, edit mode, up/down reorder, delete-strategy dialog, tabs⇄list toggle, the client-minted `deletion_batch_id` + snackbar Undo, the per-location Unsorted system shelf. Backend spec: `inventory-laravel/docs/superpowers/specs/2026-07-13-storage-architecture-editing-design.md`. |
| 7 — Household roles UI | ✅ shipped 2026-07-17 | Role badges, promote/demote/remove (Members screen), transfer-ownership, household delete with typed name confirmation; every edit-mode pencil gates on server-computed `can_restructure`/`can_manage_members`. Backend spec: `inventory-laravel/docs/superpowers/specs/2026-07-17-household-roles-design.md`. |
| 8 — GAP audit waves 4-8 + round-2 gap closers | ✅ shipped 2026-07-17 through 2026-07-19 | Iterative parity/stability audits (BACKLOG.md → Done). 2026-07-19: fixed a logcat credential leak, a backup-restore crash-loop, and a repository-cache race (BUG-1..3); closed the recently-deleted-browser and native-export (GAP6-M6) parity gaps — see PARITY below. |

Detailed build order: [`CLAUDE.md`](CLAUDE.md) and [`docs/android-plan.md`](docs/android-plan.md).

---

## Active TODOs

> **App complete (MVP + Phase 2), CI-green**, running against the production backend —
> see [`BACKLOG.md`](BACKLOG.md) → Done for history. Nothing further committed; the
> checked items below are the shipped record.

### SCREENS (next)
- [x] **Households screen** — list / create / join (shipped 2026-06-23, CI-green).
- [x] **Storage overview** — locations list + create (with type), tap-through from households
  (shipped 2026-06-23, CI-green). Shelf/item counts + tap → shelves come with the shelves screen.
- [x] **Shelves** — list + create under a location, tap-through from storage overview
  (shipped 2026-06-23, CI-green). Tab-strip + swipe-pager presentation is a later refinement.
- [x] **Products** — per-shelf list + create + add/remove steppers (shipped 2026-06-23,
  CI-green). `move()` is in the data layer; its shelf-picker UI is a follow-up.
- [x] **Search** — global product search with the location › shelf path (shipped 2026-06-23,
  CI-green); reachable from the storage overview.
- [x] **Invite** — join code + shareable link + QR (shipped 2026-06-23, CI-green).
- [x] **Settings** — in-app theme toggle (System/Light/Dark, persisted + applied app-wide) +
  sign out (shipped 2026-06-23, CI-green). Household management in settings is a later add.

> **All core screens shipped.** Remaining is refactor/polish + dependency-gated work.
  Pattern per screen: repository + Retrofit API + ViewModel + Compose UI, JVM unit-tested via fakes.
- [x] **Navigation-Compose** — typed routes + NavHost replacing the state-based nav; auth
  transitions clear the back stack (shipped 2026-06-23, CI-green).
- [x] **Product move UI** — shelf-picker dialog wired to `ProductRepository.move`
  (shipped 2026-06-23, CI-green).

> **App is functionally complete.** What's left is polish + one dependency-gated item.
- [x] **Shelves-as-tabs + swipe-pager** location detail (Frost D-020) — shipped 2026-06-24, CI-green.
- [x] **Plus Jakarta Sans / Space Mono** fonts — bundled TTFs, applied app-wide; join codes in
  Space Mono (shipped 2026-06-24, CI-green).

### PHASE 2 (unlocked 2026-07-10 — user decision; was deferred 2026-07-04)
- [x] **Barcode scanning** — shipped 2026-07-10. Scan FAB on the location detail screen
  opens a CameraX + ML Kit scanner; a code matching a product on the active shelf
  increments it by one, an unknown code is attached to the next product created on
  that shelf (create now sends `code`). Camera permission degrades gracefully (the
  camera is an accelerator, never a requirement). Result travels back via the nav
  back-stack savedStateHandle; matching logic unit-tested.
- [x] **Low-stock "running low" tile** — shipped 2026-07-10. Dashboard card listing
  products at/below their `low_stock_threshold` (backend field shipped the same day),
  with a numeric threshold field on the product detail screen (empty = off). Missing
  items (mandatory + qty 0) are excluded — one warning per item. Also fixed a latent
  PATCH bug: cleared fields were omitted from the body and silently kept their old
  server value (UpdateProductRequest now always encodes every field).

### PARITY (from the 2026-07-19 GAP-8 audit)
- [x] **Recently-deleted browser** — shipped 2026-07-19. `GET .../households/{household}/deleted`
  (new API endpoint, same `Support\RecentlyDeleted` the web view uses) feeds a new
  screen reachable from the household edit page, with a Restore action per batch.
  Closes the gap where the snackbar Undo was the only way back once it timed out.
- [x] **Native household export (GAP6-M6)** — shipped 2026-07-19. `HouseholdApi.export()`
  streams the JSON directly instead of opening the web export page in a browser; the
  bytes are written to a cache file (server's Content-Disposition filename) and handed
  to a share-sheet chooser via the existing FileProvider.

### QUALITY
- [x] **CI live and green** — wrapper validation + `testDebugUnitTest` + lint pass.
- [x] **Google Sign-In device smoke-test (release gate)** — passed 2026-07-10 on a
  Pixel 7 Pro (Android 16, Play Services) against the deployed backend: Credential
  Manager account picker → consent → Google ID token → `POST /auth/google` → Sanctum
  token → authenticated dashboard with real household data. Re-verify on device after
  any change to the Google OAuth client config or the Credential Manager integration.
- [x] **ktlint/detekt style gating in CI** (wave-2 W19) — shipped 2026-07-10. detekt 1.23.8 +
  ktlint-gradle 12.1.2, baselines generated against the real codebase and committed
  (`app/detekt-baseline.xml`, `app/ktlint-baseline.xml`), gating `ktlintCheck detekt` step in
  `ci.yml`. Fails only on NEW violations; regenerate baselines only after an intentional
  cleanup (`./gradlew detektBaseline ktlintGenerateBaseline`), never to paper over findings.
- [x] **Tag-driven release build** — confirmed 2026-07-10: pushing `v0.1.5` built and
  attached a debug-signed `app-debug.apk` to a GitHub prerelease (the workflow's
  intended preview behavior; the DEBUG_KEYSTORE_* secrets are in place). Still ahead
  of any Play Store upload: a real release signingConfig + AAB output.

### SECURITY
- [x] **CodeQL SAST + dependency review CI** — shipped 2026-07-13. `codeql.yml` runs
  static security analysis over the Kotlin/Java source on push/PR to `main` plus a
  weekly schedule (results in the repo's Security > Code scanning tab);
  `dependency-review.yml` blocks PRs introducing new high-severity-vulnerable
  dependencies. Complements the existing `gitleaks` secret scan and Dependabot.
- [x] **`SECURITY.md`** — shipped 2026-07-13. Documents supported versions, how to
  report a vulnerability privately (GitHub Security Advisory or maintainer email), and
  scope (client vs. `inventory-laravel` backend).
