package dev.atelier.menu;

import dev.atelier.AtelierPlugin;
import dev.atelier.content.GlyphDef;
import dev.atelier.glyph.GlyphManager;
import dev.atelier.item.ItemRegistry;
import dev.atelier.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/** Menus defined in plugins/Atelier/menus/*.yml. */
public final class MenuManager implements Listener {
    record MenuItem(ItemStack stack, List<String> actions) {}
    record MenuDef(String id, String title, int rows, Map<Integer, MenuItem> items) {}

    static final class Holder implements InventoryHolder {
        final MenuDef def;
        Inventory inv;
        Holder(MenuDef def) { this.def = def; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final AtelierPlugin plugin;
    private final ItemRegistry reg;
    private final GlyphManager glyphs;
    private final Map<String, MenuDef> menus = new LinkedHashMap<>();

    public MenuManager(AtelierPlugin plugin, ItemRegistry reg, GlyphManager glyphs) {
        this.plugin = plugin;
        this.reg = reg;
        this.glyphs = glyphs;
    }

    public Set<String> ids() { return menus.keySet(); }

    public void load() {
        menus.clear();
        File[] files = new File(plugin.getDataFolder(), "menus").listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String id : y.getKeys(false)) {
                ConfigurationSection s = y.getConfigurationSection(id);
                if (s == null) continue;
                try { menus.put(id, parse(id, s)); }
                catch (Exception e) { plugin.getLogger().warning("Menú '" + id + "' inválido (" + f.getName() + "): " + e.getMessage()); }
            }
        }
    }

    private MenuDef parse(String id, ConfigurationSection s) {
        int rows = Math.max(1, Math.min(6, s.getInt("rows", 3)));
        Map<Integer, MenuItem> items = new HashMap<>();
        List<MenuItem> fills = new ArrayList<>();
        ConfigurationSection is = s.getConfigurationSection("items");
        if (is != null) for (String k : is.getKeys(false)) {
            ConfigurationSection c = is.getConfigurationSection(k);
            if (c == null) continue;
            MenuItem mi = new MenuItem(build(c), c.getStringList("actions"));
            if (c.getBoolean("fill")) fills.add(mi);
            List<Integer> slots = new ArrayList<>(c.getIntegerList("slots"));
            if (c.contains("slot")) slots.add(c.getInt("slot"));
            for (int slot : slots) if (slot >= 0 && slot < rows * 9) items.put(slot, mi);
        }
        for (MenuItem f : fills) for (int i = 0; i < rows * 9; i++) items.putIfAbsent(i, f);
        return new MenuDef(id, s.getString("title", id), rows, items);
    }

    private ItemStack build(ConfigurationSection c) {
        ItemStack st = reg.stackFromString(c.getString("item", "STONE"), Math.max(1, c.getInt("amount", 1)));
        if (st == null) st = new ItemStack(Material.BARRIER);
        ItemMeta m = st.getItemMeta();
        if (m != null) {
            if (c.contains("name")) m.displayName(Text.parse(c.getString("name")));
            if (c.contains("lore")) m.lore(c.getStringList("lore").stream().map(Text::parse).toList());
            if (c.contains("custom_model_data")) m.setCustomModelData(c.getInt("custom_model_data"));
            st.setItemMeta(m);
        }
        return st;
    }

    public boolean open(Player p, String id) {
        MenuDef d = menus.get(id);
        if (d == null) return false;
        String title = d.title();
        for (GlyphDef g : glyphs.all()) for (String ph : g.placeholders) if (g.codepoint != 0) title = title.replace(ph, g.asString());
        Holder h = new Holder(d);
        h.inv = Bukkit.createInventory(h, d.rows() * 9, Text.parse(title));
        d.items().forEach((slot, mi) -> h.inv.setItem(slot, mi.stack().clone()));
        p.openInventory(h.inv);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        MenuItem mi = h.def.items().get(e.getSlot());
        if (mi != null && e.getWhoClicked() instanceof Player p) run(p, mi.actions());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }

    /** Actions: command: x | console: x | message: x | open: menu | close | sound: x | give: item [n] */
    private void run(Player p, List<String> actions) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String raw : actions) {
                String a = raw.replace("%player%", p.getName());
                int i = a.indexOf(':');
                String type = (i < 0 ? a : a.substring(0, i)).trim().toLowerCase(Locale.ROOT);
                String v = i < 0 ? "" : a.substring(i + 1).trim();
                switch (type) {
                    case "command", "player" -> p.performCommand(v.startsWith("/") ? v.substring(1) : v);
                    case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), v.startsWith("/") ? v.substring(1) : v);
                    case "message" -> p.sendMessage(Text.parse(v));
                    case "open" -> open(p, v);
                    case "close" -> p.closeInventory();
                    case "sound" -> { try { p.playSound(p.getLocation(), v, 1f, 1f); } catch (Exception ignored) {} }
                    case "give" -> {
                        String[] t = v.split("\\s+");
                        int n = 1;
                        if (t.length > 1) try { n = Math.max(1, Integer.parseInt(t[1])); } catch (NumberFormatException ignored) {}
                        ItemStack s = reg.stackFromString(t[0], n);
                        if (s != null) p.getInventory().addItem(s).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                    }
                    default -> { }
                }
            }
        });
    }
}
