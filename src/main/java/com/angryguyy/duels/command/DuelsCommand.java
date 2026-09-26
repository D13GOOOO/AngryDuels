package com.angryguyy.duels.command;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.arena.Arena;
import com.angryguyy.duels.duel.DuelSession;
import com.angryguyy.duels.gui.KitEditorGui;
import com.angryguyy.duels.gui.KitSelectionGui;
import com.angryguyy.duels.gui.LeaderboardGui;
import com.angryguyy.duels.stats.LeaderboardCategory;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Handler for the {@code /duel} command and its subcommands.
 *
 * <p>The command is the single entry point for both player-facing
 * features (sending, accepting, denying or forfeiting a duel, and
 * spectating) and administrative features (reloading the plugin,
 * managing arenas, and inspecting statistics). Subcommands are
 * dispatched in
 * {@link #onCommand(CommandSender, Command, String, String[])} based on
 * the first argument.</p>
 *
 * <p>Permission checks are performed per subcommand so that a player
 * without {@code duels.admin} cannot even see administrative entries in
 * tab completion.</p>
 */
public class DuelsCommand implements CommandExecutor, TabCompleter {

    /**
     * Owning plugin instance.
     */
    private final DuelsPlugin plugin;

    /**
     * Creates a new command handler.
     *
     * @param plugin owning plugin instance
     */
    public DuelsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> handleHelp(sender);
            case "reload" -> handleReload(sender);
            case "accept" -> handleAccept(sender);
            case "deny" -> handleDeny(sender);
            case "forfeit" -> handleForfeit(sender);
            case "arena" -> handleArena(sender, args);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "kiteditor" -> handleKitEditor(sender, args);
            case "kitreset" -> handleKitReset(sender, args);
            case "spectate" -> handleSpectate(sender, args);
            case "unspectate" -> handleUnspectate(sender);
            default -> handlePlayerTarget(sender, args);
        }
        return true;
    }

    // ------------------------------------------------------------
    // Spectate
    // ------------------------------------------------------------

    /**
     * Starts spectating the duel of the named player.
     *
     * <p>The target must be online and currently in an active duel.
     * The viewer must not be in a duel themselves. If both conditions
     * hold, the viewer is switched to spectator mode, teleported to the
     * arena's spectator spawn, and tracked by the spectator manager
     * until the match ends or {@code /duel unspectate} is used.</p>
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the target player name
     */
    private void handleSpectate(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender);
        if (viewer == null) return;
        if (args.length < 2) {
            plugin.messages().send(viewer, "spectator.usage");
            return;
        }

        if (plugin.spectators().isSpectating(viewer.getUniqueId())) {
            plugin.messages().send(viewer, "spectator.already-spectating");
            return;
        }
        if (plugin.duels().isInDuel(viewer.getUniqueId())) {
            plugin.messages().send(viewer, "spectator.self-in-duel");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.messages().send(viewer, "duel.target-offline");
            return;
        }

        DuelSession session = plugin.duels().getSession(target.getUniqueId());
        if (session == null) {
            plugin.messages().send(viewer, "spectator.target-not-in-duel");
            return;
        }

        boolean started = plugin.spectators().startSpectating(viewer, session);
        if (!started) {
            plugin.messages().send(viewer, "spectator.start-failed");
        }
    }

    /**
     * Stops the sender from spectating.
     *
     * @param sender source of the command
     */
    private void handleUnspectate(CommandSender sender) {
        Player viewer = requirePlayer(sender);
        if (viewer == null) return;
        if (!plugin.spectators().isSpectating(viewer.getUniqueId())) {
            plugin.messages().send(viewer, "spectator.not-spectating");
            return;
        }
        plugin.spectators().stopSpectating(viewer);
    }

    // ------------------------------------------------------------
    // Arena management
    // ------------------------------------------------------------

    /**
     * Dispatches the {@code arena} subcommand to its concrete handler.
     *
     * @param sender source of the command
     * @param args   full argument array
     */
    private void handleArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> handleArenaList(sender);
            case "create" -> handleArenaCreate(sender, args);
            case "setspawn" -> handleArenaSetSpawn(sender, args);
            case "setspectator" -> handleArenaSetSpectator(sender, args);
            case "delete" -> handleArenaDelete(sender, args);
            case "addspawn" -> handleArenaAddTeamSpawn(sender, args);
            case "delspawn" -> handleArenaDelTeamSpawn(sender, args);
            case "clearspawns" -> handleArenaClearTeamSpawns(sender, args);
            case "teamspawns" -> handleArenaListTeamSpawns(sender, args);
            case "reload" -> {
                plugin.arenas().load();
                plugin.messages().send(sender, "arena.reloaded");
            }
            default -> plugin.messages().send(sender, "arena.usage");
        }
    }

    /**
     * Lists every registered arena with its current occupancy state and
     * team spawn counts.
     *
     * @param sender receiver of the output
     */
    private void handleArenaList(CommandSender sender) {
        var all = plugin.arenas().all();
        if (all.isEmpty()) {
            plugin.messages().send(sender, "arena.list-empty");
            return;
        }
        plugin.messages().send(sender, "arena.list-header",
                Map.of("count", String.valueOf(all.size())));
        for (var a : all) {
            plugin.messages().send(sender, "arena.list-entry",
                    Map.of(
                            "id", a.getId(),
                            "world", a.getWorld().getName(),
                            "state", a.isOccupied() ? "occupied" : "free",
                            "team1", String.valueOf(a.getTeam1Spawns().size()),
                            "team2", String.valueOf(a.getTeam2Spawns().size())
                    ));
        }
    }

    /**
     * Creates a new arena at default spawn positions around the sender.
     *
     * @param sender source of the command; must be a player
     * @param args   full argument array
     */
    private void handleArenaCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        if (plugin.arenas().get(id) != null) {
            plugin.messages().send(sender, "arena.already-exists", Map.of("id", id));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        World world = plugin.worlds().getWorld();
        if (world == null) {
            plugin.messages().send(sender, "arena.no-world");
            return;
        }
        if (!plugin.worlds().isDuelWorld(p.getWorld())) {
            plugin.messages().send(sender, "arena.wrong-world");
            return;
        }
        Location base = p.getLocation();
        Location s1 = new Location(world, base.getX() - 5, base.getY(), base.getZ(), 90f, 0f);
        Location s2 = new Location(world, base.getX() + 5, base.getY(), base.getZ(), -90f, 0f);
        plugin.arenas().create(id, world, s1, s2);
        plugin.messages().send(sender, "arena.created", Map.of("id", id));
    }

    /**
     * Updates one of the two legacy spawn points of an existing arena.
     *
     * @param sender source of the command; must be a player
     * @param args   full argument array
     */
    private void handleArenaSetSpawn(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (!plugin.worlds().isDuelWorld(p.getWorld())) {
            plugin.messages().send(sender, "arena.wrong-world");
            return;
        }
        String which = args[3];
        Location loc = p.getLocation().clone();
        switch (which) {
            case "1" -> {
                arena.setSpawn1(loc);
                plugin.arenas().save();
                plugin.messages().send(sender, "arena.spawn-set",
                        Map.of("id", id, "which", "1"));
            }
            case "2" -> {
                arena.setSpawn2(loc);
                plugin.arenas().save();
                plugin.messages().send(sender, "arena.spawn-set",
                        Map.of("id", id, "which", "2"));
            }
            default -> plugin.messages().send(sender, "arena.usage");
        }
    }

    /**
     * Sets the spectator spawn of an arena to the sender's current
     * position.
     *
     * @param sender source of the command; must be a player inside the
     *               duel world
     * @param args   {@code args[2]} is the arena id
     */
    private void handleArenaSetSpectator(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (!plugin.worlds().isDuelWorld(p.getWorld())) {
            plugin.messages().send(sender, "arena.wrong-world");
            return;
        }
        arena.setSpectatorSpawn(p.getLocation().clone());
        plugin.arenas().save();
        plugin.messages().send(sender, "arena.spectator-set", Map.of("id", id));
    }

    /**
     * Deletes an arena by id.
     *
     * @param sender source of the command
     * @param args   full argument array
     */
    private void handleArenaDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        if (arena.isOccupied()) {
            plugin.messages().send(sender, "arena.delete-occupied", Map.of("id", id));
            return;
        }
        plugin.arenas().delete(id);
        plugin.messages().send(sender, "arena.deleted", Map.of("id", id));
    }

    /**
     * Adds a team spawn to an existing arena.
     *
     * @param sender source of the command; must be a player inside the duel world
     * @param args   {@code args[2]} arena id, {@code args[3]} team (1 or 2)
     */
    private void handleArenaAddTeamSpawn(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (!plugin.worlds().isDuelWorld(p.getWorld())) {
            plugin.messages().send(sender, "arena.wrong-world");
            return;
        }
        int team = parseTeam(args[3]);
        if (team == 0) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        arena.addTeamSpawn(team, p.getLocation().clone());
        plugin.arenas().save();
        int total = arena.getTeam1Spawns().size() + arena.getTeam2Spawns().size();
        plugin.messages().send(sender, "arena.team-spawn-added", Map.of(
                "id", id,
                "team", String.valueOf(team),
                "index", String.valueOf(total)
        ));
    }

    /**
     * Removes a team spawn from an arena.
     *
     * @param sender source of the command
     * @param args   {@code args[2]} arena id, {@code args[3]} team,
     *               {@code args[4]} one-based index
     */
    private void handleArenaDelTeamSpawn(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        int team = parseTeam(args[3]);
        if (team == 0) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        Location removed = arena.removeTeamSpawn(team, index);
        if (removed == null) {
            plugin.messages().send(sender, "arena.spawn-index-invalid", Map.of(
                    "team", String.valueOf(team),
                    "index", String.valueOf(index)
            ));
            return;
        }
        plugin.arenas().save();
        plugin.messages().send(sender, "arena.team-spawn-removed", Map.of(
                "id", id,
                "team", String.valueOf(team),
                "index", String.valueOf(index)
        ));
    }

    /**
     * Clears every team spawn of an arena.
     *
     * @param sender source of the command
     * @param args   {@code args[2]} arena id
     */
    private void handleArenaClearTeamSpawns(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        arena.clearTeamSpawns(1);
        arena.clearTeamSpawns(2);
        plugin.arenas().save();
        plugin.messages().send(sender, "arena.team-spawns-cleared", Map.of("id", id));
    }

    /**
     * Lists the team spawns of an arena.
     *
     * @param sender source of the command
     * @param args   {@code args[2]} arena id
     */
    private void handleArenaListTeamSpawns(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "arena.usage");
            return;
        }
        String id = args[2];
        var arena = plugin.arenas().get(id);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("id", id));
            return;
        }
        var team1 = arena.getTeam1Spawns();
        var team2 = arena.getTeam2Spawns();
        plugin.messages().send(sender, "arena.team-spawns-header",
                Map.of("id", id,
                        "count1", String.valueOf(team1.size()),
                        "count2", String.valueOf(team2.size())));
        for (int i = 0; i < team1.size(); i++) {
            sendTeamSpawnEntry(sender, 1, i + 1, team1.get(i));
        }
        for (int i = 0; i < team2.size(); i++) {
            sendTeamSpawnEntry(sender, 2, i + 1, team2.get(i));
        }
    }

    /**
     * Sends a formatted team spawn entry to the sender.
     *
     * @param sender receiver of the entry
     * @param team   team index
     * @param index  one-based spawn index
     * @param l      spawn location
     */
    private void sendTeamSpawnEntry(CommandSender sender, int team, int index, Location l) {
        plugin.messages().send(sender, "arena.team-spawn-entry", Map.of(
                "team", String.valueOf(team),
                "index", String.valueOf(index),
                "x", String.format(Locale.ROOT, "%.1f", l.getX()),
                "y", String.format(Locale.ROOT, "%.1f", l.getY()),
                "z", String.format(Locale.ROOT, "%.1f", l.getZ())
        ));
    }

    /**
     * Parses a team index, returning {@code 0} when the input is not
     * {@code 1} or {@code 2}.
     *
     * @param arg raw argument
     * @return parsed team, or {@code 0}
     */
    private int parseTeam(String arg) {
        try {
            int t = Integer.parseInt(arg);
            return (t == 1 || t == 2) ? t : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------
    // Generic lifecycle
    // ------------------------------------------------------------

    /**
     * Sends the help page to the sender.
     *
     * @param sender receiver of the help page
     */
    private void handleHelp(CommandSender sender) {
        plugin.messages().sendList(sender, "general.help");
    }

    /**
     * Reloads both {@code config.yml} and {@code messages.yml}.
     *
     * @param sender source of the command; requires {@code duels.admin}
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("duels.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        try {
            plugin.config().reload();
            plugin.messages().load();
            plugin.messages().send(sender, "general.reload-success");
            Log.info("Configuration reloaded by %s", sender.getName());
        } catch (Exception e) {
            plugin.messages().send(sender, "general.reload-failed");
            Log.error(e, "Reload failed");
        }
    }

    /**
     * Accepts the incoming duel request of the sender.
     *
     * @param sender source of the command
     */
    private void handleAccept(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        plugin.duels().accept(player);
    }

    /**
     * Denies the incoming duel request of the sender.
     *
     * @param sender source of the command
     */
    private void handleDeny(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        plugin.duels().deny(player);
    }

    /**
     * Makes the sender forfeit their current duel.
     *
     * @param sender source of the command
     */
    private void handleForfeit(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        plugin.duels().forfeit(player);
    }

    /**
     * Sends a duel request from the sender to the named player.
     *
     * <p>When a kit is provided as second argument, the request is sent
     * directly. Otherwise a kit selection GUI is opened. In both cases
     * the sender may not challenge themselves.</p>
     *
     * @param sender source of the command; must be a player
     * @param args   full argument array
     */
    private void handlePlayerTarget(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "duel.target-offline");
            return;
        }

        if (plugin.spectators().isSpectating(player.getUniqueId())) {
            plugin.messages().send(player, "spectator.self-in-duel");
            return;
        }
        if (plugin.spectators().isSpectating(target.getUniqueId())) {
            plugin.messages().send(player, "spectator.target-in-duel");
            return;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            plugin.messages().send(player, "duel.self");
            return;
        }

        if (args.length >= 2) {
            plugin.duels().sendRequest(player, target, args[1]);
            return;
        }

        KitSelectionGui.open(plugin, player, target);
    }

    /**
     * Resolves a command sender to a player, sending the standard
     * player-only message when the sender is not a player.
     *
     * @param sender source of the command
     * @return the player, or {@code null}
     */
    private @Nullable Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return null;
        }
        return p;
    }

    // ------------------------------------------------------------
    // Stats
    // ------------------------------------------------------------

    /**
     * Dispatches the {@code stats} subcommand, handling the special
     * reset and resetall actions before falling through to the
     * per-player display.
     *
     * @param sender source of the command
     * @param args   full argument array
     */
    private void handleStats(CommandSender sender, String[] args) {
        if (!plugin.database().isReady()) {
            plugin.messages().send(sender, "stats.disabled");
            return;
        }

        if (args.length >= 2 && (args[1].equalsIgnoreCase("reset")
                || args[1].equalsIgnoreCase("resetall"))) {
            handleStatsReset(sender, args);
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (offline == null) {
                    plugin.messages().send(sender, "stats.not-found",
                            Map.of("player", args[1]));
                    return;
                }
                String name = offline.getName() != null ? offline.getName() : args[1];
                displayStats(sender, offline.getUniqueId(), name);
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) return;
        }

        displayStats(sender, target.getUniqueId(), target.getName());
    }

    /**
     * Reads and displays the stats snapshot of a player.
     *
     * @param sender   receiver of the output
     * @param uuid     player uuid
     * @param fallback name to show if the snapshot is missing one
     */
    private void displayStats(CommandSender sender, UUID uuid, String fallback) {
        var snapshot = plugin.stats().readStats(uuid);
        if (snapshot == null) {
            plugin.messages().send(sender, "stats.not-found",
                    Map.of("player", fallback));
            return;
        }

        String name = snapshot.username() != null ? snapshot.username() : fallback;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", name);
        placeholders.put("wins", String.valueOf(snapshot.wins()));
        placeholders.put("losses", String.valueOf(snapshot.losses()));
        placeholders.put("played", String.valueOf(snapshot.totalDuels()));
        placeholders.put("kills", String.valueOf(snapshot.kills()));
        placeholders.put("deaths", String.valueOf(snapshot.deaths()));
        placeholders.put("forfeits", String.valueOf(snapshot.forfeits()));
        placeholders.put("quits", String.valueOf(snapshot.quits()));
        placeholders.put("streak", String.valueOf(snapshot.streak()));
        placeholders.put("best_streak", String.valueOf(snapshot.bestStreak()));
        placeholders.put("winrate", String.format(Locale.ROOT, "%.1f", snapshot.winRate()));
        placeholders.put("kdr", String.format(Locale.ROOT, "%.2f", snapshot.kdr()));

        plugin.messages().sendList(sender, "stats.header", Map.of("player", name));
        plugin.messages().sendList(sender, "stats.body", placeholders);
    }

    /**
     * Displays a leaderboard page, either in chat or in the GUI when
     * the literal {@code gui} argument is passed.
     *
     * @param sender source of the command
     * @param args   full argument array
     */
    private void handleTop(CommandSender sender, String[] args) {
        if (!plugin.database().isReady()) {
            plugin.messages().send(sender, "stats.disabled");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "stats.top-usage");
            return;
        }
        LeaderboardCategory category = LeaderboardCategory.fromString(args[1]);
        if (category == null) {
            plugin.messages().send(sender, "stats.top-unknown-category",
                    Map.of("category", args[1]));
            return;
        }

        if (args.length >= 3 && args[2].equalsIgnoreCase("gui")) {
            Player p = requirePlayer(sender);
            if (p == null) return;
            LeaderboardGui.open(plugin, p, category, 1);
            return;
        }

        int totalPages = plugin.leaderboards().totalPages(category);
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
            }
        }
        page = Math.min(page, totalPages);

        var entries = plugin.leaderboards().getPage(category, page);
        if (entries.isEmpty()) {
            plugin.messages().send(sender, "stats.top-empty");
            return;
        }

        plugin.messages().send(sender, "stats.top-header", Map.of(
                "category", category.getLabel(),
                "page", String.valueOf(page),
                "pages", String.valueOf(totalPages)
        ));
        for (var e : entries) {
            plugin.messages().send(sender, "stats.top-entry", Map.of(
                    "rank", String.valueOf(e.rank()),
                    "player", e.username(),
                    "value", String.valueOf(e.value())
            ));
        }
    }

    /**
     * Handles the {@code stats reset} and {@code stats resetall}
     * administrative actions.
     *
     * @param sender source of the command; requires {@code duels.admin}
     * @param args   full argument array
     */
    private void handleStatsReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }

        if (args[1].equalsIgnoreCase("resetall")) {
            plugin.stats().resetAll();
            plugin.messages().send(sender, "stats.reset-all");
            Log.info("All stats reset by %s", sender.getName());
            return;
        }

        if (args.length < 3) {
            plugin.messages().send(sender, "stats.reset-usage");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "stats.not-found",
                    Map.of("player", args[2]));
            return;
        }
        plugin.stats().resetStats(target.getUniqueId());
        plugin.messages().send(sender, "stats.reset-one",
                Map.of("player", target.getName() != null ? target.getName() : args[2]));
        Log.info("Stats reset for %s by %s", args[2], sender.getName());
    }

    // ------------------------------------------------------------
    // Kit editor
    // ------------------------------------------------------------

    /**
     * Opens the kit editor for a kit the sender can use.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the kit id or alias
     */
    private void handleKitEditor(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "kit.editor-usage");
            return;
        }
        var kit = plugin.kits().resolve(args[1]);
        if (kit == null) {
            plugin.messages().send(player, "kit.not-found", Map.of("kit", args[1]));
            return;
        }
        if (!player.hasPermission(kit.getPermission())) {
            plugin.messages().send(player, "kit.no-permission");
            return;
        }
        KitEditorGui.open(plugin, player, kit);
    }

    /**
     * Clears the personal layout override of a kit for the sender.
     *
     * @param sender source of the command
     * @param args   {@code args[1]} is the kit id or alias
     */
    private void handleKitReset(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(player, "kit.editor-reset-usage");
            return;
        }
        var kit = plugin.kits().resolve(args[1]);
        if (kit == null) {
            plugin.messages().send(player, "kit.not-found", Map.of("kit", args[1]));
            return;
        }
        plugin.playerKits().clearOverride(player.getUniqueId(), kit.getId());
        plugin.messages().send(player, "kit.editor-reset", Map.of("kit", kit.getId()));
    }

    // ------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(
                    List.of("help", "accept", "deny", "forfeit", "stats", "top",
                            "kiteditor", "kitreset", "spectate", "unspectate"));
            if (sender.hasPermission("duels.admin")) {
                options.add("reload");
                options.add("arena");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player sp && p.getUniqueId().equals(sp.getUniqueId())) continue;
                options.add(p.getName());
            }
            return options.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("arena") && sender.hasPermission("duels.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("list", "create", "setspawn", "setspectator", "delete",
                            "addspawn", "delspawn", "clearspawns", "teamspawns", "reload")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && sender.hasPermission("duels.admin")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("setspawn") || action.equals("setspectator")
                    || action.equals("delete")
                    || action.equals("addspawn") || action.equals("delspawn")
                    || action.equals("clearspawns") || action.equals("teamspawns")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.arenas().all().stream()
                        .map(Arena::getId)
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("arena")
                && sender.hasPermission("duels.admin")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("setspawn") || action.equals("addspawn")
                    || action.equals("delspawn")) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return Stream.of("1", "2")
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spectate")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.duels().isInDuel(p.getUniqueId())) continue;
                names.add(p.getName());
            }
            return names.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> cats = new ArrayList<>();
            for (LeaderboardCategory c : LeaderboardCategory.values()) {
                cats.add(c.getId());
            }
            return cats.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("stats")
                && sender.hasPermission("duels.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("reset", "resetall")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("top")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("1", "2", "3", "gui")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && !isSubcommand(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (var kit : plugin.kits().all()) {
                suggestions.add(kit.getId());
                suggestions.addAll(kit.getAliases());
            }
            return suggestions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("kiteditor")
                || args[0].equalsIgnoreCase("kitreset"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (var kit : plugin.kits().all()) {
                suggestions.add(kit.getId());
                suggestions.addAll(kit.getAliases());
            }
            return suggestions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .toList();
        }

        return List.of();
    }

    /**
     * Checks whether a token is one of the top-level subcommands of
     * the {@code /duel} command.
     *
     * @param arg token to test
     * @return {@code true} if the token is a known subcommand
     */
    private boolean isSubcommand(String arg) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "help", "reload", "accept", "deny", "forfeit", "arena",
                 "stats", "top", "kiteditor", "kitreset",
                 "spectate", "unspectate" -> true;
            default -> false;
        };
    }
}