package com.angryguyy.duels.snapshot;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of the state of a player captured before entering a
 * duel and applied back once the duel ends.
 *
 * <p>The snapshot covers everything that the duel flow mutates or may
 * mutate: inventory, armor, offhand, ender chest, XP level and points,
 * active potion effects, health, hunger, saturation, exhaustion,
 * gamemode, fire ticks, remaining air, and the exact location the player
 * was standing at, including yaw and pitch.</p>
 *
 * <p>The class implements {@link ConfigurationSerializable} so that it
 * can be written to and read from YAML without custom conversion logic.
 * The serialized form is stable across plugin versions and is used by
 * the {@link SnapshotManager} for crash recovery.</p>
 */
public final class PlayerSnapshot implements ConfigurationSerializable {

    private final UUID uuid;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final @Nullable ItemStack offhand;
    private final ItemStack[] enderChest;
    private final int level;
    private final float exp;
    private final int totalExperience;
    private final List<PotionEffect> effects;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final GameMode gameMode;
    private final int fireTicks;
    private final int remainingAir;
    private final Location location;

    /**
     * Creates a new snapshot with all fields set explicitly.
     *
     * @param uuid            unique id of the captured player
     * @param inventory       main inventory contents
     * @param armor           armor contents
     * @param offhand         offhand item, or {@code null}
     * @param enderChest      ender chest contents
     * @param level           experience level
     * @param exp             experience progress towards the next level
     * @param totalExperience total accumulated experience
     * @param effects         active potion effects
     * @param health          health value
     * @param foodLevel       hunger bar value
     * @param saturation      food saturation value
     * @param exhaustion      food exhaustion value
     * @param gameMode        current gamemode
     * @param fireTicks       remaining fire ticks
     * @param remainingAir    remaining breath
     * @param location        exact position of the player
     */
    public PlayerSnapshot(UUID uuid,
                          ItemStack[] inventory,
                          ItemStack[] armor,
                          @Nullable ItemStack offhand,
                          ItemStack[] enderChest,
                          int level,
                          float exp,
                          int totalExperience,
                          List<PotionEffect> effects,
                          double health,
                          int foodLevel,
                          float saturation,
                          float exhaustion,
                          GameMode gameMode,
                          int fireTicks,
                          int remainingAir,
                          Location location) {
        this.uuid = uuid;
        this.inventory = inventory;
        this.armor = armor;
        this.offhand = offhand;
        this.enderChest = enderChest;
        this.level = level;
        this.exp = exp;
        this.totalExperience = totalExperience;
        this.effects = effects;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.gameMode = gameMode;
        this.fireTicks = fireTicks;
        this.remainingAir = remainingAir;
        this.location = location;
    }

    /**
     * Captures the current state of a player into a new snapshot.
     *
     * <p>Inventory, armor, offhand, and ender chest arrays are cloned
     * element-by-element so that later mutations of the player's real
     * inventory do not leak into the snapshot.</p>
     *
     * @param player player to capture
     * @return an immutable snapshot representing the player state
     */
    public static PlayerSnapshot capture(Player player) {
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack[] ender = player.getEnderChest().getContents();

        return new PlayerSnapshot(
                player.getUniqueId(),
                cloneArray(inv),
                cloneArray(armor),
                offhand == null ? null : offhand.clone(),
                cloneArray(ender),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getGameMode(),
                player.getFireTicks(),
                player.getRemainingAir(),
                player.getLocation().clone()
        );
    }

    /**
     * Clones every non-null element of an array.
     *
     * @param src source array, possibly {@code null}
     * @return a deep-cloned array, or an empty array if the source was
     *         {@code null}
     */
    private static ItemStack[] cloneArray(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }

    /**
     * Returns the unique id of the captured player.
     *
     * @return player uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Returns the cloned inventory contents.
     *
     * @return inventory array
     */
    public ItemStack[] getInventory() {
        return inventory;
    }

    /**
     * Returns the cloned armor contents.
     *
     * @return armor array
     */
    public ItemStack[] getArmor() {
        return armor;
    }

    /**
     * Returns the offhand item.
     *
     * @return offhand item, or {@code null}
     */
    public @Nullable ItemStack getOffhand() {
        return offhand;
    }

    /**
     * Returns the cloned ender chest contents.
     *
     * @return ender chest array
     */
    public ItemStack[] getEnderChest() {
        return enderChest;
    }

    /**
     * Returns the experience level.
     *
     * @return level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns the experience progress.
     *
     * @return experience progress as a float between 0 and 1
     */
    public float getExp() {
        return exp;
    }

    /**
     * Returns the total accumulated experience.
     *
     * @return total experience
     */
    public int getTotalExperience() {
        return totalExperience;
    }

    /**
     * Returns the active potion effects.
     *
     * @return list of effects
     */
    public List<PotionEffect> getEffects() {
        return effects;
    }

    /**
     * Returns the health value.
     *
     * @return health
     */
    public double getHealth() {
        return health;
    }

    /**
     * Returns the hunger bar value.
     *
     * @return food level
     */
    public int getFoodLevel() {
        return foodLevel;
    }

    /**
     * Returns the food saturation value.
     *
     * @return saturation
     */
    public float getSaturation() {
        return saturation;
    }

    /**
     * Returns the food exhaustion value.
     *
     * @return exhaustion
     */
    public float getExhaustion() {
        return exhaustion;
    }

    /**
     * Returns the gamemode.
     *
     * @return gamemode
     */
    public GameMode getGameMode() {
        return gameMode;
    }

    /**
     * Returns the remaining fire ticks.
     *
     * @return fire ticks
     */
    public int getFireTicks() {
        return fireTicks;
    }

