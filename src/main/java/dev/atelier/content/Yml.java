package dev.atelier.content;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers for tolerant YAML reading (Oraxen/ItemsAdder use inconsistent casing). */
final class Yml {
    private Yml() {}

    static ConfigurationSection sec(ConfigurationSection s, String... names) {
        if (s == null) return null;
        for (String n : names) {
            ConfigurationSection c = s.getConfigurationSection(n);
            if (c != null) return c;
        }
        return null;
    }

    static Object get(ConfigurationSection s, String... names) {
        if (s == null) return null;
        for (String n : names) if (s.contains(n)) return s.get(n);
        return null;
    }

    static String str(ConfigurationSection s, String def, String... names) {
        Object o = get(s, names);
        return o == null ? def : String.valueOf(o);
    }

    static double dbl(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? def : Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    static int integer(Object o, int def) { return (int) Math.round(dbl(o, def)); }

    static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        else if (o != null) out.add(String.valueOf(o));
        return out;
    }

    /** Parse "drops"/"loots" style lists: [{item|oraxen_item|...: x, probability: 1.0, amount: 2}] */
    static List<dev.atelier.item.BlockDef.Drop> drops(Object o) {
        List<dev.atelier.item.BlockDef.Drop> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) return out;
        for (Object e : list) {
            if (!(e instanceof Map<?, ?> m)) continue;
            Object item = first(m, "item", "oraxen_item", "atelier_item", "itemsadder_item", "minecraft_type");
            if (item == null) continue;
            double p = dbl(m.get("probability"), 1.0);
            int amount = integer(m.get("amount"), 1);
            int min = integer(first(m, "minimum_amount", "min"), amount);
            int max = integer(first(m, "maximum_amount", "max"), Math.max(min, amount));
            out.add(new dev.atelier.item.BlockDef.Drop(String.valueOf(item), p, min, Math.max(min, max)));
        }
        return out;
    }

    private static Object first(Map<?, ?> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }
}
