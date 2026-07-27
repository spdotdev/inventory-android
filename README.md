# inventory-android

Android client for the **Inventory** product — a private, multi-user, multi-household
home-stock manager. Sole API client of the
[`inventory-laravel`](https://github.com/spdotdev/inventory-laravel) backend
(production: `https://inventory.scuttle.dev/api/v1`).

> Status: **functionally complete (MVP + Phase 2), CI-green** — single-activity Jetpack
> Compose app (Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20) on the Frost theme, with Hilt DI
> and Retrofit networking. Auth (email/password + Google), storage/shelves/products
> (add/remove/move, detail, image), search, invite (QR/join), settings, dashboard,
> missing-items, barcode scanning, low-stock, and live updates are all built, in EN +
> NL, covered by unit tests + nightly instrumented flow tests. See
> [`ROADMAP.md`](ROADMAP.md) for what's next and [`BACKLOG.md`](BACKLOG.md) for shipped
> history.

## Build

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # build the debug APK
make install-hooks            # optional: pre-push runs tests locally
```

Requires JDK 17 and the Android SDK (compileSdk 34). CI runs wrapper validation, unit
tests, lint, and ktlint/detekt (baseline-gated) on every push/PR; instrumented flow
tests run nightly on an emulator. Security gates: `gitleaks` secret scanning on every
push/PR, CodeQL SAST on push/PR + weekly, and dependency review on PRs — see
[`SECURITY.md`](SECURITY.md).

## Distribution

The app is **deliberately debug-only** for now (a handful of private users, no store
presence):

- Pushing a `v*` tag builds a debug-signed `app-debug.apk` and attaches it to a GitHub
  **prerelease** — that is the distribution channel.
- Installed apps learn about new versions through the backend's **app-release feed**
  (`GET /api/v1/app-version`): an in-app update dialog on open plus a background
  notification, with in-app download + install. Publishing a release means both
  tagging *and* publishing a feed entry — see the release flow below.
- A Play Store path (release signing config + AAB) is intentionally deferred; see
  [`ROADMAP.md`](ROADMAP.md).

### Cutting a release

One command does the whole flow (since 2026-07-27):

```bash
scripts/release.sh <version> <changelog-file>   # or "-" for stdin; --dry-run supported
```

It bumps `versionCode`/`versionName`, commits, tags `v<version>`, pushes, waits for
the Release workflow to build the prerelease APK, then publishes the app-release feed
entry against the production admin API (via ssh to the host — the admin token never
lives on the dev machine) and verifies `GET /api/v1/app-version`. The Claude Code
`/release` skill (`.claude/skills/release/SKILL.md`) wraps this with a changelog
review gate and the known sharp edges.

Guard rails:

- The Release workflow **fails if the tag doesn't match `versionName`** in
  `app/build.gradle.kts` — a mislabeled APK silently mutes the in-app update prompt.
- All builds are signed with one **stable debug keystore**
  (`~/.android/inventory-debug.jks` locally; restored from repo secrets in CI —
  `signingConfigs.stableDebug` in `app/build.gradle.kts`). Local and CI signatures
  must match, or Android refuses the in-app update as a signature mismatch and
  testers have to uninstall/reinstall.

Release builds point `BASE_URL` at the production API; Google sign-in requires the
matching OAuth client ID on both the device config and the server
(`INVENTORY_GOOGLE_CLIENT_IDS`).

## Documentation

- App-specific planning: [`docs/`](./docs)
- Product spec — vision, data model, API contract (canonical for all repos):
  [`inventory-laravel/docs/`](https://github.com/spdotdev/inventory-laravel/tree/main/docs)
- Engineering working agreement: [`CLAUDE.md`](CLAUDE.md)
- Security policy / reporting a vulnerability: [`SECURITY.md`](SECURITY.md)

## Related repositories

- [`inventory-laravel`](https://github.com/spdotdev/inventory-laravel) — backend / API + canonical product docs
- [`inventory-mcp`](https://github.com/spdotdev/inventory-mcp) — standalone admin MCP server

## License

[MIT](./LICENSE)
