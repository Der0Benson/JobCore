# JobCore

**JobCore** is a modern jobs and quests plugin for Paper servers that combines long-term progression, clear goals, and meaningful server economy gameplay in one package.

Players can level multiple professions at the same time, unlock rewards, complete rotating daily, weekly, and monthly quests, and track their progress through polished menus, BossBars, and leaderboards.

## Features

- **6 playable jobs**
  - Woodcutter
  - Miner
  - Farmer
  - Angler
  - Alchemist
  - Warrior

- **A real progression system**
  - Separate XP values per job
  - Unlockable perks
  - Reward-based level paths
  - Real-time BossBar progress

- **Rotating quest system**
  - Daily, weekly, and monthly quests
  - Active quest selection per cycle
  - Quest NPCs with menu support
  - Per-player quest progress and rewards
  - Individual quest rolls for each player

- **Many quest objective types**
  - Break blocks
  - Place blocks
  - Kill entities
  - Kill bosses
  - Deal damage
  - Travel distance
  - Enter biomes
  - Fish items
  - Use brewing ingredients
  - Brew potions
  - Craft items
  - Smelt items
  - Consume items
  - Enchant items
  - Breed, tame, and shear entities

- **Anti-farm systems**
  - Protection against placed blocks
  - Rapid-kill detection
  - Fishing same-spot checks
  - Brewing and automation protection
  - Tree combo logic for natural woodcutting chains

- **Job path rewards**
  - XP boosters
  - Custom reward books
  - Level path milestones
  - Special unlocks like `Veinminer` and `Timber`

- **Admin and server features**
  - YAML or MySQL storage
  - Snapshot export
  - Debug XP tools
  - Offline player support for stats, XP, and levels
  - Leaderboards for jobs and quests

- **Highly configurable**
  - One job file per profession
  - Configurable UI text
  - Configurable quest pools
  - Custom rewards and perk definitions

- **Optional integrations**
  - PlaceholderAPI
  - LuckPerms

## Integrations

<details>
<summary><strong>Permissions & LuckPerms</strong></summary>

JobCore uses standard Bukkit permissions and can be managed directly through **LuckPerms**.

**Player permissions**

- `jobcore.user`
- `jobcore.command.config`
- `jobcore.command.level`
- `jobcore.menu.leaderboard`
- `jobcore.quest.menu`
- `jobcore.quest.accept`
- `jobcore.quest.abandon`
- `jobcore.quest.claim`

**Admin permissions**

- `jobcore.admin`
- `jobcore.command.info`
- `jobcore.command.reload`
- `jobcore.command.stats`
- `jobcore.command.addxp`
- `jobcore.command.setlevel`
- `jobcore.command.debugxp`
- `jobcore.command.spawnquestnpc`
- `jobcore.command.removequestnpc`
- `jobcore.command.export`
- `jobcore.command.givebooster`

**LuckPerms examples**

```text
/lp group default permission set jobcore.user true
/lp group admin permission set jobcore.admin true
```

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

JobCore ships with its own built-in **PlaceholderAPI expansion**. If PlaceholderAPI is installed, the placeholders register automatically.

**Job placeholders**

- `%jobcore_total_level%`
- `%jobcore_highest_job%`
- `%jobcore_highest_job_level%`
- `%jobcore_job_miner_level%`
- `%jobcore_job_farmer_xp%`
- `%jobcore_job_warrior_progress_percent%`

**Quest placeholders**

- `%jobcore_quests_active%`
- `%jobcore_quests_claimable%`
- `%jobcore_quest_daily_name%`
- `%jobcore_quest_daily_progress%`
- `%jobcore_quest_weekly_required%`
- `%jobcore_quest_monthly_status%`

**In-game test**

```text
/papi parse me %jobcore_total_level%
/papi parse me %jobcore_quest_daily_name%
```

</details>

## Who is JobCore for?

JobCore is built for servers that want more than passive side activities:

- Survival servers
- Citybuild servers
- Economy servers
- SMP servers with long-term progression
- RPG-style servers with quests and NPCs

## Why JobCore?

Many jobs plugins feel outdated, bloated, or hard to customize.  
JobCore focuses on:

- clear progression
- polished menus
- rotating quests
- flexible configuration
- reliable anti-farm systems
- modern integration with PlaceholderAPI and LuckPerms

## Compatibility

- **Server software:** Paper
- **Minecraft version:** 1.21.x

## Notes

- PlaceholderAPI is **optional**, but supported.
- LuckPerms is **optional**, but fully supported through Bukkit permissions.
- MySQL is **optional**. YAML works out of the box without an external database.
- The default configuration already includes jobs, quests, menus, rewards, boosters, and quest NPC support.

## Commands

- `/level` opens the level menu
- `/jobcore` opens the main administration command

## Short Modrinth Summary

**JobCore** is a configurable jobs and quests plugin for Paper 1.21.x with 6 professions, rotating daily, weekly, and monthly quests, level paths, perks, BossBar progress, leaderboards, anti-farm systems, YAML/MySQL support, LuckPerms-compatible permissions, built-in PlaceholderAPI support, XP booster events, and custom unlocks like Veinminer and Timber.
