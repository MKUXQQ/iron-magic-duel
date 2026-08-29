# 铁魔法决斗（Iron Magic Duel）

面向 **Minecraft 1.21.1 NeoForge** 与 **Iron's Spells 'n Spellbooks** 的法术竞技与决斗模组。提供服务端管理的多场地决斗、玩家单挑、观战、法术书查看、比赛工具、禁法区域、法术兼容修复和性能优化。

当前版本：**9.19**

作者：MKUXQQ

[下载最新版本](https://github.com/MKUXQQ/iron-magic-duel/releases/latest) · [使用文档与 Wiki](https://github.com/MKUXQQ/iron-magic-duel/wiki) · [问题反馈](https://github.com/MKUXQQ/iron-magic-duel/issues)

## 前置与安装

- Minecraft `1.21.1`
- NeoForge `21.1.233` 或更高的 21.1.x 版本
- Iron's Spells 'n Spellbooks `1.21.1-3.16.2` 或更高版本
- Curios API `9.5.1+1.21.1`

从 [Releases](https://github.com/MKUXQQ/iron-magic-duel/releases) 下载 `iron_magic_duel-9.19.jar`，放入客户端与服务器的 `mods` 文件夹。多人游戏时客户端和服务器必须安装相同版本。

## 主要功能

- 服务端管理多个 `duel_N` 决斗组，可无限创建点位和决斗组。
- 玩家可使用 `/spell_duel duel <玩家>` 发起单挑；目标接受后由发起者选择空闲的 `duel_N` 场地，每个场地独立占用。
- 玩家交互器右键其他玩家可发起单挑，或查看其 Curios 法术书栏中当前装备法术书的实际法术。
- 玩家选择器读取服务端全部在线玩家；可分配 A 队与 B 队，并允许人数不足时先保存、稍后开战。
- 管理员玩家选择器优先级最高，可强制结束目标当前的新单挑并立即恢复状态，再进行旧决斗分队。
- 点位工具设置 A/B 出生点；手持工具时只有自己能看到 A 点白色粒子、B 点黑色粒子及组名标签。
- 观战系统：观战玩家不能施法，可退出并恢复进入观战前的位置和游戏模式。
- 对战结束会自动清空玩家和观战状态，但保留 A/B 点位，便于下一次复用同一决斗组。
- 商店与商店编辑器：编辑器内容持久保存，商店可无限获取物品并自动补充；最多 5 页，潜行右键切页。
- 禁法区域工具：右键中心立即创建独立的 `no_cast_N` 区域；可按 ID 单独修改范围或删除，范围内无法施法。
- 饱和度保持满格；玩家受伤后等待 5 秒脱战，第 7 秒开始每 2 秒恢复 5 颗心（10 点生命值）。
- 决斗死亡使用原版死亡与重生流程；重生后的生命不会被决斗回血限制重新写回 0。
- 观战 HUD 显示对战信息；普通玩家不会看到其他玩家的观战 HUD。
- 创造模式物品栏中提供玩家选择器、点位工具、禁法区域工具、商店编辑器和商店。
- 移除铁魔法技能冷却时的额外 ActionBar 文字提示，并为铁魔法声音提供墙体遮挡处理。
- 启动优化：客户端延后非必要的法术图标资源扫描；服务器按需读取决斗/禁法数据、按需创建计算线程，减少启动阶段压力。
- 管理员可使用环形禁锢指令，将其他在线玩家安全排列在自己周围并限制移动、跳跃和施法；新登录玩家也会自动加入。

## 快速开始

1. 管理员输入 `/spell_duel tool` 获取玩家选择器、点位工具和禁法区域工具。
2. 手持点位工具：右键方块设置 A 点，左键方块设置 B 点，潜行右键将 A/B 点绑定为新的 `duel_N`。
3. 手持玩家选择器右键，打开在线玩家列表；左键选入 A 队，右键选入 B 队，然后点击“创建对战”。
4. 管理员输入 `/spell_duel start duel_1` 开始指定决斗。
5. 玩家使用 `/spell_duel spectate duel_1` 进入正在进行的决斗观战。
6. 需要停止时使用 `/spell_duel stop duel_1` 或 `/spell_duel stop all`。
7. 普通玩家可使用 `/spell_duel duel <玩家>` 发起单挑邀请，接受后选择空闲场地。

## 指令

`duel_N` 为决斗组 ID，例如 `duel_1`、`duel_2`。管理员指令需要 OP 权限等级 2。

| 指令 | 权限 | 说明 |
|---|---:|---|
| `/spell_duel start all` | 管理员 | 尝试开始全部配置完成的决斗组。 |
| `/spell_duel start duel_N` | 管理员 | 开始指定决斗。 |
| `/spell_duel stop all` | 管理员 | 停止全部正在进行的决斗，保留点位。 |
| `/spell_duel stop duel_N` | 管理员 | 停止指定决斗，保留点位。 |
| `/spell_duel tool` | 管理员 | 获取决斗工具。 |
| `/spell_duel duel <玩家>` | 玩家 | 向在线玩家发起单挑邀请。 |
| `/spell_duel surround lock` | 管理员 | 将其他在线玩家围成一圈并禁止移动、跳跃和施法。 |
| `/spell_duel surround release` | 创建者 | 解除自己创建的环形禁锢。 |
| `/spell_duel shop` | 管理员 | 获取商店木桶和商店编辑器。 |
| `/spell_duel no_cast select <no_cast_N> <范围>` | 管理员 | 选择并直接修改指定禁法区域的正方形半径；区域 ID 支持 Tab 补全。 |
| `/spell_duel no_cast remove <no_cast_N>` | 管理员 | 删除指定禁法区域；区域 ID 支持 Tab 补全。 |
| `/spell_duel no_cast remove all` | 管理员 | 删除全部禁法区域。 |
| `/spell_duel point duel_N a` | 管理员 | 将当前位置设为指定组 A 点。 |
| `/spell_duel point duel_N b` | 管理员 | 将当前位置设为指定组 B 点。 |
| `/spell_duel clear all` | 管理员 | 删除所有决斗组、玩家和点位；新组重新从 `duel_1` 开始。 |
| `/spell_duel clear group` | 管理员 | 清空全部决斗组的玩家，保留点位。 |
| `/spell_duel clear group duel_N` | 管理员 | 清空指定组玩家，保留该组点位。 |
| `/spell_duel clear point` | 管理员 | 清除全部决斗组点位。 |
| `/spell_duel clear point duel_N` | 管理员 | 清除指定组点位。 |
| `/spell_duel spectate duel_N` | 玩家 | 观战正在进行的指定决斗。 |
| `/spell_duel spectate leave` | 玩家 | 退出观战并恢复状态。 |
| `/spell_duel hud <x> <y>` | 玩家 | 设置观战 HUD 的屏幕坐标。 |
| `/spell_duel fake_players` | 管理员 | 按服务器配置生成假人。 |
| `/ironspell gravity on\|off` | 管理员 | 开关铁魔法投射物重力。 |
| `/spellgravity on\|off` | 管理员 | 投射物重力开关的简写。 |

完整中文说明见 [GitHub Wiki](https://github.com/MKUXQQ/iron-magic-duel/wiki)。

## 9.19 更新重点

- 新增玩家单挑邀请、接受/拒绝与 `duel_N` 空闲场地选择流程。
- 新增玩家交互器与 Curios 法术书栏查看界面。
- 多个单挑场地独立占用；战斗真正结束并释放后才能再次选择。
- 管理员玩家选择器可强制结束新单挑及其延迟恢复，再继续分队。
- 新增环形禁锢命令；支持新登录玩家自动加入、死亡复活后重新锁定及有限安全重试。
- 包含持续喷雾、冰冻、冷却、伤害无敌帧、镜头晃动与附属法术的兼容修复。

## 配置文件

- 客户端：`config/iron_magic_duel-client.toml`
- 专用服务器：`world/serverconfig/iron_magic_duel-server.toml`

客户端和服务端性能优化默认开启，可通过配置文件单独关闭。性能优化不会改变法术伤害、冷却、距离、蓝耗、命中判定或网络行为。

## 构建

```powershell
.\gradlew.bat clean build
```

构建完成后的 JAR 位于 `build/libs`。
