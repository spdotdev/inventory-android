# Footer Nav Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the side navigation drawer entirely and make the bottom bar the app's
only navigation surface, growing it from 4 tabs (Dashboard, Storage, Search, Settings)
to 5 (Dashboard, Storage, Households, Missing Items, Search), with Settings moved to a
top-app-bar gear icon shown on every top-level screen.

**Architecture:** All changes are Compose UI + navigation wiring in
`inventory-android`. No new ViewModels — `DrawerViewModel` (kept, name unchanged; it's
an internal class name, not user-facing) remains the source of household/location data
for both the "Storage" tab and the new Search household-picker sheet. `MainActivity.kt`
owns the bottom bar, the household-picker `ModalBottomSheet`, and all navigation
wiring; each screen only gains/loses simple callback params and a `TopAppBar` icon.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Hilt, JUnit +
Compose UI testing (instrumented flow tests under `app/src/androidTest`).

## Global Constraints

- No offline cache, no local source of truth — unaffected by this change (spec:
  `docs/superpowers/specs/2026-07-12-footer-nav-migration-design.md`).
- Bottom bar renders only on the 5 top-level screens (Dashboard, Storage, Households,
  Missing Items, Search) — never on detail/flow screens (`STORAGE`, `LOCATION`,
  `PRODUCT_DETAIL`, `SCANNER`, `INVITE`, `AUTH`, `FORGOT_PASSWORD`, `SETTINGS`).
- EN + NL localization required for all new user-facing strings (existing convention:
  `app/src/main/res/values/strings.xml` + `app/src/main/res/values-nl/strings.xml`).
- No new abstractions beyond what's needed — reuse `DrawerViewModel`, don't rename it,
  don't introduce a new ViewModel for the household picker.

---

## Task 1: New string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml:33` (after `nav_settings`)
- Modify: `app/src/main/res/values-nl/strings.xml:33` (after `nav_settings`)
- Modify: `app/src/main/res/values/strings.xml:44` (after `action_open_menu`)
- Modify: `app/src/main/res/values-nl/strings.xml` (equivalent line)

**Interfaces:**
- Produces: `R.string.nav_households`, `R.string.nav_missing_items`,
  `R.string.action_settings`, `R.string.search_choose_household_title` — consumed by
  Tasks 2–10.

- [ ] **Step 1: Add the four new strings to `values/strings.xml`**

In `app/src/main/res/values/strings.xml`, after line 33 (`<string name="nav_settings">Settings</string>`), add:

```xml
    <string name="nav_households">Households</string>
    <string name="nav_missing_items">Missing</string>
```

After line 44 (`<string name="action_open_menu">Open menu</string>`), add:

```xml
    <string name="action_settings">Settings</string>
```

Near `search_field_label` or any other `search_*` string (search the file for
`search_field_label` to find the right neighborhood), add:

```xml
    <string name="search_choose_household_title">Search which household?</string>
```

- [ ] **Step 2: Add the Dutch translations to `values-nl/strings.xml`**

Same four insertions, Dutch text:

```xml
    <string name="nav_households">Huishoudens</string>
    <string name="nav_missing_items">Ontbrekend</string>
```

```xml
    <string name="action_settings">Instellingen</string>
```

```xml
    <string name="search_choose_household_title">In welk huishouden zoeken?</string>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL (no duplicate/missing resource errors)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-nl/strings.xml
git commit -m "feat: add strings for bottom-nav Households/Missing/Settings tabs"
```

---

