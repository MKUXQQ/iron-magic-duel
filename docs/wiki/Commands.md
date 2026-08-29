# 指令列表

`duel_N` 表示决斗组，例如 `duel_1`、`duel_2`。标记为管理员的指令需要权限等级 2。

| 指令 | 权限 | 说明 |
|---|---:|---|
| `/spell_duel duel <玩家>` | 玩家 | 发起单挑邀请。 |
| `/spell_duel spectate <duel_N>` | 玩家 | 观战指定决斗。 |
| `/spell_duel spectate leave` | 玩家 | 退出观战并恢复状态。 |
| `/spell_duel hud <x> <y>` | 玩家 | 设置观战 HUD 坐标。 |
| `/spell_duel start <duel_N>` | 管理员 | 开始指定决斗。 |
| `/spell_duel start all` | 管理员 | 尝试开始全部决斗组。 |
| `/spell_duel stop <duel_N>` | 管理员 | 停止指定决斗并保留点位。 |
| `/spell_duel stop all` | 管理员 | 停止全部进行中的决斗。 |
| `/spell_duel tool` | 管理员 | 获取玩家选择器、点位工具等赛事工具。 |
| `/spell_duel point <duel_N> a` | 管理员 | 设置 A 点。 |
| `/spell_duel point <duel_N> b` | 管理员 | 设置 B 点。 |
| `/spell_duel shop` | 管理员 | 获取商店与商店编辑器。 |
| `/spell_duel no_cast select <no_cast_N> [范围]` | 管理员 | 选择或修改禁法区域。 |
| `/spell_duel no_cast remove <no_cast_N>` | 管理员 | 删除指定禁法区域。 |
| `/spell_duel no_cast remove all` | 管理员 | 删除全部禁法区域。 |
| `/spell_duel display on\|off` | 管理员 | 开关决斗显示。 |
| `/spell_duel clear all` | 管理员 | 删除全部决斗组与点位。 |
| `/spell_duel clear group [duel_N]` | 管理员 | 清空全部或指定组玩家，保留点位。 |
| `/spell_duel clear point [duel_N]` | 管理员 | 清除全部或指定组点位。 |
| `/spell_duel surround lock` | 管理员 | 开启环形禁锢。 |
| `/spell_duel surround release` | 创建者 | 解除自己创建的环形禁锢。 |

环形禁锢开启后，其他在线玩家不能移动、跳跃或施法；新登录玩家也会自动加入。只有创建者可主动解除，创建者退出、死亡或换维时自动释放。
