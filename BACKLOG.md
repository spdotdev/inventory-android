# Backlog

> Wishes and history. Companion to [`ROADMAP.md`](ROADMAP.md) (forward-looking
> commitments). This file holds **Ideas** (detailed proposals + a brainstorm parking lot)
> and **Done** (shipped milestones). Phases are commitments, ideas are wishes — keep them apart.

Markers: 💡 IDEA · ✅ DONE.

---

## Ideas — detailed proposals

### 💡 Barcode scanning to add products

**What.** Scan a product barcode (ML Kit / CameraX) to add or increment a product on the
active shelf, instead of typing the name.

**Why.** Fastest possible "add stock" flow at the freezer/shelf; the highest-friction
action in the app is naming products. Pairs with the Phase 2 `barcode` product attribute.

**Where it touches.** New camera permission + scanner screen; product add flow; depends on
the backend gaining a `barcode` attribute (Phase 2, see `inventory-docs`).

**Risks.** Camera permission friction; offline barcode→name lookup needs a data source
(out of scope while always-online). Effort ~3–4 days. **Kill criterion:** if manual add is
fast enough in real use, don't build it.

> **Deferred 2026-07-04** (backlog-sweep decision). Kept as a Phase-2 idea: manual code entry
> already works via the product edit form (`code` field), and the CameraX + ML Kit deps +
> camera permission are a large add for marginal MVP value. Revisit if shelf-side add speed
> becomes a real pain point.

---

## Ideas — parking lot
- 💡 Widget / quick-tile for "what's low" (depends on a low-stock concept — not in MVP).
  *Deferred 2026-07-04 (backlog-sweep decision):* a real OS home-screen widget fights the
  always-online + bearer-token model (no background auth), and a low-stock **threshold** concept
  isn't agreed yet. Revisit as an **in-app** "running low" tile once a threshold is defined
  (the `is_mandatory` + qty-0 "missing items" view already covers the hard-stop case).
- 💡 Q-3: live updates (WebSockets) if pull-to-refresh proves insufficient.
- 💡 User-chosen per-household color/icon (needs a `theme` field on the household resource —
  Phase-2 backend change; the deterministic id-derived version already ships, see Done).

---

## Done
- ✅ `2026-07-04` — **Corrected stale "planning only" status docs** (gap analysis T18). `CLAUDE.md`'s
  `## Status` still read "Planning only — no Android Studio / Gradle project scaffolded yet" and the
  `README` said "project skeleton" — both the opposite of the shipped MVP. Rewrote them to describe the
  built app (auth, storage/shelves/products, search, invite, settings, dashboard, missing-items; EN+NL;
  unit + nightly instrumented tests) and to point at ROADMAP/BACKLOG.
- ✅ `2026-07-04` — **Release + fork-PR pipeline hardening** (gap analysis T17). Two problems. (1) `ci.yml`
  unconditionally decoded `DEBUG_KEYSTORE_B64` and ran `assembleDebug`, so a fork PR (no secrets) red-failed
  on signing infra it can never have — even though its code was fine. Reordered so unit-tests + lint (the
  real gates, keystore-free) run first, then gated the keystore-restore / APK-build / upload steps behind a
  `Check for signing secret` step; a fork PR now goes green. (2) `release.yml` published a debug-signed
  `app-debug.apk` as a normal GitHub Release, which reads as production. It now fails loudly if the signing
  secret is missing (a real release needs it) and marks the output a **prerelease** with a "⚠️ Preview build
  — debug-signed, not Play-ready" note; real release signing + AAB stays tracked in ROADMAP. CI-uploaded APK
  renamed `app-debug-preview`. Both YAMLs validated; CI verifies the runs (Gradle unrunnable locally).
- ✅ `2026-07-04` — **Instrumented-test CI job** (gap analysis T16). 27 `androidTest` E2E flow tests
  (real navigation + Compose UI over MockWebServer) existed but CI only ran `testDebugUnitTest`, so
  navigation/UI regressions went uncaught. Added `.github/workflows/instrumented.yml`: a KVM-accelerated
  emulator job (API 34 `google_apis` x86_64 via `reactivecircus/android-emulator-runner`, with AVD
  caching for speed) running `connectedDebugAndroidTest` on a **nightly** cron + `workflow_dispatch` —
  deliberately off per-PR CI since emulator boot costs several minutes. Restores the debug keystore from
  the secret, guards on the canonical repo so secret-less forks skip, and always uploads the connected
  test report. Emulator run is CI-only (unrunnable in this env); YAML validated. The nightly may surface
  historically-flaky flows (e.g. SearchFlowTest nav) to triage — that's the intended catch.
