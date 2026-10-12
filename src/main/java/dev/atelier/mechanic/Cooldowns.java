package dev.atelier.mechanic;

import java.util.HashMap;
import java.util.Map;

final class Cooldowns {
    private final Map<String, Long> until = new HashMap<>();

    /** @return true (and starts the cooldown) when the key is not cooling down. */
    boolean ready(String key, long millis) {
        long now = System.currentTimeMillis();
        Long u = until.get(key);
        if (u != null && u > now) return false;
        if (millis > 0) until.put(key, now + millis); else until.remove(key);
        return true;
    }
}
