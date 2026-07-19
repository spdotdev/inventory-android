# CLAUDE.md — Inventory (Android client)

Working agreement for Claude Code. Read before any task. Canonical spec lives in
[`inventory-laravel`](https://github.com/spdotdev/inventory-laravel)'s `docs/`
(`docs/planning/project-brief.md`, `docs/specs/data-model.md`,
`docs/specs/api-contract.md`); Android-specific planning in this repo's own `docs/`.

## What this is
The **sole `/api/v1` client** for the Inventory product — a native **Android
(Kotlin/Compose)** app talking to a headless Laravel API at
`https://inventory.{domain}/api/v1`. Private, multi-user, multi-household inventory.
General-purpose; freezer/fridge/pantry are *example* storage types, not the brand.
Since 2026-07-18 the web app (Blade+Alpine in `inventory-laravel`) is a
**first-class equal surface** with full feature parity — this app's one permanent
exclusive is **barcode scanning**. Keep capability cross-hints accurate in both
directions when features move.

## Stack
- Kotlin, Jetpack **Compose**, single-activity
- **MVVM/MVI** architecture
- **Hilt** (DI), **Retrofit/OkHttp** (networking), Kotlinx Serialization / Moshi (JSON)
- **No Room / no local DB** — always-online, server is the single source of truth
- Native **Google Sign-In** (Credential Manager) for the Google auth flow
- QR rendering via a client lib (invite links)

## Hard rules — LOCKED, do not relitigate
- **Always-online.** NO offline cache/store, NO sync, NO conflict resolution. If the
  network is down, surface it — don't fake local state.
- **Server-authoritative.** The API owns truth; the app uses optimistic UI + live
  updates over the `inventory.household.{id}` websocket channel (D-034), with
  pull-to-refresh as fallback — never a local source of truth.
- **Talk only to `/api/v1`** and treat it as a stable contract (see `specs/api-contract.md`).
  Base URL is configurable (build config), never hardcoded per-screen.
- **Auth = Sanctum bearer token.** Store securely (EncryptedSharedPreferences / DataStore +
  Keystore). Email/password **and** Google both resolve to a bearer token sent as
  `Authorization: Bearer <token>`.
- **Tenancy via URL.** Every household-scoped call goes through
  `/households/{household}/...`; the active household is explicit, never implicit.

## Deletes — LOCKED, do not relitigate
Rewritten this branch after a tester hit an unconfirmed, unstrategized, silent bulk
delete. Every point below was a real Critical/Important bug on this branch — the *why*
is the cost of "simplifying" it back out.

- **Every destructive delete goes through a confirmation dialog.** No silent delete
  exists anywhere: shelves/locations go through edit-mode selection →
  `DeleteStrategyDialog`; products reveal a delete button on swipe, and the button opens
  an `AlertDialog`; leaving a household is behind its own confirm. Don't add a bare
  swipe-to-delete or a one-tap destructive action anywhere — that's the bug this branch
  exists to close.
- **A non-empty container needs an EXPLICIT strategy — the server never guesses (422s
  otherwise).** Shelves: move / unsort / delete the products. Locations: move / delete
  the contents — **no unsort at the location level**, on purpose:
  `LocationDeleteStrategy` has no unsort variant because "unsorted" means off-shelf but
  still IN the location, and the location itself is what's being deleted.
- **`contentCount` asymmetry:** a SHELF asks when it has PRODUCTS; a LOCATION asks when
  it has SHELVES — **even empty ones** (`ShelfDto.product_count` /
  `LocationDto.shelf_count`, `ui/hierarchy/DeletePlan.kt`). Collapsing this to "asks
  when it holds products" 422s every delete of a location holding one empty shelf — a
  real bug caught in Task-1 review before it shipped.
- **The CLIENT mints `deletion_batch_id`** (`UUID.randomUUID()`, once per user gesture,
  reused across every request that gesture fires — see `ShelvesViewModel`,
  `StorageOverviewViewModel`, `DrawerViewModel`). That one shared id per gesture is what
  makes Undo restore the *whole* gesture instead of one item out of N.
- **Request DTOs: an unset key must be OMITTED, not encoded as `null`.** This app's
  `Json` runs on kotlinx's real defaults — `explicitNulls = true`, `encodeDefaults =
  false` — so a property with NO Kotlin default is *always* encoded, even when null;
  giving it `= null` makes an unset value OMITTED instead. `DeleteShelfRequest` /
  `DeleteLocationRequest` rely on this: `strategy`/`target_*` default to null so an
  empty-container delete's body never carries `"strategy":null`, which the server 422s
  as a type error (this was a Critical: every delete but "move" failed).
  `UpdateHouseholdRequest` is the deliberate ASYMMETRIC exception: `name` HAS a default
  (omitted = "don't touch the name" — Laravel's `sometimes|required` 422s an *explicit*
  null), while `color`/`icon` have NO default (an explicit null IS encoded, and clears
  the theme back to its derived default — Laravel's `sometimes|nullable`). Do not
  "tidy" this into one shape and do not flip `explicitNulls` globally — both directions
  are pinned at the byte level (`DeleteRequestSerializationTest`), because a
  descriptor-shape assertion alone ("this property has no default") already let this
  exact bug ship once. See "Testing lessons" below.
- **Ordering: drag order always wins. A star/favourite is a MARKER, never a sort key.**
  `ui/common/HierarchyOrder.kt`'s `orderByPosition(items, position, name)` deliberately
  takes no favourites parameter, so "the starred item floats to the top" is
  structurally unwritable, not just untested.
- **The Unsorted shelf is a system shelf (`is_system`)**: never renamable, never
  selectable for deletion, never a move target, always sorts last. Any new shelf-list
  UI must gate on `is_system` the way `EditableRow` / `ShelvesViewModel` already do.

## Auth flows
- Email/password → `POST /auth/register` or `/auth/login` → store token.
- Google → native Google Sign-In → obtain Google **ID token** → `POST /auth/google { id_token }`
  → store the returned Sanctum token. Google-only accounts have no password.
- Logout → `POST /auth/logout`, clear stored token.

## Screens (D-020/D-022/D-023)
1. **Auth** — email/password + Google sign-in.
2. **Storage overview** — household's locations (type + shelf/item counts).
3. **Shelves** — `ScrollableTabRow` (tab strip) + `HorizontalPager` (swipe), or a plain
   list — user-toggled and persisted (`ShelfViewStore`); add targets active shelf.
4. **Products** — name + quantity; add/remove quantity; relocate (move).
5. **Search** — global product search; results show location › shelf + quantity.
6. **Invite** — join code + copyable link + QR (QR rendered from the link).
7. **More** (route `settings`) — theme (System/Light/Dark), language, join-by-code,
   household management (→ Households screen), account / sign out. Reached from the
   bottom bar, not a gear icon.

Navigation: Household → Storage overview → Shelves (tabs *or* list) → Products. A 5-tab
bottom bar (**Dashboard / Storage / Scan / Missing / More**) is the app's only
navigation surface — there is no drawer. **Households** left the bar: it's a
tenant-management screen reached under **More**, which absorbed Settings *and*
households. **Search** lost its tab; it's reached via a top-bar icon on Dashboard, Storage overview,
Home and Missing items. Storage overview is already scoped to one household (its route
carries `householdId`), so its icon just opens Search directly. Dashboard, Home and
Missing items are **not** household-scoped — their icon (and Dashboard's products stat
card) opens Search directly only when the account has exactly one household; with more
than one it opens a shared household picker (`ui/common/HouseholdPickerSheet.kt`) first,
same as the centre **Scan** tab's LOOKUP mode below. (Final review, 2026-07-14: every one
of these hard-coded the *first* household instead, making a second household's search
reachable only by drilling Home → that household's own "+" icon → Storage overview → its
search icon — don't reintroduce a bare `entries.firstOrNull()`.) Households and Search
each keep their own small gear-icon shortcut back to Settings, since neither sits on the
bottom bar itself — that's the only place a gear icon survives, not "every screen" as
before this branch. The centre **Scan** tab always opens the scanner in LOOKUP mode
(hands the scanned code to Search, pre-filled and already run, via the same
household-count/picker rule above — it has genuinely no household context of its own);
opening the scanner from a shelves screen is ADD mode instead — same route
(`Routes.SCANNER`), a `ScannerMode` argument picks the behavior and delivers the code
back to that screen via `savedStateHandle` rather than to Search (`MainActivity.kt`:
`ScannerMode`, `scanDeliveryActionFor`). Because both modes share one route, the bottom
bar's own visibility/selected state for the Scan tab is resolved from the *mode*
argument, not the route alone — `scannerRouteIsTheBottomBarTab()`; matching the bare
route would show the bar (with Scan selected) over ADD mode's camera too.

**Editing the hierarchy** lives behind a pencil (edit mode) on the households, locations
and shelves lists — never inside Settings/More itself. On locations and shelves, edit
mode turns each row into a checkbox (multi-select → delete-strategy dialog) plus a
rename pencil and up/down reorder buttons; the row body itself only toggles selection.
Households have no multi-select or reorder: edit mode instead makes the row tappable,
opening a full household edit page (name, colour/icon theme, and a danger zone offering
"Leave" plus, Owner-only, "Delete household"). Deleting a non-empty shelf or location
always asks what to do with the contents — see "Deletes" above.

## Design — B · Frost (D-021)
- Frosted-glass cards, icy-blue accent **#7dd3fc**, rounded controls, **Plus Jakarta Sans**.
- Full light/dark, switched in-app (System/Light/Dark) per Settings.
- Reference mocks in [`docs/design/`](./docs/design): `frost-app.html` (interactive 5-screen
  prototype with working light/dark toggle), `frost-dark.png`, `frost-light.png`. Build the
  Compose theme to match these.

## Scope guardrails — refuse to add
No expiry/reminders, recipes, shopping list, offline mode.
**Phase 2 unlocked 2026-07-10** (user decision) and since shipped: barcode scanning,
the low-stock "running low" view, filter/sort, household color/icon theming, and the
live-updates client — see `ROADMAP.md` / `BACKLOG.md`.
**Roles/permissions: shipped (2026-07-17).** Owner/Admin/Member. `HouseholdDto` carries
the caller's own `role`, `can_restructure`, and `can_manage_members` (server-computed —
never re-derived client-side). Every edit-mode pencil (households/locations/shelves)
and the household theme-edit fields are gated on `can_restructure`; a Member no longer
sees an affordance guaranteed to 403 — this is a UI convenience, the server is still the
real boundary. A **Members** screen (reached from the household edit page) lists the
roster with role badges; promote/demote/remove are gated on `can_manage_members` and
never shown on the Owner's own row, which instead offers **Transfer ownership** (Owner
only) — a household always has exactly one Owner. Leaving as the sole Owner 409s with a
friendly message asking to transfer first. **Household delete: shipped (2026-07-18).**
`DELETE /households/{household}` is Owner-only; the request body must carry
`{"name": "<exact current name>"}` as a server-verified typed confirmation (422 on
mismatch, 403 non-owner) — this closes the solo-owner dead end (can't leave, previously
also couldn't delete). `HouseholdEditScreen`'s danger zone shows "Delete household" only
when `household.role == "owner"`; its confirm dialog requires typing the exact name
before the Delete button enables. Reuses `HouseholdsUiState.leftHouseholdId` (see its
doc comment) as the "household gone, navigate back" signal, same as leave. Keep this
repo's guardrail in sync with `inventory-laravel/CLAUDE.md`'s matching bullet.

## Conventions
- Explicit over magic; SRP; document the *why*.
- Unidirectional state; ViewModels expose immutable UI state; side-effects via events.
- Tests cover critical paths: auth/token handling, household scoping, stock actions,
  error/empty/offline states. No trivial UI tests.

## Testing lessons — this branch shipped Criticals through a fully green suite
- **A fake that lies about the server is worse than no test.** `FakeShelfRepository.
  reorder()` used to model a response the real reorder endpoint never returns — its own
  comment asserted the opposite of the truth. That alone hid a guaranteed `LazyColumn`
  duplicate-key crash (reorder duplicating the Unsorted shelf) behind green tests. It's
  fixed now, and documents the real contract explicitly: `ShelfController::reorder` ends
  `return $this->index(...)` — the FULL list, system shelf included. Before trusting a
  fake, check it against the real controller in
  `inventory-laravel/src/Http/Controllers/Api/`.
- **Assert the bytes, not the shape.** `DeleteRequestSerializationTest` used to assert
  only "this DTO property has no default" via the serializer descriptor — that PINNED a
  broken wire format (every delete but "move" 422ing) as passing. Fixed by also encoding
  a real instance per production call site and asserting the literal JSON string. A
  descriptor-shape test can pass while the wire format is wrong; only the encoded bytes
  prove it.
- **Instrumented flow tests rot silently.** They keep compiling after the UI they drive
  is removed or reworked, so a green local build proves nothing about them — they only
  fail on the nightly emulator CI gate. Re-read a flow test's actual gestures (not just
  its name) whenever the screen it drives changes.
- **`./gradlew detekt` does not currently scan `app/src/androidTest`**, though
  `ktlintCheck` does (`ktlintAndroidTestSourceSetCheck` genuinely runs;
  `detekt`'s own file-count metrics exclude every androidTest file). Don't treat a
  green `detekt` as having checked flow-test code.
- **An unused string resource breaks no gate — that's exactly how `shelf_unsorted`,
  `delete_undone` and `delete_undo_failed` shipped dead** (final review, 2026-07-14):
  each was added alongside a doc comment or a spec line promising it was wired up, and
  nothing ever called `stringResource()` on it. `StringResourceUsageTest` (JVM suite)
  now fails if any `values/strings.xml` entry has zero `R.string.<name>` / `@string/<name>`
  references anywhere in the module — the cheap check that would have caught all three
  for free. Don't add a string resource in the same commit as "TODO: wire this up
  later" — wire it in the same commit, or the gate is the only thing that will ever
  notice it wasn't.

## Measuring type or layout on a device — read the device's settings first
Android rewrites text before your measurement ever sees it. **Check these before trusting
any on-device font/layout number, and state their values alongside the result:**

```bash
adb shell settings get system font_scale               # 1.0 = unscaled
adb shell settings get secure font_weight_adjustment   # 0 = none; 300 = "Bold text" ON
```

`font_weight_adjustment` is added to *every* requested `FontWeight` (400→700, 500→800, …)
and then clamps at the family's heaviest weight — so with it on, several distinct weights
collapse onto one and glyph metrics come back **identical**. That reads exactly like a
broken font, and in 2026-07 it cost a session: the app's variable font was "diagnosed" as
ignoring its `wght` axis and nearly replaced with five static files, when the axis was fine
and the phone simply had **Bold text** enabled (this is also the whole of issue #32 — the
tester's two phones differ by that setting, not by build).

Two habits that catch it:
- **Run the control.** Before concluding the app is broken, measure the same thing with
  `FontFamily.Default` (does the system font vary by weight here?) and re-measure with the
  setting neutralised. A number that only misbehaves under one device config is a device
  config finding, not a code finding.
- **Neutralise, then restore.** Set the value for the experiment and put the user's original
  back when done — these are the phone owner's accessibility preferences, not test knobs.

The same applies to `font_scale`: a label that fits at 1.0 can ellipsize at 1.6, and flow
tests run at whatever the device is set to, so they will not catch it. To cover a font scale
the device isn't on, render the composable in isolation and override `LocalDensity`
(see `ProductFilterSortRowTest`).

## Security
Vulnerability reports go through [`SECURITY.md`](SECURITY.md) (GitHub Security
Advisory or maintainer email — **not** a public issue). CI runs `gitleaks` (secret
scan), CodeQL (SAST) on PRs + weekly schedule (dropped from per-push 2026-07-20 to cut
Actions cost — main is still scanned weekly), and dependency review (blocks
high-severity-vulnerable new dependencies) — see `.github/workflows/`.

## Status
Functionally-complete (MVP + Phase 2 + storage-architecture editing + household roles),
CI-green, running against the **production backend at
`https://inventory.scuttle.dev/api/v1`**. The
single-activity Compose + Hilt + Retrofit app covers auth (email/password + Google,
device-smoke-tested), storage overview, shelves (tabs/list toggle), products
(add/remove/move, detail, image), search, invite (QR/join), More/Settings (theme,
language, join-by-code, households, account/sign out), dashboard, missing-items,
barcode scanning (LOOKUP from the Scan tab, ADD from a shelf screen), low-stock, and
live updates — with EN + NL localization and the Frost theme. The full hierarchy
(households, locations, shelves) is now editable in place behind a pencil: rename,
up/down reorder, and delete-through-a-mandatory-strategy-dialog with a
client-minted `deletion_batch_id` per gesture and snackbar Undo — see "Deletes" above.
Every member has a role (Owner/Admin/Member); edit-mode pencils and a **Members**
screen (promote/demote/remove, transfer ownership) gate on the server-computed
`can_restructure`/`can_manage_members` flags — see "Roles/permissions" above.
Covered by JVM unit tests + instrumented flow tests (the latter run nightly on an
emulator in CI; `ktlintCheck` covers `androidTest`, `detekt` currently does not — see
"Testing lessons"). Distribution is **debug builds only** (tag-driven GitHub prerelease
APKs) — no store presence yet. Forward-looking work lives in
[`ROADMAP.md`](ROADMAP.md); shipped history in [`BACKLOG.md`](BACKLOG.md).
