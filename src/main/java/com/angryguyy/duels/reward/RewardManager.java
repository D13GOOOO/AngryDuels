package com.angryguyy.duels.reward;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.event.DuelEndEvent;
import com.angryguyy.duels.kit.ItemSerializer;
import com.angryguyy.duels.reward.impl.BroadcastReward;
import com.angryguyy.duels.reward.impl.ConsoleCommandReward;
import com.angryguyy.duels.reward.impl.ItemReward;
import com.angryguyy.duels.reward.impl.MoneyReward;
import com.angryguyy.duels.reward.impl.XpReward;
import com.angryguyy.duels.util.Log;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Loads reward definitions from {@code config.yml} and grants them to
 * the winner of a duel.
 *
 * <p>Rewards are organized in two scopes:</p>
 * <ul>
 *     <li>{@code rewards.default} — the global fallback list</li>
 *     <li>{@code rewards.kits.<kitId>} — an override that fully replaces
 *     the default list when the duel was played with that kit</li>
 * </ul>
 *
 * <p>Supported reward types are {@code console}, {@code item},
 * {@code money}, {@code xp} and {@code broadcast}. Money requires Vault
 * with an economy provider; when Vault is absent, money rewards are
 * silently skipped and a warning is logged at startup.</p>
 *
 * <p>Rewards are scheduled after a configurable delay so that the
 * winner's inventory is applied after the duel return teleport. An
 * optional cooldown prevents farming by limiting how often the same
 * player can receive rewards. The cooldown is applied at the moment
 * the duel ends, not when the delayed grant runs, so a winner who
 * disconnects within the delay window still receives the cooldown.</p>
 */
