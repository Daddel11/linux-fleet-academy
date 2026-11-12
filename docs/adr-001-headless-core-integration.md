# ADR 001: Separate headless simulator and phased integration

Status: accepted for planning, 2026-11-12. Scope: Phase 0 decision, not an implementation.

## Context

The Java Android app currently couples `app/src/main/java/com/fleetacademy/game/MainActivity.java`, `SimShell.java` and `GameData.java`. Its existing mission-specific shell and `blackstar_chapter1_v1` preferences are incompatible with the proposed authoritative VFS and game state. Preserve the current app and its content rather than replacing its engine or shell in place.

## Decision

- Build Phase 1 as a separate headless Java core, initially under `app/src/main/java/com/fleetacademy/game/simulator/`, with tests under proposed `app/src/test/java/com/fleetacademy/game/simulator/`. A separate untracked `simulator/Simulator.java` appeared during Phase 0 verification and was not inspected or changed as part of this work. The core must not depend on Android UI, `MainActivity` or `SimShell` and must run in ordinary JVM tests. A separate Gradle module can be evaluated later if needed.
- Place parser/command handling in `simulator/terminal/`, virtual filesystem and service/journal models in `simulator/simulation/`, transactional state/events in `simulator/state/`, and versioned snapshot contracts in `simulator/persistence/`. Future rules, quest evaluation and projections can occupy sibling packages, with no renderer authority over state. These are responsibility boundaries, not existing code.
- Phase 1 stays headless and unconnected. In a later phase, `MainActivity.openTerminal()` is the candidate adapter boundary for command requests and results, and `ShipView` is the candidate consumer of a derived world projection. Introduce an explicit opt-in mode or feature flag at that later integration point; no flag or UI integration exists yet. Keep legacy `SimShell`, existing screens and content reachable until an intentional cutover.
- Keep `blackstar_chapter1_v1` and old saves untouched by the new core. No automatic migration, deletion or inferred story-flag mapping. Only after an explicit user/product choice and a defined, tested mapping may a future migration back up and import a legacy save; a separate new profile is the alternative. This ADR does not implement either path.
- The future core must interpret only allowlisted virtual commands, never execute host shell/processes or access host files/network as command effects. Commit valid mutations atomically before emitting events; rebuild visual projections from persisted authoritative state.

## Consequences

Parallel implementations temporarily duplicate shell concepts, but isolate regressions and allow deterministic tests before UI adoption. Adapter, feature flag, save format, migration UX and full end-to-end tests remain future decisions/work. Phase 2 may connect one room and projection only after Phase 1 headless acceptance; no simulator or UI code is changed by this ADR.
