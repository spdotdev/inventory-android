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
the backend gaining a `barcode` attribute (Phase 2, see `inventory-laravel/docs/`).

**Risks.** Camera permission friction; offline barcode→name lookup needs a data source
(out of scope while always-online). Effort ~3–4 days. **Kill criterion:** if manual add is
fast enough in real use, don't build it.

> **Deferred 2026-07-04** (backlog-sweep decision), **unlocked 2026-07-10** (user
> decision), **shipped 2026-07-10** — see [`ROADMAP.md`](ROADMAP.md) → Phase 2.

---

## Ideas — parking lot
- 💡 Widget / quick-tile for "what's low": the **in-app** "running low" tile version was
  **unlocked 2026-07-10** (user decision) and is tracked in [`ROADMAP.md`](ROADMAP.md) →
  Phase 2, with a `low_stock_threshold` field landing on the backend. A real OS
  home-screen widget stays deferred — it fights the always-online + bearer-token model
  (no background auth).
- ~~💡 Q-3: live updates (WebSockets)~~ — **unlocked + built 2026-07-10** (user decision:
  full Reverb over cheap polling); see Done.
- ~~💡 User-chosen per-household color/icon~~ — **shipped 2026-07-10** (backend `color`/`icon`
  keys + picker dialog on My Households, drawer/list avatars honor them; null = the
  id-derived default). See Done.

---

## Done
- ✅ `2026-07-19` — **`EditModeHintViewModel` test coverage gap closed.** Last of ~20
  ViewModels with no dedicated unit test, flagged by a codebase audit.
  `EditModeHintViewModelTest.kt` covers: `visible` reflects `hasSeen()` at construction
  (both true/false), `dismiss()` marks seen and flips `visible` false, and the correct
  `HINT_EDIT_MODE_PENCIL` id is used. Also documented (not fixed, per audit scope) a real
  gap in `dismiss()`: it has no try/catch around `markSeen()`, so a `HintsStore` write
  failure is only ever delivered as an uncaught coroutine exception from
  `viewModelScope.launch` — it never propagates to the caller and `visible` is left stuck
  `true` forever, with no user-facing error. Exercising that path deliberately leaves an
  uncaught coroutine exception on `Dispatchers.Main`'s scheduler, which made
  `MainDispatcherRule` an accidental cross-test leak vector: `UnconfinedTestDispatcher()`'s
  no-scheduler-given form reuses whatever scheduler Main is already set to, so a
  full-suite run could non-deterministically fail an unrelated later `runTest`-based test
  with `UncaughtExceptionsBeforeTest` instead of the originating test. Fixed by having
  `MainDispatcherRule` always construct its own explicit `TestCoroutineScheduler()`, so
  every test's Main dispatcher is provably isolated. `ktlintCheck`/`detekt`/
  `testDebugUnitTest` green (verified stable across repeated full-suite runs, not just once).
- ✅ `2026-07-10` — **3 "device-state" instrumented failures were a real bug — geometry, not
  state.** EditProductDetail/MandatoryToggle/MissingItems::product_detail all performClick'd
  the product card, which injects at the node CENTER — and since the bin/Move overlap fix
  (06a989b) shifted the stepper cluster, the center lands on the DISABLED "−" button (qty 0),
  which silently swallows the tap; the card's navigate-onClick never fires. Humans tap the
  name, so manual testing always worked; the earlier device-state conclusion was wrong (the
  nightly emulator reproduced all 3 exactly). Fix: FlowTestBase.clickNameArea() taps at
  percentOffset(0.15, 0.5). Same sweep: the bottom nav's Settings tab made bare-text
  "Settings" ambiguous — drawer entry now has drawer-nav-settings (as drawer-nav-search).
  Full suite 35/35 on a local emulator.
- ✅ `2026-07-10` — **Live updates client (Q-3, user decision: full Reverb)**. Foreground-only
  websocket (pusher-java-client → Reverb, Pusher protocol) subscribed to every household's
  private channel; a `household.changed` ping debounce-triggers a silent HierarchyStore
  refresh — machine-initiated pull-to-refresh, no client-side state (server-authoritative).
  Auth via the Sanctum-gated /api/v1/broadcasting/auth. Gateway abstracted for JVM tests.
  ~~END-TO-END PENDING server steps: REVERB_* env keys on d051 + host proxy websocket
  pass-through (tracked in inventory-laravel ROADMAP).~~ Server steps completed the same
  day, and the loop was **smoke-verified end-to-end against prod 2026-07-10**: an
  external Pusher-protocol client on the exact wss://{REVERB_HOST}/app/{key} path this
  gateway uses (connect → channel auth → subscribe → API mutation) received
  `household.changed` ~1.6 s after the mutation. The client's reaction to the ping
  stays covered by JVM unit tests.
