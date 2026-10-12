package dev.atelier.hud;

import dev.atelier.AtelierPlugin;
import dev.atelier.content.GlyphDef;
import dev.atelier.glyph.GlyphManager;
import dev.atelier.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/** HUD en actionbar definido en huds/*.yml. Placeholders: %health% %max_health% %food% %armor% %level% %player% %bar_health% %bar_food% y :glyphs: */
public final class HudManager {
    private record Hud(String id, String text, int update, String permission) {}

    private final AtelierPlugin plugin;
    private final GlyphManager glyphs;
    private final List<Hud> huds = new ArrayList<>();
    private long tick;

    public HudManager(AtelierPlugin plugin, GlyphManager glyphs) {
        this.plugin = plugin;
        this.glyphs = glyphs;
        Bukkit.getScheduler().runTaskTimer(plugin, this::run, 5L, 5L);
    }

    public int count() { return huds.size(); }

    public void load() {
        huds.clear();
        File[] files = new File(plugin.getDataFolder(), "huds").listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String id : y.getKeys(false)) {
                ConfigurationSection s = y.getConfigurationSection(id);
                if (s == null || s.getString("text") == null) continue;
                huds.add(new Hud(id, s.getString("text"), Math.max(5, s.getInt("update", 10)), s.getString("permission", "")));
            }
        }
    }

    private void run() {
        tick += 5;
        if (huds.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            StringBuilder sb = new StringBuilder();
            for (Hud h : huds) {
                if (tick % h.update() >= 5) continue;
                if (!h.permission().isEmpty() && !p.hasPermission(h.permission())) continue;
                sb.append(render(h.text(), p)).append("   ");
            }
            if (sb.length() > 0) p.sendActionBar(Text.parse(sb.toString().trim()));
        }
    }

    private String render(String t, Player p) {
        double max = value(p, Attribute.GENERIC_MAX_HEALTH, 20);
        t = t.replace("%health%", String.valueOf((int) Math.ceil(p.getHealth())))
                .replace("%max_health%", String.valueOf((int) max))
                .replace("%food%", String.valueOf(p.getFoodLevel()))
                .replace("%armor%", String.valueOf((int) value(p, Attribute.GENERIC_ARMOR, 0)))
                .replace("%level%", String.valueOf(p.getLevel()))
                .replace("%player%", p.getName())
                .replace("%bar_health%", bar(p.getHealth(), max, "red"))
                .replace("%bar_food%", bar(p.getFoodLevel(), 20, "gold"));
        for (GlyphDef g : glyphs.all()) if (g.codepoint != 0) for (String ph : g.placeholders) t = t.replace(ph, g.asString());
        return t;
    }

    private static double value(Player p, Attribute a, double def) {
        AttributeInstance i = p.getAttribute(a);
        return i == null ? def : i.getValue();
    }

    private static String bar(double cur, double max, String color) {
        int f = (int) Math.round(Math.max(0, Math.min(1, cur / Math.max(1, max))) * 10);
        return "<" + color + ">" + "█".repeat(f) + "<dark_gray>" + "█".repeat(10 - f) + "<reset>";
    }
}
