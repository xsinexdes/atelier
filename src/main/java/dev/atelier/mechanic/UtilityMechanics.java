package dev.atelier.mechanic;

import dev.atelier.item.ItemDef;
import dev.atelier.item.ItemRegistry;
import dev.atelier.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * commands:  {permission, cooldown(ticks), one_usage, console: [..], player: [..]}
 * custom:    {name: {event: "CLICK:right:all", cooldown, one_usage, conditions, actions: ["[console] cmd", ...]}}
 * soulbound: {lose_chance}          keep the item on death
 * hat / components.equippable.slot  right click to wear any item on HEAD/CHEST/LEGS/FEET
 * backpack:  {rows, title}          right click opens a persistent inventory stored in the item
 */
public final class UtilityMechanics implements Listener {
    private static final Pattern PERM = Pattern.compile("(!?)#player\\.hasPermission\\('([^']+)'\\)");
    private static final Set<String> VANILLA_WEAR = Set.of("ELYTRA", "CARVED_PUMPKIN", "PLAYER_HEAD", "ZOMBIE_HEAD",
            "SKELETON_SKULL", "CREEPER_HEAD", "DRAGON_HEAD", "PIGLIN_HEAD", "WITHER_SKELETON_SKULL");

    private static final class BagHolder implements InventoryHolder {
        Inventory inv; int slot; String id;
        @Override public Inventory getInventory() { return inv; }
    }

    private final ItemRegistry reg;
    private final Cooldowns cd = new Cooldowns();

    public UtilityMechanics(ItemRegistry reg) { this.reg = reg; }

    // ───────────── interaction ─────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        ItemDef d = reg.fromStack(item);
        if (d == null) return;
        boolean right = e.getAction().isRightClick(), left = e.getAction().isLeftClick();
        if (!right && !left) return;
        Player p = e.getPlayer();

        ConfigurationSection cm = d.mech("commands");
        if (cm != null && right) commands(p, d, cm);

        ConfigurationSection cu = d.mech("custom");
        if (cu != null) custom(e, p, d, cu, right, left);

        if (!right) return;
        ConfigurationSection bag = d.mech("backpack");
        if (bag != null) { e.setCancelled(true); openBag(p, item, d, bag); return; }

