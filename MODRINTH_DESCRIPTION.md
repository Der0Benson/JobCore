# JobCore

**JobCore** ist ein modernes Jobs- und Quest-System für Paper-Server, das langfristigen Progress, klare Ziele und eine sinnvolle Server-Ökonomie in einem Plugin bündelt.

Spieler leveln mehrere Berufe parallel, schalten Belohnungen frei, erledigen rotierende Daily-, Weekly- und Monthly-Quests und verfolgen ihren Fortschritt über übersichtliche Menüs, BossBars und Bestenlisten.

## Features

- **6 spielbare Jobs**
  - Holzfäller
  - Miner
  - Farmer
  - Angler
  - Alchemist
  - Krieger

- **Level-System mit echtem Fortschritt**
  - Eigene XP-Werte pro Job
  - Freischaltbare Perks
  - Levelpfade mit Belohnungen
  - BossBar-Fortschritt in Echtzeit

- **Quest-System mit Rotation**
  - Daily-, Weekly- und Monthly-Quests
  - Aktive Quest-Auswahl pro Zyklus
  - Quest-NPCs mit Menü
  - Quest-Fortschritt und Belohnungen pro Spieler

- **Neue Objective-Typen**
  - Block abbauen
  - Block platzieren
  - Entity töten
  - Item angeln
  - Brau-Zutat verwenden
  - Item craften
  - Item schmelzen
  - Item konsumieren
  - Item verzaubern

- **Anti-Farm-Mechaniken**
  - Schutz gegen platzierte Blöcke
  - Rapid-Kill-Erkennung
  - Fishing-Same-Spot-Checks
  - Brewing-/Automation-Schutz
  - Tree-Combo-Logik für natürliche Holzfäller-Ketten

- **Admin- und Server-Features**
  - YAML- oder MySQL-Speicher
  - Snapshot-Export
  - Debug-XP
  - Offline-Spieler-Support für Stats, XP und Level
  - Leaderboards für Jobs und Quests

- **Hohe Anpassbarkeit**
  - Job-Dateien pro Beruf
  - Anpassbare UI-Texte
  - Konfigurierbare Quest-Pools
  - Eigene Reward- und Perk-Definitionen

- **Optionale Integrationen**
  - PlaceholderAPI
  - LuckPerms

## Integrationen

<details>
<summary><strong>Permissions & LuckPerms</strong></summary>

JobCore nutzt normale Bukkit-Permissions und kann direkt mit **LuckPerms** verwaltet werden.

**Spielerrechte**

- `jobcore.user`
- `jobcore.command.config`
- `jobcore.command.level`
- `jobcore.menu.leaderboard`
- `jobcore.quest.menu`
- `jobcore.quest.accept`
- `jobcore.quest.abandon`
- `jobcore.quest.claim`

**Adminrechte**

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

**LuckPerms-Beispiele**

```text
/lp group default permission set jobcore.user true
/lp group admin permission set jobcore.admin true
```

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

JobCore bringt eine eigene **PlaceholderAPI-Expansion** direkt mit. Wenn PlaceholderAPI installiert ist, werden die Platzhalter automatisch registriert.

**Job-Placeholder**

- `%jobcore_total_level%`
- `%jobcore_highest_job%`
- `%jobcore_highest_job_level%`
- `%jobcore_job_miner_level%`
- `%jobcore_job_farmer_xp%`
- `%jobcore_job_warrior_progress_percent%`

**Quest-Placeholder**

- `%jobcore_quests_active%`
- `%jobcore_quests_claimable%`
- `%jobcore_quest_daily_name%`
- `%jobcore_quest_daily_progress%`
- `%jobcore_quest_weekly_required%`
- `%jobcore_quest_monthly_status%`

**Test im Spiel**

```text
/papi parse me %jobcore_total_level%
/papi parse me %jobcore_quest_daily_name%
```

</details>

## Für wen ist JobCore gedacht?

JobCore ist für Server gedacht, die ihren Spielern mehr als nur passive Nebenaktivitäten geben wollen:

- Survival-Server
- Citybuild-Server
- Economy-Server
- SMP-Server mit Langzeitprogress
- RPG-nahe Konzepte mit Quests und NPCs

## Warum JobCore?

Viele Job-Plugins wirken entweder alt, überladen oder schwer anpassbar.  
JobCore setzt stattdessen auf:

- klare Progression
- saubere Menüs
- rotierende Quests
- flexible Konfiguration
- robuste Anti-Farm-Systeme
- moderne Integration mit PlaceholderAPI und LuckPerms

## Kompatibilität

- **Server-Software:** Paper
- **Minecraft-Version:** 1.21.x

## Hinweise

- PlaceholderAPI ist **optional**, wird aber unterstützt.
- LuckPerms ist **optional**, wird aber vollständig über Bukkit-Permissions unterstützt.
- MySQL ist **optional**, YAML funktioniert direkt ohne externe Datenbank.
- Die Standard-Konfiguration liefert bereits Jobs, Quests, Menüs und Belohnungen mit.

## Befehle

- `/level` öffnet das Level-Menü
- `/jobcore` öffnet die Hauptverwaltung

## Kurzfassung für Modrinth

**JobCore** ist ein konfigurierbares Jobs- und Quest-Plugin für Paper 1.21.x mit 6 Berufen, rotierenden Daily-/Weekly-/Monthly-Quests, Levelpfaden, Perks, BossBar-Fortschritt, Leaderboards, Anti-Farm-Mechaniken, YAML/MySQL-Support, LuckPerms-kompatiblen Permissions und optionaler PlaceholderAPI-Integration.
