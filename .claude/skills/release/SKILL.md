---
name: release
description: Cut and publish an inventory-android release end-to-end. Use when the user says "prepare a release", "release 0.1.x", "cut a release", "ship a new version", or "tag a new version". Covers version bump, tag, prerelease APK, and the app-release feed entry that drives the in-app update prompt.
---

# /release — inventory-android release flow

One release = one run of `scripts/release.sh`, prepared and reviewed like this:

## 1. Preflight
- Working tree clean, on `main`, everything meant for this release already pushed (check `git log origin/main..main` is empty).
- Pick the version: patch-bump the latest `v*` tag unless the user named one.
- If backend changes shipped since the last release, confirm prod (sd-admin) is already on the matching inventory-laravel version — an APK that calls endpoints prod doesn't have yet ships broken.

## 2. Draft the changelog (MANDATORY review gate)
- Build it from `git log --oneline <last-tag>..HEAD`, written for TESTERS (features, not commit subjects).
- Rules: first line ≤ 100 chars (it is the notification text); NO `&` (the server HTML-escapes it into the update dialog); EN is fine (the dialog isn't localized).
- Show the user: chosen version, versionCode it will get, and the full changelog. Wait for explicit approval before running anything.

## 3. Run it
```bash
scripts/release.sh <version> <changelog-file>
```
- The script bumps versionCode/versionName, commits, tags, pushes, waits for the Release workflow, then publishes the feed entry **via ssh d051** (admin token lives only in `/opt/sd-admin/.env`, never locally). The ssh step may need the user present.
- If the session's permission mode blocks ssh, hand the user the single command to run with the `!` prefix instead — the script is idempotent per step (a re-run after the tag exists must instead PATCH the feed by hand; see §5).

## 4. Verify
- Script ends by printing `GET /api/v1/app-version` — confirm version_code/name/changelog/download_url are exactly what was approved.
- `./gradlew installDebug` on the connected device if the user wants it on their phone; `adb shell dumpsys package dev.scuttle.inventory | grep version` to confirm.

## 5. Known sharp edges
- **Stale feed drafts claim version codes** (422 "already been taken") — the script auto-PATCHes the existing entry; if doing it by hand, `GET /admin/app-releases`, find the id, PATCH it with the new fields + `"publish": true`.
- The Release workflow **fails if tag ≠ versionName** (guard added 2026-07-27 after a drift shipped a mislabeled APK). The script can't hit this (it sets both), but manual tags can.
- Testers on WhatsApp: after publishing, offer to draft the announcement (wa-send skill; Mother gets Dutch).
- **ONE-TIME signature transition (pending as of 2026-07-27):** the CI signing keystore was switched to the dev machine's `~/.android/inventory-debug.jks` (backed up off-machine). The FIRST release after v0.1.23 is signed differently from what testers have installed — in-app update will fail for them. That release's changelog MUST tell testers to uninstall and reinstall from the download link (server data is safe; device-local settings reset). Remove this bullet once that release has shipped.
