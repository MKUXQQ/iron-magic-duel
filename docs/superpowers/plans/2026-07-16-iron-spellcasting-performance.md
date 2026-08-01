# Iron Spellcasting Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a NeoForge 1.21.1 optimization mod that improves client FPS and server TPS/MSPT during Iron's Spells 'n Spellbooks casting without changing spell outcomes.

**Architecture:** Keep client rendering controls and server scheduling controls in separate classes behind a common configuration. Client work will reduce avoidable particle/resource churn; server work will cache immutable spell metadata and budget safe pure calculations while keeping all Level/Entity mutation on the main thread. The existing scroll-icon feature will be preserved only if it remains compatible, while the mod identity and description will be updated to describe the combined optimizer.

**Tech Stack:** Java 21, NeoForge 21.1.235, Minecraft 1.21.1, Iron's Spells 'n Spellbooks 1.21.1-3.16.2, Gradle userdev, JUnit pure-logic tests.

## Global Constraints

- Target Minecraft version is `1.21.1` and NeoForge is `21.1.235`.
- Support both integrated single-player servers and dedicated servers.
- Do not change spell damage, cooldown, range, cost, hit results, or network protocol.
- Never access or mutate `Level`, `Entity`, `BlockEntity`, inventories, or event-bus state from worker threads.
- Client visual limits must be configurable and independently disableable.
- If Iron's Spells 'n Spellbooks is unavailable or incompatible, the mod must fail safe instead of preventing game startup.
- Existing uncommitted scroll-icon files are user work and must not be deleted or reset.

---

### Task 1: Rename the project as the spellcasting optimizer

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`
- Modify: `src/main/java/com/example/scrollspellicons/ScrollSpellIcons.java`
- Create: `src/main/java/com/example/scrollspellicons/IronSpellPerformance.java`

**Interfaces:**
- Produces the stable mod id and logger used by all later tasks.
- `IronSpellPerformance.MOD_ID` remains the value used by the NeoForge `@Mod` annotation and event subscribers.

- [ ] **Step 1: Add a failing metadata assertion**

Add a small self-test class that reads `gradle.properties` and asserts the new id, name, and version are not the old scroll-only values. Run it through the existing `resolverTest` JavaExec task so it works without launching Minecraft.

- [ ] **Step 2: Run the metadata test and verify it fails**

Run `./gradlew resolverTest`; expected result is a failed assertion because `mod_id=scroll_spell_icons` and `mod_name=Scroll Spell Icons` are still present.

- [ ] **Step 3: Change metadata and main entrypoint**

Use a new id such as `iron_spell_performance`, set the display name to `Iron Spellcasting Performance`, update the description, increment the project version to `1.0.0`, and move the `@Mod` entrypoint/logger to `IronSpellPerformance`. Keep the old scroll renderer classes compiling until compatibility is verified.

- [ ] **Step 4: Run the metadata test and resource processing**

Run `./gradlew resolverTest processResources`; expected result is success and generated `neoforge.mods.toml` contains the new id/name/version.

- [ ] **Step 5: Commit the isolated metadata change**

Run `git add gradle.properties src/main/resources/META-INF/neoforge.mods.toml src/main/java/com/example/scrollspellicons/ScrollSpellIcons.java src/main/java/com/example/scrollspellicons/IronSpellPerformance.java src/test` and commit with `feat: rename mod for spellcasting performance`.

### Task 2: Add validated client/server configuration

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/config/PerformanceConfig.java`
- Modify: `src/main/java/com/example/scrollspellicons/IronSpellPerformance.java`
- Create: `src/test/java/com/example/scrollspellicons/config/PerformanceConfigTest.java`

**Interfaces:**
- `PerformanceConfig.CLIENT` exposes `enableClientOptimizations`, `particleDistanceMultiplier`, `maxParticlesPerFrame`, and `preloadSpellResources`.
- `PerformanceConfig.SERVER` exposes `enableServerOptimizations`, `maxSpellScanMillisPerTick`, `enableSafeAsyncCalculations`, and `debugPerformanceLogging`.
- Values are clamped at load time: distance `>= 0`, particle budget `>= 0`, scan budget `>= 0`; zero means unlimited/disabled according to the option documentation.

- [ ] **Step 1: Write failing pure validation tests**

Test that negative distance, particle budget, and scan budget are rejected or clamped; test that default settings preserve the original behavior (`1.0` distance and optimization switches off until explicitly enabled).

- [ ] **Step 2: Run the focused test and verify it fails**

Run `./gradlew resolverTest`; expected failure is missing `PerformanceConfig` validation.

- [ ] **Step 3: Implement NeoForge common/client/server config registration**

