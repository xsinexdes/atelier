package dev.atelier.content;

import dev.atelier.item.BlockDef;
import dev.atelier.item.FurnitureDef;
import dev.atelier.item.ItemDef;
import dev.atelier.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads ItemsAdder configs (contents/&lt;ns&gt;/configs/*.yml). Original implementation. */
final class ItemsAdderParser {
    private ItemsAdderParser() {}

    static void file(File file, Content out) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String ns = y.getString("info.namespace");
        if (ns == null) return;

        ConfigurationSection items = y.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;
            String id = ns + ":" + key;
            try {
                ItemDef d = item(id, ns, s, out);
                d.source = file.getName();
                out.items.put(id, d);
            } catch (Exception e) {
                out.warnings.add("Error en item '" + id + "' (" + file.getName() + "): " + e.getMessage());
            }
        }

        ConfigurationSection fonts = y.getConfigurationSection("font_images");
        if (fonts != null) for (String key : fonts.getKeys(false)) {
            ConfigurationSection s = fonts.getConfigurationSection(key);
            if (s == null || s.getString("path") == null) continue;
            GlyphDef g = new GlyphDef(ns + ":" + key);
            g.texture = Text.qualify(s.getString("path"), ns);
            g.ascent = s.getInt("y_position", 8);
            g.height = s.getInt("scale_ratio", 8);
            g.placeholders.add(":" + key + ":");
            out.glyphs.put(g.id, g);
        }

        ConfigurationSection rec = y.getConfigurationSection("recipes");
        if (rec != null) for (String station : rec.getKeys(false)) {
            ConfigurationSection group = rec.getConfigurationSection(station);
            if (group == null) continue;
            for (String rid : group.getKeys(false)) {
                ConfigurationSection r = group.getConfigurationSection(rid);
                if (r == null) continue;
                String kind = switch (station) {
                    case "furnace" -> "furnace";
                    case "blast_furnace", "blasting" -> "blasting";
                    case "smoker", "smoking" -> "smoking";
                    case "campfire" -> "campfire";
                    case "stonecutter" -> "stonecutter";
                    default -> r.contains("pattern") ? "shaped" : "shapeless";
                };
                out.recipes.add(new Content.RecipeDef(ns + ":" + rid, kind, true, r));
            }
        }
    }

    private static ItemDef item(String id, String ns, ConfigurationSection s, Content out) {
        ItemDef d = new ItemDef(id, ItemDef.Format.ITEMSADDER);
        d.namespace = ns;
        d.displayName = s.getString("display_name");
        d.lore = s.getStringList("lore");

        ConfigurationSection res = Yml.sec(s, "resource");
        if (res != null) {
            Material m = Material.matchMaterial(res.getString("material", "PAPER"));
            if (m == null) throw new IllegalArgumentException("material desconocido: " + res.getString("material"));
            d.material = m;
            if (res.contains("model_id")) d.explicitCmd = res.getInt("model_id");
            d.generateModel = res.getBoolean("generate", false);
            if (res.getString("model_path") != null) d.modelRef = Text.qualify(res.getString("model_path"), ns);
            d.parentModel = res.getString("parent");
            OraxenParser.readTextures(res.get("textures"), d, ns);
        }

        ConfigurationSection ench = Yml.sec(s, "enchants");
        if (ench != null) for (String k : ench.getKeys(false)) d.enchants.put(k, ench.getInt(k, 1));

        ConfigurationSection mods = Yml.sec(s, "attribute_modifiers");
        if (mods != null) for (String slot : mods.getKeys(false)) {
            ConfigurationSection sl = mods.getConfigurationSection(slot);
            if (sl == null) continue;
            for (String attr : sl.getKeys(false)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("attribute", attr); m.put("amount", sl.getDouble(attr)); m.put("operation", 0);
                m.put("slot", slot.equalsIgnoreCase("mainhand") ? "HAND" : slot.equalsIgnoreCase("offhand") ? "OFF_HAND" : slot.toUpperCase());
                d.attributes.add(m);
            }
        }

        ConfigurationSection beh = Yml.sec(s, "behaviours");
        ConfigurationSection fur = Yml.sec(beh, "furniture");
        if (fur != null) {
            FurnitureDef fd = new FurnitureDef();
            fd.solid = fur.getBoolean("solid", false);
            fd.fixedRotation = fur.getBoolean("fixed_rotation", false);
            ConfigurationSection hb = Yml.sec(fur, "hitbox");
            if (hb != null) {
                fd.width = (float) Math.max(hb.getDouble("width", 1), hb.getDouble("length", 1));
                fd.height = (float) hb.getDouble("height", 1);
            }
            ConfigurationSection pl = Yml.sec(fur, "placeable_on");
            if (pl != null) { fd.floor = pl.getBoolean("floor", true); fd.wall = pl.getBoolean("walls", false); fd.roof = pl.getBoolean("ceiling", false); }
            d.furniture = fd;
        }

        ConfigurationSection blk = Yml.sec(Yml.sec(s, "specific_properties"), "block");
        if (blk != null) {
            String type = Yml.str(Yml.sec(blk, "placed_model"), "", "type").toUpperCase();
            if (type.startsWith("REAL_NOTE")) {
                BlockDef bd = new BlockDef();
                bd.rules.add(new BlockDef.Rule(new ArrayList<>(), List.of(new BlockDef.Drop(id, 1.0, 1, 1))));
                d.block = bd;
            } else {
                out.warnings.add("Bloque '" + id + "' tipo " + type + " no soportado aún (solo REAL_NOTE) - se carga como item");
            }
        }
        return d;
    }
}
