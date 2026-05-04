# JobCore Permissions

JobCore nutzt normale Bukkit-Permissions. LuckPerms kann diese Nodes direkt verwalten, es ist keine separate Expansion oder Bridge nötig.

## Spielerrechte

- `jobcore.user`
- `jobcore.command.config`
- `jobcore.command.level`
- `jobcore.menu.leaderboard`
- `jobcore.quest.menu`
- `jobcore.quest.accept`
- `jobcore.quest.abandon`
- `jobcore.quest.claim`

## Adminrechte

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

## Legacy-Nodes

- `jobcore.config`
- `jobcore.level`
- `customjobs.admin`
- `customjobs.config`
- `customjobs.level`

## LuckPerms Beispiele

Default-Spieler:

```text
/lp group default permission set jobcore.user true
```

Wenn du Quests nur lesen, aber nicht annehmen lassen willst:

```text
/lp group default permission set jobcore.quest.menu true
/lp group default permission set jobcore.quest.accept false
/lp group default permission set jobcore.quest.abandon false
/lp group default permission set jobcore.quest.claim false
```

Moderator mit Einsicht in Stats, aber ohne XP-Manipulation:

```text
/lp group mod permission set jobcore.command.stats true
/lp group mod permission set jobcore.command.info true
```

Voller Adminzugriff:

```text
/lp group admin permission set jobcore.admin true
```
