# Iron Magic Duel

NeoForge 1.21.1 custom duel HUD and performance helper for Iron's Spells 'n Spellbooks.

Author: MKUXQQ

## Multiplayer spell duels

The mod also provides server-authoritative multiplayer spell duels. Give the two wooden-looking selectors with:

```mcfunction
/give @p iron_magic_duel:duel_player_selector
/give @p iron_magic_duel:duel_point_selector
```

With the player selector, right-click players for team A and left-click players for team B. Crouch-right-click air to create a group. With the point selector, right-click the A spawn point and left-click the B spawn point, then crouch-right-click air to bind them to the current group.

Commands:

```mcfunction
/spell_duel start all
/spell_duel start <group>
/spell_duel display on|off
/spell_duel clear players
/spell_duel clear points
/spell_duel clear point <group>
```

Administrators crouch-right-click a `minecraft:sculk_catalyst` to cycle its selected spectator group. Other players right-click it to enter spectator mode. At the end of a duel, all participants and spectators return to their saved game modes and positions, and the winner is announced in chat.

## Included optimizations

- Client-side spell-resource preloading after resource reload.
- `/ironspell gravity on|off` command to toggle gravity for Iron's Spells 'n Spellbooks projectiles.
- Per-frame spell-particle admission budget that can be used by compatible spell render integrations.
- Server-side tick budget for deferrable pure spell calculations.
- Immutable spell metadata cache and a bounded daemon worker pool for copied pure calculations.
- Lifecycle cleanup on server stop and client resource reload.

The mod does not change spell damage, cooldown, range, mana cost, hit results, or network behavior. World and entity mutations remain on the Minecraft server thread.

## Installation

Put the built jar from `build/libs` into the `mods` folder of a NeoForge 1.21.1 instance. The mod requires Iron's Spells 'n Spellbooks 1.21.1-3.16.2 or newer in the 1.21.1 line.

## Configuration

- Client: `config/iron_magic_duel-client.toml`
- Server: `world/serverconfig/iron_magic_duel-server.toml` on a dedicated server, or the integrated server's server config.

The defaults enable safe caching and resource preloading. Set `enableClientOptimizations` or `enableServerOptimizations` to `false` to disable either side. Set `maxParticlesPerFrame` or `maxSpellScanMillisPerTick` to `0` to remove that budget. `debugPerformanceLogging` is disabled by default.

The client particle admission API is deliberately conservative and does not remove ordinary Minecraft particles. It is intended for spell-effect integrations and future compatible Iron spell render hooks.
