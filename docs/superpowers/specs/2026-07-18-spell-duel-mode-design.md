# Multiplayer Spell Duel Mode

## Goal

为 Minecraft 1.21.1 NeoForge 21.1.233 的 Iron Spellcasting Performance 增加服务器权威的多人法术决斗模式。

## Player and Point Selection

- 玩家选择器使用木棍外形。
- 右键玩家加入 A 队。
- 左键玩家加入 B 队。
- 每队人数不限。
- 潜行右键空地创建当前决斗组。
- 点位选择器右键记录 A 点，左键记录 B 点。
- 潜行右键空地将两个点位绑定到当前决斗组。

## Commands

- `/spell_duel start all`: 启动所有配置完整的组；不完整组跳过并提示管理员。
- `/spell_duel start <group>`: 启动指定组并传送该组玩家。
- `/spell_duel display on|off`: 控制全体玩家头顶法术显示。
- `/spell_duel clear players`: 清理所有玩家的决斗组绑定。
- `/spell_duel clear points`: 清理所有保存点位。
- `/spell_duel clear point <pointId>`: 清理单独点位。

## Arena and Spectating

- 观战方块使用 `minecraft:sculk_catalyst` 的原版模型。
- 管理员潜行右键方块，在绑定的决斗组之间切换当前观战组。
- 普通玩家右键方块进入当前组旁观。
- 旁观界面显示 A/B 队玩家名称、血量、法术书内容、当前法术和冷却。
- 全体玩家头顶法术显示由管理员指令统一开关。

## Lifecycle

- `/spell_duel start` 将参赛玩家保存的游戏模式和位置记录下来，然后分别传送到 A/B 点位。
- A 队或 B 队全部玩家死亡时，当前组结束并判定另一队胜利。
- 参赛玩家和该组观战玩家一起恢复决斗前的游戏模式，并传送回决斗前位置。
- 聊天框广播决斗组名称、胜利队伍和结束结果。
- 其他决斗组继续独立运行。

## Architecture

服务器保存组、队伍、点位、运行状态、原始玩家状态和观战者；所有传送、模式切换和胜负判定在服务器线程执行。客户端收到同步数据后渲染头顶法术和观战面板，法术书数据从 Iron’s Spellbooks 的玩家法术数据/法术书容器读取。

## Acceptance Criteria

1. 多个组可以同时配置和运行，组之间状态互不覆盖。
2. `start all` 只启动配置完整的组，并为不完整组提供原因。
3. 决斗结束后所有相关玩家恢复原游戏模式和原位置。
4. 聊天框显示胜利队伍。
5. 全体头顶法术显示可以通过指令开关。
6. 构建和自测通过，兼容 NeoForge 21.1.233。
