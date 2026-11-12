# Architecture and test baseline

Date: 2026-11-12. Scope: Phase 0 only. Target specification: [gameplay specification](gameplay/Linux_Space_Pirates_Gameplay_Spezifikation.md). See [integration ADR](adr-001-headless-core-integration.md). The local checkout was clean at commit `2e6b3f93384d7717978d59d1a1fb4314f1a04ac6` before these documentation changes.

## Existing architecture

Paths below are relative to the repository root; Java classes are under `app/src/main/java/com/fleetacademy/game/`.

| Area | Existing implementation |
| --- | --- |
| Stack and build | Single Android Java application, Android Gradle Plugin 8.1.0 in `build.gradle`; `settings.gradle` includes `:app`. `app/build.gradle` uses compile/target SDK 36, build tools 36.1.0, min SDK 23. No Gradle wrapper is present in this checkout. |
| Entry and menu | `app/src/main/AndroidManifest.xml` launches portrait `MainActivity`; `MainActivity.onCreate()` restores state and calls `showMenu()`, which displays `MenuView`. |
| Navigation and input | `MainActivity` swaps views and opens dialogs. `ShipView` draws the ship on Android Canvas and handles touch, movement and interactions through its `Host` callbacks; `GalaxyView`, `MenuView` and `ComicView` also draw and handle touch. Terminal input is an Android dialog in `MainActivity.openTerminal()`. |
| Content and graphics | `GameData` embeds rooms, doors, NPCs, props, missions and comic data as Java structures. `ShipView` and `ComicView` generate/draw graphics in code; no separate asset or resource tree is present under `app/src/main`. |
| Terminal | `MainActivity.openTerminal()` calls `SimShell.run()` and handles its output. `SimShell` is a mission-specific virtual interpreter: a flat path-to-content map, flags, hardcoded command branches and limited tokenization; it is not the requested general VFS/session/transaction/event model. It rejects shell operators and does not invoke a host process in the inspected code. This is not a complete security audit. |
| Missions | `GameData` defines mission data, prerequisites and availability; `MainActivity` routes quiz, configuration and shell tasks and records completion. `SimShell.solved()` checks mission-specific state; `NetworkConfigValidator` validates configuration missions. Existing story and progression differ from the proposed slice. |
| Save | `MainActivity` uses Android `SharedPreferences` named `blackstar_chapter1_v1`: `done`, `shell_state`, `started`, `x`, `y`, `pending_comic`, `comic_*` and `legacy_imported`. `SimShell.snapshot()` writes JSON version 1 with virtual files, flags and revisions; `restore()` checks the version and required files. The existing explicit restart clears Blackstar progress while preserving the earlier Fleet save. |
| Tests | Four Java entry points in `app/src/test/java/com/fleetacademy/game/`: `ChapterOneRegression`, `ChapterOneGuidanceTest`, `NetworkConfigValidatorTest`, `PositionPreferencesTest`. They are standalone `main` programs, not evidence that Gradle discovers JUnit tests. |

## Integration risks

- `MainActivity`, mission gating, `SimShell` and `GameData` are coupled; replacing the shell in place could break existing tasks, guidance and saves.
- Existing JSON and preference keys are not the proposed versioned authoritative GameState. No automatic mapping of old mission flags or virtual files to new story state is justified.
- Existing shell has no general directory tree, permissions, transactional mutations, structured domain events or generic service registry; do not claim Phase 1 semantics from this baseline.
- The planned UI must consume a projection of committed simulation state, not make rendering or mission dialogs authoritative. Host process, host filesystem and networking access from simulated commands must remain forbidden.
- Device interaction, visual behavior, accessibility, save roundtrips on Android and screenshots were not checked in this baseline.

## Remote build baseline

Remote testbench baseline, 2026-11-12; Java 17.0.12, Gradle 8.5. The remote directory is not a Git checkout. SHA-256 values matched the local checkout for root `build.gradle`, `settings.gradle`, `app/build.gradle`, the nine Java files directly in the main game package and the four test Java files. A separate untracked `simulator/Simulator.java` appeared locally later and was not part of this comparison or baseline. This does not establish identity of every build input, SDK or generated artifact.

Exact requested command, executed from the local workspace:

```powershell
ANDROID_HOME=<sdk-directory> gradle --offline assembleDebug
```

Result: **FAILED**, `SDK location not found` because the configured SDK path was a broken symlink.

Separate retry with the actual SDK location:

```powershell
ANDROID_HOME=<actual-sdk-directory> gradle --offline assembleDebug
```

Result: **BUILD SUCCESSFUL**, 30 actionable tasks all up-to-date; SDK XML version 4 warning (tool understands up to 3). This is a cached build, not proof of a clean rebuild. Offline reproducibility depends on the remote Gradle dependency cache, SDK platform/build tools and a compatible installed Gradle/JDK; the requested SDK path itself remains broken.

## Standalone test baseline

Exact command, executed from the local workspace, compiles to a remote temporary directory and executes all four mains:

```powershell
mkdir -p /tmp/linux-fleet-phase0-tests
javac -cp "$ANDROID_HOME/platforms/android-36/android.jar" -d /tmp/linux-fleet-phase0-tests app/src/main/java/com/fleetacademy/game/{GameData,SimShell,NetworkConfigValidator,PositionPreferences}.java app/src/test/java/com/fleetacademy/game/{ChapterOneRegression,ChapterOneGuidanceTest,NetworkConfigValidatorTest,PositionPreferencesTest}.java
for test in ChapterOneRegression ChapterOneGuidanceTest NetworkConfigValidatorTest PositionPreferencesTest; do java -cp "/tmp/linux-fleet-phase0-tests:$ANDROID_HOME/platforms/android-36/android.jar" com.fleetacademy.game.$test; done
```

Output: `ChapterOneRegression: PASS`, `ChapterOneGuidanceTest: PASS`, `NetworkConfigValidatorTest passed`, `PositionPreferencesTest passed`. Each printed its own success marker; the shell loop alone returns only the final iteration's status. The compile uses the Android platform stub jar, not a functional Android runtime. Passing these mains does not validate Android `org.json` snapshot/restore, instrumentation, UI or the future simulator. The temporary output directory is reused rather than cleaned; source compilation completed successfully in this run.

## Next increment

Phase 1 should implement and test a separate headless core incrementally, including transactional state, VFS/session, parser, services/journal, events and save roundtrip as specified. No UI hookup, simulator implementation or migration was made in Phase 0.