        if (d.equipSlot != null && !vanillaWearable(item.getType())) { if (equip(p, item, d.equipSlot)) e.setCancelled(true); }
    }

    private void commands(Player p, ItemDef d, ConfigurationSection c) {
        String perm = c.getString("permission");
        if (perm != null && !perm.isEmpty() && !p.hasPermission(perm)) { p.sendMessage(Text.parse("<red>No tienes permiso.")); return; }
        if (!cd.ready(p.getUniqueId() + ":" + d.id + ":commands", c.getLong("cooldown", 0) * 50)) return;
        for (String s : list(c, "console")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fmt(p, s));
        for (String s : list(c, "player")) p.performCommand(fmt(p, s));
        if (c.getBoolean("one_usage", false)) consume(p);
    }

    private void custom(PlayerInteractEvent e, Player p, ItemDef d, ConfigurationSection cu, boolean right, boolean left) {
        for (String name : cu.getKeys(false)) {
            ConfigurationSection c = cu.getConfigurationSection(name);
            if (c == null) continue;
            String[] ev = c.getString("event", "CLICK:right:all").split(":");
            if (!ev[0].equalsIgnoreCase("CLICK")) continue;
            String side = ev.length > 1 ? ev[1].toLowerCase(Locale.ROOT) : "all";
            String where = ev.length > 2 ? ev[2].toLowerCase(Locale.ROOT) : "all";
            if ((side.equals("right") && !right) || (side.equals("left") && !left)) continue;
            boolean onBlock = e.getClickedBlock() != null;
            if ((where.equals("air") && onBlock) || (where.equals("block") && !onBlock)) continue;
            if (!conditions(p, c.getStringList("conditions"))) continue;
            if (!cd.ready(p.getUniqueId() + ":" + d.id + ":" + name, c.getLong("cooldown", 0) * 50)) continue;
            actions(p, c.getStringList("actions"));
            if (c.getBoolean("one_usage", false)) consume(p);
        }
    }

    private static boolean conditions(Player p, List<String> list) {
        for (String s : list) {
            Matcher m = PERM.matcher(s.trim());
            if (m.matches() && p.hasPermission(m.group(2)) == m.group(1).equals("!")) return false;
        }
        return true;
    }

    /** [console] cmd | [player] cmd | [message] text | [sound] name | [effect] type level seconds | [heal] n | [feed] n */
    private void actions(Player p, List<String> list) {
        for (String raw : list) {
            String a = raw.replace("<player>", p.getName()).replace("%player%", p.getName()).trim();
            String tag = "console", v = a;
            int i = a.indexOf(']');
            if (a.startsWith("[") && i > 0) { tag = a.substring(1, i).toLowerCase(Locale.ROOT); v = a.substring(i + 1).trim(); }
            switch (tag) {
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), v.startsWith("/") ? v.substring(1) : v);
                case "player" -> p.performCommand(v.startsWith("/") ? v.substring(1) : v);
                case "message" -> p.sendMessage(Text.parse(v));
                case "sound" -> p.playSound(p.getLocation(), v, 1f, 1f);
                case "effect" -> {
                    String[] t = v.split("\\s+");
                    PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(t[0].toLowerCase(Locale.ROOT)));
                    if (type != null) p.addPotionEffect(new PotionEffect(type, num(t, 2, 10) * 20, Math.max(0, num(t, 1, 1) - 1)));
                }
                case "heal" -> {
                    AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + num(v.split("\\s+"), 0, 2)));
                }
                case "feed" -> p.setFoodLevel(Math.min(20, p.getFoodLevel() + num(v.split("\\s+"), 0, 4)));
                default -> { }
            }
        }
    }

    private static int num(String[] t, int i, int def) {
        try { return i < t.length ? (int) Double.parseDouble(t[i]) : def; } catch (NumberFormatException e) { return def; }
    }

    private static List<String> list(ConfigurationSection c, String key) {
        Object o = c.get(key);
        if (o instanceof ConfigurationSection cs) return cs.getStringList("commands");
        return c.getStringList(key);
    }

    private static String fmt(Player p, String s) {
        String r = s.replace("<player>", p.getName()).replace("%player%", p.getName()).trim();
        return r.startsWith("/") ? r.substring(1) : r;
    }

    private static void consume(Player p) {
        ItemStack h = p.getInventory().getItemInMainHand();
        h.setAmount(h.getAmount() - 1);
        p.getInventory().setItemInMainHand(h.getAmount() <= 0 ? null : h);
    }

    // ───────────── soulbound ─────────────
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            ItemDef d = reg.fromStack(s);
            if (d == null) continue;
            ConfigurationSection sb = d.mech("soulbound");
            if (sb == null || ThreadLocalRandom.current().nextDouble() < sb.getDouble("lose_chance", 0)) continue;
            it.remove();
            e.getItemsToKeep().add(s);
        }
    }

    // ───────────── hats / wearable ─────────────
    private static boolean vanillaWearable(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || VANILLA_WEAR.contains(n);
    }

    private boolean equip(Player p, ItemStack item, String slot) {
        PlayerInventory inv = p.getInventory();
        ItemStack one = item.clone();
        one.setAmount(1);
        ItemStack old;
        switch (slot.toUpperCase(Locale.ROOT)) {
            case "HEAD" -> { old = inv.getHelmet(); inv.setHelmet(one); }
            case "CHEST" -> { old = inv.getChestplate(); inv.setChestplate(one); }
            case "LEGS" -> { old = inv.getLeggings(); inv.setLeggings(one); }
            case "FEET" -> { old = inv.getBoots(); inv.setBoots(one); }
            default -> { return false; }
        }
        consume(p);
        if (old != null && !old.getType().isAir()) inv.addItem(old).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        return true;
    }

    // ───────────── backpack ─────────────
    private boolean isBag(ItemStack s) {
        ItemDef d = reg.fromStack(s);
        return d != null && d.mech("backpack") != null;
    }

    private void openBag(Player p, ItemStack item, ItemDef d, ConfigurationSection b) {
        int rows = Math.max(1, Math.min(6, b.getInt("rows", 3)));
        BagHolder h = new BagHolder();
        h.slot = p.getInventory().getHeldItemSlot();
        h.id = d.id;
        h.inv = Bukkit.createInventory(h, rows * 9, Text.parse(b.getString("title", d.displayName != null ? d.displayName : "Mochila")));
        byte[] data = item.getItemMeta().getPersistentDataContainer().get(reg.keyBag, PersistentDataType.BYTE_ARRAY);
        if (data != null) {
            try {
                ItemStack[] arr = ItemStack.deserializeItemsFromBytes(data);
                for (int i = 0; i < arr.length && i < rows * 9; i++) if (arr[i] != null) h.inv.setItem(i, arr[i]);
            } catch (Exception ignored) { }
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof BagHolder h) || !(e.getPlayer() instanceof Player p)) return;
        ItemStack item = p.getInventory().getItem(h.slot);
        ItemDef d = reg.fromStack(item);
        if (d == null || !d.id.equals(h.id)) return;
        ItemStack[] c = h.inv.getContents();
        for (int i = 0; i < c.length; i++) if (c[i] == null) c[i] = new ItemStack(Material.AIR);
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(reg.keyBag, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(c));
        item.setItemMeta(m);
        p.getInventory().setItem(h.slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof BagHolder h)) return;
        boolean bagSlot = e.getClickedInventory() == e.getView().getBottomInventory() && e.getSlot() == h.slot;
        boolean hotbarBag = e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == h.slot;
        if (e.getClick() == ClickType.SWAP_OFFHAND || bagSlot || hotbarBag || isBag(e.getCursor()) || isBag(e.getCurrentItem()))
            e.setCancelled(true);                    // can't move the open backpack nor nest backpacks
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof BagHolder && isBag(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof BagHolder) e.setCancelled(true);
    }
}
