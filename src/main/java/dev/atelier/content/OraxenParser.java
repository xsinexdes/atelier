package dev.atelier.content;

import dev.atelier.item.BlockDef;
import dev.atelier.item.FurnitureDef;
import dev.atelier.item.ItemDef;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Reads the Oraxen YAML layout (items/, glyphs/, recipes/). Original implementation. */
final class OraxenParser {
    private OraxenParser() {}

    static void items(File file, Content out, ItemDef.Format fmt) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                ItemDef d = parseItem(id, s, fmt);
                d.source = file.getName();
                if (out.items.put(id, d) != null) out.warnings.add("Item duplicado '" + id + "' (" + file.getName() + ") - gana el último");
            } catch (Exception e) {
                out.warnings.add("Error en item '" + id + "' (" + file.getName() + "): " + e.getMessage());
            }
        }
    }

    private static ItemDef parseItem(String id, ConfigurationSection s, ItemDef.Format fmt) {
        ItemDef d = new ItemDef(id, fmt);
        d.displayName = Yml.str(s, null, "displayname", "displayName", "display_name");
        String mat = Yml.str(s, "PAPER", "material");
        Material m = Material.matchMaterial(mat);
        if (m == null) throw new IllegalArgumentException("material desconocido: " + mat);
        d.material = m;
        d.lore = s.getStringList("lore");
        d.unbreakable = s.getBoolean("Unbreakable", s.getBoolean("unbreakable", false));
        d.glow = s.getBoolean("glow", false);
        d.flags = s.getStringList("ItemFlags");
        d.attributes = s.getMapList("AttributeModifiers");
        d.pdc = s.getMapList("PersistentData");
        d.effects = s.getMapList("Effects");
        armor(s, d);

        ConfigurationSection ench = Yml.sec(s, "Enchantments", "enchantments");
        if (ench != null) for (String k : ench.getKeys(false)) d.enchants.put(k, ench.getInt(k, 1));

        ConfigurationSection pack = Yml.sec(s, "Pack", "pack");
        if (pack != null) {
            if (pack.contains("custom_model_data")) d.explicitCmd = pack.getInt("custom_model_data");
            d.generateModel = pack.getBoolean("generate_model", false);
            d.parentModel = pack.getString("parent_model");
            if (pack.getString("model") != null) d.modelRef = dev.atelier.util.Text.qualify(pack.getString("model"), "minecraft");
            readTextures(pack.get("textures", pack.get("texture")), d, "minecraft");
        }

        ConfigurationSection mech = Yml.sec(s, "mechanics", "Mechanics");
        if (mech != null) {
            ConfigurationSection blk = Yml.sec(mech, "block", "noteblock");
            if (blk != null) d.block = block(blk);
            ConfigurationSection fur = Yml.sec(mech, "furniture");
            if (fur != null) d.furniture = furniture(fur);
        }
        d.mechanics = mech;
        d.components = Yml.sec(s, "components", "Components");
        mapMechanics(d, mech);
        return d;
    }

    /** hat / equippable slot, armor_effects and efficiency are converted into Atelier equivalents. */
    private static void mapMechanics(ItemDef d, ConfigurationSection mech) {
        ConfigurationSection eq = Yml.sec(d.components, "equippable");
        if (eq != null && eq.getString("slot") != null) d.equipSlot = eq.getString("slot");
        if (mech == null) return;
        if (mech.contains("hat") && d.equipSlot == null) d.equipSlot = "HEAD";
        ConfigurationSection ae = Yml.sec(mech, "armor_effects");
        if (ae != null) for (String k : ae.getKeys(false)) {
            ConfigurationSection c = ae.getConfigurationSection(k);
            if (c == null) continue;                      // skips plain options (delay_in_ticks, requires_full_set...)
            d.effects.add(effect(k, c.getInt("amplifier", 0) + 1, "WEAR"));
        }
        ConfigurationSection ef = Yml.sec(mech, "efficiency");
        if (ef != null) d.effects.add(effect("haste", Math.max(1, ef.getInt("amount", 1)), "HOLD"));
    }

    private static Map<String, Object> effect(String name, int level, String trigger) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("effect", name.toLowerCase(java.util.Locale.ROOT)); m.put("level", level);
        m.put("duration", 10); m.put("target", "SELF"); m.put("trigger", trigger);
        return m;
    }

    /** Armor: { set, color, defense, toughness, knockback_resistance, bonus: [...] } -> attribute modifiers on the right slot. */
    private static void armor(ConfigurationSection s, ItemDef d) {
        ConfigurationSection a = Yml.sec(s, "Armor", "armor");
        if (a == null) return;
        d.armorSet = a.getString("set");
        d.setBonuses = a.getMapList("bonus");
        String col = a.getString("color");
        if (col != null) {
            try { d.armorColor = Integer.parseInt(col.replace("#", ""), 16); } catch (NumberFormatException ignored) {}
        }
        String n = d.material.name();
        String slot = n.endsWith("_HELMET") ? "HEAD" : n.endsWith("_CHESTPLATE") ? "CHEST"
                : n.endsWith("_LEGGINGS") ? "LEGS" : n.endsWith("_BOOTS") ? "FEET" : null;
        if (slot == null) return;
        String[][] map = {{"defense", "ARMOR"}, {"toughness", "ARMOR_TOUGHNESS"}, {"knockback_resistance", "KNOCKBACK_RESISTANCE"},
                {"max_health", "MAX_HEALTH"}, {"speed", "MOVEMENT_SPEED"}, {"attack_damage", "ATTACK_DAMAGE"}};
        for (String[] m : map) {
            if (!a.contains(m[0])) continue;
            Map<String, Object> mod = new java.util.LinkedHashMap<>();
            mod.put("attribute", m[1]); mod.put("amount", a.getDouble(m[0])); mod.put("operation", 0); mod.put("slot", slot);
            d.attributes.add(mod);
        }
    }

    static void readTextures(Object o, ItemDef d, String ns) {
        if (o instanceof List<?> l) {
            int i = 0;
            for (Object t : l) d.textures.put("layer" + i++, dev.atelier.util.Text.qualify(String.valueOf(t), ns));
        } else if (o instanceof ConfigurationSection cs) {
            for (String k : cs.getKeys(false)) d.textures.put(k, dev.atelier.util.Text.qualify(cs.getString(k), ns));
        } else if (o instanceof Map<?, ?> mp) {
            for (Map.Entry<?, ?> e : mp.entrySet()) d.textures.put(String.valueOf(e.getKey()), dev.atelier.util.Text.qualify(String.valueOf(e.getValue()), ns));
        } else if (o instanceof String str) {
            d.textures.put("layer0", dev.atelier.util.Text.qualify(str, ns));
        }
    }

    private static BlockDef block(ConfigurationSection b) {
        BlockDef bd = new BlockDef();
        Object v = Yml.get(b, "custom-variation", "custom_variation");
        if (v != null) bd.variation = Yml.integer(v, -1);
        ConfigurationSection snd = Yml.sec(b, "block-sounds", "block_sounds", "sounds");
        if (snd != null) {
            bd.breakSound = Yml.str(snd, bd.breakSound, "break-sound", "break_sound", "break");
            bd.placeSound = Yml.str(snd, bd.placeSound, "place-sound", "place_sound", "place");
        }
        for (Map<?, ?> rule : b.getMapList("breaking")) {
            List<String> tools = Yml.strings(rule.get("when"));
            bd.rules.add(new BlockDef.Rule(tools, Yml.drops(rule.get("drops"))));
        }
        if (bd.rules.isEmpty()) {
            ConfigurationSection drop = Yml.sec(b, "drop");
            if (drop != null) bd.rules.add(new BlockDef.Rule(List.of(), Yml.drops(drop.getList("loots"))));
        }
        return bd;
    }

    private static FurnitureDef furniture(ConfigurationSection f) {
        FurnitureDef fd = new FurnitureDef();
        for (String h : f.getStringList("hitboxes")) {          // "x,y,z w,h"
            try {
                String[] p = h.trim().split("\\s+");
                String[] wh = p[1].split(",");
                fd.width = (float) Double.parseDouble(wh[0]);
                fd.height = (float) Double.parseDouble(wh[1]);
                break;
            } catch (Exception ignored) {}
        }
        fd.solid = f.getBoolean("barrier", false) || !f.getMapList("barriers").isEmpty();
        fd.fixedRotation = f.getBoolean("fixed_rotation", false);
        ConfigurationSection dp = Yml.sec(f, "display_entity_properties");
        if (dp != null) {
            fd.transform = dp.getString("display_transform", "FIXED");
            ConfigurationSection sc = Yml.sec(dp, "scale");
            if (sc != null) {
                fd.scaleX = (float) sc.getDouble("x", 1); fd.scaleY = (float) sc.getDouble("y", 1); fd.scaleZ = (float) sc.getDouble("z", 1);
            }
        }
        ConfigurationSection lp = Yml.sec(f, "limited_placing");
        if (lp != null) {
            fd.floor = lp.getBoolean("floor", true); fd.wall = lp.getBoolean("wall", false); fd.roof = lp.getBoolean("roof", false);
        }
        ConfigurationSection drop = Yml.sec(f, "drop");
        if (drop != null) fd.drops.addAll(Yml.drops(drop.getList("loots")));
        return fd;
    }

    static void glyphs(File file, Content out) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null || s.getString("texture") == null) continue;
            GlyphDef g = new GlyphDef(id);
            g.texture = dev.atelier.util.Text.qualify(s.getString("texture"), "minecraft");
            g.ascent = s.getInt("ascent", 8);
            g.height = s.getInt("height", 8);
            String ch = s.getString("char");
            if (ch != null && ch.length() == 1) g.explicitChar = ch.charAt(0);
            g.placeholders.add(":" + id + ":");
            ConfigurationSection ph = Yml.sec(s, "chat");
            if (ph != null) g.placeholders.addAll(ph.getStringList("placeholders"));
            g.placeholders.addAll(s.getStringList("placeholders"));
            g.permission = s.getString("permission");
            out.glyphs.put(id, g);
        }
    }

    static void recipes(File file, Content out) {
        String name = file.getName().toLowerCase();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            String kind;
            if (name.contains("blast")) kind = "blasting";
            else if (name.contains("smok")) kind = "smoking";
            else if (name.contains("campfire")) kind = "campfire";
            else if (name.contains("stonecut")) kind = "stonecutter";
            else if (name.contains("furnace") || name.contains("smelt") || s.contains("input")) kind = "furnace";
            else if (s.contains("shape")) kind = "shaped";
            else kind = "shapeless";
            out.recipes.add(new Content.RecipeDef(id, kind, false, s));
        }
    }
}
