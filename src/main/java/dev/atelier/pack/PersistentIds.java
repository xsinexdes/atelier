package dev.atelier.pack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/** Stable key -> int assignments persisted on disk (custom model data, note variations, glyph chars). */
final class PersistentIds {
    private final File file;
    private final YamlConfiguration yaml;

    PersistentIds(File file) {
        this.file = file;
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Reuses the stored value if it is still free (reserving it); otherwise null. */
    Integer claim(String key, Set<Integer> taken) {
        String k = key(key);
        if (!yaml.contains(k)) return null;
        int v = yaml.getInt(k);
        return taken.add(v) ? v : null;
    }

    /** Allocates the lowest free value in [start,max] and stores it. */
    int allocate(String key, int start, int max, Set<Integer> taken) {
        for (int v = start; v <= max; v++) {
            if (taken.add(v)) { yaml.set(key(key), v); return v; }
        }
        throw new IllegalStateException("Sin valores libres para " + key + " (rango " + start + ".." + max + ")");
    }

    void save() {
        try { yaml.save(file); } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static String key(String k) { return k.replace('.', '_'); }
}
