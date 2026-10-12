package dev.atelier;

import dev.atelier.block.BlockManager;
import dev.atelier.command.AtelierCommand;
import dev.atelier.content.Content;
import dev.atelier.content.ContentLoader;
import dev.atelier.furniture.FurnitureManager;
import dev.atelier.glyph.GlyphManager;
import dev.atelier.item.ItemRegistry;
import dev.atelier.pack.PackManager;
import dev.atelier.recipe.RecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import dev.atelier.hud.HudManager;
import dev.atelier.menu.ItemBrowser;
import dev.atelier.menu.MenuManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class AtelierPlugin extends JavaPlugin implements Listener {
    private static AtelierPlugin instance;
    private ItemRegistry registry;
    private PackManager pack;
    private GlyphManager glyphs;
    private RecipeManager recipes;
    private MenuManager menus;
    private ItemBrowser browser;
    private HudManager hud;
    private String lastSha = "";
    private List<String> lastWarningList = new ArrayList<>();

    public static AtelierPlugin get() { return instance; }
    public ItemRegistry registry() { return registry; }
    public PackManager pack() { return pack; }
    public GlyphManager glyphs() { return glyphs; }
    public RecipeManager recipes() { return recipes; }
    public MenuManager menus() { return menus; }
    public ItemBrowser browser() { return browser; }
    public List<String> warningList() { return lastWarningList; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        seedDefaults();

        registry = new ItemRegistry(this);
        pack = new PackManager(this);
        glyphs = new GlyphManager(this);
        recipes = new RecipeManager(this, registry);
        menus = new MenuManager(this, registry, glyphs);
        browser = new ItemBrowser(registry);
        hud = new HudManager(this, glyphs);

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(glyphs, this);
        pm.registerEvents(menus, this);
        pm.registerEvents(browser, this);
        pm.registerEvents(new BlockManager(this, registry), this);
        pm.registerEvents(new FurnitureManager(this, registry), this);
        pm.registerEvents(new dev.atelier.effects.EffectsManager(this, registry), this);
        pm.registerEvents(new dev.atelier.mechanic.ToolMechanics(registry), this);
        pm.registerEvents(new dev.atelier.mechanic.CombatMechanics(this, registry), this);
        pm.registerEvents(new dev.atelier.mechanic.UtilityMechanics(registry), this);

        var cmd = new AtelierCommand(this);
        getCommand("atelier").setExecutor(cmd);
        getCommand("atelier").setTabCompleter(cmd);

        for (String other : new String[]{"Oraxen", "ItemsAdder"}) {
            if (pm.isPluginEnabled(other))
                getLogger().warning(other + " está activo: ambos plugins usan los mismos estados de noteblock/pack. Desactiva uno de los dos.");
        }
        // Wait one tick so every world/registry is ready.
        Bukkit.getScheduler().runTask(this, this::reloadAll);
    }

    @Override
    public void onDisable() {
        if (pack != null) pack.shutdown();
    }

    /** Re-reads every config, rebuilds the pack and re-registers recipes. @return false if errors occurred. */
    public boolean reloadAll() {
        reloadConfig();
        boolean ok = true;
        try {
            Content content = ContentLoader.load(this);
            try { pack.build(content); }          // assigns custom_model_data / variations / glyph chars
            catch (Exception ex) { ok = false; getLogger().severe("Fallo al generar el resource pack: " + ex); ex.printStackTrace(); }
            registry.set(content.items.values());
            glyphs.set(content.glyphs);
            recipes.register(content.recipes);
            lastWarningList = new ArrayList<>(content.warnings);
            menus.load();
            hud.load();
            content.warnings.forEach(w -> getLogger().warning(w));
            getLogger().info("Cargados " + content.items.size() + " items, " + content.glyphs.size() + " glyphs, "
                    + recipes.count() + " recetas. Pack sha1=" + pack.sha1Hex());
            if (getConfig().getBoolean("pack.send-on-join", true) && !pack.sha1Hex().equals(lastSha))
                for (Player p : Bukkit.getOnlinePlayers()) pack.send(p);
            lastSha = pack.sha1Hex();
        } catch (Exception e) {
            ok = false;
            getLogger().severe("Fallo al recargar: " + e);
            e.printStackTrace();
        }
        return ok;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("pack.send-on-join", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> { if (e.getPlayer().isOnline()) pack.send(e.getPlayer()); }, 20L);
    }

    private void seedDefaults() {
        if (!new File(getDataFolder(), "items").exists()) {
            for (String r : new String[]{"items/example.yml", "glyphs/example.yml", "recipes/example.yml", "menus/example.yml",
                    "pack/textures/default/ruby.png", "pack/textures/default/ruby_block.png", "pack/textures/default/glyph_star.png"}) {
                saveResource(r, false);
            }
        }
        for (String r : new String[]{"menus/example.yml", "huds/example.yml"})
            if (!new File(getDataFolder(), r).exists() && !new File(getDataFolder(), r.split("/")[0]).exists()) saveResource(r, false);
        File drop = new File(getDataFolder(), "drop");
        if (!drop.exists()) {
            drop.mkdirs();
            try {
                java.nio.file.Files.writeString(new File(drop, "LEEME.txt").toPath(),
                        "SUELTA AQUI TUS PNG y ejecuta /atelier reload. Cada PNG es un item nuevo (el nombre del archivo es el id).\n"
                        + "El tipo se decide por el final del nombre:\n"
                        + "  ruby_sword.png / _pickaxe / _axe / _shovel / _hoe  -> herramientas\n"
                        + "  ruby_helmet.png / _chestplate / _leggings / _boots -> armadura (set 'ruby', bonus en items/*.yml si quieres)\n"
                        + "  ruby_block.png  -> bloque colocable\n"
                        + "  ruby_furniture.png -> mueble\n"
                        + "  cualquier otro nombre -> item normal (papel)\n"
                        + "Para dar el item: /atelier give TuNombre ruby_sword  (o /atelier items)\n");
            } catch (java.io.IOException ignored) {}
        }
        new File(getDataFolder(), "import/oraxen").mkdirs();
        new File(getDataFolder(), "import/itemsadder").mkdirs();
    }
}
