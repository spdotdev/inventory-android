# Security Policy

## Supported versions

This is a small, actively-developed app distributed as **debug-signed prereleases**
(no store presence yet — see [`README.md`](README.md#distribution)). Only the latest
tagged release and `main` are supported; there is no LTS branch.

## Reporting a vulnerability

Please report security issues **privately**, not via a public GitHub issue:

- Open a [GitHub Security Advisory](https://github.com/spdotdev/inventory-android/security/advisories/new)
  for this repo, **or**
- Email the maintainer (see the GitHub profile of [@spdotdev](https://github.com/spdotdev)).

Include what you found, how to reproduce it, and its impact. Expect an acknowledgement
within a few days. If the issue also affects the backend
([`inventory-laravel`](https://github.com/spdotdev/inventory-laravel)), report it there
too (or flag the overlap) since that repo owns the API contract and auth semantics.

Please don't test against the production backend
(`https://inventory.scuttle.dev/api/v1`) with anything beyond your own account/household
— it's a live service for real users.

## What's in scope

- The Android client in this repo: auth/token handling, API request construction,
  local data handling (this app has **no local DB / offline cache** — see
  [`CLAUDE.md`](CLAUDE.md), so most sensitive state is the Sanctum bearer token and
  Google Sign-In flow), and the CI/release pipeline (signing, secrets, dependency
  supply chain).
- Out of scope: the Laravel API itself (report to `inventory-laravel`), and anything
  requiring physical access to an already-unlocked, already-compromised device.

## How this repo defends itself (for context, not a guarantee)

- **Secret scanning** (`gitleaks`, `.github/workflows/secret-scan.yml`) on every push/PR.
- **Static analysis** (CodeQL, `.github/workflows/codeql.yml`) for the Kotlin/Java
  source, on push/PR to `main` plus a weekly scheduled scan.
- **Dependency review** (`.github/workflows/dependency-review.yml`) blocks PRs that
  introduce new dependencies with high-severity known vulnerabilities; Dependabot
  (`.github/dependabot.yml`) keeps existing dependencies patched.
- **Style/quality gates** (ktlint + detekt, baseline-aware) in `ci.yml` — not a security
  control per se, but catches classes of bugs before they ship.
- Auth tokens are stored via EncryptedSharedPreferences/DataStore + Android Keystore,
  never in plaintext; see `CLAUDE.md` for the auth model.

None of this is a substitute for a report — if you find something, tell us.
