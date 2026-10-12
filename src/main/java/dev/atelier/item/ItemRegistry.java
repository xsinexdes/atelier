package dev.atelier.item;

import dev.atelier.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

public final class ItemRegistry {
    public final NamespacedKey keyId, keyDur, keyBag;
    /** Same key Oraxen writes, so items already in player inventories keep working. */
    private static final NamespacedKey KEY_ORAXEN = new NamespacedKey("oraxen", "id");

    private final Logger log;
    private volatile Map<String, ItemDef> items = Map.of();
    private volatile Map<Integer, ItemDef> byVariation = Map.of();

    public ItemRegistry(Plugin plugin) {
        this.keyId = new NamespacedKey(plugin, "id");
        this.keyDur = new NamespacedKey(plugin, "dur");
        this.keyBag = new NamespacedKey(plugin, "bag");
        this.log = plugin.getLogger();
    }

    public void set(Collection<ItemDef> defs) {
        Map<String, ItemDef> m = new LinkedHashMap<>();
        Map<Integer, ItemDef> v = new HashMap<>();
        for (ItemDef d : defs) {
            m.put(d.id, d);
            if (d.block != null && d.block.variation >= 0) v.put(d.block.variation, d);
        }
        this.items = m;
        this.byVariation = v;
    }

    public Collection<ItemDef> all() { return items.values(); }
    public Set<String> ids() { return items.keySet(); }
    public ItemDef byVariation(int variation) { return byVariation.get(variation); }

    /** Accepts "id", "atelier:id", "oraxen:id", "itemsadder:ns:id" or "ns:id". */
    public ItemDef get(String id) {
        if (id == null) return null;
        ItemDef d = items.get(id);
        if (d != null) return d;
        for (String p : new String[]{"atelier:", "oraxen:", "itemsadder:"}) {
            if (id.regionMatches(true, 0, p, 0, p.length())) {
                d = items.get(id.substring(p.length()));
                if (d != null) return d;
            }
        }
        return null;
    }