## Task 2: AllStoragesScreen — settings gear, drop drawer, add location testTag

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/home/AllStoragesScreen.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` (Task 1).
- Produces: `AllStoragesScreen(onOpenSettings: () -> Unit, ...)` replacing
  `onOpenDrawer` — consumed by Task 11 (MainActivity wiring). Location cards now carry
  `testTag("home-location-${location.name}")` — consumed by Task 13.

- [ ] **Step 1: Replace `onOpenDrawer` with `onOpenSettings` in the signature**

In `AllStoragesScreen.kt`, change:

```kotlin
fun AllStoragesScreen(
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenStorage: (householdId: Long) -> Unit = {},
    localViewModel: AllStoragesViewModel = hiltViewModel(),
) {
```

to:

```kotlin
fun AllStoragesScreen(
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenStorage: (householdId: Long) -> Unit = {},
    localViewModel: AllStoragesViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Swap the hamburger icon for a settings gear**

Change the `TopAppBar`:

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.all_storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
```

to:

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.all_storage_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
```

- [ ] **Step 3: Add `import androidx.compose.material.icons.filled.Settings` and remove the now-unused `Menu` import**

Remove `import androidx.compose.material.icons.filled.Menu` (line 21).
Add `import androidx.compose.material.icons.filled.Settings` in its place
(alphabetical order among the `androidx.compose.material.icons.filled.*` imports).

- [ ] **Step 4: Tag each location card for test navigation**

There are two card variants for a location row (`hasWarning` and the normal case),
both around line 250–268. Change:

```kotlin
                                if (hasWarning) {
                                    Card(
                                        onClick = { onOpenLocation(entry.id, location.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.4f,
                                                    ),
                                            ),
                                        content = { rowContent() },
                                    )
                                } else {
                                    FrostCard(
                                        onClick = { onOpenLocation(entry.id, location.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        content = { rowContent() },
                                    )
                                }
```

to:

```kotlin
                                if (hasWarning) {
                                    Card(
                                        onClick = { onOpenLocation(entry.id, location.id) },
                                        modifier = Modifier.fillMaxWidth().testTag("home-location-${location.name}"),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.4f,
                                                    ),
                                            ),
                                        content = { rowContent() },
                                    )
                                } else {
                                    FrostCard(
                                        onClick = { onOpenLocation(entry.id, location.id) },
                                        modifier = Modifier.fillMaxWidth().testTag("home-location-${location.name}"),
                                        content = { rowContent() },
                                    )
                                }
```

Add `import androidx.compose.ui.platform.testTag` to the import list (it's not
currently imported in this file).

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`onOpenDrawer` callers won't compile yet — that's
expected until Task 11 rewires `MainActivity.kt`; this step just confirms
`AllStoragesScreen.kt` itself is syntactically valid in isolation is not possible
with a broken caller, so instead run the full build after Task 11. Skip this step
here and rely on Task 11's build check.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/home/AllStoragesScreen.kt
git commit -m "feat: AllStoragesScreen settings gear + location testTags (footer nav migration)"
```

---

## Task 3: DashboardScreen — settings gear, drop drawer

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` (Task 1).
- Produces: `DashboardScreen(onOpenSettings: () -> Unit, ...)` replacing
  `onOpenDrawer` — consumed by Task 11.

- [ ] **Step 1: Replace `onOpenDrawer` with `onOpenSettings` in the signature**

```kotlin
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenHouseholds: () -> Unit = {},
    onOpenMissingItems: () -> Unit = {},
    onOpenAllStorage: () -> Unit = {},
    onOpenSearch: (householdId: Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit = { _, _ -> },
    onOpenHouseholds: () -> Unit = {},
    onOpenMissingItems: () -> Unit = {},
    onOpenAllStorage: () -> Unit = {},
    onOpenSearch: (householdId: Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Swap the hamburger for a settings gear**

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.dashboard_title),
                        modifier = Modifier.testTag(DASHBOARD_TITLE_TEST_TAG),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
```

becomes:

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.dashboard_title),
                        modifier = Modifier.testTag(DASHBOARD_TITLE_TEST_TAG),
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
```

- [ ] **Step 3: Fix imports**

Remove `import androidx.compose.material.icons.filled.Menu`, add
`import androidx.compose.material.icons.filled.Settings` (alphabetical order).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/dashboard/DashboardScreen.kt
git commit -m "feat: DashboardScreen settings gear, drop drawer (footer nav migration)"
```

---

## Task 4: SettingsScreen — back arrow instead of hamburger

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Produces: `SettingsScreen(onBack: () -> Unit, ...)` replacing `onOpenDrawer` —
  consumed by Task 11 (`navController.popBackStack()`).

- [ ] **Step 1: Replace `onOpenDrawer` with `onBack` in the signature**

```kotlin
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenHouseholds: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    joinViewModel: JoinHouseholdViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenHouseholds: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    joinViewModel: JoinHouseholdViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Swap the hamburger icon for a back arrow**

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
            )
```

becomes:

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
```

- [ ] **Step 3: Fix imports**

Remove `import androidx.compose.material.icons.filled.Menu` if no longer used
elsewhere in the file (check with `grep -n "Icons.Default.Menu"
SettingsScreen.kt` first). Add
`import androidx.compose.material.icons.automirrored.filled.ArrowBack` if not
already present (check with `grep -n "AutoMirrored.Filled.ArrowBack"
SettingsScreen.kt` — other screens in this codebase already import it this way, e.g.
`StorageOverviewScreen.kt`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/settings/SettingsScreen.kt
git commit -m "feat: SettingsScreen back arrow instead of drawer hamburger"
```

---

## Task 5: StorageOverviewScreen — drop the drawer icon (no replacement)

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/storage/StorageOverviewScreen.kt`

**Interfaces:**
- Produces: `StorageOverviewScreen(...)` with `onOpenDrawer` removed entirely —
  consumed by Task 11.

This screen is not a bottom-nav destination (per-household overview, reached by
drilling into a household from the Storage tab) and never had a settings gear — it
just loses the dead hamburger icon.

- [ ] **Step 1: Remove `onOpenDrawer` from the signature**

```kotlin
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun StorageOverviewScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenLocation: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: StorageOverviewViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Remove the hamburger `IconButton` from `actions`**

```kotlin
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.storage_overview_search_cd),
                        )
                    }
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                },
```

becomes:

```kotlin
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.storage_overview_search_cd),
                        )
                    }
                },
```

- [ ] **Step 3: Remove the now-unused `Menu` import**

Remove `import androidx.compose.material.icons.filled.Menu`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/storage/StorageOverviewScreen.kt
git commit -m "chore: drop dead drawer icon from StorageOverviewScreen"
```

---

## Task 6: LocationDetailScreen — drop the drawer icon (no replacement)

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/location/LocationDetailScreen.kt`

**Interfaces:**
- Produces: `LocationDetailScreen(...)` with `onOpenDrawer` removed entirely —
  consumed by Task 11.

- [ ] **Step 1: Remove `onOpenDrawer` from the signature**

```kotlin
fun LocationDetailScreen(
    householdId: Long,
    locationId: Long,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    onOpenScanner: () -> Unit = {},
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun LocationDetailScreen(
    householdId: Long,
    locationId: Long,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    onOpenScanner: () -> Unit = {},
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Remove the hamburger `IconButton` from the non-delete-mode `actions` branch**

```kotlin
                    } else {
                        if (state.shelves.isNotEmpty()) {
                            IconButton(onClick = shelvesViewModel::enterDeleteMode) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.location_delete_shelves_cd))
                            }
                        }
                        IconButton(onClick = { showAddShelfSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_shelf_cd))
                        }
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                        }
                    }