- ✅ `2026-07-10` — **Bottom navigation bar** (UX wave 2, user-approved, c368b96). Material3
  `NavigationBar` with Dashboard / Storage / Search / Settings tabs, shown only on top-level
  routes — detail screens (shelves, product detail, scanner) stay full-screen. Tabs use
  `popUpTo(dashboard)` + `saveState`/`restoreState` + `launchSingleTop` so per-tab state
  survives switching. The drawer stays for household switching and location quick-jumps.
- ✅ `2026-07-10` — **Dashboard navigates** (UX wave 1, tester feedback: "numbers are shown
  but not clickable", bf4b335). Stat cards, per-location bars (with chevron), running-low
  rows, and missing-items entries now navigate to the storage overview / location / search.
  Drawer entries gained testTags because the newly-clickable dashboard rows made bare-text
  matchers ambiguous in the flow tests.
- ✅ `2026-07-10` — **Long product names ellipsize instead of squashing** (tester report on a
  narrow device, 7923d03). Product-row names cap at 2 lines with `Ellipsis` (full name lives
  on the detail screen); the code chip moved under the name. Chosen over the suggested
  name-on-own-line layout: rows stay compact and degrade gracefully at any width.
- ✅ `2026-07-10` — **Scanner viewfinder overlay** (4 iterations with the user, 1bba555 →
  cc01b65). Final look: four rounded corner brackets, centered pulsing translucent red laser,
  even-odd scrim with radial falloff focusing the eye on the frame (magnific.com reference).
- ✅ `2026-07-10` — **Delete affordance no longer bleeds through frosted cards at rest**
  (tester report: bin icon and Move button overlapped, 06a989b). Swipe background renders
  only while a dismiss is actually in progress.
- ✅ `2026-07-10` — **2026 toolchain migration** (09648ec + follow-ups). Gradle 9.6.1,
  AGP 9.2.1 (built-in Kotlin — classic KGP removed), KSP 2.3.10, compiler plugins 2.4.0,
  compileSdk 37, Retrofit 3, OkHttp 5, Compose BOM 2026.06; ktlint-gradle 12.1.2 + detekt
  1.23.8 baselines regenerated. Verified with repeated 35/35 green instrumented device runs.
- ✅ `2026-07-04` — **Android CI runs when its own workflow file changes** (wave-3 X7). `ci.yml`'s `paths:`
  filters caught `**/*.kts` but not `.github/workflows/ci.yml` itself, so a PR editing only the CI workflow
  skipped the build/test job (the Laravel repo already had this via W20). Added the workflow path to both the
  `push` and `pull_request` `paths:` lists.
- ✅ `2026-07-04` — **`AuthInterceptor` 401 session-expiry path is unit-tested** (wave-3 X8). The reactive
  logout (a 401 on any call clears the token and flips `sessionActive` → the app returns to login) is named a
  critical path in `CLAUDE.md` but had no unit coverage. Added `AuthInterceptorTest`: 401 clears the token +
  flips auth state; 200 leaves it; the `Authorization: Bearer` header is attached only when a token exists.
  MockWebServer is `androidTestImplementation`-only, so it uses a fake `Interceptor.Chain` + fake
  `TokenStore` — pure JVM, no emulator.
- ✅ `2026-07-04` — **Google sign-in 401 shows Google-specific copy (regression test)** (wave-3 X12). A
  rejected Google ID token comes back as a 401 — the same status the email/password flow maps to "Incorrect
  email or password." The audit suspected the Google button would show that wrong copy, but on re-check
  `AuthViewModel.toGoogleAuthErrorMessage()` already maps a Google 401 → "Google sign-in failed. Please try
  again." No behaviour change was needed; added a regression test pinning that copy so a future refactor
  can't route the Google path through the shared password mapper (`FakeAuthRepository`'s failure `Throwable`
  is now injectable).
- ✅ `2026-07-04` — **Household create/join/leave now refreshes the shared hierarchy** (wave-3 X4). `HouseholdsViewModel.create()/leave()`
  and `JoinHouseholdViewModel.join()` updated only the `HouseholdRepository` cache + their own UI state, so
  the drawer/home/dashboard (driven by the `@Singleton HierarchyStore`) stayed stale until a manual
  pull-to-refresh or relaunch — product create/delete already refreshed, but household-level mutations were
  missed. Inject `HierarchyStore` into both VMs and call `refresh()` after a successful create/join/leave.
  Tests assert the store reflects the mutation (shared `TestHierarchy` helper).