    /**
     * Returns the remaining air.
     *
     * @return remaining air ticks
     */
    public int getRemainingAir() {
        return remainingAir;
    }

    /**
     * Returns the captured location.
     *
     * @return location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Serializes this snapshot into a map suitable for YAML storage.
     *
     * @return a serializable map
     */
    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", uuid.toString());
        map.put("inventory", inventory);
        map.put("armor", armor);
        map.put("offhand", offhand);
        map.put("enderChest", enderChest);
        map.put("level", level);
        map.put("exp", exp);
        map.put("totalExperience", totalExperience);
        map.put("effects", serializeEffects(effects));
        map.put("health", health);
        map.put("foodLevel", foodLevel);
        map.put("saturation", saturation);
        map.put("exhaustion", exhaustion);
        map.put("gameMode", gameMode.name());
        map.put("fireTicks", fireTicks);
        map.put("remainingAir", remainingAir);
        map.put("location", location.serialize());
        return map;
    }

    /**
     * Deserializes a snapshot from a YAML section.
     *
     * @param map serialized map
     * @return the reconstructed snapshot
     */
    @SuppressWarnings("unchecked")
    public static PlayerSnapshot deserialize(Map<String, Object> map) {
        UUID uuid = UUID.fromString((String) map.get("uuid"));

        ItemStack[] inv = readItemStackArray(map.get("inventory"));
        ItemStack[] armor = readItemStackArray(map.get("armor"));
        ItemStack offhand = (ItemStack) map.get("offhand");
        ItemStack[] ender = readItemStackArray(map.get("enderChest"));

        int level = ((Number) map.get("level")).intValue();
        float exp = ((Number) map.get("exp")).floatValue();
        int total = ((Number) map.get("totalExperience")).intValue();

        List<PotionEffect> effects = readEffects(map.get("effects"));

        double health = ((Number) map.get("health")).doubleValue();
        int foodLevel = ((Number) map.get("foodLevel")).intValue();
        float saturation = ((Number) map.get("saturation")).floatValue();
        float exhaustion = ((Number) map.get("exhaustion")).floatValue();
        GameMode mode = GameMode.valueOf((String) map.get("gameMode"));
        int fireTicks = ((Number) map.get("fireTicks")).intValue();
        int remainingAir = ((Number) map.get("remainingAir")).intValue();
        Location location = Location.deserialize((Map<String, Object>) map.get("location"));

        return new PlayerSnapshot(uuid, inv, armor, offhand, ender,
                level, exp, total, effects, health, foodLevel, saturation,
                exhaustion, mode, fireTicks, remainingAir, location);
    }

    /**
     * Converts a list of effects into a YAML-friendly list of maps.
     *
     * <p>Each entry carries the potion type key, the duration, the
     * amplifier and the three visual flags used by the client. The key
     * is stored as a namespaced string so that it remains stable across
     * server versions and readable in the YAML file.</p>
     *
     * @param effects effects to serialize
     * @return serialized effect list
     */
    private static List<Map<String, Object>> serializeEffects(List<PotionEffect> effects) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", effect.getType().getKey().toString());
            m.put("duration", effect.getDuration());
            m.put("amplifier", effect.getAmplifier());
            m.put("ambient", effect.isAmbient());
            m.put("particles", effect.hasParticles());
            m.put("icon", effect.hasIcon());
            out.add(m);
        }
        return out;
    }

    /**
     * Reconstructs effects from their serialized form.
     *
     * <p>Effects whose potion type key no longer exists in the registry
     * are skipped silently, so that an outdated snapshot does not
     * prevent the rest of the state from being restored.</p>
     *
     * @param raw serialized effect data
     * @return list of effects
     */
    @SuppressWarnings("unchecked")
    private static List<PotionEffect> readEffects(Object raw) {
        List<PotionEffect> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object entry : (List<Object>) raw) {
            Map<String, Object> m = (Map<String, Object>) entry;
            NamespacedKey key = NamespacedKey.fromString((String) m.get("type"));
            if (key == null) continue;
            PotionEffectType type = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.MOB_EFFECT)
                    .get(key);
            if (type == null) continue;
            int duration = ((Number) m.get("duration")).intValue();
            int amplifier = ((Number) m.get("amplifier")).intValue();
            boolean ambient = (boolean) m.getOrDefault("ambient", false);
            boolean particles = (boolean) m.getOrDefault("particles", true);
            boolean icon = (boolean) m.getOrDefault("icon", true);
            out.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }
        return out;
    }

    /**
     * Reads an ItemStack array from a serialized value.
     *
     * @param raw raw value, usually a list
     * @return the reconstructed array, possibly empty
     */
    @SuppressWarnings("unchecked")
    private static ItemStack[] readItemStackArray(Object raw) {
        if (raw == null) return new ItemStack[0];
        if (raw instanceof ItemStack[] arr) return arr;
        List<Object> list = (List<Object>) raw;
        ItemStack[] out = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            out[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
        }
        return out;
    }

    /**
     * Restores this snapshot's state onto the given player.
     *
     * <p>Ordering matters: the location is applied first so that the
     * player is already in the right world when the rest of the state
     * is restored, then inventory and armor, then effects (clearing the
     * current ones), and finally health and hunger. Fire ticks and
     * remaining air are reset last to avoid the server overwriting them
     * during the same tick.</p>
     *
     * @param player player to restore
     */
    public void apply(Player player) {
        player.teleport(location);
        player.setFallDistance(0f);

        player.getInventory().setContents(inventory);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(offhand);
        player.getEnderChest().setContents(enderChest);

        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExperience);

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        player.setGameMode(gameMode);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(Math.min(health, maxHealth));

        player.setFireTicks(fireTicks);
        player.setRemainingAir(remainingAir);

        player.updateInventory();
    }
}