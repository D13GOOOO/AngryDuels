# AngryDuels

A realistic duel system for modern Paper servers.

AngryDuels lets players challenge each other to 1v1 duels and team
battles in a dedicated arena world, with full inventory snapshots,
configurable kits, persistent parties, spectator mode, rewards, MySQL
statistics, in-game leaderboards and PlaceholderAPI support.

## Features

### Duels

- **Duel requests** with clickable accept/deny messages and cooldowns
- **Three match modes**: 1v1, team-vs-team between two parties, and
  free-for-all inside a single party
- **Dedicated duel world** generated automatically as a void world
- **Named arenas** managed in-game with legacy spawns, per-team spawn
  lists and a dedicated spectator spawn
- **Full inventory snapshot** restored after every duel, including
  armor, offhand, ender chest, XP, potions, health, hunger and gamemode
- **Crash recovery**: if the server stops while a duel is running, the
  player state is restored on next join
- **Countdown isolation**: damage, movement, teleport, item drop and
  world modification are blocked until the fight begins

### Parties

- **Persistent parties** stored in MySQL, up to 20 members each
- **Leader hotbar items** for fight setup, info, invite and disband
- **Internal matches**: free-for-all or automatic team split
- **Cross-party challenges** with team-aware request handling
- **Public parties** joinable with `/party join <leader>`
- **Leader transfer, kick, invite and promote** fully managed in-game

### Kits

- **Configurable kits** defined in `kits.yml`, with per-kit permission
  nodes and a visual selection GUI
- **In-game kit editor** to rearrange items into your preferred slots,
  stored per player in MySQL
- **Starter kits** shipped by default: `sword` and `netheritepot`

### Rewards

- **Five reward types**: console commands, items, money through Vault,
  experience and MiniMessage broadcasts
- **Per-kit overrides**: a kit-specific reward list fully replaces the
  default list for matches played with that kit
- **Placeholder support** in every reward: `%winner%`, `%loser%`,
  `%arena%`, `%kit%`, `%reason%`, `%duration%`
- **Optional cooldown** to prevent reward farming

### Statistics and leaderboards

- **MySQL statistics** with a normalized schema, async writes and an
  offline retry queue that survives temporary outages
- **Leaderboard categories**: wins, losses, played, streak, best streak
  and K/D ratio
- **Command output** (`/duel top <category> [page]`) or **paginated GUI**
  (`/duel top <category> gui`)
- **Per-player stats** with win rate, K/D ratio and current / best
  streak

### Spectator mode

- **Watch any active duel** with `/duel spectate <player>`
- **Action bar** shows live team health or FFA survivors
- **Movement radius** keeps spectators near the arena, configurable via
  `spectator.radius-blocks`
- **Chat is blocked** while spectating to keep fights clean
- **Automatic cleanup** when the watched match ends

### Integrations

- **PlaceholderAPI** expansion (`angryduels`) with per-player stats and
  leaderboard rank
- **Vault** support for money rewards
- **Event API** for external plugins (`DuelRequestEvent`,
  `DuelStartEvent`, `DuelEndEvent`, party events)

## Requirements

| Component | Version |
|---|---|
| Server | Paper 1.21 or newer (compatible with `api-version: 1.21`) |
| Java | 21 or newer |
| Database | MySQL 5.7+ or MariaDB 10.2+ (optional, required for stats and parties) |
| Vault | Optional, for money rewards |
| PlaceholderAPI | Optional, for placeholders |

Folia is **not** supported (`folia-supported: false`).

## Installation

1. Drop `AngryDuels-x.y.z.jar` into your server's `plugins/` folder.
2. Start the server once to generate the default configuration files.
3. (Optional) Edit `plugins/AngryDuels/config.yml` to point at your
   MySQL database. If the storage section is left as default or the
   database is unreachable, the plugin runs without statistics and
   without persistent parties.
4. (Optional) Add your kits to `plugins/AngryDuels/kits.yml` or use the
   two built-in kits as a starting point.
5. Restart the server.

On first startup the plugin creates a `duels_world` void world. Do not
add it to `bukkit.yml`; the plugin loads it explicitly.

## Commands

### Duel commands

Root command: `/duels`, with `/duel` and `/d` as aliases.

| Command | Description | Permission |
|---|---|---|
| `/duel help` | Show the help page | — |
| `/duel <player> [kit]` | Send a duel request. Without a kit argument, opens the kit selection GUI | — |
| `/duel accept` | Accept the pending request | — |
| `/duel deny` | Deny the pending request | — |
| `/duel forfeit` | Forfeit your active duel | — |
| `/duel spectate <player>` | Spectate an active duel | — |
| `/duel unspectate` | Stop spectating | — |
| `/duel stats [player]` | Show duel statistics | — |
| `/duel top <category> [page\|gui]` | Show the leaderboard in chat or in a GUI | — |
| `/duel kiteditor <kit>` | Open the kit editor for a kit you can use | kit permission |
| `/duel kitreset <kit>` | Reset your personal layout to the default | — |
| `/duel reload` | Reload `config.yml` and `messages.yml` | `duels.admin` |
| `/duel arena <sub>` | Manage arenas (see below) | `duels.admin` |
| `/duel stats reset <player>` | Reset a player's statistics | `duels.admin` |
| `/duel stats resetall` | Reset every statistic | `duels.admin` |

### Arena subcommands