- ✅ `2026-07-04` — **Config-change / process-death polish** (gap analysis T13). Two fixes. (1) Dialog &
  sheet visibility flags were bare `remember{}`, so an open add-sheet / delete-confirm / sign-out dialog
  vanished on rotation — converted to `rememberSaveable` (StorageOverview, LocationDetail ×2, Households,
  ProductDetail, Settings); left the transient clipboard-`copied` toast and the `SortMenu` dropdown as
  bare `remember` (persisting those would be wrong). (2) The `MainActivity` auth redirect ran its
  stack-clearing `popUpTo(graph){inclusive}` on any `authenticated == true` composition, so on
  process-death restore it bounced the user from wherever they were back to the dashboard. It now fires
  only on a real login/logout **transition**, decided by a pure `authRedirectFor(previous, current)` +
  a `rememberSaveable` last-auth marker: on restore `previous == current` → no-op, so the NavController's
  restored back stack survives. `AuthRedirectTest` covers cold-start / login / logout / restore / no-change.
  Local Gradle unrunnable here; CI verifies compile + tests.
- ✅ `2026-07-04` — **Completed the Dutch translation** (gap analysis T12). `values-nl` was missing 3
  keys (`dashboard_favorite_shelves`, `drawer_no_households_hint`, `products_pane_swipe_hint`), so those
  strings fell back to English mid-UI on a Dutch device. Added all three (Favoriete planken / "Nog geen
  huishoudens…" / "← Veeg een product naar links om te verwijderen"). Verified the locales are now in
  exact lockstep — 140/140 keys, no locale-only keys, and matching `%`-format-arg counts on every key.
  Android lint's `MissingTranslation` remains the standing guard against future drift. Local Gradle
  unrunnable here; CI verifies.
- ✅ `2026-07-04` — **Localized storage-type labels** (gap analysis T11). `location.type` labels and the
  add-sheet type chips rendered the raw server enum ("freezer"…), English-only even on a Dutch device.
  Added `storage_type_{freezer,fridge,pantry,other}` to `values/` and `values-nl/` (Vriezer/Koelkast/
  Voorraadkast/Overig) and a reusable `storageTypeLabel(type)` composable — backed by a pure,
  JVM-testable `storageTypeLabelRes` — that maps the transport enum to a localized label, with a
  capitalized-raw fallback so an unknown/future server value never renders blank. Applied at all three
  render sites; the raw enum still travels on the wire (server-authoritative). Unit test asserts the
  mapping and that it stays in lockstep with `STORAGE_TYPES`. Local Gradle unrunnable here; CI verifies.
- ✅ `2026-07-04` — **Accessible error announcements + one-shot Snackbar** (gap analysis T10). Two fixes.
  a11y: error messages were silent to TalkBack — the shared `ErrorRetry` (used across ~9 read screens)
  and the two auth screens' inline error `Text` now carry `semantics { liveRegion = Assertive }`, so a
  failure is announced the moment it appears. One-shot action errors: added a reusable `SnackbarErrorEffect`
  (+`SnackbarHost`) in `ui/common` and wired it into `ProductDetail` — the canonical action-error surface
  (save/upload/delete have no sensible inline retry); `ProductDetailViewModel.consumeError()` clears the
  error after it's shown so it can't re-announce on recomposition. Design split (recorded in GAP-ANALYSIS):
  load-failure screens keep the inline `ErrorRetry` (a retry affordance a transient snackbar can't give,
  now announced), pure-action results use the snackbar; Material3 Snackbar is itself a live region.
  Unit test covers `consumeError()`. Local Gradle unrunnable here; CI verifies compile + tests.
- ✅ `2026-07-04` — **Pull-to-refresh spinner no longer fires on mutations** (gap analysis T9). Every
  `PullToRefreshBox` bound `isRefreshing = state.loading`, but `loading` is also set by create/delete/
  save/upload/+/- — so any mutation spun the pull indicator. Split a dedicated `refreshing` flag from
  the generic `loading` across all seven refreshable screens: the shared `HierarchyStore.refresh` gained
  a `userInitiated` param (pull/refresh-button pass true → `refreshing`; post-mutation reloads pass false
  → silent), mapped through Drawer/Missing/Dashboard VMs; the `launchLoading` VMs (StorageOverview/
  Shelves/Households) gained a `refreshing` param that only `refresh()` sets true; ProductDetail's
  `load()` (its refresh target) sets it, its save/upload/delete don't. Screens now bind
  `isRefreshing = state.refreshing`; `loading` still drives the inline progress + empty-state gating.
  Added a gated-repository unit test asserting create() sets `loading` but not `refreshing`, while
  refresh() sets both. Self-reviewed (local Gradle unrunnable here); CI verifies compile + tests.
- ✅ `2026-07-04` — **Friendly network-error mapping + Retry** (gap analysis T5). VMs surfaced raw
  `error.message` (e.g. "Unable to resolve host…") as sticky red text. New shared
  `data/error/toUserMessage(fallback)` maps any `IOException` → "Can't reach the server…" and known
  HTTP codes (401/403/404/422/429/5xx) → friendly copy, else the caller's contextual fallback. Applied
  across all 9 error-surfacing VMs (Products, ProductDetail, StorageOverview, Shelves, Search,
  Households, Join, Invite, HierarchyStore) and added an `IOException` branch to the two bespoke
  `AuthViewModel` mappers. New reusable `ui/common/ErrorRetry` (message + Retry) wired into the
  StorageOverview error state; other list screens already have pull-to-refresh as the retry path.
  `ErrorMappingTest` (4) covers network/HTTP/unknown/generic. Strings `action_retry` (EN+NL). Local
  Gradle unrunnable here — CI verifies compile/tests.
- ✅ `2026-07-04` — **Reactive session/401 handling** (gap analysis T4). `AuthInterceptor` cleared the
  token on a mid-session 401, but `AuthViewModel.authenticated` was read once at init and nothing
  observed the token — so an expired session left the user on authed screens, every call silently
  401ing until a cold restart. `TokenStore` now exposes `authState: StateFlow<Boolean>` (emits on
  set/clear); `AuthRepository.sessionActive` surfaces it; `AuthViewModel` collects it in `init` and
  flips `authenticated=false` on loss, so `MainActivity` redirects to login immediately.
  `AuthViewModelTest` gains a mid-session-token-loss case (fake exposes a controllable `session` flow).
  Local Gradle build isn't runnable here — relying on CI for compile/test.
- ✅ `2026-07-04` — **Per-household color + icon theming on top of Frost.** Each household gets a
  stable, distinguishable identity in the UI, derived deterministically from its id — purely
  visual and client-side, nothing persisted or sent to the API (keeps the server-authoritative
  rule intact). `ui/theme/HouseholdTheme.kt`: `householdTheme(id)` → an ice-toned accent (8-hue
  palette that harmonises with Frost light/dark) + a place/storage icon, plus a reusable
  `HouseholdAvatar` composable (round accent-tinted badge, icon tinted `onSurface` so it stays
  legible on either theme). The accent and icon use different strides so they don't move in
  lockstep for small sequential ids. Index math split into a Compose-free `HouseholdThemeIndex.kt`
  so it's unit-testable on a plain JVM: `HouseholdThemeIndexTest` (bounds, determinism,
  decorrelation). Wired into the households list cards and the drawer's per-household headers.
  User-chosen colors/icons deferred (needs a household `theme` field — see parking lot). Local
  Gradle build isn't runnable here (pre-existing) — relying on CI for compile/test-green.
