# Roadmap

> Forward-looking **commitments** only — the phased build plan and concrete TODOs being
> tracked now. Companion file [`BACKLOG.md`](BACKLOG.md) holds **wishes** (Ideas +
> brainstorm parking lot) and **history** (shipped milestones). Keeping commitments and
> wishes apart stops commitments from looking negotiable and ideas from looking promised.

The **Inventory** Android client (Kotlin/Compose). Authoritative spec lives in
[`inventory-docs`](https://github.com/spdotdev/inventory-docs); this file tracks build work.

Markers: 🟡 TBD · 🔲 TODO · 🛠 in progress · ✅ done (shipped work moves to `BACKLOG.md`).

---

## Phased plan

| Phase | Status | Scope |
|---|---|---|
| 0 — Project skeleton | 🔲 TODO | Single-activity Compose app + Hilt + Retrofit + Frost theme (palette in `docs/design/`). |
| 1 — Auth | 🔲 TODO | Email/password + native Google Sign-In; secure token storage; auth interceptor + 401 recovery. |
| 2 — Households | 🔲 TODO | List / switcher / create / join-by-code / leave; invite (copy link + QR). |
| 3 — Inventory | 🔲 TODO | Storage overview → shelves (tab strip + swipe) → products; add/remove/move; global search. |
| 4 — Settings + polish | 🔲 TODO | Theme (System/Light/Dark), household mgmt, account/sign out; empty/error/offline states. |
| 5 — Phase 2 | 🟡 TBD | Barcode scanning, filter/sort, product attributes. |

Detailed build order: [`CLAUDE.md`](CLAUDE.md) and [`docs/android-plan.md`](docs/android-plan.md).

---

## Active TODOs

### FOUNDATION
- [ ] **Scaffold the Android project** — single-activity Compose, Hilt, Retrofit/OkHttp,
  Kotlinx Serialization/Moshi; no Room (always-online). Base URL build-config driven.
- [ ] **Frost theme** — implement the committed design tokens from
  [`docs/design/`](docs/design) (accent `#7dd3fc`; dark `#0c1822`/`#10212e`,
  light `#cfe4f6`/`#dfeefb`; Plus Jakarta Sans + Space Mono); full light/dark with in-app toggle.

### QUALITY
- [ ] **Stand up CI against real code** — the dormant workflows (ci, release-on-tag,
  secret-scan, dependency-review) activate when the Gradle project lands; align task names
  (`testDebugUnitTest`, lint, style) with the scaffold and confirm green. Wire the pre-push hook.
- [ ] **Tag-driven release build** — confirm `release.yml` builds APK + AAB on `v*` tags;
  add the signing keystore as a repo secret before any Play Store upload.
