package cz.mgholograms.manager.bridge.core;

import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface HologramContentProvider {

    String getGroupId();

    List<String> getLines(UUID playerUuid, Player player);

    List<String> getLoadingLines();

    /**
     * Returns text lines grouped per TEXT display, in the same order as the
     * group's displays in hologram-groups.yml. Default implementation wraps
     * getLines() as a single block, preserving behavior for providers with
     * only one TEXT display. Override this for providers backing multiple
     * TEXT displays (e.g. WelcomeHologramProvider).
     */
    default List<List<String>> getLinesPerDisplay(UUID playerUuid, Player player) {
        List<String> lines = getLines(playerUuid, player);
        List<List<String>> wrapped = new ArrayList<>();
        wrapped.add(lines);
        return wrapped;
    }
    default java.util.Set<Integer> getDynamicDisplayIndices() {
        return null;
    }

    /**
     * Loading-state equivalent of getLinesPerDisplay().
     */
    default List<List<String>> getLoadingLinesPerDisplay() {
        List<List<String>> wrapped = new ArrayList<>();
        wrapped.add(getLoadingLines());
        return wrapped;
    }

    default long getRefreshIntervalTicks() {
        return 40L;
    }
}