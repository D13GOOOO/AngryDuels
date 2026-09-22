package com.angryguyy.duels.command;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DuelsCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "general.unknown-command");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "help" -> handleHelp(sender);
            default -> plugin.messages().send(sender, "general.unknown-command");
        }
        return true;
    }

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

    private void handleHelp(CommandSender sender) {
        // TODO: expand when subcommands grow
        plugin.messages().send(sender, "general.unknown-command");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>();
            opts.add("help");
            if (sender.hasPermission("duels.admin")) {
                opts.add("reload");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return opts.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}