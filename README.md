# inventory-android

Android client for the **Inventory** product.

> Status: **functionally-complete MVP, CI-green** — single-activity Jetpack Compose app
> (Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20) on the Frost theme, with Hilt DI and Retrofit
> networking. Auth (email/password + Google), storage/shelves/products (add/remove/move,
> detail, image), search, invite (QR/join), settings, dashboard, and missing-items are all
> built, in EN + NL, covered by unit tests + nightly instrumented flow tests. See
> [`ROADMAP.md`](ROADMAP.md) for what's next and [`BACKLOG.md`](BACKLOG.md) for shipped history.

## Build

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # build the debug APK
make install-hooks            # optional: pre-push runs tests locally
```

Requires JDK 17 and the Android SDK (compileSdk 34). CI runs wrapper validation + unit
tests + lint on every push/PR.

## Documentation

- App-specific planning: [`docs/`](./docs)
- Shared specs (product vision, data model, API contract): [`inventory-docs`](https://github.com/spdotdev/inventory-docs)

## Related repositories

- [`inventory-laravel`](https://github.com/spdotdev/inventory-laravel) — backend / API
- [`inventory-docs`](https://github.com/spdotdev/inventory-docs) — shared planning & specs

## License

[MIT](./LICENSE)
