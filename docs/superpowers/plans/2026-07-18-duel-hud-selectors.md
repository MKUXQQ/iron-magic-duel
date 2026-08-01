# Duel HUD and Selector Corrections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make duel selectors visible in the creative inventory, make the point selector create and bind sequential `duel_N` groups, simplify clear commands, and render actual spellbook spell icons in the client HUD for creative and survival players.

**Architecture:** Keep duel state and command behavior server-side in `SpellDuelManager`, expose registered selector items through the existing item registration and creative-tab event, and make `SpellDuelHud` a client-only screen renderer. HUD packets carry snapshots of spellbook item stacks/icons and cooldown text; no name-tag rendering is used.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.233+, Java, Gradle, Iron's Spells 'n Spellbooks 1.21.1-3.16.2.

## Global Constraints

- Keep Minecraft version range `[1.21.1,1.21.2)`.
- Keep NeoForge version range `[21.1.233,)`.
- Keep mod ID `iron_spell_performance`.
- Selectors must be available in the creative inventory.
- `/spell_duel start all` starts every complete duel group.
- Remove `/spell_duel clear point` and `/spell_duel clear points`.
- Add clearing for a named duel group and all duel groups.
- `/spell_duel display` affects only creative and survival players, not spectators.
- Do not render spell data in player name tags.

---

### Task 1: Creative inventory exposure

**Files:**
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelItems.java`
- Modify: `src/main/java/com/example/scrollspellicons/IronSpellPerformance.java`
- Modify: `src/main/resources/assets/iron_spell_performance/lang/zh_cn.json`

- [ ] Register both selector items in the existing creative tab event using the current NeoForge item registration.
- [ ] Keep the item IDs `duel_player_selector` and `duel_point_selector`.
- [ ] Verify the item registration compiles and both IDs appear in the generated item registry.

### Task 2: Sequential point duel groups

**Files:**
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java`
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelSelectionEvents.java`

- [ ] Preserve right-click block = A point and left-click block = B point.
- [ ] Change crouch-right-click with the point selector to create the next unused `duel_N` ID and bind the player’s two selected points to it.
- [ ] Report missing A/B points without creating an incomplete group.
- [ ] Keep player selector group creation behavior unchanged.

### Task 3: Clear command surface

**Files:**
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java`
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java`

- [ ] Remove the `clear point` and `clear points` command branches.
- [ ] Add `/spell_duel clear group <duel_N>`.
- [ ] Add `/spell_duel clear groups`.
- [ ] Ensure clearing removes the configured group and its point bindings without affecting unrelated groups.

### Task 4: Screen HUD spell icons

**Files:**
- Modify: `src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java`
- Modify: `src/main/java/com/example/scrollspellicons/client/SpellDuelHud.java`
- Modify: `src/main/java/com/example/scrollspellicons/client/SpellDuelClientState.java`

- [ ] Remove the `RenderNameTagEvent` spell text path.
- [ ] Restrict the HUD to non-spectator players when display is enabled.
- [ ] Send enough snapshot data for the client to render each player’s actual spellbook item stack in a grid, alongside player name, health, and cooldown.
- [ ] Render using `GuiGraphics.renderItem` / `renderItemDecorations` so the screen shows item icons rather than generated text or name tags.
- [ ] Continue to render duel spectator snapshots separately; the display command must not enable the global HUD for spectators.

### Task 5: Version, tests, and build

**Files:**
- Modify: `gradle.properties`
- Modify: `src/test/java/com/example/scrollspellicons/client/ScrollIconResolverSelfTest.java`

- [ ] Bump the mod version from `1.0.5` to `1.0.6`.
- [ ] Add/adjust lightweight self-tests for command shape, creative registration, and removal of name-tag rendering.
- [ ] Run `.gradlew.bat clean build`.
- [ ] Verify the output JAR metadata reports version `1.0.6`, NeoForge `[21.1.233,)`, and Minecraft `[1.21.1,1.21.2)`.

