package dev.atelier.pack;

import com.google.gson.*;
import dev.atelier.AtelierPlugin;
import dev.atelier.block.NoteStates;
import dev.atelier.content.Content;
import dev.atelier.content.GlyphDef;
import dev.atelier.item.ItemDef;
import dev.atelier.util.Text;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the resource pack: merges Oraxen/ItemsAdder asset folders, generates item models with
 * custom_model_data overrides (1.21.1 style), note block blockstates, glyph fonts, and the zip.
 */
public final class PackManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Oraxen-style shortcut folders that live under assets/minecraft/. */
    private static final Set<String> MC_DIRS = Set.of("textures", "models", "sounds", "font", "lang", "shaders",
            "particles", "atlases", "blockstates", "items", "equipment", "post_effect", "texts");

    private final AtelierPlugin plugin;
    private final PackHost host = new PackHost();
    private Path stage;
    private volatile byte[] sha1 = new byte[0];
    private volatile String sha1Hex = "";
    private volatile long zipSize;
    private final UUID packId = UUID.nameUUIDFromBytes("atelier-pack".getBytes(StandardCharsets.UTF_8));
    public int missingTextures;

    public PackManager(AtelierPlugin plugin) { this.plugin = plugin; }

    public String sha1Hex() { return sha1Hex; }
    public long size() { return zipSize; }

    // ───────────────────────── build ─────────────────────────

    public void build(Content content) throws Exception {
        Path buildDir = plugin.getDataFolder().toPath().resolve("build");
        stage = buildDir.resolve("pack");
        deleteTree(stage);
        Files.createDirectories(stage);
        missingTextures = 0;

        for (Content.AssetRoot r : content.assets) {
            if (r.itemsAdder()) copyItemsAdder(r.dir()); else copyOraxen(r.dir());
        }

        for (ItemDef d : content.items.values())
            if (d.autoTexture != null) copyFile(d.autoTexture, stage.resolve("assets/atelier/textures/drop/" + d.id + ".png"));

        assignIds(content);
        generateItemModels(content);
        writeItemOverrides(content);
        writeBlockstates(content);
        writeFonts(content);
        writeMcmeta();

        Path zip = buildDir.resolve("pack.zip");
        zip(stage, zip);
        byte[] bytes = Files.readAllBytes(zip);
        zipSize = bytes.length;
        sha1 = MessageDigest.getInstance("SHA-1").digest(bytes);
        sha1Hex = HexFormat.of().formatHex(sha1);
        host.setData(bytes);
        startHost();
    }

    private void startHost() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("pack.enabled", true)) return;
        if (!"embedded".equalsIgnoreCase(cfg.getString("pack.host.mode", "embedded"))) { host.stop(); return; }
        try { host.start(cfg.getInt("pack.host.bind-port", 8082)); }
        catch (Exception e) { plugin.getLogger().warning("No se pudo iniciar el servidor del pack: " + e.getMessage()); }
    }

    public void shutdown() { host.stop(); }

    // ───────────────────────── asset import ─────────────────────────

    private void copyOraxen(Path pack) throws IOException {
        try (Stream<Path> s = Files.list(pack)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (n.equals("assets") && Files.isDirectory(p)) copyTree(p, stage.resolve("assets"));
                else if (n.equals("pack.png")) Files.copy(p, stage.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
                else if (n.equals("sounds.json")) copyFile(p, stage.resolve("assets/minecraft/sounds.json"));
                else if (Files.isDirectory(p) && MC_DIRS.contains(n)) copyTree(p, stage.resolve("assets/minecraft/" + n));
            }
        }
    }

    private void copyItemsAdder(Path rp) throws IOException {
        try (Stream<Path> s = Files.list(rp)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (n.equals("assets")) copyTree(p, stage.resolve("assets"));
                    else copyTree(p, stage.resolve("assets/" + n));
                } else if (n.equals("pack.png")) {
                    Files.copy(p, stage.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> w = Files.walk(from)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dest);
                else copyFile(p, dest);
            }
        }
    }

    private static void copyFile(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    // ───────────────────────── ids ─────────────────────────

    private void assignIds(Content content) {
        PersistentIds cmds = new PersistentIds(new java.io.File(plugin.getDataFolder(), "registry-cmd.yml"));
        PersistentIds vars = new PersistentIds(new java.io.File(plugin.getDataFolder(), "registry-blocks.yml"));
        Set<Integer> takenCmd = new HashSet<>(), takenVar = new HashSet<>();
        List<ItemDef> needCmd = new ArrayList<>(), needVar = new ArrayList<>();

        for (ItemDef d : content.items.values()) {
            if (d.explicitCmd != null) { d.cmd = d.explicitCmd; takenCmd.add(d.explicitCmd); }
            else if (d.modelRef != null || d.generateModel || !d.textures.isEmpty()) needCmd.add(d);
            if (d.block != null) {
                if (d.block.variation > NoteStates.MAX_VARIATION)
                    throw new IllegalStateException("custom-variation fuera de rango (max " + NoteStates.MAX_VARIATION + ") en " + d.id);
                if (d.block.variation >= 0) takenVar.add(d.block.variation); else needVar.add(d);
            }
        }
        // 1) keep values stored on disk so existing items/blocks never change; 2) allocate only the new ones
        for (ItemDef d : needCmd) { Integer v = cmds.claim(d.id, takenCmd); if (v != null) d.cmd = v; }
        for (ItemDef d : needCmd) if (d.cmd == 0) d.cmd = cmds.allocate(d.id, 100000, Integer.MAX_VALUE - 1, takenCmd);
        for (ItemDef d : needVar) { Integer v = vars.claim(d.id, takenVar); if (v != null) d.block.variation = v; }
        for (ItemDef d : needVar) if (d.block.variation < 0) d.block.variation = vars.allocate(d.id, 1, NoteStates.MAX_VARIATION, takenVar);
        cmds.save();
        vars.save();
    }

    // ───────────────────────── models ─────────────────────────

    private static boolean isHandheld(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.equals("STICK") || n.equals("BLAZE_ROD") || n.equals("BONE") || n.equals("MACE");
    }

    private static String genPath(ItemDef d) {
        String safe = d.id.toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^a-z0-9/._-]", "_");
        return d.namespace + ":atelier/" + safe;
    }

    private void generateItemModels(Content content) throws IOException {
        for (ItemDef d : content.items.values()) {
            boolean hasTex = !d.textures.isEmpty();
            if (d.modelRef != null && !d.generateModel && !hasTex) { d.finalModel = d.modelRef; continue; }
            if (!d.generateModel && !hasTex) continue;

            String parent = d.parentModel != null ? d.parentModel
                    : d.block != null ? "block/cube_all"
                    : isHandheld(d.material) ? "item/handheld" : "item/generated";
            parent = Text.qualify(parent, "minecraft");

            Map<String, String> tex = new LinkedHashMap<>(d.textures);
            if (parent.endsWith("cube_all") && tex.containsKey("layer0")) tex.put("all", tex.remove("layer0"));
            if (!tex.isEmpty() && !tex.containsKey("particle")) tex.put("particle", tex.values().iterator().next());

            JsonObject o = new JsonObject();
            o.addProperty("parent", parent);
            JsonObject t = new JsonObject();
            for (Map.Entry<String, String> e : tex.entrySet()) {
                t.addProperty(e.getKey(), e.getValue());
                checkTexture(d, e.getValue());
            }
            if (t.size() > 0) o.add("textures", t);

            String target = d.modelRef != null ? d.modelRef : genPath(d);
            writeJson(stage.resolve("assets/" + Text.ns(target) + "/models/" + Text.path(target) + ".json"), o);
            d.finalModel = target;
        }
    }

    private void checkTexture(ItemDef d, String ref) {
        Path p = stage.resolve("assets/" + Text.ns(ref) + "/textures/" + Text.path(ref) + ".png");
        String path = Text.path(ref);
        boolean vanilla = Text.ns(ref).equals("minecraft")
                && (path.startsWith("item/") || path.startsWith("block/") || path.startsWith("entity/"));
        if (!vanilla && !Files.exists(p)) {
            missingTextures++;
            plugin.getLogger().warning("Textura no encontrada para '" + d.id + "': " + ref);
        }
    }

    private void writeItemOverrides(Content content) throws IOException {
        Map<Material, List<ItemDef>> byMat = new EnumMap<>(Material.class);
        for (ItemDef d : content.items.values())
            if (d.cmd > 0 && d.finalModel != null) byMat.computeIfAbsent(d.material, k -> new ArrayList<>()).add(d);

        for (Map.Entry<Material, List<ItemDef>> e : byMat.entrySet()) {
            Material mat = e.getKey();
            String key = mat.getKey().getKey();
            Path file = stage.resolve("assets/minecraft/models/item/" + key + ".json");
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();

            if (!root.has("parent")) {
                root.addProperty("parent", mat.isBlock() ? "minecraft:block/" + key
                        : isHandheld(mat) ? "minecraft:item/handheld" : "minecraft:item/generated");
                if (!mat.isBlock() && !root.has("textures")) {
                    JsonObject t = new JsonObject();
                    t.addProperty("layer0", "minecraft:item/" + key);
                    root.add("textures", t);
                }
            }

            List<JsonElement> others = new ArrayList<>();
            TreeMap<Integer, JsonElement> byCmd = new TreeMap<>();
            if (root.has("overrides")) for (JsonElement el : root.getAsJsonArray("overrides")) {
                JsonObject pred = el.getAsJsonObject().has("predicate") ? el.getAsJsonObject().getAsJsonObject("predicate") : null;
                if (pred != null && pred.has("custom_model_data")) byCmd.put(pred.get("custom_model_data").getAsInt(), el);
                else others.add(el);
            }
            for (ItemDef d : e.getValue()) {
                JsonObject ov = new JsonObject();
                JsonObject pred = new JsonObject();
                pred.addProperty("custom_model_data", d.cmd);
                ov.add("predicate", pred);
                ov.addProperty("model", d.finalModel);
                byCmd.put(d.cmd, ov);
            }
            JsonArray arr = new JsonArray();
            others.forEach(arr::add);
            byCmd.values().forEach(arr::add);
            root.add("overrides", arr);
            writeJson(file, root);
        }
    }

    private void writeBlockstates(Content content) throws IOException {
        Map<Integer, String> models = new HashMap<>();
        for (ItemDef d : content.items.values()) {
            if (d.block == null) continue;
            if (d.finalModel == null) {
                plugin.getLogger().warning("Bloque '" + d.id + "' sin modelo/texturas: se verá como noteblock vanilla");
                continue;
            }
            models.put(d.block.variation + 1, d.finalModel);
        }
        if (models.isEmpty()) return;

        JsonObject variants = new JsonObject();
        for (int i = 0; i < NoteStates.INSTRUMENTS.length; i++)
            for (int n = 0; n < 25; n++)
                for (int p = 0; p < 2; p++) {
                    int idx = ((i * 25) + n) * 2 + p;
                    addVariant(variants, NoteStates.INSTRUMENTS[i][1], n, p == 1, models.getOrDefault(idx, "minecraft:block/note_block"));
                }
        for (String head : NoteStates.HEAD_INSTRUMENTS)
            for (int n = 0; n < 25; n++)
                for (int p = 0; p < 2; p++) addVariant(variants, head, n, p == 1, "minecraft:block/note_block");

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        writeJson(stage.resolve("assets/minecraft/blockstates/note_block.json"), root);
    }

    private static void addVariant(JsonObject variants, String inst, int note, boolean powered, String model) {
        JsonObject v = new JsonObject();
        v.addProperty("model", model);
        variants.add("instrument=" + inst + ",note=" + note + ",powered=" + powered, v);
    }

    // ───────────────────────── fonts ─────────────────────────

    private void writeFonts(Content content) throws IOException {
        if (content.glyphs.isEmpty()) return;
        PersistentIds ids = new PersistentIds(new java.io.File(plugin.getDataFolder(), "registry-glyphs.yml"));
        Set<Integer> taken = new HashSet<>();
        for (GlyphDef g : content.glyphs.values()) if (g.explicitChar != null) { g.codepoint = g.explicitChar; taken.add(g.codepoint); }
        for (GlyphDef g : content.glyphs.values()) if (g.codepoint == 0) { Integer v = ids.claim(g.id, taken); if (v != null) g.codepoint = v; }
        for (GlyphDef g : content.glyphs.values()) if (g.codepoint == 0) g.codepoint = ids.allocate(g.id, 0xE000, 0xF8FF, taken);
        ids.save();

        Path file = stage.resolve("assets/minecraft/font/default.json");
        JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
        JsonArray providers = root.has("providers") ? root.getAsJsonArray("providers") : new JsonArray();
        for (GlyphDef g : content.glyphs.values()) {
            Path tex = stage.resolve("assets/" + Text.ns(g.texture) + "/textures/" + Text.path(g.texture) + ".png");
            if (!Files.exists(tex)) {
                missingTextures++;
                plugin.getLogger().warning("Glyph '" + g.id + "': falta textura " + g.texture);
                continue;
            }
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", g.texture + ".png");
            p.addProperty("ascent", g.ascent);
            p.addProperty("height", g.height);
            JsonArray chars = new JsonArray();
            chars.add(g.asString());
            p.add("chars", chars);
            providers.add(p);
        }
        root.add("providers", providers);
        writeJson(file, root);
    }

    private void writeMcmeta() throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", plugin.getConfig().getInt("pack.pack-format", 34));
        pack.addProperty("description", plugin.getConfig().getString("pack.description", "Atelier"));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        writeJson(stage.resolve("pack.mcmeta"), root);
    }

    // ───────────────────────── zip / send ─────────────────────────

    private static void zip(Path dir, Path zip) throws IOException {
        Files.createDirectories(zip.getParent());
        List<Path> files;
        try (Stream<Path> w = Files.walk(dir)) { files = w.filter(Files::isRegularFile).sorted().toList(); }
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.setLevel(9);
            for (Path p : files) {
                ZipEntry e = new ZipEntry(dir.relativize(p).toString().replace('\\', '/'));
                e.setTime(1577836800000L); // fixed => reproducible sha1
                z.putNextEntry(e);
                Files.copy(p, z);
                z.closeEntry();
            }
        }
    }

    public String url() {
        var cfg = plugin.getConfig();
        String base = "external".equalsIgnoreCase(cfg.getString("pack.host.mode"))
                ? cfg.getString("pack.host.external-url", "")
                : cfg.getString("pack.host.public-url", "");
        if (base.isBlank()) return null;
        return base + (base.contains("?") ? "&" : "?") + "v=" + sha1Hex.substring(0, Math.min(8, sha1Hex.length()));
    }

    public void send(Player player) {
        var cfg = plugin.getConfig();
        String url = url();
        if (!cfg.getBoolean("pack.enabled", true) || url == null || sha1.length == 0) return;
        byte[] hash = sha1;
        String ext = cfg.getString("pack.host.external-sha1", "");
        if ("external".equalsIgnoreCase(cfg.getString("pack.host.mode")) && !ext.isBlank()) hash = HexFormat.of().parseHex(ext);
        player.setResourcePack(packId, url, hash, MiniMessage.miniMessage().deserialize(cfg.getString("pack.prompt", "")),
                cfg.getBoolean("pack.force", false));
    }

    // ───────────────────────── io helpers ─────────────────────────

    private static void writeJson(Path p, JsonElement el) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, GSON.toJson(el), StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> w = Files.walk(p)) {
            for (Path x : (Iterable<Path>) w.sorted(Comparator.reverseOrder())::iterator) Files.delete(x);
        }
    }
}
