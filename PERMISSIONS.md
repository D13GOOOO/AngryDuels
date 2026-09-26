# AngryDuels — Permissions

This document lists every permission node used by the plugin, its
default value, and where it is checked.

## Overview

| Node | Description | Default |
|---|---|---|
| `duels.use` | Base permission node for player-facing duel commands | `true` |
| `duels.admin` | Access to administrative subcommands | `op` |
| `duels.kit.<kitId>` | Permission to use a specific kit | `op` |

## `duels.use`

Declared in `plugin.yml` with `default: true` and intended as the base
gate for every player-facing duel command.

The commands conceptually covered by this node are:

- `/duel <player> [kit]` — send a duel request
- `/duel accept` — accept an incoming request
- `/duel deny` — deny an incoming request
- `/duel forfeit` — forfeit an active duel
- `/duel spectate <player>` — spectate an active duel
- `/duel unspectate` — stop spectating
- `/duel stats [player]` — show statistics
- `/duel top <category> [page|gui]` — show the leaderboard
- `/duel kiteditor <kit>` — open the kit editor
- `/duel kitreset <kit>` — reset a personal kit layout
- `/duel help` — show the help page

> **Note:** the current codebase does not enforce `duels.use` with an
> explicit `hasPermission` check on each player-facing subcommand. The
> node exists in `plugin.yml` for future gating and for permission
> plugins to display. Administrators who need to disable duels for a
> group today should deny access at the command level (for example via
> your permission plugin's command controls), or wait for a future
> release that wires the node through the command handlers.

## `duels.admin`

Granted to operators by default. Required for:

- `/duel reload` — reload `config.yml` and `messages.yml`
- `/duel arena list` — list arenas
- `/duel arena create <id>` — create a new arena
- `/duel arena setspawn <id> <1|2>` — set a legacy 1v1 spawn
- `/duel arena setspectator <id>` — set the spectator spawn
- `/duel arena delete <id>` — delete an arena (refused if occupied)
- `/duel arena addspawn <id> <1|2>` — add a team spawn
- `/duel arena delspawn <id> <1|2> <index>` — remove a team spawn
- `/duel arena clearspawns <id>` — remove all team spawns
- `/duel arena teamspawns <id>` — list team spawns
- `/duel arena reload` — reload arenas from config
- `/duel stats reset <player>` — reset a player's statistics
- `/duel stats resetall` — wipe every statistic

This node also hides administrative entries from tab completion for
players who do not have it.

## `duels.kit.<kitId>`

One node per kit, generated automatically from the `permission` key in
each kit definition. If the key is omitted in `kits.yml`, the plugin
defaults to `duels.kit.<kitId>`.

For the kits shipped by default:

| Node | Kit |
|---|---|
| `duels.kit.sword` | Sword |
| `duels.kit.netheritepot` | Netherite Pot |

Kits the player does not have permission to use are still shown in the
selection GUI, but cannot be clicked. The lore line changes to
"No permission" for those entries.

To grant a kit to a group:

```
/lp group default permission set duels.kit.sword true
```

To revoke a kit from a specific player:

```
/lp user <player> permission set duels.kit.netheritepot false
```

## Party commands

The `/party` command tree has **no dedicated permission node** in the
current release. All subcommands are available to every player:

- `/party create`, `/party disband`, `/party invite`, `/party accept`,
  `/party deny`, `/party kick`, `/party leave`, `/party transfer`,
  `/party list`, `/party info`, `/party announce`, `/party public`,
  `/party join`, `/party ffa`, `/party split`, `/party challenge`,
  `/party help`

Leader-only actions (disband, invite, kick, transfer, public toggle,
ffa, split, challenge) are enforced by the plugin through runtime
checks: the caller must be the current leader of their party. Denying
the command outright for a group requires a command-level restriction
in your permissions plugin.

## Notes

- The plugin reads permission nodes from the `permission` field in
  `kits.yml` at load time, so custom nodes are fully supported. The
  example above uses the default naming convention only as a suggestion.
- Wildcards are not managed by the plugin itself. If your permissions
  plugin supports them (LuckPerms does), you can use `duels.kit.*` to
  grant every kit at once.
- `duels.use` and `duels.admin` are declared in `plugin.yml`, so
  permissions plugins pick them up automatically on first install.