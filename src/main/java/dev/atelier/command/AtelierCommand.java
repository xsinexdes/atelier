package dev.atelier.command;

import dev.atelier.AtelierPlugin;
import dev.atelier.content.GlyphDef;
import dev.atelier.item.ItemDef;
import dev.atelier.util.Text;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class AtelierCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBS = List.of("give", "items", "menu", "create", "delete", "reload", "list", "check", "pack", "glyphs", "info", "help");
    private final AtelierPlugin plugin;

    public AtelierCommand(AtelierPlugin plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String mm) {
        s.sendMessage(MM.deserialize("<gradient:#f9a8d4:#a78bfa>Atelier</gradient> <gray>»</gray> " + mm));
    }

    private List<Player> targets(CommandSender s, String sel) {
        if (sel.startsWith("@")) {
            try { return Bukkit.selectEntities(s, sel).stream().filter(e -> e instanceof Player).map(e -> (Player) e).toList(); }
            catch (IllegalArgumentException ex) { return List.of(); }
        }
        Player p = Bukkit.getPlayerExact(sel);
        return p == null ? List.of() : List.of(p);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("atelier.admin")) { msg(s, "<red>Sin permiso."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            msg(s, "<white>Comandos:");
            for (String l : new String[]{
                    "/atelier give <jugador|@p|@a> <id> [cantidad]  <dark_gray>- dar un item",
                    "/atelier items [filtro]  <dark_gray>- menú con todos los items (clic = tomar)",
                    "/atelier menu <id> [jugador]  <dark_gray>- abrir un menú de menus/*.yml",
                    "/atelier create <id>  <dark_gray>- convierte el item de tu mano en item Atelier",
                    "/atelier delete <id>  <dark_gray>- borra un item creado con /atelier create",
                    "/atelier reload  <dark_gray>- recarga todo y regenera el pack",
                    "/atelier check  <dark_gray>- muestra errores/advertencias de tus configs",
                    "/atelier pack [send [jugador]]  <dark_gray>- info / reenviar el pack",
                    "/atelier list [filtro] | glyphs | info"}) s.sendMessage(MM.deserialize("<gray> " + l));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(s, a);
            case "items" -> {
                if (!(s instanceof Player p)) { msg(s, "<red>Solo jugadores."); return true; }
                plugin.browser().open(p, a.length > 1 ? a[1] : "", 0);
            }
            case "menu" -> {
                if (a.length < 2) { msg(s, "<gray>/atelier menu <id> [jugador]  <dark_gray>(" + String.join(", ", plugin.menus().ids()) + ")"); return true; }
                List<Player> ts = a.length > 2 ? targets(s, a[2]) : (s instanceof Player p ? List.of(p) : List.<Player>of());
                if (ts.isEmpty()) { msg(s, "<red>Indica un jugador."); return true; }
                if (!plugin.menus().open(ts.get(0), a[1])) msg(s, "<red>Menú no existe: <white>" + a[1]);
            }
            case "create" -> create(s, a);
            case "delete" -> delete(s, a);
            case "reload" -> {
                long t0 = System.currentTimeMillis();
                boolean ok = plugin.reloadAll();
                msg(s, (ok ? "<green>Recargado" : "<red>Recargado con errores (ver consola)") + " <gray>(" + (System.currentTimeMillis() - t0) + " ms)");
            }
            case "check" -> {
                List<String> w = plugin.warningList();
                if (w.isEmpty()) msg(s, "<green>Todo correcto, sin advertencias.");
                else { msg(s, "<yellow>" + w.size() + " advertencias:"); w.stream().limit(25).forEach(x -> s.sendMessage(Text.parse("<gray>• " + x.replace("<", "\\<")))); }
            }
            case "list" -> {
                String f = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                List<String> ids = plugin.registry().ids().stream().filter(i -> i.toLowerCase(Locale.ROOT).contains(f)).sorted().toList();
                msg(s, "<gray>" + ids.size() + " items" + (ids.size() > 60 ? " (mostrando 60)" : "") + ":");
                s.sendMessage(Text.parse("<white>" + String.join("<gray>, <white>", ids.stream().limit(60).toList())));
            }
            case "pack" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("send")) {
                    List<Player> ts = a.length > 2 ? targets(s, a[2]) : (s instanceof Player p ? List.of(p) : List.<Player>of());
                    if (ts.isEmpty()) { msg(s, "<red>Indica un jugador."); return true; }
                    ts.forEach(plugin.pack()::send);
                    msg(s, "<green>Pack enviado a " + ts.size() + " jugador(es)");
                } else {
                    msg(s, "<gray>sha1: <white>" + plugin.pack().sha1Hex());
                    msg(s, "<gray>tamaño: <white>" + plugin.pack().size() / 1024 + " KB <gray>| url: <white>" + plugin.pack().url());
                }
            }
            case "glyphs" -> {
                msg(s, "<gray>" + plugin.glyphs().all().size() + " glyphs");
                for (GlyphDef g : plugin.glyphs().all().stream().limit(40).toList())
                    s.sendMessage(Text.parse("<gray>" + g.id + " <white>" + g.asString() + " <dark_gray>" + g.placeholders));
            }
            case "info" -> {
                long blocks = plugin.registry().all().stream().filter(d -> d.block != null).count();
                long furn = plugin.registry().all().stream().filter(d -> d.furniture != null).count();
                msg(s, "<gray>items: <white>" + plugin.registry().all().size() + " <gray>bloques: <white>" + blocks + " <gray>muebles: <white>" + furn
                        + " <gray>glyphs: <white>" + plugin.glyphs().all().size() + " <gray>recetas: <white>" + plugin.recipes().count()
                        + " <gray>menús: <white>" + plugin.menus().ids().size());
                msg(s, "<gray>texturas faltantes: <white>" + plugin.pack().missingTextures + " <gray>advertencias: <white>" + plugin.warningList().size() + " <dark_gray>(/atelier check)");
            }
            default -> msg(s, "<red>Subcomando desconocido. <gray>/atelier help");
        }
        return true;
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<gray>/atelier give <jugador|@p|@a> <id> [cantidad]"); return; }
        List<Player> ts = targets(s, a[1]);
        if (ts.isEmpty()) { msg(s, "<red>Jugador no encontrado."); return; }
        ItemDef d = plugin.registry().get(a[2]);
        if (d == null) { msg(s, "<red>Item no existe: <white>" + a[2] + " <gray>(usa /atelier list)"); return; }
        int n = 1;
        if (a.length > 3) try { n = Math.max(1, Math.min(2304, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) {}
        for (Player t : ts) {
            t.getInventory().addItem(plugin.registry().create(d, n)).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
        }
        msg(s, "<green>Dado <white>" + n + "x " + d.id + " <green>a " + ts.size() + " jugador(es)");
    }

    private File ingameFile() { return new File(plugin.getDataFolder(), "items/ingame.yml"); }

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { msg(s, "<red>Solo jugadores."); return; }
        if (a.length < 2) { msg(s, "<gray>/atelier create <id>  <dark_gray>(con el item en la mano)"); return; }
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getType().isAir()) { msg(s, "<red>Sostén un item en la mano."); return; }
        String id = a[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(ingameFile());
        y.set(id, null);
        y.set(id + ".material", h.getType().name());
        ItemMeta m = h.getItemMeta();
        if (m != null) {
            if (m.displayName() != null) y.set(id + ".displayname", MM.serialize(m.displayName()));
            if (m.lore() != null) y.set(id + ".lore", m.lore().stream().map(MM::serialize).toList());
            for (Map.Entry<Enchantment, Integer> e : m.getEnchants().entrySet())
                y.set(id + ".Enchantments." + e.getKey().getKey().getKey(), e.getValue());
            if (m.isUnbreakable()) y.set(id + ".Unbreakable", true);
            if (m.hasCustomModelData()) y.set(id + ".Pack.custom_model_data", m.getCustomModelData());
        }
        try { y.save(ingameFile()); } catch (IOException e) { msg(s, "<red>No se pudo guardar: " + e.getMessage()); return; }
        plugin.reloadAll();
        msg(s, "<green>Item <white>" + id + "<green> creado en items/ingame.yml. Edítalo ahí para añadir textura, efectos, etc.");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<gray>/atelier delete <id>"); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(ingameFile());
        if (!y.contains(a[1])) { msg(s, "<red>No está en items/ingame.yml (solo se borran items creados con /atelier create)."); return; }
        y.set(a[1], null);
        try { y.save(ingameFile()); } catch (IOException e) { msg(s, "<red>" + e.getMessage()); return; }
        plugin.reloadAll();
        msg(s, "<green>Item <white>" + a[1] + "<green> eliminado.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("atelier.admin")) return List.of();
        List<String> out = new ArrayList<>();
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (a.length == 1) out.addAll(SUBS);
        else if (a.length == 2 && (sub.equals("give") || sub.equals("pack"))) { out.addAll(List.of("@p", "@a", "@s")); Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); if (sub.equals("pack")) out.add("send"); }
        else if (a.length == 3 && sub.equals("give")) out.addAll(plugin.registry().ids());
        else if (a.length == 2 && sub.equals("menu")) out.addAll(plugin.menus().ids());
        else if (a.length == 2 && (sub.equals("delete") || sub.equals("items") || sub.equals("list"))) out.addAll(plugin.registry().ids());
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).limit(100).toList();
    }
}
