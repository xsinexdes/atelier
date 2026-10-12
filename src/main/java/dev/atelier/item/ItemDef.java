package dev.atelier.item;

import org.bukkit.Material;

import java.util.*;

public final class ItemDef {
    public enum Format { ATELIER, ORAXEN, ITEMSADDER }

    public final String id;
    public final Format format;
    public String namespace = "minecraft";
    public String displayName;
    public Material material = Material.PAPER;
    public List<String> lore = new ArrayList<>();

    public Integer explicitCmd;
    public int cmd;                       // assigned by PackManager
    public String modelRef;               // "ns:path"
    public String parentModel;
    public boolean generateModel;
    public final Map<String, String> textures = new LinkedHashMap<>();
    public String finalModel;             // resolved by PackManager

    public List<String> flags = new ArrayList<>();
    public Map<String, Integer> enchants = new LinkedHashMap<>();
    public List<Map<?, ?>> attributes = new ArrayList<>();
    public List<Map<?, ?>> pdc = new ArrayList<>();
    /** Potion effects: {effect, level, duration(s), target SELF|ENEMY, trigger HIT|USE|HOLD|WEAR} */
    public List<Map<?, ?>> effects = new ArrayList<>();
    public boolean unbreakable, glow;

    /** Armor: set name, optional leather dye (RGB) and set bonuses [{pieces, effect, level}] */
    public String armorSet;
    public Integer armorColor;
    public List<Map<?, ?>> setBonuses = new ArrayList<>();

    /** Raw Oraxen-style "mechanics:" and "components:" sections (read by the mechanic listeners). */
    public org.bukkit.configuration.ConfigurationSection mechanics, components;
    /** HEAD / CHEST / LEGS / FEET for hats & custom wearables (components.equippable.slot or mechanics.hat). */
    public String equipSlot;

    public BlockDef block;
    public FurnitureDef furniture;
    public String source;
    /** Texture file of an item auto-created from drop/*.png */
    public java.nio.file.Path autoTexture;

    /** Returns the first mechanic section present under any of the names (empty section if declared without options). */
    public org.bukkit.configuration.ConfigurationSection mech(String... names) {
        if (mechanics == null) return null;
        for (String n : names) {
            org.bukkit.configuration.ConfigurationSection c = mechanics.getConfigurationSection(n);
            if (c != null) return c;
            if (mechanics.contains(n)) return new org.bukkit.configuration.MemoryConfiguration();
        }
        return null;
    }

    public ItemDef(String id, Format format) {
        this.id = id;
        this.format = format;
    }
}