Register the config with the mod container, use separate client and server config files, document every option in Chinese/English-neutral comments, and expose immutable snapshot getters to avoid reading live config repeatedly in hot loops.

- [ ] **Step 4: Run tests and inspect generated config behavior**

Run `./gradlew resolverTest processResources`; expected success, with no Minecraft classes loaded by the pure validation test.

- [ ] **Step 5: Commit configuration**

Run `git add src/main/java/com/example/scrollspellicons/config src/main/java/com/example/scrollspellicons/IronSpellPerformance.java src/test` and commit with `feat: add performance configuration`.

### Task 3: Implement client-side resource and particle budgeting

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/client/ClientPerformanceState.java`
- Create: `src/main/java/com/example/scrollspellicons/client/ClientPerformanceEvents.java`
- Create: `src/main/java/com/example/scrollspellicons/client/ParticleBudget.java`
- Create: `src/test/java/com/example/scrollspellicons/client/ParticleBudgetTest.java`
- Modify: `src/main/java/com/example/scrollspellicons/client/ClientModEvents.java`

**Interfaces:**
- `ParticleBudget.beginFrame(long frameId)` resets the frame counter once.
- `ParticleBudget.tryAccept(double squaredDistance, int cost)` returns false only when configured distance/budget limits are exceeded.
- `ClientPerformanceState` owns the client-only cache and clears it on resource reload/disconnect.

- [ ] **Step 1: Write failing tests for deterministic particle budgeting**

Cover unlimited mode, distance rejection, exact budget exhaustion, and reset at the next frame. Tests must use only primitive values and must not instantiate Minecraft client classes.

- [ ] **Step 2: Run the focused test and verify it fails**

Run `./gradlew resolverTest`; expected failure is missing `ParticleBudget`.

- [ ] **Step 3: Implement the budget and client state**

Use a per-frame counter, squared-distance comparison, and no allocation in the acceptance path. Register client tick/render/resource-reload hooks using NeoForge 1.21.1 APIs discovered from the userdev mappings. Preload only already-registered Iron spell resources and cache immutable `ResourceLocation`/sprite references.

- [ ] **Step 4: Connect particle filtering at the narrowest compatible hook**

Use a NeoForge particle-add/client render hook if available; otherwise use a minimal client event subscriber rather than a coremod. Filter only configured Iron spell particle types or particles tagged by the Iron spell integration, preserve all non-Iron particles, and never touch server state.

- [ ] **Step 5: Verify client code compiles and budget tests pass**

Run `./gradlew resolverTest compileJava`; expected success with no dedicated-server classloading error. Run the client dev configuration long enough to confirm resource reload and disconnect clear the cache.

- [ ] **Step 6: Commit client optimization**

Run `git add src/main/java/com/example/scrollspellicons/client src/test` and commit with `feat: add client spell effect budgeting`.

### Task 4: Implement server-safe caching and bounded work scheduling

**Files:**
- Create: `src/main/java/com/example/scrollspellicons/server/SpellMetadataCache.java`
- Create: `src/main/java/com/example/scrollspellicons/server/SpellWorkBudget.java`
- Create: `src/main/java/com/example/scrollspellicons/server/SafeCalculationExecutor.java`
- Create: `src/main/java/com/example/scrollspellicons/server/ServerPerformanceEvents.java`
- Create: `src/test/java/com/example/scrollspellicons/server/SpellWorkBudgetTest.java`
- Create: `src/test/java/com/example/scrollspellicons/server/SafeCalculationExecutorTest.java`

**Interfaces:**
- `SpellMetadataCache.getOrCompute(ResourceLocation id, Supplier<ImmutableSpellData> supplier)` caches immutable data and invalidates on reload.
- `SpellWorkBudget.beginTick(long tick, long nanoBudget)` and `tryConsume(long nanos)` provide deterministic main-thread budget accounting.
- `SafeCalculationExecutor.submit(Callable<T>)` accepts only copied immutable inputs and returns a future; shutdown occurs on server stop.

- [ ] **Step 1: Write failing tests for budget, cache, and executor lifecycle**

Test that a tick budget accepts work up to the limit, carries no work into the next tick, cache computes once per id, and executor rejects submissions after shutdown.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run `./gradlew resolverTest`; expected failure is missing server utility classes.

- [ ] **Step 3: Implement pure budget/cache/executor utilities**

Use immutable records for copied spell inputs/results, a bounded daemon executor with a small fixed worker count, and explicit cancellation on server stop. Avoid unbounded queues and avoid creating a task per entity when a batch can be processed.

- [ ] **Step 4: Add server lifecycle hooks**

Create and close the executor on server start/stop, clear caches on level/resource lifecycle events, and emit optional once-per-second performance summaries. Main-thread callbacks must validate the server and level before applying results.

- [ ] **Step 5: Integrate only safe Iron spell workload boundaries**

Use NeoForge events or supported Iron spell APIs to cache repeated metadata and batch pure target-distance/math calculations. Keep entity lookup, hit checks, damage, effects, block changes, and event dispatch on the server thread. If the installed Iron spell version does not expose a safe integration point, leave that integration disabled and retain lifecycle/configuration utilities instead of using reflection into private mutable state.

- [ ] **Step 6: Run server-side compile and pure tests**

Run `./gradlew resolverTest compileJava`; expected success. Launch the dedicated server run configuration once with the optimizer enabled and verify clean start/stop, no async world-access exception, and no missing client-class error.

- [ ] **Step 7: Commit server optimization**

Run `git add src/main/java/com/example/scrollspellicons/server src/test` and commit with `feat: add safe server spell workload scheduling`.

### Task 5: Preserve scroll-icon compatibility and update documentation

**Files:**
- Modify: `src/main/java/com/example/scrollspellicons/client/ClientModEvents.java`
- Modify: `src/main/java/com/example/scrollspellicons/client/SpellIconItemModel.java` only if required by the renamed mod id
- Modify: `src/main/resources/pack.mcmeta`
- Modify: `gradle.properties`
- Create: `README.md`

**Interfaces:**
- The existing scroll icon model remains optional and must not be required for server-only execution.
- README documents client/server config locations, safe defaults, compatibility behavior, and installation for NeoForge 1.21.1.

- [ ] **Step 1: Write a compatibility check**

Run the existing resolver self-test and add an assertion that the optimizer can load without client-only classes in the server source set.

- [ ] **Step 2: Implement the minimal compatibility changes**

Replace hard-coded old mod id references in event subscribers and generated pack metadata, keep scroll icon behavior isolated behind the client dist, and ensure the server jar does not initialize atlas/model code.

- [ ] **Step 3: Verify resource/model compatibility**

Run `./gradlew resolverTest processResources compileJava`; expected success. Start the client with Iron's Spells 'n Spellbooks and confirm the scroll icon code does not prevent resource loading.

- [ ] **Step 4: Write the installation and tuning guide**

Document the jar name, config files, recommended values for low/high-end clients and servers, how to disable each optimization, and the fact that increasing utilization cannot compensate for an actually CPU/GPU-limited machine.

- [ ] **Step 5: Commit documentation and compatibility updates**

Run `git add README.md gradle.properties src/main src/main/resources src/test` and commit with `docs: document spellcasting optimizer compatibility`.

### Task 6: Full verification and distributable build

**Files:**
- Modify: `gradle.properties` only if the final version number needs the release increment.
- Create: no new source files.

- [ ] **Step 1: Run all automated checks**

Run `./gradlew clean check`; expected output ends with `BUILD SUCCESSFUL`, resolver self-test passes, and no Java compilation errors occur.

- [ ] **Step 2: Build the release jar**

Run `./gradlew build`; verify a single optimizer jar exists under `build/libs` and inspect it with `jar tf` for `neoforge.mods.toml`, config classes, client classes, and server classes.

- [ ] **Step 3: Run both dev smoke tests**

Run the client and server NeoForge configurations. Exercise continuous casting, area spells, simultaneous players, resource reload, disconnect/reconnect, and server shutdown. Compare with all optimizer switches disabled and check that spell outcomes remain identical.

- [ ] **Step 4: Record measured results**

Capture baseline and optimized FPS/frame time plus server MSPT/TPS in the documented scenarios. If a limit increases visual delay or causes compatibility issues, lower its default or disable that integration rather than changing spell behavior.

- [ ] **Step 5: Hand off the jar and configuration path**

Provide the absolute `build/libs` jar path, final version, config file paths, test commands, and any measured caveats. Do not copy into the user’s live mods directory until the user explicitly requests installation of this new optimizer jar.

## Self-review

- Spec coverage: client preload/cache and particle controls are covered by Task 3; server cache, safe async calculations, bounded scans, and lifecycle shutdown are covered by Task 4; configuration and fail-safe behavior are covered by Tasks 1–2 and 4–5; single-player/dedicated-server validation is covered by Task 6.
- Placeholder scan: no `TBD`, `TODO`, or unspecified “handle edge cases” implementation steps are used; each task names files, interfaces, commands, and expected outcomes.
- Type consistency: `ParticleBudget`, `SpellMetadataCache`, `SpellWorkBudget`, and `SafeCalculationExecutor` are defined in their producing tasks before later tasks consume them; configuration property names match the design specification.
