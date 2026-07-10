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
| 0 — Project skeleton | 🔲 TODO | Single-activity Compose app + Hilt + Retrofit + Frost theme (palette in `docs/design/`). |
| 1 — Auth | 🔲 TODO | Email/password + native Google Sign-In; secure token storage; auth interceptor + 401 recovery. |
| 2 — Households | 🔲 TODO | List / switcher / create / join-by-code / leave; invite (copy link + QR). |
| 3 — Inventory | 🔲 TODO | Storage overview → shelves (tab strip + swipe) → products; add/remove/move; global search. |
| 4 — Settings + polish | 🔲 TODO | Theme (System/Light/Dark), household mgmt, account/sign out; empty/error/offline states. |
| 5 — Phase 2 | ✅ shipped 2026-07-10 | **Unlocked 2026-07-10** (user decision): barcode scanning ✅, low-stock tile ✅. Filter/sort stays 🟡 TBD. |

Detailed build order: [`CLAUDE.md`](CLAUDE.md) and [`docs/android-plan.md`](docs/android-plan.md).

---

## Active TODOs

> Skeleton + Frost theme + DI/networking + email/password auth all shipped and CI-green
> 2026-06-23 — see [`BACKLOG.md`](BACKLOG.md) → Done. Next: the inventory screens.

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
- [ ] **Tag-driven release build** — confirm `release.yml` builds APK + AAB on `v*` tags;
  add the signing keystore as a repo secret before any Play Store upload.
