# CLAUDE.md — Inventory (Android client)

Working agreement for Claude Code. Read before any task. Canonical spec lives in
[`inventory-laravel`](https://github.com/spdotdev/inventory-laravel)'s `docs/`
(`docs/planning/project-brief.md`, `docs/specs/data-model.md`,
`docs/specs/api-contract.md`); Android-specific planning in this repo's own `docs/`.

## What this is
The **sole client** for the Inventory product — a native **Android (Kotlin/Compose)**
app talking to a headless Laravel API at `https://inventory.{domain}/api/v1`. Private,
multi-user, multi-household inventory. General-purpose; freezer/fridge/pantry are
*example* storage types, not the brand.

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

## Auth flows
- Email/password → `POST /auth/register` or `/auth/login` → store token.
- Google → native Google Sign-In → obtain Google **ID token** → `POST /auth/google { id_token }`
  → store the returned Sanctum token. Google-only accounts have no password.
- Logout → `POST /auth/logout`, clear stored token.

## Screens (D-020/D-022/D-023)
1. **Auth** — email/password + Google sign-in.
2. **Storage overview** — household's locations (type + shelf/item counts).
3. **Shelves** — `ScrollableTabRow` (tab strip) + `HorizontalPager` (swipe); add targets active shelf.
4. **Products** — name + quantity; add/remove quantity; relocate (move).
5. **Search** — global product search; results show location › shelf + quantity.
6. **Invite** — join code + copyable link + QR (QR rendered from the link).
7. **Settings** — theme (System/Light/Dark), household management, account / sign out.
Navigation: Household → Storage overview → Shelves (tabs) → Products. A 5-tab bottom
bar (Dashboard / Storage / Households / Missing / Search) is the app's only navigation
surface — there is no drawer. Settings is reached via a gear icon in each top-level
screen's app bar.

## Design — B · Frost (D-021)
- Frosted-glass cards, icy-blue accent **#7dd3fc**, rounded controls, **Plus Jakarta Sans**.
- Full light/dark, switched in-app (System/Light/Dark) per Settings.
- Reference mocks in [`docs/design/`](./docs/design): `frost-app.html` (interactive 5-screen
  prototype with working light/dark toggle), `frost-dark.png`, `frost-light.png`. Build the
  Compose theme to match these.

## Scope guardrails — refuse to add
No expiry/reminders, recipes, shopping list, offline mode, roles/permissions.
**Phase 2 unlocked 2026-07-10** (user decision) and since shipped: barcode scanning,
the low-stock "running low" view, filter/sort, household color/icon theming, and the
live-updates client — see `ROADMAP.md` / `BACKLOG.md`.

## Conventions
- Explicit over magic; SRP; document the *why*.
- Unidirectional state; ViewModels expose immutable UI state; side-effects via events.
- Tests cover critical paths: auth/token handling, household scoping, stock actions,
  error/empty/offline states. No trivial UI tests.

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

## Status
Functionally-complete (MVP + Phase 2), CI-green, running against the **production
backend at `https://inventory.scuttle.dev/api/v1`**. The single-activity Compose +
Hilt + Retrofit app covers auth (email/password + Google, device-smoke-tested),
storage overview, shelves (tabs/pager), products (add/remove/move, detail, image),
search, invite (QR/join), settings, dashboard, missing-items, barcode scanning,
low-stock, and live updates — with EN + NL localization and the Frost theme. Covered by
JVM unit tests + instrumented flow tests (the latter run nightly on an emulator in CI).
Distribution is **debug builds only** (tag-driven GitHub prerelease APKs) — no store
presence yet. Forward-looking work lives in [`ROADMAP.md`](ROADMAP.md); shipped history
in [`BACKLOG.md`](BACKLOG.md).
