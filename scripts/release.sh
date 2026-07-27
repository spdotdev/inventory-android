#!/usr/bin/env bash
# release.sh — one-command release for inventory-android.
#
# Usage:
#   scripts/release.sh <version> <changelog-file>     e.g. scripts/release.sh 0.1.23 /tmp/notes.md
#   scripts/release.sh <version> -                    changelog from stdin
#   scripts/release.sh --dry-run <version> <file>     print what would happen, change nothing
#
# Does, in order:
#   1. sanity: on main, clean tree, gh authenticated, version not already tagged
#   2. bumps versionCode (+1) and sets versionName in app/build.gradle.kts, commits, pushes
#   3. tags v<version> and pushes the tag (Release workflow builds the prerelease APK;
#      the workflow itself re-checks tag == versionName and fails loudly on drift)
#   4. waits for the Release workflow to go green
#   5. publishes the app-release feed entry via ssh d051 (the admin token lives ONLY in
#      d051:/opt/sd-admin/.env — it never touches this machine). If the version_code is
#      already claimed by a stale draft, that entry is PATCHed and published instead.
#
# Changelog notes: first line <= 100 chars (it becomes the push notification);
# avoid "&" (the server HTML-escapes it into the update dialog).
set -euo pipefail

DRY=0
if [ "${1:-}" = "--dry-run" ]; then DRY=1; shift; fi
VERSION="${1:?usage: release.sh [--dry-run] <version> <changelog-file|->}"
CHANGELOG_SRC="${2:?usage: release.sh [--dry-run] <version> <changelog-file|->}"

cd "$(git rev-parse --show-toplevel)"
GRADLE=app/build.gradle.kts
API=https://inventory.scuttle.dev/api/v1
APK_URL="https://github.com/spdotdev/inventory-android/releases/download/v${VERSION}/app-debug.apk"

[ "$(git branch --show-current)" = "main" ] || { echo "not on main"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "working tree not clean"; exit 1; }
gh auth status >/dev/null || { echo "gh not authenticated"; exit 1; }
if git rev-parse "v$VERSION" >/dev/null 2>&1; then echo "tag v$VERSION already exists"; exit 1; fi

if [ "$CHANGELOG_SRC" = "-" ]; then CHANGELOG="$(cat)"; else CHANGELOG="$(cat "$CHANGELOG_SRC")"; fi
[ -n "$CHANGELOG" ] || { echo "empty changelog"; exit 1; }
case "$CHANGELOG" in *"&"*) echo "WARNING: changelog contains '&' — the server HTML-escapes it; consider 'and'.";; esac
FIRST_LINE_LEN=$(printf '%s' "$CHANGELOG" | head -1 | wc -c)
[ "$FIRST_LINE_LEN" -le 100 ] || echo "WARNING: first changelog line is ${FIRST_LINE_LEN} chars (>100); the notification truncates it."

OLD_CODE=$(sed -n 's/^[[:space:]]*versionCode = \([0-9]*\)/\1/p' "$GRADLE")
NEW_CODE=$((OLD_CODE + 1))
echo "versionCode $OLD_CODE -> $NEW_CODE, versionName -> $VERSION"

if [ "$DRY" = 1 ]; then
  echo "[dry-run] would bump, commit, tag v$VERSION, push, wait for Release, publish feed entry:"
  echo "[dry-run] download_url=$APK_URL"
  printf '[dry-run] changelog:\n%s\n' "$CHANGELOG"
  exit 0
fi

sed -i "s/^\([[:space:]]*\)versionCode = $OLD_CODE/\1versionCode = $NEW_CODE/" "$GRADLE"
sed -i "s/^\([[:space:]]*\)versionName = \".*\"/\1versionName = \"$VERSION\"/" "$GRADLE"
git add "$GRADLE"
git commit -m "Release $VERSION (versionCode $NEW_CODE)"
git push origin main
git tag "v$VERSION"
git push origin "v$VERSION"

echo "waiting for Release workflow..."
sleep 10
RUN_ID=$(gh run list --workflow Release -L1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status
echo "prerelease built: $APK_URL"

# Build payload with python for safe JSON encoding (changelog may hold quotes/newlines).
PAYLOAD=$(CHANGELOG="$CHANGELOG" python3 -c "
import json, os
print(json.dumps({
  'version_code': $NEW_CODE,
  'version_name': '$VERSION',
  'is_breaking': False,
  'min_supported_version_code': None,
  'changelog': os.environ['CHANGELOG'],
  'download_url': '$APK_URL',
  'publish': True,
}))")

echo "publishing feed entry via d051 (you may get an ssh prompt)..."
RESP=$(printf '%s' "$PAYLOAD" | ssh d051 'T=$(grep -oP "^INVENTORY_ADMIN_TOKEN=\K.*" /opt/sd-admin/.env); curl -s -X POST '"$API"'/admin/app-releases -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d @-')
if printf '%s' "$RESP" | grep -q "already been taken"; then
  echo "version_code $NEW_CODE already has a feed entry — patching it instead"
  ID=$(ssh d051 'T=$(grep -oP "^INVENTORY_ADMIN_TOKEN=\K.*" /opt/sd-admin/.env); curl -s '"$API"'/admin/app-releases -H "Authorization: Bearer $T"' \
    | python3 -c "import json,sys; print([r['id'] for r in json.load(sys.stdin)['data'] if r['version_code']==$NEW_CODE][0])")
  RESP=$(printf '%s' "$PAYLOAD" | ssh d051 'T=$(grep -oP "^INVENTORY_ADMIN_TOKEN=\K.*" /opt/sd-admin/.env); curl -s -X PATCH '"$API"'/admin/app-releases/'"$ID"' -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d @-')
fi
printf 'feed response: %s\n' "$RESP"

echo "verifying public endpoint..."
curl -s "$API/app-version" | python3 -m json.tool
echo "RELEASE $VERSION DONE"