```

becomes:

```kotlin
                    } else {
                        if (state.shelves.isNotEmpty()) {
                            IconButton(onClick = shelvesViewModel::enterDeleteMode) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.location_delete_shelves_cd))
                            }
                        }
                        IconButton(onClick = { showAddShelfSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.location_add_shelf_cd))
                        }
                    }
```

- [ ] **Step 3: Remove the now-unused `Menu` import**

Remove `import androidx.compose.material.icons.filled.Menu`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/location/LocationDetailScreen.kt
git commit -m "chore: drop dead drawer icon from LocationDetailScreen"
```

---

## Task 7: HouseholdsScreen — add settings gear

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/households/HouseholdsScreen.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` (Task 1).
- Produces: `HouseholdsScreen(onOpenSettings: () -> Unit, ...)` (new param, `onBack`
  stays — it's still pushed from Dashboard/Settings in addition to being a bottom
  tab) — consumed by Task 11.

- [ ] **Step 1: Add `onOpenSettings` to the signature**

```kotlin
fun HouseholdsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenInvite: (householdId: Long, householdName: String) -> Unit = { _, _ -> },
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun HouseholdsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenInvite: (householdId: Long, householdName: String) -> Unit = { _, _ -> },
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Add the gear to `actions`**

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.households_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
```

becomes:

```kotlin
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.households_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
```

- [ ] **Step 3: Add the `Settings` icon import**

Add `import androidx.compose.material.icons.filled.Settings` (alphabetical order
among existing `androidx.compose.material.icons.filled.*` imports; check the file
doesn't already import it under a different alias first with
`grep -n "icons.filled.Settings" HouseholdsScreen.kt`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/households/HouseholdsScreen.kt
git commit -m "feat: HouseholdsScreen settings gear (footer nav migration)"
```

---

