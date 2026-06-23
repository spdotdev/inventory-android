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
