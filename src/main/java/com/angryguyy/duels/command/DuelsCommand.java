package com.angryguyy.duels.command;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.arena.Arena;
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
 * features (sending, accepting, denying or forfeiting a duel) and
 * administrative features (reloading the plugin, managing arenas, and
 * inspecting statistics). Subcommands are dispatched in
 * {@link #onCommand(CommandSender, Command, String, String[])} based on
 * the first argument.</p>
 *
 * <p>Permission checks are performed per subcommand so that a player
 * without {@code duels.admin} cannot even see administrative entries in
 * tab completion.</p>
 */
public class DuelsCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    /**
     * Creates a new command handler.
     *
     * @param plugin owning plugin instance
     */
    public DuelsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the command.
     *
     * <p>When no argument is supplied, the help page is shown. Otherwise
     * the first argument is matched against the known subcommands; if no
     * match is found, the argument is treated as the name of the player
     * to challenge.</p>
     *
     * @param sender  source of the command
     * @param command the command being executed
     * @param label   alias used by the sender
     * @param args    arguments provided after the command
     * @return always {@code true} to prevent the server from displaying
     *         its own usage message
     */
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
            default -> handlePlayerTarget(sender, args);
        }
        return true;
    }

    /**
     * Dispatches the {@code arena} subcommand to its concrete handler.
     *
     * @param sender source of the command
     * @param args   full argument array, where {@code args[1]} is the
     *               arena action
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
            case "delete" -> handleArenaDelete(sender, args);
            case "reload" -> {
                plugin.arenas().load();
                plugin.messages().send(sender, "arena.reloaded");
            }
            default -> plugin.messages().send(sender, "arena.usage");
        }
    }

    /**
     * Lists every registered arena with its current occupancy state.
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
                            "state", a.isOccupied() ? "occupied" : "free"
                    ));
        }
    }

    /**
     * Creates a new arena at default spawn positions around the sender.
     *
     * <p>The two spawn points are generated five blocks apart along the
     * X axis, facing each other. Administrators are expected to refine
     * them immediately with {@code /duel arena setspawn}.</p>
     *
     * <p>The sender must be inside the duel world; otherwise the
     * generated spawn locations would belong to the wrong world and the
     * arena would be unusable.</p>
     *
     * @param sender source of the command; must be a player
     * @param args   full argument array, where {@code args[2]} is the
     *               new arena id
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
     * Updates one of the two spawn points of an existing arena.
     *
     * <p>The sender must be inside the duel world, for the same reason
     * as {@link #handleArenaCreate(CommandSender, String[])}: a spawn
     * captured in the wrong world would silently break the arena.</p>
     *
     * @param sender source of the command; must be a player
     * @param args   full argument array, where {@code args[2]} is the
     *               arena id and {@code args[3]} is the spawn index
     *               ({@code 1} or {@code 2})
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
     * Deletes an arena by id.
     *
     * <p>The arena cannot be deleted while it is occupied by an active
     * duel; the caller is informed with a dedicated message.</p>
     *
     * @param sender source of the command
     * @param args   full argument array, where {@code args[2]} is the
     *               arena id
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
     * <p>Any exception thrown during the reload is caught and reported
     * to the sender without crashing the plugin, so that a broken YAML
     * file does not require a server restart.</p>
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
     * Accepts the sender's pending incoming duel request.
     *
     * @param sender source of the command
     */
    private void handleAccept(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        plugin.duels().accept(player);
    }

    /**
     * Denies the sender's pending incoming duel request.
     *
     * @param sender source of the command
     */
    private void handleDeny(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        plugin.duels().deny(player);
    }

    /**
     * Makes the sender forfeit their active duel.
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
     * <p>If a kit is provided as the second argument, the request is
     * sent immediately with that kit. Otherwise, the kit selection GUI
     * is opened so the sender can pick a kit visually. When the player
     * clicks a kit, the request is sent by the GUI listener.</p>
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

        if (args.length >= 2) {
            plugin.duels().sendRequest(player, target, args[1]);
            return;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            plugin.messages().send(player, "duel.self");
            return;
        }

        KitSelectionGui.open(plugin, player, target);
    }

    /**
     * Ensures the sender is a player and reports an error otherwise.
     *
     * @param sender source of the command
     * @return the sender cast to {@link Player}, or {@code null} if the
     *         sender is not a player
     */
    private @Nullable Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return null;
        }
        return p;
    }

    /**
     * Dispatches the {@code stats} subcommand.
     *
     * <p>If the second argument is {@code reset} or {@code resetall},
     * the request is routed to the administrative reset handler.
     * Otherwise the command displays the stats of a target player or of
     * the sender itself.</p>
     *
     * <p>If the stats subsystem is not available (database offline or
     * disabled in config), a dedicated message is shown instead of a
     * misleading "no data" reply.</p>
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
     * Displays a stats snapshot to the sender.
     *
     * <p>Values that are percentages or ratios are formatted with a
     * single or double decimal respectively, so that the output is
     * stable regardless of locale.</p>
     *
     * @param sender   receiver
     * @param uuid     player uuid
     * @param fallback username to display if the snapshot lacks one
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
     * Dispatches the {@code top} subcommand.
     *
     * <p>Without a second argument the usage message is shown. The
     * second argument selects a category; the optional third argument
     * selects a page or requests the GUI. Out-of-range pages are
     * clamped to the last available page, so an over-large page number
     * never results in a blank output.</p>
     *
     * <p>If the stats subsystem is not available (database offline or
     * disabled in config), a dedicated message is shown.</p>
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
     * Handles the reset and resetall subcommands.
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

    /**
     * Provides context-aware tab completion.
     *
     * <p>Completion is layered: the first argument suggests subcommands
     * and online player names, the second argument suggests arena
     * actions, kit ids, leaderboard categories or reset actions
     * depending on the subcommand, and deeper arguments suggest arena
     * ids or spawn indexes. Administrative entries are hidden from
     * senders without the {@code duels.admin} permission.</p>
     *
     * @param sender  source of the completion request
     * @param command the command being completed
     * @param alias   alias used by the sender
     * @param args    arguments typed so far
     * @return list of completion candidates, possibly empty
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(
                    List.of("help", "accept", "deny", "forfeit", "stats", "top"));
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
            return Stream.of("list", "create", "setspawn", "delete", "reload")
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && sender.hasPermission("duels.admin")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("setspawn") || action.equals("delete")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.arenas().all().stream()
                        .map(Arena::getId)
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("arena")
                && args[1].equalsIgnoreCase("setspawn")
                && sender.hasPermission("duels.admin")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return Stream.of("1", "2")
                    .filter(s -> s.startsWith(prefix))
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

        return List.of();
    }

    /**
     * Returns whether an argument matches a known top-level subcommand.
     *
     * <p>Used by tab completion to distinguish between a subcommand and
     * a player name typed as the first argument.</p>
     *
     * @param arg argument to test
     * @return {@code true} if the argument is a known subcommand
     */
    private boolean isSubcommand(String arg) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "help", "reload", "accept", "deny", "forfeit", "arena", "stats", "top" -> true;
            default -> false;
        };
    }
}