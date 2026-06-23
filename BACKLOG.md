# Backlog

> Wishes and history. Companion to [`ROADMAP.md`](ROADMAP.md) (forward-looking
> commitments). This file holds **Ideas** (detailed proposals + a brainstorm parking lot)
> and **Done** (shipped milestones). Phases are commitments, ideas are wishes — keep them apart.

Markers: 💡 IDEA · ✅ DONE.

---

## Ideas — detailed proposals

### 💡 Barcode scanning to add products

**What.** Scan a product barcode (ML Kit / CameraX) to add or increment a product on the
active shelf, instead of typing the name.

**Why.** Fastest possible "add stock" flow at the freezer/shelf; the highest-friction
action in the app is naming products. Pairs with the Phase 2 `barcode` product attribute.

**Where it touches.** New camera permission + scanner screen; product add flow; depends on
the backend gaining a `barcode` attribute (Phase 2, see `inventory-docs`).

**Risks.** Camera permission friction; offline barcode→name lookup needs a data source
(out of scope while always-online). Effort ~3–4 days. **Kill criterion:** if manual add is
fast enough in real use, don't build it.

---

## Ideas — parking lot
- 💡 Filter / sort products within a shelf or search results.
- 💡 Widget / quick-tile for "what's low" (depends on a low-stock concept — not in MVP).
- 💡 Per-household color/icon theming on top of Frost.
- 💡 Q-3: live updates (WebSockets) if pull-to-refresh proves insufficient.

---

## Done
_Nothing shipped yet — repo is at planning stage. Design mocks committed under `docs/design/`._
