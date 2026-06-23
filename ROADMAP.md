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

> Skeleton + Frost theme + DI/networking + email/password auth all shipped and CI-green
> 2026-06-23 — see [`BACKLOG.md`](BACKLOG.md) → Done. Next: the inventory screens.

### SCREENS (next)
- [x] **Households screen** — list / create / join (shipped 2026-06-23, CI-green).
- [x] **Storage overview** — locations list + create (with type), tap-through from households
  (shipped 2026-06-23, CI-green). Shelf/item counts + tap → shelves come with the shelves screen.
- [x] **Shelves** — list + create under a location, tap-through from storage overview
  (shipped 2026-06-23, CI-green). Tab-strip + swipe-pager presentation is a later refinement.
- [x] **Products** — per-shelf list + create + add/remove steppers (shipped 2026-06-23,
  CI-green). `move()` is in the data layer; its shelf-picker UI is a follow-up.
- [ ] **Search** (location › shelf path), **invite** (code/link/QR), **settings** (theme/account).
  Pattern per screen: repository + Retrofit API + ViewModel + Compose UI, JVM unit-tested via fakes.
- [ ] **Navigation + active-household** — proper nav (e.g. Navigation-Compose) and an active
  household selection feeding the household-scoped screens (currently a flat list post-auth).
- [ ] **Plus Jakarta Sans / Space Mono** fonts + full in-app light/dark toggle (currently
  follows system dark/light + default type).

### DEFERRED (need a decision or external dependency)
- [ ] **Native Google Sign-In** — wire Credential Manager → Google ID token →
  `AuthRepository.loginWithGoogle` (the API path + button are in place). Needs a real Google
  OAuth client ID configured.
- [ ] **ktlint/detekt** style gating in CI — its own pass (kept out of the auth pass to
  avoid blind style churn while the codebase is moving fast).

### QUALITY
- [x] **CI live and green** — wrapper validation + `testDebugUnitTest` + lint pass.
- [ ] **Tag-driven release build** — confirm `release.yml` builds APK + AAB on `v*` tags;
  add the signing keystore as a repo secret before any Play Store upload.
