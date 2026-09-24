# AngryDuels — Permissions

This document lists every permission node used by the plugin, its
default value, and where it is checked.

## Overview

| Node | Description | Default |
|---|---|---|
| `duels.use` | Base permission to run player-facing duel commands | `true` |
| `duels.admin` | Access to administrative subcommands | `op` |
| `duels.kit.<kitId>` | Permission to use a specific kit | `op` |

## `duels.use`

Granted to every player by default. Required for:

- `/duel <player> [kit]` — send a duel request
- `/duel accept` — accept an incoming request
- `/duel deny` — deny an incoming request
- `/duel forfeit` — forfeit an active duel
- `/duel stats` — show statistics
- `/duel top` — show the leaderboard
- `/duel help` — show the help page

Revoke this node to disable duelling for a group or a single player
without touching the configuration.

## `duels.admin`

Granted to operators by default. Required for:

- `/duel reload` — reload `config.yml` and `messages.yml`
- `/duel arena list` — list arenas
- `/duel arena create <id>` — create a new arena
- `/duel arena setspawn <id> <1|2>` — set a spawn point
- `/duel arena delete <id>` — delete an arena
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
"no permission" for those entries.

To grant a kit to a group:

```
/lp group default permission set duels.kit.sword true
```

To revoke a kit from a specific player:

```
/lp user <player> permission set duels.kit.netheritepot false
```

## Notes

- The plugin also reads permissions from the `permission` field in
  `kits.yml` at load time, so custom nodes are fully supported. The
  example above uses the default naming convention only as a suggestion.
- Wildcards are not managed by the plugin itself. If your permissions
  plugin supports them (LuckPerms does), you can use
  `duels.kit.*` to grant every kit at once.
- `duels.use` and `duels.admin` are declared in `plugin.yml`, so
  permissions plugins pick them up automatically on first install.