package com.angryguyy.duels.command;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handler for the {@code /duel} command and its subcommands.
 *
 * <p>The command is the single entry point for both player-facing
 * features (sending, accepting, denying or forfeiting a duel) and
 * administrative features (reloading the plugin or managing arenas).
 * Subcommands are dispatched in {@link #onCommand(CommandSender, Command, String, String[])}
 * based on the first argument.</p>
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
            default -> handlePlayerTarget(sender, args[0]);
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
                java.util.Map.of("count", String.valueOf(all.size())));
        for (var a : all) {
            plugin.messages().send(sender, "arena.list-entry",
                    java.util.Map.of(
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
            plugin.messages().send(sender, "arena.already-exists",
                    java.util.Map.of("id", id));
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
        Location base = p.getLocation();
        Location s1 = new Location(world, base.getX() - 5, base.getY(), base.getZ(), 90f, 0f);
        Location s2 = new Location(world, base.getX() + 5, base.getY(), base.getZ(), -90f, 0f);
        plugin.arenas().create(id, world, s1, s2);
        plugin.messages().send(sender, "arena.created",
                java.util.Map.of("id", id));
    }

    /**
     * Updates one of the two spawn points of an existing arena.
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
            plugin.messages().send(sender, "arena.not-found",
                    java.util.Map.of("id", id));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        String which = args[3];
        Location loc = p.getLocation().clone();
        switch (which) {
            case "1" -> {
                arena.setSpawn1(loc);
                plugin.arenas().save();
                plugin.messages().send(sender, "arena.spawn-set",
                        java.util.Map.of("id", id, "which", "1"));
            }
            case "2" -> {
                arena.setSpawn2(loc);
                plugin.arenas().save();
                plugin.messages().send(sender, "arena.spawn-set",
                        java.util.Map.of("id", id, "which", "2"));
            }
            default -> plugin.messages().send(sender, "arena.usage");
        }
    }

    /**
     * Deletes an arena by id.
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
        if (plugin.arenas().delete(id)) {
            plugin.messages().send(sender, "arena.deleted",
                    java.util.Map.of("id", id));
        } else {
            plugin.messages().send(sender, "arena.not-found",
                    java.util.Map.of("id", id));
        }
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
     * @param sender source of the command; must be a player
     * @param name   name of the target player
     */
    private void handlePlayerTarget(CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            plugin.messages().send(player, "duel.target-offline");
            return;
        }
        plugin.duels().sendRequest(player, target, null);
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
     * Provides context-aware tab completion.
     *
     * <p>Completion is layered: the first argument suggests subcommands
     * and online player names, the second argument suggests arena
     * actions, and deeper arguments suggest arena ids or spawn indexes
     * depending on the action. Administrative entries are hidden from
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
            List<String> options = new ArrayList<>(List.of("help", "accept", "deny", "forfeit"));
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
            return List.of("list", "create", "setspawn", "delete", "reload").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && sender.hasPermission("duels.admin")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("setspawn") || action.equals("delete")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.arenas().all().stream()
                        .map(a -> a.getId())
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("arena")
                && args[1].equalsIgnoreCase("setspawn")
                && sender.hasPermission("duels.admin")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return List.of("1", "2").stream().filter(s -> s.startsWith(prefix)).toList();
        }

        return List.of();
    }
}