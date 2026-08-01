# Multiplayer Spell Duel Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Add a server-authoritative multiplayer spell duel mode with team selection, point binding, commands, spectators, restoration, and global spell HUD control.

**Architecture:** `SpellDuelManager` owns persistent groups, teams, point locations, active matches, saved player states, and spectator routing on the server thread. `SpellDuelCommands` exposes admin commands; `SpellDuelItems` handles the wooden selection tools; `SpellDuelBlockEvents` handles sculk catalyst interactions. Client packets carry only active match display data and a global HUD toggle to a client renderer.

**Tech Stack:** Java 21, NeoForge 21.1.233, Minecraft 1.21.1, Brigadier, vanilla networking, Iron’s Spellbooks API.

## Global Constraints

- Minecraft remains `1.21.1`.
- NeoForge remains compatible with `21.1.233`.
- Existing spell icon and performance behavior must remain intact.
- World mutations and teleportation run on the server thread.
- A match contains two teams with unlimited players and can run alongside other matches.
- A match ends when either team has no living members.

---

### Task 1: Add pure duel state and tests

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelGroup.java`
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java`
- Create: `src/test/java/com/example/scrollspellicons/duel/SpellDuelGroupTest.java`

- [ ] Add tests for team membership, complete point validation, and winner detection.
- [ ] Implement group state with group id, A/B UUID sets, A/B positions, active flag, and saved player states.
- [ ] Implement manager methods `createGroup`, `addPlayer`, `setPoint`, `isComplete`, `start`, `tick`, `finish`, `clearPlayers`, `clearPoints`, and `clearPoint`.
- [ ] Run the focused test and then the existing self-tests.

### Task 2: Register selection items and interaction state

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelItems.java`
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelSelectionEvents.java`
- Modify: `src/main/java/com/example/scrollspellicons/IronSpellPerformance.java`
- Create: `src/test/java/com/example/scrollspellicons/duel/SelectionStateTest.java`

- [ ] Add a player selector item with a wooden-stick appearance and a point selector item.
- [ ] Add per-admin pending selection state so different administrators can prepare different groups concurrently.
- [ ] Implement player selector right-click as team A, left-click as team B, and crouch-right-click air as group creation.
- [ ] Implement point selector right-click as A point, left-click as B point, and crouch-right-click air as point binding.
- [ ] Run focused selection tests.

### Task 3: Add commands and match lifecycle events

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java`
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java`
- Modify: `src/main/java/com/example/scrollspellicons/IronSpellPerformance.java`

- [ ] Register `/spell_duel start all`, `/spell_duel start <group>`, `/spell_duel display on|off`, `/spell_duel clear players`, `/spell_duel clear points`, and `/spell_duel clear point <pointId>`.
- [ ] Restrict management commands to permission level 2.
- [ ] Save each participant’s original position and game mode before teleporting.
- [ ] Tick active groups, detect an eliminated team, broadcast the winner, restore participants and spectators, and clear active state.
- [ ] Skip incomplete groups during `start all` and report the missing teams or points.

### Task 4: Add sculk catalyst group selection and spectator entry

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelCatalystEvents.java`
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java`

- [ ] Treat `minecraft:sculk_catalyst` as the control block without replacing its model.
- [ ] Let an administrator crouch-right-click cycle the group shown by that catalyst.
- [ ] Let a normal player right-click enter spectator mode for the catalyst’s selected active group.
- [ ] Save and restore spectator game mode and position with the match.

### Task 5: Add networked spell and spectator HUD

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java`
- Create: `src/main/java/com/example/scrollspellicons/client/SpellDuelHud.java`
- Modify: `src/main/java/com/example/scrollspellicons/client/ClientModEvents.java`
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java`

- [ ] Send global display state and active match snapshots to clients.
- [ ] Render every player’s loaded spell-book spell list beside their head when enabled.
- [ ] Render spectator-side A/B columns with player name, health, spell-book contents, active spell, and cooldown.
- [ ] Remove stale snapshots when a match finishes or a player leaves.

### Task 6: Build and verify

- [ ] Run `./gradlew.bat clean build`.
- [ ] Verify generated metadata targets NeoForge 21.1.233 and Mod version 1.0.3 or the next explicitly chosen version.
- [ ] Run all focused and existing self-tests.
- [ ] Inspect `build/libs` and provide the final Jar path.
