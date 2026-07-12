# Footer nav migration — progress ledger

Plan: docs/superpowers/plans/2026-07-12-footer-nav-migration.md
Branch: feat/footer-nav-migration

## Execution batching (decided at execution time)
Tasks 2-9 each change a screen signature and break MainActivity's call sites
until Task 11 lands — so they cannot compile independently. Batched to avoid
non-compiling intermediate commits:

- Dispatch A = Task 1 (strings)
- Dispatch B = Tasks 2-12 (all screens + MainActivity + delete AppDrawer) -> compiles
- Dispatch C = Task 13 (12 location-quick-jump flow tests)
- Dispatch D = Tasks 14-18 (10 remaining flow tests)
- Dispatch E = Task 19 (verification; physical device attached, instrumented tests runnable)

## Progress