    public ItemDef fromStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(keyId, PersistentDataType.STRING);
        if (id == null) id = pdc.get(KEY_ORAXEN, PersistentDataType.STRING);
        return id == null ? null : items.get(id);
    }

    /** Resolves a custom item id OR a vanilla material name. */
    public ItemStack stackFromString(String s, int amount) {
        ItemDef d = get(s);
        if (d != null) return create(d, amount);
        String name = s.startsWith("minecraft:") ? s.substring(10) : s;
        Material m = Material.matchMaterial(name);
        return m == null || m.isAir() ? null : new ItemStack(m, amount);
    }

    public ItemStack create(ItemDef d, int amount) {
        ItemStack stack = new ItemStack(d.material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (d.displayName != null) meta.displayName(Text.parse(d.displayName));
        if (!d.lore.isEmpty()) meta.lore(d.lore.stream().map(Text::parse).toList());
        if (d.cmd > 0) meta.setCustomModelData(d.cmd);
        if (d.unbreakable) meta.setUnbreakable(true);
        if (d.glow) meta.setEnchantmentGlintOverride(true);
        if (d.components != null && d.components.getConfigurationSection("food") != null) {
            var food = d.components.getConfigurationSection("food");
            var fc = meta.getFood();
            fc.setNutrition(food.getInt("nutrition", 4));
            fc.setSaturation((float) food.getDouble("saturation", 2.4));
            fc.setCanAlwaysEat(food.getBoolean("can_always_eat", false));
            var cons = d.components.getConfigurationSection("consumable");
            if (cons != null) fc.setEatSeconds((float) cons.getDouble("consume_seconds", 1.6));
            meta.setFood(fc);
        }
        if (d.armorColor != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm)
            lm.setColor(org.bukkit.Color.fromRGB(d.armorColor));

        for (String f : d.flags) {
            String n = f.toUpperCase(Locale.ROOT);
            if (n.equals("HIDE_POTION_EFFECTS")) n = "HIDE_ADDITIONAL_TOOLTIP";
            try { meta.addItemFlags(ItemFlag.valueOf(n)); } catch (IllegalArgumentException ignored) {}
        }
        for (Map.Entry<String, Integer> e : d.enchants.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase(Locale.ROOT)));
            if (ench != null) meta.addEnchant(ench, e.getValue(), true);
            else log.warning("Encantamiento desconocido '" + e.getKey() + "' en " + d.id);
        }

        int i = 0;
        for (Map<?, ?> a : d.attributes) {
            try { applyAttribute(meta, d, a, i++); }
            catch (Exception ex) { log.warning("Atributo inválido en " + d.id + ": " + ex.getMessage()); }
        }
        for (Map<?, ?> p : d.pdc) {
            try { applyPdc(meta.getPersistentDataContainer(), p); }
            catch (Exception ex) { log.warning("PersistentData inválido en " + d.id + ": " + ex.getMessage()); }
        }
        meta.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, d.id);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyAttribute(ItemMeta meta, ItemDef d, Map<?, ?> a, int index) {
        String raw = String.valueOf(a.get("attribute")).toUpperCase(Locale.ROOT)
                .replace("GENERIC.", "").replace("GENERIC_", "").replace("PLAYER.", "PLAYER_");
        Attribute attr = null;
        for (String cand : new String[]{"GENERIC_" + raw, "PLAYER_" + raw, raw}) {
            try { attr = Attribute.valueOf(cand); break; } catch (IllegalArgumentException ignored) {}
        }
        if (attr == null) throw new IllegalArgumentException("atributo " + raw);
        double amount = Double.parseDouble(String.valueOf(a.get("amount")));
        int op = a.containsKey("operation") ? Integer.parseInt(String.valueOf(a.get("operation"))) : 0;
        AttributeModifier.Operation operation = AttributeModifier.Operation.values()[Math.max(0, Math.min(2, op))];
        String slot = a.containsKey("slot") ? String.valueOf(a.get("slot")).toUpperCase(Locale.ROOT) : "ANY";
        String group = switch (slot) {
            case "HAND", "MAINHAND", "MAIN_HAND" -> "mainhand";
            case "OFF_HAND", "OFFHAND" -> "offhand";
            default -> slot.toLowerCase(Locale.ROOT);
        };
        EquipmentSlotGroup g = EquipmentSlotGroup.getByName(group);
        if (g == null) g = EquipmentSlotGroup.ANY;
        String keyName = (d.id + "_" + raw + "_" + index).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        meta.addAttributeModifier(attr, new AttributeModifier(new NamespacedKey("atelier", keyName), amount, operation, g));
    }

    private void applyPdc(PersistentDataContainer pdc, Map<?, ?> p) {
        NamespacedKey key = NamespacedKey.fromString(String.valueOf(p.get("key")));
        if (key == null) throw new IllegalArgumentException("key inválida");
        String type = String.valueOf(p.get("type")).toUpperCase(Locale.ROOT);
        String v = String.valueOf(p.get("value"));
        switch (type) {
            case "STRING" -> pdc.set(key, PersistentDataType.STRING, v);
            case "INTEGER" -> pdc.set(key, PersistentDataType.INTEGER, Integer.parseInt(v));
            case "LONG" -> pdc.set(key, PersistentDataType.LONG, Long.parseLong(v));
            case "DOUBLE" -> pdc.set(key, PersistentDataType.DOUBLE, Double.parseDouble(v));
            case "BYTE", "BOOLEAN" -> pdc.set(key, PersistentDataType.BYTE,
                    v.equalsIgnoreCase("true") ? (byte) 1 : v.equalsIgnoreCase("false") ? (byte) 0 : Byte.parseByte(v));
            default -> throw new IllegalArgumentException("tipo " + type);
        }
    }
}
