# NeoForge 21.1.233 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Build Iron Spellcasting Performance against NeoForge 21.1.233 so the Mod loads in the user's Minecraft 1.21.1 profile.

**Architecture:** This is a build-metadata-only compatibility change. The Gradle dependency and generated Mod metadata will use NeoForge 21.1.233 as the lower bound; Java source files and gameplay behavior remain unchanged.

**Tech Stack:** Gradle, NeoForge UserDev, Minecraft 1.21.1, Java 21.

## Global Constraints

- Minecraft must remain `1.21.1`.
- NeoForge compile version must be `21.1.233`.
- NeoForge metadata range must accept `21.1.233` and newer compatible versions.
- Mod version must become `1.0.3`.
- Do not change Java gameplay or rendering code.

---

### Task 1: Update compatibility metadata

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Change the compile and Mod versions**

Set `neo_version=21.1.233` and `mod_version=1.0.3`. Set the NeoForge dependency range in the Mod metadata to start at `21.1.233`.

- [ ] **Step 2: Verify no old minimum remains**

Run:

```powershell
rg -n "21\.1\.235|neo_version|neo_version_range|mod_version" gradle.properties src/main/resources/META-INF/neoforge.mods.toml
```

Expected: the compile version and minimum metadata version are `21.1.233`; `21.1.235` is absent from the active metadata.

### Task 2: Build and inspect the artifact

**Files:**
- Generated: `build/libs/iron_spell_performance-1.0.3.jar`

- [ ] **Step 1: Run the project checks and build**

Run:

```powershell
./gradlew.bat clean build
```

Expected: exit code 0 and a 1.0.3 Jar in `build/libs`.

- [ ] **Step 2: Inspect the generated Mod metadata**

Run:

```powershell
$jar = (Get-ChildItem build/libs/iron_spell_performance-1.0.3.jar).FullName
& 'E:\JAVA\bin\jar.exe' xf $jar META-INF/neoforge.mods.toml
Select-String -Path META-INF/neoforge.mods.toml -Pattern 'version|neoforge|loader'
Remove-Item -Recurse -Force META-INF
```

Expected: Mod version is `1.0.3`, Minecraft is `1.21.1`, and NeoForge dependency accepts `21.1.233`.

- [ ] **Step 3: Confirm the final artifact exists**

Run:

```powershell
Get-Item build/libs/iron_spell_performance-1.0.3.jar
```

Expected: one readable Jar file is listed.
