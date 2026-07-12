# Footer Nav Migration — Design

**Date:** 2026-07-12
**Status:** Approved

## Problem

The app has two navigation surfaces today:

- **Side drawer** (`AppDrawer.kt`, wired in `MainActivity.kt`): Dashboard, Storage/Home,
  Search (single-household only), Households, Missing Items (with badge), per-household
  location quick-jumps, per-household search icon (multi-household), Settings (pinned to
  bottom).
- **Bottom bar** (`MainActivity.kt` `bottomTabs`): Dashboard, Storage ("Home"), Search
  (hardcodes `firstHouseholdId`), Settings. Rendered only on these 4 top-level screens.

Households and Missing Items exist only in the drawer. The bottom bar's Search tab breaks
for multi-household users since it hardcodes the first household. This is confusing and
redundant — two nav systems covering overlapping ground.

## Design

### Remove the drawer entirely

Delete `ModalNavigationDrawer` wrapper, `DrawerState`/`rememberDrawerState`,
`openDrawer`/`closeDrawer`, `AppDrawer.kt`, `DrawerViewModel`. Remove the hamburger
`IconButton`/`Icons.Default.Menu` from each screen's `TopAppBar`:
`DashboardScreen`, `AllStoragesScreen`, `SettingsScreen`, `StorageOverviewScreen`,
`LocationDetailScreen`.

### Bottom bar — 5 tabs

Dashboard, Storage, Households, Missing Items (badge count carried over from the drawer's
existing badge logic), Search. The bar renders only on these 5 top-level screens — same
exclusion rule as today for detail/flow screens (`LOCATION`, `PRODUCT_DETAIL`, `SCANNER`,
`INVITE`, `STORAGE` per-household overview, `AUTH`, `FORGOT_PASSWORD`).

### Settings moves to the top app bar

A gear icon in the `TopAppBar`, shown consistently across all 5 top-level screens,
navigating to `SettingsScreen` (same destination as before — just a different entry
point, and no longer a bottom tab).

### Search tab — household picker for multi-household users

- Single household: tapping Search navigates straight to that household's search screen
  (unchanged from today).
- 2+ households: tapping Search opens a bottom-sheet picker listing the user's
  households; selecting one navigates to that household's search. The search screen
  itself is unchanged — only the entry point gains a selection step.

### Location quick-jumps — dropped

These were a drawer-only shortcut (dynamic list of locations per household). They're
dropped: reaching a location via Storage → household → location is one drill-down away,
and without the always-visible drawer there's no meaningfully faster path worth
preserving as separate UI.

### Unaffected destinations

`AUTH`, `FORGOT_PASSWORD`, `STORAGE` (per-household overview), `INVITE`, `LOCATION`,
`PRODUCT_DETAIL`, `SCANNER` — reached via in-content navigation, never had persistent
chrome, no change.

## Testing

- Update/replace existing drawer-related tests (`AppDrawer` composable tests, if any,
  and the `ModalNavigationDrawer` false-positive nav-state issue previously fixed in
  `FlowTestBase` per the 2026-07-09 testTag fix — confirm that fix's scope shrinks
  correctly once the drawer is gone, doesn't leave dead test infra).
- New instrumented coverage: bottom bar shows exactly the 5 correct tabs; badge count on
  Missing Items renders; Settings gear present on every top-level screen; multi-household
  Search opens the picker and navigates correctly; single-household Search skips the
  picker.
