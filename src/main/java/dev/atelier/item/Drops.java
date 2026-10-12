package dev.atelier.item;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Drops {
    private Drops() {}

    public static void drop(ItemRegistry reg, List<BlockDef.Drop> drops, Location at) {
        for (BlockDef.Drop d : drops) {
            if (ThreadLocalRandom.current().nextDouble() > d.probability()) continue;
            int amount = d.min() >= d.max() ? d.min() : ThreadLocalRandom.current().nextInt(d.min(), d.max() + 1);
            ItemStack s = reg.stackFromString(d.item(), amount);
            if (s != null && amount > 0) at.getWorld().dropItemNaturally(at, s);
        }
    }
}