- ✅ `2026-07-04` — **Filter + sort for products and search results (Phase 2).** Shared,
  server-agnostic view controls (transient — nothing persisted; never sent to the API).
  New `ui/common/SortOrder.kt` (enum NAME_ASC/NAME_DESC/QUANTITY_DESC/QUANTITY_ASC + a generic
  `List<T>.sortedByOrder(order, name, quantity)` helper — case-insensitive name compare, quantity
  ties broken by name) and `ui/common/SortMenu.kt` (reusable "Sort: <current>" dropdown with a
  check on the active option). Products: `ui/products/ProductView.kt` `List<ProductDto>.applyView`
  (query over name/code + mandatory-only + out-of-stock-only flags, then sort); `ProductsViewModel`
  gains `filterQuery`/`mandatoryOnly`/`outOfStockOnly`/`sort` state with a computed `visibleProducts`
  + `filteredToEmpty`, and `onFilterQueryChange`/`toggleMandatoryOnly`/`toggleOutOfStockOnly`/`setSort`;
  `ProductsPane` renders a filter field + two FilterChips + the SortMenu (only when the shelf is
  non-empty) and a "no match" message. Search: `SearchViewModel` gains `sort` + computed
  `sortedResults` + `setSort`; `SearchScreen` shows the SortMenu above results (the server query is
  the filter, sort is client-side). Tests: `ProductViewTest` (6 — case-insensitive name sort, desc,
  quantity tie-break, name/code query, mandatory/out-of-stock flags incl. combined, generic helper)
  + a `SearchViewModelTest` sort-without-refetch case. Strings added to EN + NL. Local Gradle build
  isn't runnable in this environment (pre-existing) — relying on CI to confirm compile/test-green.
