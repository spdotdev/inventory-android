# inventory-android

Android client for the **Inventory** product.

> Status: **project skeleton** — single-activity Jetpack Compose app (Gradle 8.9, AGP 8.5.2,
> Kotlin 2.0.20) with the Frost theme and a unit test. DI (Hilt), networking (Retrofit), and
> the auth/inventory screens land next — see [`ROADMAP.md`](ROADMAP.md).

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
