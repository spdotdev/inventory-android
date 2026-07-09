# inventory-android — client planning slice

> Android-specific planning. The shared, authoritative spec lives in
> [`inventory-laravel`](https://github.com/spdotdev/inventory-laravel)'s `docs/`:
> `docs/planning/project-brief.md`, `docs/specs/data-model.md`, `docs/specs/api-contract.md`.
> (The former `inventory-docs` repo has been retired and merged in.)

## Architecture
- Single-activity Compose app, **MVVM/MVI**, unidirectional state.
- Layers: `ui/` (Compose screens + ViewModels) → `domain/` (use cases, models) →
  `data/` (Retrofit services, repositories, DTO ↔ domain mappers).
- **Hilt** for DI; **Retrofit/OkHttp** for networking; JSON via Kotlinx Serialization or Moshi.
- **No persistence layer** (always-online). In-memory caches per screen are fine; no Room.

## Networking
- Base URL = `https://inventory.{domain}/api/v1` (build-config / flavor driven; dev vs prod).
- OkHttp auth interceptor injects `Authorization: Bearer <token>`.
- 401 → clear token, route to Auth. 403 → not a member. 404 → gone/out-of-tenant.
- Pull-to-refresh + optimistic UI (no realtime; D-008).

## Auth
- Token stored in EncryptedSharedPreferences / DataStore (Keystore-backed).
- Email/password: `/auth/register`, `/auth/login`.
- Google: Credential Manager → Google **ID token** → `/auth/google { id_token }` → store Sanctum token.
- Logout clears the token and Google credential state.

## Screens & navigation
Auth → Storage overview → Shelves (ScrollableTabRow + HorizontalPager) → Products;
plus Search, Invite (code/link/QR), Settings. See `CLAUDE.md` for detail.

## Design — B · Frost
- Compose theme: icy-blue **#7dd3fc** accent, frosted-glass surfaces, rounded shapes,
  **Plus Jakarta Sans**; full light/dark with in-app System/Light/Dark toggle.
- Visual reference committed in [`docs/design/`](./design): `frost-app.html` (interactive
  5-screen prototype), `frost-dark.png`, `frost-light.png`. Mirror these exactly in the
  Compose theme (palette below comes straight from the prototype CSS):
  - page bg dark `#0c1822` / light `#cfe4f6`; screen bg dark `#10212e` / light `#dfeefb`
  - accent `#7dd3fc`; on-accent `#06283b`; muted `#6f9bbb`
  - fonts: Plus Jakarta Sans (UI), Space Mono (join codes)

## Build order
1. Project skeleton: single-activity Compose + Hilt + Retrofit + theme (Frost).
2. Auth (email/password + Google) + token storage + interceptor.
3. Household list / switcher / create / join (code) / leave; invite (link + QR).
4. Storage overview → shelves (tabs/swipe) → products.
5. Stock actions: add / remove / relocate.
6. Global search.
7. Settings (theme, household mgmt, account).

## Testing
Critical paths: token handling + 401 recovery, household scoping, stock actions,
error/empty/offline states. No trivial UI tests.

## Open items
- Q-3 realtime (currently pull-to-refresh — no WebSockets).