- ✅ `2026-07-04` — **Google Sign-In `GOOGLE_CLIENT_ID` wired (verification).** Confirmed the
  Web OAuth 2.0 client ID is set in `app/build.gradle.kts` (`buildConfigField` →
  `BuildConfig.GOOGLE_CLIENT_ID = 758637503304-…apps.googleusercontent.com`) and consumed by
  `AuthScreen.launchGoogleSignIn()` via `GoogleSignInOptions.Builder(...).requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)`,
  whose ID token feeds `AuthViewModel.loginWithGoogle` → backend verifier. No code change needed —
  the RMMBR "set GOOGLE_CLIENT_ID" task was stale. (If the tenant's Web client ID ever rotates,
  update the single `buildConfigField` line.)
- ✅ `2026-07-04` — **Q-3 (realtime) resolved → pull-to-refresh** (see `inventory-docs/ROADMAP.md`).
  WebSockets/Reverb deferred to Phase 2; the live-updates idea stays in this file's parking lot as
  the re-open trigger.
- ✅ `2026-07-04` — **Frosted-glass card treatment (Frost D-021, ralph-loop r17).** New
  `FrostCard` composable (`ui/theme/FrostCard.kt`): translucent tinted container + hairline
  border + 22dp corners, matching `docs/design/frost-app.html` `.card`/`.rcard`/`.fcard`
  (dark: primary-tinted wash; light: near-white 72%-opacity wash). Colors provided theme-wide
  via `LocalFrostCardColors`, set in `InventoryTheme`. Swapped the plain M3 `Card` for
  `FrostCard` on every list/info card across Storage overview, Missing items, Products,
  Search, Dashboard (stat cards, bar chart, favorites), Households, and All-storages;
  semantically-colored warning cards (`errorContainer` tint for out-of-stock/mandatory items)
  intentionally kept on plain `Card` so they still stand out from the Frost tint. Local Gradle
  build isn't runnable in this environment (pre-existing, unrelated to this change — even
  `./gradlew help` fails); relying on CI to confirm compile/test-green.
- ✅ `2026-06-24` — **Frost fonts.** Bundled Plus Jakarta Sans (variable; weights pinned via
  `FontVariation`) + Space Mono TTFs from google/fonts into `res/font/`. `InventoryTypography`
  applies Jakarta across all M3 text styles; join codes render in Space Mono. No runtime font
  fetch / no certificate config (bundled, deterministic). CI green (1 fix: opt-in to
  ExperimentalTextApi for the variable-font API).
- ✅ `2026-06-24` — **Shelves-as-tabs + swipe-pager (Frost D-020).** Merged the separate
  shelves/products screens into `LocationDetailScreen`: shelves render as a `ScrollableTabRow`
  and products in a `HorizontalPager` (one page per shelf; tap a tab or swipe to switch).
  Product content extracted to a reusable `ProductsPane` (ViewModel keyed per shelf); add-shelf
  form sits above the tabs. Nav simplified: storage → single `location` route replaces the
  shelves + products routes. CI green.
- ✅ `2026-06-23` — **Product move UI.** `ProductsViewModel` now injects Location + Shelf
  repositories to build the household's shelves; `startMove`/`confirmMove`/`cancelMove` drive a
  move flow on `ProductRepository.move`. `ProductsScreen` adds a per-product Move button + an
  `AlertDialog` shelf-picker (current shelf excluded, label `Location › Shelf`); the product
  drops off the current list after moving. `ProductsViewModelTest` updated (3-repo ctor + move
  test). CI green. **The Android app is now functionally complete.**
- ✅ `2026-06-23` — **Navigation-Compose refactor.** Added `androidx.navigation:navigation-compose`;
  `MainActivity` now uses a `NavHost` with typed routes (auth/households/settings/storage/search/
  invite/shelves/products) and `Long` path args, replacing the six state-based nav flags. Auth
  transitions drive a back-stack-clearing redirect; sign-out returns to auth. The activity-scoped
  `ThemeViewModel` is passed into `SettingsScreen` so the theme stays shared now that per-screen
  `hiltViewModel()` scopes to nav back-stack entries. CI green.
- ✅ `2026-06-23` — **Settings screen (theme + sign out).** `ThemeMode` enum + `ThemeModeStore`
  (SharedPreferences) Hilt-bound; `ThemeViewModel` (activity-scoped) drives an in-app
  System/Light/Dark toggle applied app-wide in `MainActivity`. `SettingsScreen` (theme chips +
  sign out). `AuthViewModel.signOut()` clears the token + resets state, and sign-out resets nav
  to the auth screen. Reachable from the households screen. `ThemeViewModelTest` + AuthViewModel
  sign-out test. CI green. **All core screens are now shipped.**
