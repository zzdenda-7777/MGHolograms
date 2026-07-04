package cz.mgholograms.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the join time of each online player so hologram engines can delay
 * showing holograms for a couple of seconds after join (gives the client's
 * chunk/entity loading time to settle and avoids the "two holograms flicker
 * on top of each other" glitch some players see right at spawn).
 */
public final class PlayerJoinTracker {

    private static final long DELAY_MILLIS = 2000L; // 2s

    private static final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    private PlayerJoinTracker() {
    }

    public static void markJoin(UUID uuid) {
        joinTimestamps.put(uuid, System.currentTimeMillis());
    }

    public static void clear(UUID uuid) {
        joinTimestamps.remove(uuid);
    }

    /**
     * @return true if the player either has no recorded join time (e.g. was
     * already online when the plugin was reloaded - treat as ready), or has
     * been online for at least DELAY_MILLIS.
     */
    public static boolean isReady(UUID uuid) {
        Long joinedAt = joinTimestamps.get(uuid);
        if (joinedAt == null) {
            return true;
        }
        return System.currentTimeMillis() - joinedAt >= DELAY_MILLIS;
    }
}