## Task 8: MissingItemsScreen — add settings gear

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/missing/MissingItemsScreen.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` (Task 1).
- Produces: `MissingItemsScreen(onOpenSettings: () -> Unit, ...)` — consumed by
  Task 11.

- [ ] **Step 1: Add `onOpenSettings` to the signature**

```kotlin
fun MissingItemsScreen(
    onBack: () -> Unit,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
    viewModel: MissingItemsViewModel = hiltViewModel(),
)
```

becomes:

```kotlin
fun MissingItemsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
    viewModel: MissingItemsViewModel = hiltViewModel(),
)
```

- [ ] **Step 2: Add the gear next to the existing warning icon in `actions`**

The current `actions` block (lines 60+) starts:

```kotlin
                actions = {
                    if (state.items.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.missing_items_warning_cd),
```

Read the rest of that `actions` block in the file first (it continues past what's
shown here), then append a settings `IconButton` as the last child of `actions`,
after the existing warning `Icon`'s closing brace:

```kotlin
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
```

- [ ] **Step 3: Add the `Settings` icon import**

Add `import androidx.compose.material.icons.filled.Settings` and
`import androidx.compose.material3.IconButton` if not already imported (check
first with `grep -n "^import androidx.compose.material3.IconButton"
MissingItemsScreen.kt`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/missing/MissingItemsScreen.kt
git commit -m "feat: MissingItemsScreen settings gear (footer nav migration)"
```

---

## Task 9: SearchScreen — add settings gear next to the back button

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/ui/search/SearchScreen.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` (Task 1).
- Produces: `SearchScreen(onOpenSettings: () -> Unit, ...)` — consumed by Task 11.

`SearchScreen` has no `TopAppBar` today — just a `TextButton` "back" as the first
child of its `Column`. Turn that into a `Row` with the back button and a settings
gear.

- [ ] **Step 1: Add `onOpenSettings` to the signature**

```kotlin
fun SearchScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
) {
```

becomes:

```kotlin
fun SearchScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProduct: (householdId: Long, shelfId: Long, productId: Long) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Replace the bare back button with a back+settings row**

```kotlin
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.search_back_button))
        }
```

becomes:

```kotlin
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.search_back_button))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        }
```

- [ ] **Step 3: Add the missing imports**

Add:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```
`Arrangement` and `Column`/`Row`-related layout imports are already present
(`androidx.compose.foundation.layout.Arrangement` is imported; `Row` is not — add
`import androidx.compose.foundation.layout.Row`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/ui/search/SearchScreen.kt
git commit -m "feat: SearchScreen settings gear (footer nav migration)"
```

---

## Task 10: Delete AppDrawer.kt

**Files:**
- Delete: `app/src/main/java/dev/scuttle/inventory/ui/app/AppDrawer.kt`

**Interfaces:**
- Consumes: nothing — this file becomes dead code once Task 11 removes its only
  caller (`MainActivity.kt`'s `ModalNavigationDrawer` block).

Do this task *after* Task 11 (so the caller is already gone and the deletion is a
clean no-op change, not a compile break). It's listed here for file-organization
clarity but executed last, right before Task 12.

- [ ] **Step 1: Confirm no remaining references**

Run: `grep -rn "AppDrawer" app/src/main/java app/src/androidTest`
Expected: no matches (after Task 11 lands).

- [ ] **Step 2: Delete the file**

```bash
git rm app/src/main/java/dev/scuttle/inventory/ui/app/AppDrawer.kt
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: delete AppDrawer.kt (superseded by bottom nav)"
```

---

## Task 11: MainActivity — 5-tab bottom bar, drop the drawer, household picker

**Files:**
- Modify: `app/src/main/java/dev/scuttle/inventory/MainActivity.kt`

**Interfaces:**
- Consumes: `AllStoragesScreen(onOpenSettings, ...)` (Task 2),
  `DashboardScreen(onOpenSettings, ...)` (Task 3), `SettingsScreen(onBack, ...)`
  (Task 4), `StorageOverviewScreen(...)` without `onOpenDrawer` (Task 5),
  `LocationDetailScreen(...)` without `onOpenDrawer` (Task 6),
  `HouseholdsScreen(onOpenSettings, ...)` (Task 7),
  `MissingItemsScreen(onOpenSettings, ...)` (Task 8),
  `SearchScreen(onOpenSettings, ...)` (Task 9), `R.string.nav_households`,
  `R.string.nav_missing_items`, `R.string.search_choose_household_title` (Task 1).
- Produces: bottom-nav test tags `bottom-nav-dashboard`, `bottom-nav-home`,
  `bottom-nav-households`, `bottom-nav-missing-items`, `bottom-nav-search` — consumed
  by Tasks 12–15.

This is the largest single change: remove `ModalNavigationDrawer`/`DrawerState`,
grow `bottomTabs` to 5 entries with a stable `key`, add the missing-items badge, and
add a household-picker `ModalBottomSheet` for the Search tab.

- [ ] **Step 1: Trim imports**

Remove (no longer used once the drawer is gone):
```kotlin
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import dev.scuttle.inventory.ui.app.AppDrawer
import kotlinx.coroutines.launch
```
Add:
```kotlin
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
```
(`mutableStateOf`/`getValue`/`setValue`/`remember` are already imported — verify
with `grep -n "^import androidx.compose.runtime" MainActivity.kt` before adding
duplicates.)

- [ ] **Step 2: Add `HOUSEHOLDS` route reuse and extend `BottomTab`**

`Routes.HOUSEHOLDS` and `Routes.MISSING_ITEMS` already exist — no new route
constants needed. Change the `BottomTab` data class:

```kotlin
private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
```

to:

```kotlin
private data class BottomTab(
    val key: String,
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
```

- [ ] **Step 3: Remove the drawer state and `ModalNavigationDrawer` wrapper**

In `InventoryNavHost`, remove:

```kotlin
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
```

Also remove `drawerViewModel.refresh()` is called from the auth `LaunchedEffect` —
**keep that line**, it's unrelated to the drawer UI (it refreshes household data on
login, still needed for the bottom bar / picker).

Replace the whole:

```kotlin
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                viewModel = drawerViewModel,
                onNavigateHome = { ... },
                ... // all the onNavigate* callbacks
            )
        },
        gesturesEnabled = authState.authenticated,
    ) {
        // Bottom navigation (UX wave 2): ...
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val drawerUi by drawerViewModel.state.collectAsState()
        val firstHouseholdId = drawerUi.entries.firstOrNull()?.id
        val bottomTabs =
            listOf(
                BottomTab(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
                BottomTab(Routes.HOME, R.string.nav_storage, Icons.Filled.Home),
                BottomTab(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
                BottomTab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
            )
        Scaffold(
            ...
        ) { scaffoldPadding ->
        NavHost(
            ...
        )
        }
    }
```

with (everything that was previously *inside* the `ModalNavigationDrawer`'s content
lambda now lives directly in `InventoryNavHost`'s body, unindented one level):

```kotlin
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerUi by drawerViewModel.state.collectAsState()
    var showHouseholdPicker by remember { mutableStateOf(false) }
    val bottomTabs =
        listOf(
            BottomTab("dashboard", Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
            BottomTab("home", Routes.HOME, R.string.nav_storage, Icons.Filled.Home),
            BottomTab("households", Routes.HOUSEHOLDS, R.string.nav_households, Icons.Filled.People),
            BottomTab("missing-items", Routes.MISSING_ITEMS, R.string.nav_missing_items, Icons.Filled.Warning),
            BottomTab("search", Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
        )
    val onOpenSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (tab.key == "search") {
                                    val entries = drawerUi.entries
                                    when {
                                        entries.size == 1 ->
                                            navController.navigate(Routes.search(entries.first().id)) {
                                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        entries.size > 1 -> showHouseholdPicker = true
                                        else -> Unit
                                    }
                                } else {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (tab.key == "missing-items" && drawerUi.missingItemCount > 0) {
                                    BadgedBox(badge = { Badge { Text("${drawerUi.missingItemCount}") } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("bottom-nav-${tab.key}"),
                        )
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        if (showHouseholdPicker) {
            ModalBottomSheet(onDismissRequest = { showHouseholdPicker = false }) {
                Text(
                    stringResource(R.string.search_choose_household_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                drawerUi.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.name) },
                        selected = false,
                        onClick = {
                            showHouseholdPicker = false
                            navController.navigate(Routes.search(entry.id)) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("household-picker-${entry.name}"),
                    )
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = Routes.AUTH,
            modifier = Modifier.padding(scaffoldPadding),
        ) {
```

Note: `NavigationDrawerItem` and `MaterialTheme` need their imports kept/added —
`MaterialTheme` likely isn't imported in this file yet (`grep -n "^import
androidx.compose.material3.MaterialTheme" MainActivity.kt` to check); add
`import androidx.compose.material3.MaterialTheme` and
`import androidx.compose.material3.NavigationDrawerItem` and
`import androidx.compose.foundation.layout.padding` if missing (padding is likely
already imported).

The closing brace structure: the old code had two closing braces at the very end
(`}` for the `Scaffold` trailing lambda's `NavHost`, `}` for the
`ModalNavigationDrawer` content lambda). Now there's one fewer nesting level —
make sure the final `}` count in `InventoryNavHost` matches (one `}` closes the
`NavHost` composable's builder lambda, one `}` closes the `Scaffold` trailing
lambda, one `}` closes `InventoryNavHost` itself — same as before minus the
`ModalNavigationDrawer` layer).

- [ ] **Step 4: Update the screen call sites for the changed params**

```kotlin
            composable(Routes.HOME) {
                AllStoragesScreen(
                    viewModel = drawerViewModel,
                    onOpenDrawer = openDrawer,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenStorage = { hhId ->
                        navController.navigate(Routes.storage(hhId))
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenDrawer = openDrawer,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenAllStorage = {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    },
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    onOpenMissingItems = { navController.navigate(Routes.MISSING_ITEMS) { launchSingleTop = true } },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenDrawer = openDrawer,
                    onSignOut = { authViewModel.signOut() },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    themeViewModel = themeViewModel,
                )
            }

            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInvite = { id, name -> navController.navigate(Routes.invite(id, name)) },
                )
            }
```

becomes:

```kotlin
            composable(Routes.HOME) {
                AllStoragesScreen(
                    viewModel = drawerViewModel,
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenStorage = { hhId ->
                        navController.navigate(Routes.storage(hhId))
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                    onOpenAllStorage = {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    },
                    onOpenSearch = { hhId ->
                        navController.navigate(Routes.search(hhId)) { launchSingleTop = true }
                    },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    onOpenMissingItems = { navController.navigate(Routes.MISSING_ITEMS) { launchSingleTop = true } },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignOut = { authViewModel.signOut() },
                    onOpenHouseholds = { navController.navigate(Routes.HOUSEHOLDS) },
                    themeViewModel = themeViewModel,
                )
            }

            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenInvite = { id, name -> navController.navigate(Routes.invite(id, name)) },
                )
            }
```

- [ ] **Step 5: Update `STORAGE`, `SEARCH`, `LOCATION`, `MISSING_ITEMS` composables**

```kotlin
            composable(
                route = Routes.STORAGE,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                StorageOverviewScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
                    onOpenDrawer = openDrawer,
                    onOpenLocation = { navController.navigate(Routes.location(householdId, it)) },
                    onOpenSearch = { navController.navigate(Routes.search(householdId)) },
                )
            }

            composable(
                route = Routes.SEARCH,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                SearchScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                )
            }
```

becomes:

```kotlin
            composable(
                route = Routes.STORAGE,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                StorageOverviewScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
                    onOpenLocation = { navController.navigate(Routes.location(householdId, it)) },
                    onOpenSearch = { navController.navigate(Routes.search(householdId)) },
                )
            }

            composable(
                route = Routes.SEARCH,
                arguments = listOf(navArgument("householdId") { type = NavType.LongType }),
            ) { entry ->
                val householdId = entry.arguments?.getLong("householdId") ?: return@composable
                SearchScreen(
                    householdId = householdId,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                )
            }
```

And:

```kotlin
                LocationDetailScreen(
                    householdId = householdId,
                    locationId = locationId,
                    drawerViewModel = drawerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDrawer = openDrawer,
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                    onOpenScanner = { navController.navigate(Routes.SCANNER) },
                    scannedCode = scannedCode,
                    onScannedCodeConsumed = { entry.savedStateHandle["scanned_code"] = null },
                )
```

becomes:

```kotlin
                LocationDetailScreen(
                    householdId = householdId,
                    locationId = locationId,
                    drawerViewModel = drawerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { hhId, shelfId, productId ->
                        navController.navigate(Routes.productDetail(hhId, shelfId, productId))
                    },
                    onOpenScanner = { navController.navigate(Routes.SCANNER) },
                    scannedCode = scannedCode,
                    onScannedCodeConsumed = { entry.savedStateHandle["scanned_code"] = null },
                )
```

And:

```kotlin
            composable(Routes.MISSING_ITEMS) {
                MissingItemsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                )
            }
```

becomes:

```kotlin
            composable(Routes.MISSING_ITEMS) {
                MissingItemsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings,
                    onOpenLocation = { hhId, locId ->
                        navController.navigate(Routes.location(hhId, locId))
                    },
                )
            }
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any remaining reference to `openDrawer`,
`closeDrawer`, `drawerState`, or `AppDrawer` the compiler flags.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/scuttle/inventory/MainActivity.kt
git commit -m "feat: 5-tab bottom bar, drop side drawer, household-picker for Search"
```

---

## Task 12: Execute Task 10 (delete AppDrawer.kt) now that its caller is gone

Run Task 10's four steps now — they were sequenced here because Task 11 had to
land first.

---

## Task 13: Migrate the 12 location-quick-jump instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/AddShelfFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/StorageToProductFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteProductFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/EditProductDetailFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyShelfFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteShelfFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/MoveProductFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/MissingItemsFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/AddProductFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/QuantityFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/DashboardLocationCardFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/MandatoryToggleFlowTest.kt`

**Interfaces:**
- Consumes: `bottom-nav-home` testTag (Task 11), `home-location-Fridge` testTag
  (Task 2).

Every one of these 12 files has the identical 3-line pattern (surrounding lines
differ slightly per file — mock-route setup lines sit between the `waitUntil` and
the `.performClick()`, keep those lines untouched, only change the three shown):

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-location-Fridge"), timeoutMillis = 8_000)
```
...
```kotlin
            onNodeWithTag("drawer-location-Fridge").performClick()
```

- [ ] **Step 1: Apply the same two-line replacement to all 12 files**

In each of the 12 files listed above, change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-location-Fridge"), timeoutMillis = 8_000)
```

to:

```kotlin
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)
```

and change:

```kotlin
            onNodeWithTag("drawer-location-Fridge").performClick()
```

to:

```kotlin
            onNodeWithTag("home-location-Fridge").performClick()
```

(Leave every other line — mock route registrations, `Thread.sleep`, comments —
untouched. Update any comment that says `// Drawer → Fridge → ...` or similar to
`// Storage tab → Fridge → ...` while you're in each file, for accuracy, but this
is cosmetic and not required for the test to pass.)

- [ ] **Step 2: Remove now-unused `onNodeWithContentDescription` import if applicable**

For any of the 12 files where `onNodeWithContentDescription` is no longer used
anywhere else in the file (check with `grep -n
"onNodeWithContentDescription" <file>` — most files use it elsewhere for other
icons, e.g. "Add product", so don't remove blindly), remove the import if the
grep shows zero remaining usages.

- [ ] **Step 3: Run each test individually if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.AddProductFlowTest"`
(repeat per class name for the other 11)
Expected: BUILD SUCCESSFUL, 1 test passed, for each class.

If no local emulator is available, skip this step and rely on Task 17's full CI
run — note that in the commit message.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/AddShelfFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/StorageToProductFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteProductFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/EditProductDetailFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyShelfFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteShelfFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/MoveProductFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/MissingItemsFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/AddProductFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/QuantityFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/DashboardLocationCardFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/MandatoryToggleFlowTest.kt
git commit -m "test: migrate 12 location-quick-jump flow tests from drawer to Storage tab"
```

---

## Task 14: Migrate the 3 "All storage" text-click instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteLocationFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyStorageFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/CreateLocationFlowTest.kt`

**Interfaces:**
- Consumes: `bottom-nav-home` testTag (Task 11).

- [ ] **Step 1: Replace the "All storage" text-click with a bottom-tab tap**

In all 3 files, change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("All storage").and(hasClickAction()), timeoutMillis = 5_000)

            onAllNodesWithText("All storage").filterToOne(hasClickAction()).performClick()
```

(note: `DeleteLocationFlowTest.kt` and `EmptyStorageFlowTest.kt` have the
`mockServer.route(...)` line positioned differently relative to these 3 lines in
some cases — check each file's exact surrounding lines with `Read` before editing,
since some have `mockServer.route("/households/1/locations", ...)` interleaved.
Only change the `onNodeWithContentDescription`/`waitUntilAtLeastOneExists`/
`onAllNodesWithText` lines shown, leave any interleaved mock route lines exactly
where they are.)

to:

```kotlin
            onNodeWithTag("bottom-nav-home").performClick()
            waitForIdle()
```

If `onNodeWithTag` isn't already imported in a given file, add
`import androidx.compose.ui.test.onNodeWithTag`.

- [ ] **Step 2: Remove now-unused imports where applicable**

Check `hasText`, `hasClickAction`, `onAllNodesWithText`,
`onNodeWithContentDescription` are still used elsewhere in each file before
removing their imports (most are — these files use them for other assertions
further down).

- [ ] **Step 3: Run each test if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.DeleteLocationFlowTest"`
(repeat for `EmptyStorageFlowTest`, `CreateLocationFlowTest`)
Expected: BUILD SUCCESSFUL, 1 test passed, for each.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/DeleteLocationFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyStorageFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/CreateLocationFlowTest.kt
git commit -m "test: migrate 3 'All storage' flow tests from drawer to Storage tab"
```

---

## Task 15: Migrate the 3 "Households" text-click instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/InviteFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyHouseholdsFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/LeaveHouseholdFlowTest.kt`

**Interfaces:**
- Consumes: `bottom-nav-households` testTag (Task 11).

- [ ] **Step 1: Replace the "Households" text-click with a bottom-tab tap**

In all 3 files, change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Households").and(hasClickAction()), timeoutMillis = 5_000)
```

(keep whatever `mockServer.route(...)` line comes next — e.g.
`mockServer.route("/households", fixture("households_one.json"))` — untouched, and
keep the following `onAllNodesWithText("Households").filterToOne(hasClickAction()).performClick()`
line, but redirect it too, see below)

to:

```kotlin
            onNodeWithTag("bottom-nav-households").performClick()
```

and change the following line:

```kotlin
            onAllNodesWithText("Households").filterToOne(hasClickAction()).performClick()
```

Delete it entirely — the bottom-tab tap above already navigates to the Households
screen in one step (unlike the old drawer flow, which opened the drawer *then*
required a second click on the "Households" item).

- [ ] **Step 2: Add `onNodeWithTag` import if missing**

`grep -n "import androidx.compose.ui.test.onNodeWithTag"` each of the 3 files;
add if absent.

- [ ] **Step 3: Run each test if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.InviteFlowTest"`
(repeat for `EmptyHouseholdsFlowTest`, `LeaveHouseholdFlowTest`)
Expected: BUILD SUCCESSFUL, 1 test passed, for each.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/InviteFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/EmptyHouseholdsFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/LeaveHouseholdFlowTest.kt
git commit -m "test: migrate 3 Households flow tests from drawer to bottom tab"
```

---

## Task 16: Migrate the "Missing items" text-click instrumented test

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/MissingItemsEmptyFlowTest.kt`

**Interfaces:**
- Consumes: `bottom-nav-missing-items` testTag (Task 11).

- [ ] **Step 1: Replace the "Missing items" text-click with a bottom-tab tap**

Change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Missing items").and(hasClickAction()), timeoutMillis = 5_000)

            onAllNodesWithText("Missing items").filterToOne(hasClickAction()).performClick()
```

to:

```kotlin
            onNodeWithTag("bottom-nav-missing-items").performClick()
            waitForIdle()
```

- [ ] **Step 2: Add `onNodeWithTag` import if missing**

`grep -n "import androidx.compose.ui.test.onNodeWithTag" MissingItemsEmptyFlowTest.kt`

- [ ] **Step 3: Run the test if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.MissingItemsEmptyFlowTest"`
Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/MissingItemsEmptyFlowTest.kt
git commit -m "test: migrate Missing Items empty-state flow test to bottom tab"
```

---

## Task 17: Migrate the 2 search instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/SearchFlowTest.kt` (two `@Test` methods)
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/SearchNoResultsFlowTest.kt`

**Interfaces:**
- Consumes: `bottom-nav-search` testTag (Task 11).

Both fixtures used by these tests (`households_one.json`) mean the app has exactly
one household, so the bottom-nav Search tab navigates straight to search — no
household picker involved.

- [ ] **Step 1: `SearchFlowTest.kt` — `clicking_search_result_navigates_to_product_detail`**

Change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-search"), timeoutMillis = 5_000)

            mockServer.route("/households/1/search", fixture("search_results.json"))
            onNodeWithTag("drawer-nav-search").performClick()
            waitUntilAtLeastOneExists(hasTestTag("search_field"), timeoutMillis = 5_000)
```

to:

```kotlin
            mockServer.route("/households/1/search", fixture("search_results.json"))
            onNodeWithTag("bottom-nav-search").performClick()
            waitUntilAtLeastOneExists(hasTestTag("search_field"), timeoutMillis = 5_000)
```

- [ ] **Step 2: `SearchFlowTest.kt` — `search_returns_matching_product`**

Change:

```kotlin
            // Open drawer → Search
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-search"), timeoutMillis = 5_000)

            mockServer.route("/households/1/search", fixture("search_results.json"))
            onNodeWithTag("drawer-nav-search").performClick()
            waitForIdle()
```

to:

```kotlin
            mockServer.route("/households/1/search", fixture("search_results.json"))
            onNodeWithTag("bottom-nav-search").performClick()
            waitForIdle()
```

- [ ] **Step 3: `SearchNoResultsFlowTest.kt`**

Change:

```kotlin
            // Drawer → Search
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-search"), timeoutMillis = 5_000)
            mockServer.route("/households", fixture("households_one.json"))
            onNodeWithTag("drawer-nav-search").performClick()
            waitForIdle()
```

to:

```kotlin
            mockServer.route("/households", fixture("households_one.json"))
            onNodeWithTag("bottom-nav-search").performClick()
            waitForIdle()
```

- [ ] **Step 4: Remove `onNodeWithContentDescription` import from these 2 files if unused elsewhere**

`grep -n "onNodeWithContentDescription"` each file; remove the import only if the
grep shows zero remaining usages.

- [ ] **Step 5: Run both test classes if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.SearchFlowTest"`
Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.SearchNoResultsFlowTest"`
Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/SearchFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/SearchNoResultsFlowTest.kt
git commit -m "test: migrate search flow tests from drawer to bottom tab"
```

---

## Task 18: Migrate the 2 settings instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/LogoutFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/scuttle/inventory/flow/JoinHouseholdFlowTest.kt`

**Interfaces:**
- Consumes: `R.string.action_settings` resolved content description ("Settings")
  from the new gear icon on `DashboardScreen` (Task 3).

- [ ] **Step 1: `LogoutFlowTest.kt`**

Change:

```kotlin
            // Open drawer → Settings
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-settings"), timeoutMillis = 5_000)
            onNodeWithTag("drawer-nav-settings").performClick()
            waitForIdle()
```

to:

```kotlin
            // Dashboard's settings gear → SettingsScreen
            onNodeWithContentDescription("Settings").performClick()
            waitForIdle()
```

- [ ] **Step 2: `JoinHouseholdFlowTest.kt`**

Change:

```kotlin
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-settings"), timeoutMillis = 5_000)
            onNodeWithTag("drawer-nav-settings").performClick()
```

to:

```kotlin
            onNodeWithContentDescription("Settings").performClick()
```

(check what immediately follows in the original file — likely a `waitForIdle()` on
its own next line; leave it as-is.)

- [ ] **Step 3: Remove `hasTestTag`/`onNodeWithTag` imports from these 2 files if unused elsewhere**

`grep -n "hasTestTag\|onNodeWithTag"` each file; both are very likely used
elsewhere in each test (e.g. `DASHBOARD_TITLE_TEST_TAG`), so this step will
probably be a no-op — verify, don't remove blindly.

- [ ] **Step 4: Run both tests if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.LogoutFlowTest"`
Run: `./gradlew connectedDebugAndroidTest --tests "dev.scuttle.inventory.flow.JoinHouseholdFlowTest"`
Expected: BUILD SUCCESSFUL, all tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/dev/scuttle/inventory/flow/LogoutFlowTest.kt \
        app/src/androidTest/java/dev/scuttle/inventory/flow/JoinHouseholdFlowTest.kt
git commit -m "test: migrate settings flow tests from drawer to top-bar gear icon"
```

---

## Task 19: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, `authRedirectFor` and all other unit tests pass
unchanged (this migration touches no pure logic covered by JVM unit tests).

- [ ] **Step 2: Run Android lint**

Run: `./gradlew lint`
Expected: BUILD SUCCESSFUL, no new warnings introduced (unused-resource warnings
for anything still referencing `action_open_menu`/`drawer_*` strings — if lint
flags those as now-unused, that's expected and fine; they can stay in
`strings.xml` as harmless dead entries, or be removed as a follow-up cleanup, not
required for this plan).

- [ ] **Step 3: Run the full instrumented suite if an emulator is available**

Run: `./gradlew connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, all flow tests pass. If no local emulator, push and
let the `instrumented.yml` CI workflow run it (nightly / on-demand per existing
project convention) — note this explicitly to the user rather than claiming
verification that didn't happen.

- [ ] **Step 4: Manual smoke check (if a device/emulator with the debug APK is available)**

Launch the app, sign in, and click through: Dashboard → Storage → Households →
Missing Items → Search (single household, direct nav) and, if a test/dev account
with 2+ households exists, Search (multi-household, picker sheet appears) →
Settings gear from each of the 5 tabs → back arrow returns correctly. If no
device is available, state that this step was skipped rather than claiming it
passed.

- [ ] **Step 5: Final commit (if any cleanup fell out of verification)**

Only if Steps 1–4 surfaced something to fix — otherwise this task produces no
commit, verification-only.