- ✅ `2026-06-23` — **Invite screen.** `InviteApi` (`households/{household}/invite` → `{code, link}`)
  + DTO, `InviteRepository`(+impl) Hilt-bound, `InviteViewModel` (load), `InviteScreen` (Compose:
  join code, QR rendered from the link via `zxing-core`, copy-link to clipboard). Reachable from
  the storage overview via an Invite button. `InviteViewModelTest` (load/error). CI green.
- ✅ `2026-06-23` — **Search screen.** `SearchApi` (`households/{household}/search?q=`) + DTO
  with the `location › shelf` path, `SearchRepository`(+impl) Hilt-bound, `SearchViewModel`
  (`setHousehold` + query-driven search, blank clears results), `SearchScreen` (Compose: query
  field + result cards: name / path / qty). Reachable from the storage overview via a Search
  button. `SearchViewModelTest` (match / blank / error). CI green.
- ✅ `2026-06-23` — **Products screen + stock steppers.** `ProductApi` (list/create/add/remove/
  move) + DTOs, `ProductRepository`(+impl) Hilt-bound, `ProductsViewModel` (load + create +
  increment/decrement updating quantity in place), `ProductsScreen` (Compose: per-product +/-
  steppers + create). Shelves tap through; nav now household → location → shelves → products
  (with back at each level). `move()` is implemented in the data layer; its shelf-picker UI is
  a follow-up. `ProductsViewModelTest` (load/increment/decrement/create/error). CI green.
- ✅ `2026-06-23` — **Shelves screen.** Location-scoped `ShelfApi` + DTOs, `ShelfRepository`
  (+impl) Hilt-bound, `ShelvesViewModel` (`load(householdId, locationId)` + create),
  `ShelvesScreen` (Compose: list + add). Storage-overview location cards tap through; nav
  extended household → location → shelves (with back). `ShelvesViewModelTest` (load/create/error). CI green.
- ✅ `2026-06-23` — **Storage overview screen.** Household-scoped `LocationApi` + DTOs,
  `LocationRepository`(+impl) Hilt-bound, `StorageOverviewViewModel` (`load(householdId)` +
  create-with-type), `StorageOverviewScreen` (Compose: list + type FilterChips + add).
  Households cards tap through via minimal state-based nav in `MainActivity` (with back);
  full Navigation-Compose deferred. `StorageOverviewViewModelTest` (load/create/error).
  CI green (took 1 fix: opt-in to ExperimentalLayoutApi for FlowRow).
- ✅ `2026-06-23` — **Households screen (list / create / join).** `HouseholdApi` + data-envelope
  DTOs, `HouseholdRepository`(+impl) bound via Hilt, `HouseholdsViewModel` (load-on-init +
  create + join state machine), `HouseholdsScreen` (Compose: list + create + join). `MainActivity`
  routes authenticated users here. `HouseholdsViewModelTest` (load/create/error) with a fake repo.
  CI green first try.
- ✅ `2026-06-23` — **DI + networking + email/password auth.** Hilt (KSP) wired
  (`InventoryApp`, `MainActivity` `@AndroidEntryPoint`, Network/Storage/Repository modules);
  Retrofit + OkHttp + Kotlinx Serialization (Square `converter-kotlinx-serialization:2.11.0`),
  build-config `BASE_URL`; `AuthApi` + DTOs; `AuthInterceptor` injects the bearer token;
  `EncryptedTokenStore` (Keystore-wrapped). `AuthRepository` + `AuthViewModel` (login/register
  state machine) + `AuthScreen` (Compose). Google button stubbed — `loginWithGoogle` path
  ready, native sign-in deferred (needs a client ID). `AuthViewModelTest` (success/failure)
  with a fake repo + `MainDispatcherRule`. CI green (took 1 fix: converter artifact + a
  `result.fold` `it`-shadowing bug). ktlint still deferred.
- ✅ `2026-06-23` — **Compose project skeleton + Frost theme.** Single-activity Jetpack
  Compose app: Gradle 8.9 (wrapper from the official release tag, validated in CI), AGP 8.5.2,
  Kotlin 2.0.20, compose-bom 2024.09.03, compileSdk 34 / minSdk 26. `MainActivity` +
  `InventoryTheme` (Frost Color/Theme/Type from the design tokens), `ExampleUnitTest`, lint
  non-fatal at skeleton stage. CI (wrapper validation + `testDebugUnitTest` + lint) green.
  Hilt/Retrofit/ktlint + screens deferred to the next pass. Design mocks under `docs/design/`.
