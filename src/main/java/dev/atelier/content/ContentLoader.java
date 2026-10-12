package dev.atelier.content;

import dev.atelier.AtelierPlugin;
import dev.atelier.item.BlockDef;
import dev.atelier.item.FurnitureDef;
import dev.atelier.item.ItemDef;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Scans plugin folder + configured sources and merges Oraxen/ItemsAdder-style content. */
public final class ContentLoader {
    private ContentLoader() {}

    public static Content load(AtelierPlugin plugin) {
        Content out = new Content();
        Path server = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
        List<Path> roots = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("sources")) addIfDir(roots, server.resolve(s));
        if (plugin.getConfig().getBoolean("auto-detect-plugins", true)) {
            addIfDir(roots, server.resolve("plugins/Oraxen"));
            addIfDir(roots, server.resolve("plugins/ItemsAdder"));
        }
        roots.add(plugin.getDataFolder().toPath());   // last => own content wins over imports
        for (Path root : roots) {
            try { scan(root, out); }
            catch (IOException e) { out.warnings.add("No se pudo leer " + root + ": " + e.getMessage()); }
        }
        return out;
    }

    private static void addIfDir(List<Path> roots, Path p) {
        Path n = p.normalize();
        if (Files.isDirectory(n) && !roots.contains(n)) roots.add(n);
    }

    private static void scan(Path root, Content out) throws IOException {
        // Oraxen-style layout (also Atelier's own)
        yml(root.resolve("items"), f -> OraxenParser.items(f, out, ItemDef.Format.ORAXEN));
        yml(root.resolve("glyphs"), f -> OraxenParser.glyphs(f, out));
        yml(root.resolve("recipes"), f -> OraxenParser.recipes(f, out));
        Path drop = root.resolve("drop");
        if (Files.isDirectory(drop)) autoItems(drop, out);
        Path pack = root.resolve("pack");
        if (Files.isDirectory(pack)) out.assets.add(new Content.AssetRoot(pack, false));

        // ItemsAdder-style layout
        Path contents = root.resolve("contents");
        if (Files.isDirectory(contents)) {
            try (Stream<Path> nss = Files.list(contents)) {
                for (Path ns : (Iterable<Path>) nss::iterator) {
                    if (!Files.isDirectory(ns)) continue;
                    Path rp = ns.resolve("resourcepack");
                    try (Stream<Path> w = Files.walk(ns)) {
                        w.filter(p -> p.toString().endsWith(".yml") && !p.startsWith(rp))
                                .sorted()
                                .forEach(p -> ItemsAdderParser.file(p.toFile(), out));
                    }
                    if (Files.isDirectory(rp)) out.assets.add(new Content.AssetRoot(rp, true));
                }
            }
        }
    }

    private static void yml(Path dir, java.util.function.Consumer<File> action) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> w = Files.walk(dir)) {
            w.filter(p -> p.toString().endsWith(".yml")).sorted().forEach(p -> action.accept(p.toFile()));
        }
    }

    // ───────── drop/*.png -> items automáticos ─────────
    private static final String[][] SUFFIX = {
            {"_sword", "DIAMOND_SWORD"}, {"_pickaxe", "DIAMOND_PICKAXE"}, {"_axe", "DIAMOND_AXE"},
            {"_shovel", "DIAMOND_SHOVEL"}, {"_hoe", "DIAMOND_HOE"},
            {"_helmet", "LEATHER_HELMET"}, {"_chestplate", "LEATHER_CHESTPLATE"},
            {"_leggings", "LEATHER_LEGGINGS"}, {"_boots", "LEATHER_BOOTS"}};
    private static final Map<String, Object[]> ARMOR = Map.of(
            "_helmet", new Object[]{"HEAD", 3.0}, "_chestplate", new Object[]{"CHEST", 8.0},
            "_leggings", new Object[]{"LEGS", 6.0}, "_boots", new Object[]{"FEET", 3.0});

    private static void autoItems(Path dir, Content out) throws IOException {
        List<Path> pngs;
        try (Stream<Path> w = Files.walk(dir)) {
            pngs = w.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList();
        }
        for (Path p : pngs) {
            String id = p.getFileName().toString().replaceAll("(?i)\\.png$", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            if (out.items.containsKey(id)) { out.warnings.add("drop/" + p.getFileName() + ": ya existe un item '" + id + "' (se ignora el PNG)"); continue; }
            ItemDef d = new ItemDef(id, ItemDef.Format.ATELIER);
            d.source = "drop/" + p.getFileName();
            d.autoTexture = p;
            d.textures.put("layer0", "atelier:drop/" + id);
            StringBuilder name = new StringBuilder();
            for (String w : id.split("_")) if (!w.isEmpty()) name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            d.displayName = name.toString().trim();
            for (String[] s : SUFFIX) {
                if (!id.endsWith(s[0])) continue;
                d.material = Material.valueOf(s[1]);
                Object[] arm = ARMOR.get(s[0]);
                if (arm != null) {
                    d.armorSet = id.substring(0, id.length() - s[0].length());
                    d.armorColor = 0xFFFFFF;                 // sin tinte marrón del cuero
                    d.flags.add("HIDE_DYE");
                    Map<String, Object> mod = new LinkedHashMap<>();
                    mod.put("attribute", "ARMOR"); mod.put("amount", arm[1]); mod.put("operation", 0); mod.put("slot", arm[0]);
                    d.attributes.add(mod);
                }
                break;
            }
            if (id.endsWith("_block")) { d.block = new BlockDef(); d.parentModel = "block/cube_all"; }
            else if (id.endsWith("_furniture")) { d.furniture = new FurnitureDef(); d.parentModel = "block/cube_all"; }
            out.items.put(id, d);
        }
    }
}
