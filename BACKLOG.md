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

---

## Ideas — parking lot
- 💡 Filter / sort products within a shelf or search results.
- 💡 Widget / quick-tile for "what's low" (depends on a low-stock concept — not in MVP).
- 💡 Per-household color/icon theming on top of Frost.
- 💡 Q-3: live updates (WebSockets) if pull-to-refresh proves insufficient.

---

## Done
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