- ✅ `2026-07-04` — **Per-location "⚠ needs attention" indicator now lights up on load** (wave-3 X2, HIGH/MED).
  Home (`AllStoragesScreen`) and the drawer rendered the warning purely from `state.locationWarnings`, which
  was populated *only* by a composed `ProductsPane` via `reportLocationWarning` — so a location whose
  out-of-stock mandatory item sat on a non-visible shelf showed no ⚠ until the user opened it and swiped to
  that shelf, making the at-a-glance rollup silently show "all good". `HierarchyStore` now computes
  `locationWarnings` authoritatively in both `loadFromCache` and `buildFromNetwork` by grouping the
  `missingItems` it already builds by `locationId`, and `refresh` onSuccess takes the server-computed map as
  truth (dropping the old partial-map preservation — the API is server-authoritative and mutations refresh
  after the server confirms). Tests cover the warning appearing from a non-visible shelf and clearing when stocked.
- ✅ `2026-07-04` — **Logout/login no longer leaks the previous user's data** (wave-3 X1, HIGH). `logout()`
  cleared only the bearer token, but every repository + `HierarchyStore` + the favorites/default-household
  prefs are `@Singleton`, so an account switch on the same process rendered User A's households/locations/
  products/missing-items/favorites to User B — `HierarchyStore.refresh()` → `loadFromCache()` repopulates
  synchronously from the stale caches before the network returns. Added `clear()` to each repo cache
  (default no-op on the interface so test fakes are unaffected), `HierarchyStore` (reset state + cancel the
  in-flight job), and `FavoritesStore`; centralized in a `SessionCleaner` called on `logout()` **and** at
  the start of every new sign-in (register/login/google) — the latter also covers the reactive-401 path,
  where the interceptor clears the token but can't reach the singletons. In-memory/prefs only (no local DB).
  Tests: `HierarchyStore.clear()` resets, `SessionCleaner.clear()` fans out to every cache/store.
- ✅ `2026-07-04` — **Drawer search reaches every household, not just the first** (wave-2 W23). `AppDrawer`
  wired the single global "Search" entry to `entries.firstOrNull()?.id`, so a multi-household user could
  only ever search their first household — silently. Search is legitimately household-scoped, so: keep the
  global entry only when there's exactly one household; when there are several, render a per-household
  search `IconButton` on each household row (content-described "Search <household>", EN + NL), so every
  household is reachable and the scope is explicit. No new endpoints.
- ✅ `2026-07-04` — **Invite QR generated off the composition thread** (wave-2 W22). `InviteScreen` built the
  512×512 QR with a ~262k-pixel `setPixel` loop inside `remember(link)` — on the composition thread, so the
  invite load visibly hitched. Moved it to `produceState(null, link){ withContext(Dispatchers.Default){ … } }`
  with a `CircularProgressIndicator` in the QR's 220dp footprint until it's ready.
- ✅ `2026-07-04` — **Error-path unit tests for the hierarchy layer** (wave-2 W16). There were no error-path
  tests for `HierarchyStore`/`DrawerViewModel`/`MissingItemsViewModel`. Added a new `HierarchyStoreTest`
  (refresh success populates + clears indicators; refresh failure sets `error` and clears loading/refreshing),
  plus the `DrawerUiState.error` mapping (W3), `DrawerViewModel.deleteLocation` failure (W10), and
  `MissingItemsViewModel` error (W4) tests added alongside those fixes — all pure-JVM, no emulator.
- ✅ `2026-07-04` — **Drawer/home location-delete failure is surfaced, not swallowed** (wave-2 W10).
  `DrawerViewModel.deleteLocation` had `runCatching{…}.onSuccess{ refresh() }` with no `onFailure`, so a
  failed swipe-to-delete on AllStorages left the location in place with no feedback. Added a one-shot
  `actionError` flow (separate from the store-derived load `error`, since a delete fails while the list is
  populated and the inline empty-state ErrorRetry would never show it), rendered via the existing
  `SnackbarErrorEffect` + a new `SnackbarHost` on the screen. Added a VM delete-failure unit test.
- ✅ `2026-07-04` — **Shelf tabs announce missing-stock instead of signalling by color alone** (wave-2 W9).
  `LocationDetailScreen` marked an out-of-stock-mandatory shelf with red tab text + a decorative 6dp dot
  and no semantics — color-only (WCAG 1.4.1) and silent to TalkBack. Added a `location_shelf_missing_cd`
  string (EN + NL) and a `clearAndSetSemantics { contentDescription = "<shelf>, has missing items" }` on
  the warning tab's text row, so the state is conveyed non-visually without doubling the shelf name.