public class RewardManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, Instant> cooldowns = new HashMap<>();

    private boolean enabled;
    private int grantDelayTicks;
    private int cooldownSeconds;
    private List<Reward> defaultRewards = List.of();
    private Map<String, List<Reward>> kitRewards = new HashMap<>();
    private Economy economy;

    /**
     * Creates a new reward manager.
     *
     * @param plugin owning plugin
     */
    public RewardManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the reward configuration from {@code config.yml}.
     *
     * <p>Any previously loaded reward is discarded. Malformed entries
     * are logged and skipped individually so that the rest of the
     * configuration still loads.</p>
     *
     * <p>A delayed task is scheduled to resolve the Vault economy
     * provider a couple of seconds after startup, giving third-party
     * plugins the time to register their economy service.</p>
     */
    public void load() {
        FileConfiguration cfg = plugin.config().raw();
        enabled = cfg.getBoolean("rewards.enabled", true);
        grantDelayTicks = cfg.getInt("rewards.grant-delay-ticks", 20);
        cooldownSeconds = cfg.getInt("rewards.cooldown-seconds", 0);

        defaultRewards = parseList(cfg.getMapList("rewards.default"));

        Map<String, List<Reward>> newKitRewards = new HashMap<>();
        ConfigurationSection kitsSec = cfg.getConfigurationSection("rewards.kits");
        if (kitsSec != null) {
            for (String kitId : kitsSec.getKeys(false)) {
                List<Reward> parsed = parseList(kitsSec.getMapList(kitId));
                if (!parsed.isEmpty()) {
                    newKitRewards.put(kitId, parsed);
                }
            }
        }
        kitRewards = newKitRewards;

        Log.info("Loaded rewards: %d default, %d kit override(s).",
                defaultRewards.size(), kitRewards.size());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Economy e = getEconomy();
            if (e != null) {
                Log.info("Vault economy provider resolved: %s", e.getName());
            } else {
                Log.warn("Vault economy provider not available.");
            }
        }, 40L);
    }

    /**
     * Parses a list of reward definitions.
     *
     * @param raw raw list from the config
     * @return parsed rewards, possibly empty
     */
    private List<Reward> parseList(List<Map<?, ?>> raw) {
        List<Reward> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> entry : raw) {
            try {
                Reward r = parseEntry(entry);
                if (r != null) out.add(r);
            } catch (Exception e) {
                Log.error(e, "Failed to parse reward entry");
            }
        }
        return out;
    }

    /**
     * Parses a single reward definition.
     *
     * @param entry raw entry map
     * @return parsed reward, or {@code null} if the entry is invalid
     */
    private Reward parseEntry(Map<?, ?> entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        entry.forEach((k, v) -> map.put(String.valueOf(k), v));

        String type = (String) map.get("type");
        if (type == null) {
            Log.warn("Reward entry missing 'type', skipping.");
            return null;
        }

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "console" -> {
                String cmd = (String) map.get("command");
                if (cmd == null) {
                    Log.warn("Console reward missing 'command', skipping.");
                    yield null;
                }
                yield new ConsoleCommandReward(cmd);
            }
            case "item" -> {
                Object material = map.get("material");
                if (material == null) {
                    Log.warn("Item reward missing 'material', skipping.");
                    yield null;
                }
                Map<String, Object> itemMap = new LinkedHashMap<>(map);
                itemMap.remove("type");
                itemMap.put("type", material);
                yield new ItemReward(ItemSerializer.fromMap(itemMap));
            }
            case "money" -> {
                double amount = ((Number) map.getOrDefault("amount", 0)).doubleValue();
                yield new MoneyReward(plugin, amount);
            }
            case "xp" -> {
                int points = map.containsKey("points") ? ((Number) map.get("points")).intValue() : 0;
                int levels = map.containsKey("levels") ? ((Number) map.get("levels")).intValue() : 0;
                yield new XpReward(points, levels);
            }
            case "broadcast" -> {
                String msg = (String) map.get("message");
                if (msg == null) {
                    Log.warn("Broadcast reward missing 'message', skipping.");
                    yield null;
                }
                yield new BroadcastReward(msg);
            }
            default -> {
                Log.warn("Unknown reward type '%s'", type);
                yield null;
            }
        };
    }

    /**
     * Grants rewards to the winner of a duel.
     *
     * <p>If rewards are disabled, if there is no winner, or if the
     * winner is on cooldown, the method returns without scheduling
     * anything. Otherwise it captures a {@link RewardContext} at the
     * time the duel ends, applies the cooldown immediately, and
     * schedules the actual granting after the configured delay, so the
     * winner has already been teleported back to their pre-duel
     * location.</p>
     *
     * <p>The winner is re-fetched by uuid inside the delayed task, so a
     * disconnect-and-reconnect cycle within the delay window is handled
     * correctly and the rewards are applied to the new player instance.
     * If the winner is offline when the task runs, the rewards are
     * silently skipped and the cooldown remains in place.</p>
     *
     * @param event the duel end event
     */
    public void grant(DuelEndEvent event) {
        if (!enabled) return;
        Player winner = event.getWinner();
        if (winner == null) return;

        if (cooldownSeconds > 0 && hasCooldown(winner.getUniqueId())) {
            Log.debug("Reward cooldown active for %s, skipping.", winner.getName());
            return;
        }

        List<Reward> rewards = resolveRewards(event.getSession().getKitId());
        if (rewards.isEmpty()) return;

        if (cooldownSeconds > 0) {
            cooldowns.put(winner.getUniqueId(), Instant.now().plusSeconds(cooldownSeconds));
        }

        String arenaId = event.getSession().getArena() != null
                ? event.getSession().getArena().getId() : null;
        long durationSeconds = Duration.between(
                event.getSession().getStartedAt(), Instant.now()).getSeconds();

        RewardContext baseCtx = new RewardContext(
                winner,
                event.getLoser(),
                arenaId,
                event.getSession().getKitId(),
                event.getReason(),
                durationSeconds
        );

        UUID winnerId = winner.getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player current = Bukkit.getPlayer(winnerId);
            if (current == null || !current.isOnline()) {
                Log.debug("Reward grant skipped: winner %s is offline.", winnerId);
                return;
            }

            RewardContext ctx = new RewardContext(
                    current,
                    baseCtx.getLoser(),
                    baseCtx.getArenaId(),
                    baseCtx.getKitId(),
                    baseCtx.getReason(),
                    baseCtx.getDurationSeconds()
            );

            for (Reward r : rewards) {
                try {
                    r.grant(ctx);
                } catch (Exception e) {
                    Log.error(e, "Failed to grant reward to %s", current.getName());
                }
            }
        }, grantDelayTicks);
    }

    /**
     * Resolves the reward list to use for a given kit.
     *
     * <p>If the kit has an override, that list is used. Otherwise the
     * default list is returned.</p>
     *
     * @param kitId kit id, possibly {@code null}
     * @return the reward list to grant
     */
    private List<Reward> resolveRewards(String kitId) {
        if (kitId != null && kitRewards.containsKey(kitId)) {
            return kitRewards.get(kitId);
        }
        return defaultRewards;
    }

    /**
     * Checks whether a player is currently on reward cooldown.
     *
     * <p>Expired entries are removed lazily on access.</p>
     *
     * @param uuid player uuid
     * @return {@code true} if the player cannot receive rewards yet
     */
    public boolean hasCooldown(UUID uuid) {
        Instant until = cooldowns.get(uuid);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Resolves the Vault economy provider if Vault is installed.
     *
     * @return the economy provider, or {@code null} if unavailable
     */
    private Economy resolveEconomy() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            return rsp != null ? rsp.getProvider() : null;
        } catch (Throwable t) {
            Log.warn("Vault present but economy service unavailable.");
            return null;
        }
    }

    /**
     * Returns the resolved Vault economy provider, resolving it on
     * first use if needed.
     *
     * <p>The provider is not resolved in the constructor because other
     * plugins may register their economy service after this plugin has
     * been enabled. The lookup is therefore deferred until the first
     * actual use, and re-attempted on every call while no provider has
     * been found yet.</p>
     *
     * @return economy provider, or {@code null} if unavailable
     */
    public Economy getEconomy() {
        if (economy == null) {
            economy = resolveEconomy();
        }
        return economy;
    }
}