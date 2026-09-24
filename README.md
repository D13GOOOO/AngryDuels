# AngryDuels

A realistic duel system for modern Paper servers.

AngryDuels lets players challenge each other to 1v1 duels in a dedicated
arena world, with full inventory snapshots, configurable kits, rewards,
MySQL statistics, in-game leaderboards and PlaceholderAPI support.

## Features

- **Duel requests** with clickable accept/deny messages and cooldowns
- **Dedicated duel world** generated automatically as a void world
- **Named arenas** managed in-game with two spawn points each
- **Full inventory snapshot** restored after every duel, including
  armor, offhand, ender chest, XP, potions, health, hunger and gamemode
- **Crash recovery**: if the server stops while a duel is running, the
  player state is restored on next join
- **Configurable kits** defined in `kits.yml`, with per-kit permission
  and a visual selection GUI
- **Reward system** supporting console commands, items, money through
  Vault, experience and broadcasts, scoped globally or per kit
- **MySQL statistics** with normalized schema, async writes and a retry
  queue when the database is temporarily unreachable
- **Leaderboards** as a command (`/duel top`) or as a paginated GUI
- **PlaceholderAPI** integration for scoreboards and holograms
- **Countdown isolation**: damage, movement, teleport, item drop and
  world modification are blocked until the fight begins

## Requirements

| Component | Version |
|---|---|
| Server | Paper 1.21.11 (or compatible) |
| Java | 21 or newer |
| Database | MySQL 5.7+ or MariaDB 10.2+ (optional) |
| Vault | Optional, for money rewards |
| PlaceholderAPI | Optional, for placeholders |

## Installation

1. Drop `AngryDuels-x.y.z.jar` into your server's `plugins/` folder.
2. Start the server once to generate the default configuration files.
3. (Optional) Edit `plugins/AngryDuels/config.yml` to point at your
   MySQL database. If you leave the storage section empty, the plugin
   runs without statistics.
4. (Optional) Add your kits to `plugins/AngryDuels/kits.yml` or use the
   two built-in kits as a starting point.
5. Restart the server.

On first startup the plugin creates a `duels_world` void world. Do not
add it to `bukkit.yml`; the plugin loads it explicitly.

## Commands

All commands use the root `/duels`, with `/duel` and `/d` as aliases.

| Command | Description | Permission |
|---|---|---|
| `/duel help` | Show the help page | `duels.use` |
| `/duel <player> [kit]` | Send a duel request. Without a kit, opens the kit selection GUI | `duels.use` |
| `/duel accept` | Accept the pending request | `duels.use` |
| `/duel deny` | Deny the pending request | `duels.use` |
| `/duel forfeit` | Forfeit your active duel | `duels.use` |
| `/duel stats [player]` | Show duel statistics | `duels.use` |
| `/duel top <category> [page\|gui]` | Show the leaderboard | `duels.use` |
| `/duel reload` | Reload config and messages | `duels.admin` |
| `/duel arena <list\|create\|setspawn\|delete\|reload>` | Manage arenas | `duels.admin` |
| `/duel stats reset <player>` | Reset a player's statistics | `duels.admin` |
| `/duel stats resetall` | Reset every statistic | `duels.admin` |

Leaderboard categories: `wins`, `losses`, `played`, `streak`,
`beststreak`, `kdr`.

Full permission reference: see [PERMISSIONS.md](PERMISSIONS.md).

## Configuration

The plugin ships with three configuration files in `plugins/AngryDuels/`:

- **`config.yml`** — duel behaviour, storage, rewards, statistics
- **`messages.yml`** — every user-facing string, in MiniMessage format
- **`kits.yml`** — kit definitions, slot by slot

Every file can be reloaded in-game with `/duel reload`. The kit list is
also read from disk on load.

### Rewards

Rewards are organized in two scopes:

```yaml
rewards:
  default:        # used when no kit-specific override is present
    - type: broadcast
      message: "<white>%winner%</white> defeated <white>%loser%</white>!"
    - type: xp
      points: 30
  kits:
    sword:        # overrides the default list entirely
      - type: xp
        points: 50
```

Supported types: `console`, `item`, `money`, `xp`, `broadcast`.

Placeholders available in every reward:
`%winner%`, `%loser%`, `%arena%`, `%kit%`, `%reason%`, `%duration%`.

### Statistics

Statistics require a MySQL database. The plugin creates the database and
the tables on first startup if the configured user has the required
privileges. Four tables are used:

- `duels_players` — player roster
- `duels_stats` — aggregate counters
- `duels_kit_stats` — per-kit counters
- `duels_history` — match history

If MySQL is unreachable at startup, the plugin runs normally and stats
are disabled. If MySQL goes down while the server is running, write
operations are queued and retried every ten seconds until the connection
is restored.

## Kits

Kits live in `kits.yml`. Each item is defined by its exact slot in the
player inventory (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand):

```yaml
kits:
  sword:
    display-name: "Sword"
    permission: duels.kit.sword
    aliases: []
    icon:
      type: DIAMOND_SWORD
    slots:
      0:
        type: DIAMOND_SWORD
        enchants:
          unbreaking: 3
```

The optional `icon` block defines what the selection GUI shows; if it is
missing, the item in slot 0 is used as a fallback.

## PlaceholderAPI

When PlaceholderAPI is installed, the plugin registers an expansion with
the identifier `angryduels`. Available placeholders include:

```
%angryduels_wins%
%angryduels_losses%
%angryduels_played%
%angryduels_kills%
%angryduels_deaths%
%angryduels_forfeits%
%angryduels_quits%
%angryduels_streak%
%angryduels_best_streak%
%angryduels_winrate%
%angryduels_kdr%
%angryduels_rank_wins%
```

If the database is not available, placeholders return an empty string
rather than `0`, so scoreboards do not display misleading numbers.

## Building from source

```bash
git clone https://github.com/angryguyy/AngryDuels.git
cd AngryDuels
./gradlew clean build
```

The compiled jar is written to `build/libs/AngryDuels-x.y.z.jar`.

Requirements: JDK 21, Gradle wrapper included in the repository.

## License

See [LICENSE](LICENSE) if present, otherwise the project is provided
as-is for personal and community use.