- ✅ `2026-07-04` — **Inline error/status text is now announced by TalkBack on six screens** (wave-2 W8).
  The wave-1 `liveRegion=Assertive` pattern (AuthScreen/ErrorRetry) wasn't propagated to the bare
  `Text(error)` on Dashboard, Households, Search, Invite, LocationDetail, and Settings (join error *and*
  success) — all left silent until focused. Added a shared `LiveStatusText` helper (Assertive live region,
  error/primary color) and routed all seven sites through it, including announcing the Settings join success.
- ✅ `2026-07-04` — **Empty vs error no longer conflated on StorageOverview + LocationDetail** (wave-2 W7).
  Both screens rendered their error line *and* the empty text on a failed load ("Something went wrong" +
  "No storages yet" / "no shelves yet") — the empty text reads as a false "your account is empty". Gated
  the empty text on `state.error == null` in both.
- ✅ `2026-07-04` — **Missing-items screen surfaces load errors instead of a false "all stocked"** (wave-2 W4).
  `MissingItemsViewModel` exposed `error` but `MissingItemsScreen` never rendered it — a failed load fell
  through to `missing_items_empty`, i.e. a screen whose whole job is surfacing warnings would silently
  claim everything's fine on a network error. Render `state.error` via `ErrorRetry(onRetry = refresh)`
  when there are no items, ahead of the empty state. Added a VM error-path unit test.
- ✅ `2026-07-04` — **AllStorages shows load errors instead of a false empty state** (wave-2 W3).
  `DrawerViewModel` mapped `HierarchyStore.state` but dropped the store's `error` (it wasn't even a field
  on `DrawerUiState`), so a network failure rendered "No storages yet" — indistinguishable from a real
  empty account, and pull-to-refresh failures vanished silently. Added `error` to `DrawerUiState`, mapped
  it, and gated the empty text on `error == null`, rendering a failure through the existing `ErrorRetry`
  (Assertive live region + retry). Added a `DrawerViewModel` error-path unit test.
- ✅ `2026-07-04` — **ForgotPasswordViewModel unit test** (gap analysis T21, found in the final
  completeness scan). A coverage sweep showed it was the only one of 16 ViewModels without a unit test,
  despite real `submit()` loading→sent/error logic on a security-adjacent flow. Added
  `ForgotPasswordViewModelTest` (fake `AuthRepository`): success sets `sent`; failure surfaces the
  friendly message and asserts the raw "Unable to resolve host…" does **not** leak; `onEmailChange`
  clears a prior error. All 16 VMs now covered. Local Gradle unrunnable here; CI runs it.
- ✅ `2026-07-04` — **ProductsPane errors → Snackbar + a11y** (gap analysis T20, found in the Tier-3
  milestone re-scan). The products list surfaced add/remove/move/delete/load failures as a persistent
  inline `Text` with no `liveRegion` — sticky and silent to TalkBack, the exact issue T10 fixed on the
  other screens. Routed them through the shared `SnackbarErrorEffect`: added `consumeError()` to
  `ProductsViewModel`, hung a `SnackbarHost` on `LocationDetailScreen`'s Scaffold and threaded its state
  into each `ProductsPane` in the pager, and dropped the inline error text. Material3's Snackbar is a live
  region, so failures are now announced. Unit test covers `consumeError()`; the pane UI is exercised by the
  nightly instrumented flows. Local Gradle unrunnable here; CI verifies.
- ✅ `2026-07-04` — **Google Sign-In → Credential Manager** (gap analysis T19). The auth screen used the
  deprecated `com.google.android.gms:play-services-auth` `GoogleSignIn` API — a direct `CLAUDE.md`
  violation ("Native Google Sign-In (Credential Manager)") and a live breakage risk as Google retires
  it. Migrated to `androidx.credentials.CredentialManager` + `GetGoogleIdOption` / `GoogleIdTokenCredential`
  (deps swapped to `androidx.credentials:credentials` + `:credentials-play-services-auth` + `googleid`;
  current API confirmed via Context7). `launchGoogleSignIn` now requests a `GetGoogleIdOption`
  (`serverClientId = GOOGLE_CLIENT_ID`, `filterByAuthorizedAccounts=false`), awaits the suspend
  `getCredential`, extracts the ID token, and hands it to the **unchanged** `loginWithGoogle(idToken)`
  path (server verification untouched); cancellation/parse/credential exceptions map to friendly errors.
  No test exercises the Google UI, so nothing breaks. ⚠️ **CI verifies compile only** — the live account
  picker → ID-token handshake needs a **manual device smoke-test with Play Services** before release
  (tracked as a ROADMAP release gate). Gradle unrunnable locally.
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
- ✅ `2026-07-04` — **Q-3 (realtime) resolved → pull-to-refresh** (see `inventory-laravel/docs/archive/` for the original discussion).
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
