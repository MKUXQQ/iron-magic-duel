# 卷轴法术图标替换设计

## 目标

为 NeoForge 1.21.1 制作一个独立客户端 Mod，使 `irons_spellbooks:scroll` 在物品栏、创造栏和 JEI 等 2D 物品渲染场景中显示卷轴内具体法术自己的图标。所有通过 Iron's Spells 'n Spellbooks API 注册的附属 Mod 法术自动适配。

## 范围

- 读取卷轴 `SPELL_CONTAINER` Data Component 中第一个法术的 ResourceLocation。
- 使用 `AbstractSpell#getSpellIconResource()` 对应的 GUI 图标纹理。
- 支持主 Mod 与附属 Mod 的图标命名空间。
- 无法读取法术、图标不存在或资源加载失败时回退到原版卷轴图标。
- 只修改客户端 2D 图标，不修改卷轴数据、名称、使用逻辑、手持模型或掉落物模型。

## 方案

独立 NeoForge Mod 依赖 Iron's Spells 'n Spellbooks，并注册一个基于 ItemStack 的客户端物品模型选择/渲染层。模型层按 ItemStack 缓存法术图标对应的 baked model，使用共享的 `TextureAtlasSprite`/GUI sprite 资源；资源重载时清除缓存。这样不会为每个附属 Mod 写硬编码列表，且同一卷轴在不同界面都能由 ItemStack 数据决定图标。

## 兼容与回退

仅在 `irons_spellbooks` 已加载时启用；非卷轴物品完全走原版模型。空卷轴、`none` 法术、缺少图标纹理或模型构建失败时使用 `irons_spellbooks:item/scroll`。

## 验证标准

- Gradle 构建成功并生成可安装 jar。
- 单元级纯 Java 测试覆盖法术图标路径生成、有效卷轴识别和回退逻辑。
- 客户端启动日志无模型加载异常。
- 主 Mod 火焰法术卷轴显示火焰法术自己的图标；附属 Mod 法术显示附属 Mod 命名空间下的图标。
