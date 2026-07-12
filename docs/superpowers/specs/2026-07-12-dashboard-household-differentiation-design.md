# Dashboard household differentiation — design

**Issue:** [#33](https://github.com/spdotdev/inventory-android/issues/33) — "Two households, two different colours on Dashboard to differentiate?"
**Date:** 2026-07-12
**Status:** approved

## Problem

The Dashboard is the only screen that aggregates *across* households, and it drops the
attribution while doing so. On a two-household account the tester sees:

- **Stat cards** (`4 Locations / 14 Shelves / 11 Products`) that silently sum both households.
- **Products by location** — `Freezer Downstairs`, `Freezer upstares`, `Bijkeuken`, `Kelder` in
  one flat list. Two households each with a "Freezer" are indistinguishable.
- **Running low** and **Favorites** — same flat treatment.

The issue title asks for "two different colours". Colour alone is the fix for *telling two rows
apart*; it is not a fix for *knowing which household a row belongs to* — for that the household
has to be named. So the design groups and names first, and uses colour to reinforce.

## What already exists (and is not being rebuilt)

`ui/theme/HouseholdTheme.kt` already provides a per-household accent + icon:

- 8 accents and 8 icons, keyed (`sky`, `teal`, … / `home`, `kitchen`, …), user-choosable via
  `HouseholdThemeDialog`, persisted server-side as keys, with a stable id-derived fallback.
- `HouseholdAvatar` — a round accent-tinted badge with the household's icon, already used by
  `HouseholdsScreen`.

The data layer already carries what the Dashboard needs: `HouseholdWithLocations` has
`name`/`color`/`icon`, and `LocationStats`, `LowStockItem` and `ShelfEntry` each carry a
`householdId`. **No API, DTO, or repository change is required.** This is a presentation fix.

## Design

### 1. Gate everything on more than one household

`DashboardUiState` gains `households: List<DashboardHousehold>` (`id`, `name`, `color`, `icon`),
mapped from `store.state.entries`, and a derived `showHouseholdAttribution = households.size > 1`.

When the user has a single household, the Dashboard renders exactly as it does today — no
headers, no badges, no caption. Most testers have one household and should not pay for a
two-household problem.

### 2. Group "Products by location" by household

One `FrostCard` per household (in `entries` order) instead of a single flat card. Each card
leads with a header row: `HouseholdAvatar` (24dp) + household name (`titleSmall`, medium
weight). Households with no locations get no card.

**The bar scale stays global.** `maxVal` is computed across *all* `locationStats`, not per card.
A per-card max would scale each household independently, so a household whose best location
holds 2 products would render a full-width bar next to another household's full-width bar
holding 9 — the chart would misrepresent the data it exists to show.

### 3. Badge the flat lists

Running-low rows and favorite location/shelf rows get a leading `HouseholdAvatar` (20dp).
`HouseholdAvatar` currently hardcodes `contentDescription = null`; it gains an optional
`contentDescription` parameter so these rows announce their household to TalkBack.

### 4. Stat cards stay aggregate, but say so

The numbers are unchanged. A caption below the row — "across 2 households" — is shown only when
`showHouseholdAttribution`. The sum stops being a silent one.

### 5. Colour: bars need a light-theme variant

The accent palette is a set of Tailwind-300 pastels (`#7DD3FC`, `#FCD34D`, …). Two different
uses with two different contrast requirements:

- **Avatar wash** — the accent at `alpha = 0.28` behind an `onSurface`-tinted icon. Legible in
  both themes today. **Unchanged.**
- **Bar fill** — a solid graphical object, which needs ≥3:1 against its `surfaceVariant` track.
  The pastels fail this on the light theme (amber `#FCD34D` on the light track `#DCECF6` is
  nowhere near 3:1).

Frost already solved exactly this for its own accent: `FrostAccent #7DD3FC` (dark) →
`FrostLightPrimary #2298BA` (light, commented "~4.5:1 on white"). Mirror that pattern with a
parallel light palette — the Tailwind-600 tone of each hue:

| key | dark (existing) | light (new) |
|--------|-----------|-----------|
| sky | `#7DD3FC` | `#0284C7` |
| teal | `#5EEAD4` | `#0D9488` |
| indigo | `#A5B4FC` | `#4F46E5` |
| pink | `#F9A8D4` | `#DB2777` |
| amber | `#FCD34D` | `#D97706` |
| green | `#86EFAC` | `#16A34A` |
| violet | `#C4B5FD` | `#7C3AED` |
| orange | `#FDBA74` | `#EA580C` |

Selecting between them needs the app's *resolved* dark flag — `isSystemInDarkTheme()` is wrong
here, because the app has its own System/Light/Dark setting and `InventoryTheme(darkTheme=…)` is
resolved by the caller. Add a `LocalFrostIsDark` CompositionLocal, provided in `InventoryTheme`
alongside the existing `LocalFrostCardColors`.

Colour is never the sole signal: every grouped card is headed by the household's **name**, and
every badged row carries the household's **icon**. A colour-blind user loses nothing.

### 6. Split the screen file

`DashboardScreen.kt` is 410 lines and this change adds to its densest section. Lift the
location chart into `ui/dashboard/DashboardLocationChart.kt` as part of the change. This is
scoped to the code being touched, not a general refactor.

## Testing

- **Unit (`DashboardViewModelTest`)** — `entries` map to `households`; `showHouseholdAttribution`
  is false for one household and true for two; grouping preserves store order.
- **Unit (palette guard)** — the light and dark accent tables have identical keys and sizes.
  `HouseholdThemeIndex` derives fallbacks by `id % HOUSEHOLD_ACCENT_COUNT`, so a table that
  drifts out of sync would silently mis-key or crash. Mirrors the existing count guard in
  `HouseholdThemeIndexTest`.
- **Instrumented (`DashboardFlowTest`)** — with two households, both names render as headers and
  each location appears under its own household; with one household, no header renders.

## Out of scope

- Changing the household theme picker, palette contents, or the server contract.
- The `Households` bottom-nav label truncating to "Household s" (visible in the issue
  screenshot) — a separate nav-bar bug, tracked separately.
