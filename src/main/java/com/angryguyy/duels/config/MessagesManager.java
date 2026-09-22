package com.angryguyy.duels.config;

import com.angryguyy.duels.DuelsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/**
 * Loads messages.yml and renders MiniMessage strings.
 * Placeholders are inserted as <b>{key}</b> and resolved unparsed (safe for player input).
 */
public class MessagesManager {

    private final DuelsPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private YamlConfiguration messages;
    private Component prefix;

    public MessagesManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        this.prefix = mm.deserialize(messages.getString("prefix", ""));
    }

    public Component prefix() {
        return prefix;
    }

    public Component get(String key) {
        return get(key, null);
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            return mm.deserialize("<red>[missing message: " + key + "]");
        }
        TagResolver.Builder builder = TagResolver.builder();
        if (placeholders != null) {
            placeholders.forEach((k, v) -> builder.resolver(Placeholder.unparsed(k, v)));
        }
        return mm.deserialize(raw, builder.build());
    }

    public Component prefixed(String key) {
        return prefixed(key, null);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        return prefix.append(get(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefixed(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }
}