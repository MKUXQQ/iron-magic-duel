# Scroll Spell Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a NeoForge 1.21.1 client mod that renders every Iron's Spells 'n Spellbooks scroll with the icon of the spell stored in that scroll, including addon spells.

**Architecture:** The mod depends on Iron's Spellbooks and installs a client-side ItemStack-aware model override for the scroll item. It extracts the first spell from the scroll's `SPELL_CONTAINER`, resolves the spell's own icon resource, caches the resolved model per resource location, and falls back to the original scroll model when resolution fails.

**Tech Stack:** Java 21, NeoForge 21.1.x, Gradle 8 userdev, Iron's Spellbooks 1.21.1-3.16.2 API, JUnit 5 for pure helper tests.

## Global Constraints

- Target Minecraft version: `1.21.1`.
- Target loader: NeoForge `21.1.x`.
- Client-only visual change; no server gameplay or item data mutation.
- Only 2D inventory/creative/JEI item rendering is in scope.
- Addon spell IDs and icon namespaces must be discovered dynamically through the Iron's Spellbooks API.
- Missing icons and invalid scroll data must preserve the original scroll icon.

## Files

- Create `settings.gradle` and `build.gradle`: standalone NeoForge userdev project.
- Create `gradle.properties`: Minecraft, NeoForge, Iron's Spellbooks and mod metadata versions.
- Create `src/main/resources/META-INF/neoforge.mods.toml`: mod metadata and runtime dependency.
- Create `src/main/resources/pack.mcmeta`: resource-pack metadata.
- Create `src/main/java/com/example/scrollspellicons/ScrollSpellIcons.java`: mod entry point.
- Create `src/main/java/com/example/scrollspellicons/client/ScrollIconResolver.java`: pure resolution and fallback boundary.
- Create `src/main/java/com/example/scrollspellicons/client/ClientModelEvents.java`: client registration and model-cache invalidation.
- Create `src/main/java/com/example/scrollspellicons/client/ScrollBakedModel.java`: ItemStack-aware baked-model delegation.
- Create `src/test/java/com/example/scrollspellicons/client/ScrollIconResolverTest.java`: resolution behavior tests.

### Task 1: Scaffold the NeoForge project

- [ ] Add the Gradle files and metadata with mod id `scroll_spell_icons`, version `1.0.0`, Java 21, Minecraft `1.21.1`, NeoForge `21.1.200`, and a required dependency on `irons_spellbooks` version range `[1.21.1,)`.
- [ ] Add a minimal mod entry point that has no server-side behavior.
- [ ] Run `./gradlew tasks` and confirm Gradle configures successfully.

### Task 2: Add the failing resolver tests

- [ ] Write tests for: an ordinary spell ID maps to `<namespace>:textures/gui/spell_icons/<path>.png`; empty/none data returns the original scroll texture; a path with nested segments preserves the complete path; addon namespaces are preserved.
- [ ] Run `./gradlew test` and confirm the tests fail because the resolver is not implemented.

### Task 3: Implement spell icon resolution

- [ ] Implement `ScrollIconResolver.resolve(ResourceLocation spellId, ResourceLocation fallback)` with the exact mapping `ResourceLocation.fromNamespaceAndPath(spellId.getNamespace(), "textures/gui/spell_icons/" + spellId.getPath() + ".png")`.
- [ ] Implement `isRenderableSpell(ResourceLocation)` to reject `null`, `irons_spellbooks:none`, and empty paths.
- [ ] Run `./gradlew test` and confirm all resolver tests pass.

### Task 4: Implement the client model delegation

- [ ] Register the scroll model hook during `FMLClientSetupEvent`/NeoForge client setup, keeping all registration behind a client-only class.
- [ ] In `ScrollBakedModel`, inspect only `irons_spellbooks:scroll` ItemStacks, read `ISpellContainer.get(itemStack)`, obtain slot 0 and its `AbstractSpell#getSpellResource()`, then delegate to the baked model for the resolved icon.
- [ ] Cache resolved models by icon ResourceLocation and clear the cache on client resource reload.
- [ ] Delegate every non-inventory transform and every non-scroll item to the original scroll model so the change remains 2D-only.
- [ ] On missing sprite/model or malformed component, return the original scroll baked model without throwing.

### Task 5: Package and verify

- [ ] Run `./gradlew test`.
- [ ] Run `./gradlew build`.
- [ ] Confirm `build/libs/scroll_spell_icons-1.0.0.jar` exists.
- [ ] Inspect the jar for `neoforge.mods.toml`, the entry point, and client classes.
- [ ] Report the jar path and installation instructions.
