package dev.atelier.content;

import dev.atelier.item.ItemDef;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.*;

/** Everything discovered on disk during a (re)load. */
public final class Content {
    public record AssetRoot(Path dir, boolean itemsAdder) {}
    /** kind: shaped | shapeless | furnace | blasting | smoking | campfire | stonecutter */
    public record RecipeDef(String id, String kind, boolean itemsAdder, ConfigurationSection section) {}

    public final Map<String, ItemDef> items = new LinkedHashMap<>();
    public final Map<String, GlyphDef> glyphs = new LinkedHashMap<>();
    public final List<RecipeDef> recipes = new ArrayList<>();
    public final List<AssetRoot> assets = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
}
