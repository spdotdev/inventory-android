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

> Compose project skeleton + Frost theme shipped and CI-green 2026-06-23 — see
> [`BACKLOG.md`](BACKLOG.md) → Done. Next: DI + networking, then the auth screen.

### FOUNDATION (next)
- [ ] **DI + networking** — add Hilt, Retrofit/OkHttp, Kotlinx Serialization; build-config
  driven base URL (`https://inventory.{domain}/api/v1`); OkHttp auth interceptor +
  secure token storage. No Room (always-online). (Adds `ktlint` + Hilt's KSP at the same time.)
- [ ] **Auth screen** — email/password + Google sign-in → store Sanctum token (per
  `inventory-docs/specs/api-contract.md`).

### SCREENS
- [ ] Household list/switcher → storage overview → shelves (tabs/swipe) → products; search;
  invite (code/link/QR); settings (theme/account). Frost styling per `docs/design/`.
- [ ] **Plus Jakarta Sans / Space Mono** fonts + full in-app light/dark toggle (skeleton uses
  system dark/light + default type).

### QUALITY
- [x] **CI live and green** — wrapper validation + `testDebugUnitTest` + lint pass on the
  skeleton. ktlint/detekt style gating to be added with the DI/networking pass.
- [ ] **Tag-driven release build** — confirm `release.yml` builds APK + AAB on `v*` tags;
  add the signing keystore as a repo secret before any Play Store upload.
