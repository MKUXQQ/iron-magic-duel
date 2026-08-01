# NeoForge 21.1.233 Compatibility

## Goal

让 Iron Spellcasting Performance 在 Minecraft 1.21.1 + NeoForge 21.1.233 环境中加载，同时继续兼容同一 1.21.1 NeoForge 系列的较新版本。

## Scope

- 将 Gradle 的 NeoForge 编译依赖切换到 `21.1.233`。
- 将 Mod 版本从 `1.0.2` 更新到 `1.0.3`。
- 将 `neoforge.mods.toml` 中的 NeoForge 版本下限改为 `21.1.233`。
- 不修改法术图标、施法、重力或性能逻辑。
- 使用 `build` 任务验证可编译，并检查最终 Jar 的 Mod 元数据。

## Compatibility

Minecraft 版本仍限制为 `1.21.1`。NeoForge 版本范围为 `[21.1.233,)`，因此 21.1.233 及更高的 21.1 系列版本可以加载；实际运行时仍应使用与其他 Mod 兼容的 NeoForge 版本。

## Acceptance Criteria

1. `gradle.properties` 的 `neo_version` 为 `21.1.233`。
2. Mod 元数据不再要求 `21.1.235`。
3. Gradle 构建成功并生成 `iron_spell_performance-1.0.3.jar`。
4. Jar 中的 Mod 版本为 `1.0.3`。
