package dev.atelier.recipe;

import dev.atelier.AtelierPlugin;
import dev.atelier.content.Content;
import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeManager {
    private final AtelierPlugin plugin;
    private final ItemRegistry reg;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeManager(AtelierPlugin plugin, ItemRegistry reg) {
        this.plugin = plugin;
        this.reg = reg;
    }

    public int count() { return registered.size(); }

    public void register(List<Content.RecipeDef> defs) {
        for (NamespacedKey k : registered) Bukkit.removeRecipe(k);
        registered.clear();
        for (Content.RecipeDef d : defs) {
            try { add(d); }
            catch (Exception e) { plugin.getLogger().warning("Receta '" + d.id() + "' inválida: " + e.getMessage()); }
        }
    }

    private void add(Content.RecipeDef d) {
        ConfigurationSection s = d.section();
        NamespacedKey key = new NamespacedKey(plugin, d.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
        Bukkit.removeRecipe(key);

        Object resObj = s.get("result");
        ItemStack result = stack(resObj);
        if (result == null) throw new IllegalArgumentException("result no resuelto");

        Recipe recipe;
        switch (d.kind()) {
            case "shaped" -> {
                ShapedRecipe r = new ShapedRecipe(key, result);
                List<String> shape = s.contains("shape") ? s.getStringList("shape") : s.getStringList("pattern");
                if (!d.itemsAdder()) shape = shape.stream().map(row -> row.replace('_', ' ')).toList();
                r.shape(shape.toArray(new String[0]));
                ConfigurationSection ing = s.getConfigurationSection("ingredients");
                if (ing == null) throw new IllegalArgumentException("sin ingredients");
                for (String k : ing.getKeys(false)) r.setIngredient(k.charAt(0), choice(ing.get(k)));
                recipe = r;
            }
            case "shapeless" -> {
                ShapelessRecipe r = new ShapelessRecipe(key, result);
                Object ing = s.get("ingredients");
                if (ing instanceof ConfigurationSection cs) {
                    for (String k : cs.getKeys(false)) {
                        Object o = cs.get(k);
                        int n = o instanceof ConfigurationSection c ? Math.max(1, c.getInt("amount", 1)) : 1;
                        for (int i = 0; i < n; i++) r.addIngredient(choice(o));
                    }
                } else if (ing instanceof List<?> l) {
                    for (Object o : l) r.addIngredient(choice(o));
                } else throw new IllegalArgumentException("sin ingredients");
                recipe = r;
            }
            default -> {
                Object in = s.contains("input") ? s.get("input") : s.contains("ingredient") ? s.get("ingredient") : s.get("source");
                RecipeChoice c = choice(in);
                float xp = (float) s.getDouble("experience", 0);
                int time = s.getInt("cookingTime", s.getInt("time", 200));
                recipe = switch (d.kind()) {
                    case "blasting" -> new BlastingRecipe(key, result, c, xp, time);
                    case "smoking" -> new SmokingRecipe(key, result, c, xp, time);
                    case "campfire" -> new CampfireRecipe(key, result, c, xp, time);
                    case "stonecutter" -> new StonecuttingRecipe(key, result, c);
                    default -> new FurnaceRecipe(key, result, c, xp, time);
                };
            }
        }
        Bukkit.addRecipe(recipe);
        registered.add(key);
    }

    private static final String[] ID_KEYS = {"oraxen_item", "atelier_item", "itemsadder_item", "item", "minecraft_type", "minecraft_item"};

    private ItemStack stack(Object o) {
        if (o instanceof String str) return reg.stackFromString(str, 1);
        if (o instanceof ConfigurationSection c) {
            int amount = Math.max(1, c.getInt("amount", 1));
            for (String k : ID_KEYS) if (c.contains(k)) return reg.stackFromString(c.getString(k), amount);
        }
        return null;
    }

    private RecipeChoice choice(Object o) {
        ItemStack st = stack(o);
        if (st == null) throw new IllegalArgumentException("ingrediente no resuelto: " + o);
        return reg.fromStack(st) != null ? new RecipeChoice.ExactChoice(st) : new RecipeChoice.MaterialChoice(st.getType());
    }
}
