package dev.atelier.menu;

import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import dev.atelier.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /atelier items [filtro] : paged GUI with every custom item. Click = take 1, shift+click = take 64. */
public final class ItemBrowser implements Listener {
    private static final class Holder implements InventoryHolder {
        int page; String filter; Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final ItemRegistry reg;

    public ItemBrowser(ItemRegistry reg) { this.reg = reg; }

    public void open(Player p, String filter, int page) {
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<ItemDef> list = reg.all().stream().filter(d -> f.isEmpty() || d.id.toLowerCase(Locale.ROOT).contains(f))
                .sorted(Comparator.comparing(d -> d.id)).toList();
        int pages = Math.max(1, (int) Math.ceil(list.size() / 45.0));
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        h.page = pg; h.filter = f;
        h.inv = Bukkit.createInventory(h, 54, Text.parse("<dark_gray>Atelier <gray>" + (pg + 1) + "/" + pages + " <dark_gray>(" + list.size() + ")"));
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx < list.size()) h.inv.setItem(i, reg.create(list.get(idx), 1));
        }
        h.inv.setItem(45, button(Material.ARROW, "<yellow>« Anterior"));
        h.inv.setItem(49, button(Material.BARRIER, "<red>Cerrar"));
        h.inv.setItem(53, button(Material.ARROW, "<yellow>Siguiente »"));
        p.openInventory(h.inv);
    }

    private static ItemStack button(Material m, String name) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Text.parse(name));
        s.setItemMeta(meta);
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || !(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        if (slot == 45) { open(p, h.filter, h.page - 1); return; }
        if (slot == 53) { open(p, h.filter, h.page + 1); return; }
        if (slot == 49) { p.closeInventory(); return; }
        if (slot < 45) {
            ItemDef d = reg.fromStack(e.getCurrentItem());
            if (d != null) p.getInventory().addItem(reg.create(d, e.isShiftClick() ? 64 : 1))
                    .values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }
}
