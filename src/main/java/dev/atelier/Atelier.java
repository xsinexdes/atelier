package dev.atelier;

import dev.atelier.item.ItemDef;
import org.bukkit.inventory.ItemStack;

/** Public API for other plugins. */
public final class Atelier {
    private Atelier() {}

    /** @return a new stack for the custom item id ("id", "ns:id", "oraxen:id"...), or null. */
    public static ItemStack item(String id, int amount) {
        var reg = AtelierPlugin.get().registry();
        ItemDef d = reg.get(id);
        return d == null ? null : reg.create(d, amount);
    }

    public static boolean isCustom(ItemStack stack) { return idOf(stack) != null; }

    /** @return the custom item id of this stack, or null. Also recognises items made by Oraxen. */
    public static String idOf(ItemStack stack) {
        ItemDef d = AtelierPlugin.get().registry().fromStack(stack);
        return d == null ? null : d.id;
    }
}
