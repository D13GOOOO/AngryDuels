package com.angryguyy.duels.kit;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a YAML item definition into a live {@link ItemStack}.
 *
 * <p>The serialization format used by kits is intentionally readable so
 * that administrators can edit {@code kits.yml} by hand. Only the
 * fields that are commonly needed are supported; exotic NBT must be
 * applied programmatically by a future kit editor.</p>
 *
 * <p>Recognized keys:</p>
 * <ul>
 *     <li>{@code type} — a {@link Material} name (required)</li>
 *     <li>{@code amount} — stack size, defaults to {@code 1}</li>
 *     <li>{@code enchants} — map of enchantment key to level</li>
 *     <li>{@code potion-type} — a {@link PotionType} key, only valid
 *     for potion-like materials</li>
 *     <li>{@code name} — a MiniMessage display name</li>
 *     <li>{@code unbreakable} — boolean flag</li>
 * </ul>
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    /**
     * Deserializes an item from a YAML map.
     *
     * <p>The map may come from either {@code getValues(false)} on a
     * configuration section or a plain Java map, since the caller is
     * not required to normalize it beforehand.</p>
     *
     * @param map configuration values
     * @return the deserialized item
     * @throws IllegalArgumentException if the type is missing or
     *                                  invalid
     */
    public static ItemStack fromMap(Map<String, Object> map) {
        String typeName = (String) map.get("type");
        if (typeName == null) {
            throw new IllegalArgumentException("Missing 'type'");
        }
        Material material = Material.matchMaterial(typeName);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + typeName);
        }

        int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        Object rawEnchants = map.get("enchants");
        if (rawEnchants != null && meta != null) {
            Map<String, Object> enchMap = normalizeMap(rawEnchants);
            if (enchMap != null) {
                for (Map.Entry<String, Object> e : enchMap.entrySet()) {
                    String key = e.getKey().toLowerCase(Locale.ROOT);
                    Enchantment ench = RegistryAccess.registryAccess()
                            .getRegistry(RegistryKey.ENCHANTMENT)
                            .get(NamespacedKey.minecraft(key));
                    if (ench == null) continue;
                    int level = ((Number) e.getValue()).intValue();
                    meta.addEnchant(ench, level, true);
                }
            }
        }

        Object rawPotion = map.get("potion-type");
        if (rawPotion instanceof String potionName && meta instanceof PotionMeta potionMeta) {
            PotionType type = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.POTION)
                    .get(NamespacedKey.minecraft(potionName.toLowerCase(Locale.ROOT)));
            if (type != null) {
                potionMeta.setBasePotionType(type);
            }
        }

        Object rawName = map.get("name");
        if (rawName instanceof String name && meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize(name));
        }

        Object rawUnbreakable = map.get("unbreakable");
        if (rawUnbreakable instanceof Boolean flag && flag && meta != null) {
            meta.setUnbreakable(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Normalizes a nested value to a string-keyed map.
     *
     * <p>Bukkit's {@code getValues(false)} returns nested sections as
     * {@link ConfigurationSection} instances rather than {@link Map}
     * objects. This method accepts either form and returns a plain
     * string-keyed map so callers do not need to distinguish between
     * them.</p>
     *
     * @param raw raw value, usually a section or a map
     * @return a normalized map, or {@code null} if the value is neither
     */
    private static Map<String, Object> normalizeMap(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        if (raw instanceof ConfigurationSection cs) {
            return cs.getValues(false);
        }
        return null;
    }
}