| Command | Description |
|---|---|
| `/duel arena list` | List registered arenas with their state and team spawn counts |
| `/duel arena create <id>` | Create a new arena at your current position |
| `/duel arena setspawn <id> <1\|2>` | Set a legacy 1v1 spawn |
| `/duel arena setspectator <id>` | Set the spectator spawn |
| `/duel arena delete <id>` | Delete an arena (refused if occupied) |
| `/duel arena addspawn <id> <1\|2>` | Add a team spawn at your position |
| `/duel arena delspawn <id> <1\|2> <index>` | Remove a team spawn by index |
| `/duel arena clearspawns <id>` | Remove all team spawns |
| `/duel arena teamspawns <id>` | List team spawns with coordinates |
| `/duel arena reload` | Reload arenas from `config.yml` |

### Party commands

Root command: `/party`, with `/p` as alias.

| Command | Description |
|---|---|
| `/party create` | Create a new party (you become the leader) |
| `/party disband` | Disband your party (leader only) |
| `/party invite <player>` | Invite an online player (leader only) |
| `/party accept` | Accept a pending invitation |
| `/party deny` | Deny a pending invitation |
| `/party kick <player>` | Remove a member (leader only) |
| `/party leave` | Leave your party |
| `/party transfer <player>` | Transfer leadership to another member (leader only) |
| `/party list` | List every member with their online state |
| `/party info` | Show a compact summary of your party |
| `/party announce <message>` | Send a message to every online member |
| `/party public [on\|off]` | Toggle or set the public flag (leader only) |
| `/party join <leader>` | Join a public party by its leader's name |
| `/party ffa` | Open the kit selector to start a free-for-all (leader only) |
| `/party split` | Open the kit selector to start a split match (leader only) |
| `/party challenge <leader>` | Open the kit selector to challenge another party (leader only) |
| `/party help` | Show the party help page |

### Party hotbar items

Once a party is created, the leader receives four items in their
hotbar:

| Item | Action |
|---|---|
| Party Fight | Opens the mode selection (FFA / Split) |
| Party Info | Opens the party info GUI |
| Invite | Opens the invite GUI to send invitations in bulk |
| Disband | Opens the disband confirmation GUI |

Party items are locked to their slots and cannot be dropped, moved,
dragged or swapped.

Leaderboard categories: `wins`, `losses`, `played`, `streak`,
`beststreak`, `kdr`.

Full permission reference: see [PERMISSIONS.md](PERMISSIONS.md).

## Configuration

The plugin ships with three configuration files in `plugins/AngryDuels/`:

- **`config.yml`** — duel behaviour, world, storage, rewards,
  statistics, spectator
- **`messages.yml`** — every user-facing string, in MiniMessage format
- **`kits.yml`** — kit definitions, slot by slot

Every file can be reloaded in-game with `/duel reload`. Arenas are also
stored inside `config.yml` and can be reloaded with
`/duel arena reload`.

### Key options

| Path | Description |
|---|---|
| `settings.debug` | Enable verbose debug logging |
| `duel.request-timeout-seconds` | How long a duel request stays valid |
| `duel.countdown-seconds` | Pre-fight countdown duration |
| `duel.cooldown-seconds` | Cooldown between outgoing requests |
| `world.name` | Name of the dedicated duel world |
| `world.auto-create` | Generate the duel world if missing |
| `storage.mysql.*` | MySQL connection settings |
| `stats.enabled` | Master switch for MySQL statistics |
| `stats.leaderboard.entries-per-page` | Entries shown per leaderboard page |
| `stats.leaderboard.refresh-minutes` | How often the leaderboard cache refreshes |
| `spectator.radius-blocks` | Maximum distance a spectator may stray from the spawn |
| `rewards.enabled` | Master switch for rewards |
| `rewards.grant-delay-ticks` | Delay before granting rewards |
| `rewards.cooldown-seconds` | Minimum time between two rewarded wins |

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

Supported types:

- `console` — dispatch a command as the console sender (`command`)
- `item` — give an item using the `kits.yml` item format
  (`material`, `amount`, `enchants`, `name`, `unbreakable`)
- `money` — deposit through Vault (`amount`, requires Vault + economy)
- `xp` — grant experience (`points` and/or `levels`)
- `broadcast` — send a MiniMessage line to everyone (`message`)

Placeholders available in every reward:
`%winner%`, `%loser%`, `%arena%`, `%kit%`, `%reason%`, `%duration%`.

### Statistics

Statistics require a MySQL database. The plugin creates the database
and the tables on first startup if the configured user has the required
privileges. Eight tables are used:

- `duels_players` — player roster (uuid, username, first / last seen)
- `duels_stats` — aggregate counters (wins, losses, kills, deaths,
  forfeits, quits, streak, best streak)
- `duels_kit_stats` — per-kit win / loss counters
- `duels_history` — one row per concluded match
- `duels_player_kits` — per-player kit layout overrides
- `duels_parties` — persistent parties
- `duels_party_members` — party membership with join timestamps
- `duels_party_invites` — pending party invitations

If MySQL is unreachable at startup, the plugin runs normally and stats
and parties are disabled. If MySQL goes down while the server is
running, write operations are queued and retried every ten seconds
until the connection is restored.

### Spectator

Spectators are constrained to a configurable radius around the arena's
spectator spawn:

```yaml
spectator:
  radius-blocks: 50
```

When a spectator tries to move beyond the radius, their destination is
clamped to 95% of the allowed area, keeping them near the fight.

### Parties

A party holds up to 20 members. Leaders can toggle the public flag to
let anyone join with `/party join <leader>`. Party data is stored in
MySQL, so memberships survive server restarts. The public flag is
in-memory only and reverts to private on restart.

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

Players can rearrange kits for themselves with `/duel kiteditor <kit>`.
Personal layouts are stored per player in MySQL and never affect the
shared kit definition.

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