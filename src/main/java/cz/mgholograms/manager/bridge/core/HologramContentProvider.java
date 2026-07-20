package cz.mgholograms.manager.bridge.core;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

/**
 * Interface for providing hologram content on a per-player basis.
 * 
 * HOW TO ADD A NEW HOLOGRAM:
 * 1. Create a new class implementing this interface in the content/ package
 * 2. Register it in HologramBridge.init() by creating a PlayerHologramEngine instance
 * 3. Add a config entry block in HologramBridge.ensureConfigEntry() for the new group
 * 
 * This separation allows adding new holograms without modifying the core engine logic.
 */
public interface HologramContentProvider {

    /**
     * Unique ID of the group in hologram-groups.yml (e.g., "Production" or "Tier").
     */
    String getGroupId();

    /**
     * Returns the current text lines for the given player, or null if data
     * is not yet available (e.g., profile is still loading). In that case,
     * the engine will display the fallback "Loading..." text.
     */
    List<String> getLines(UUID playerUuid, Player player);

    /**
     * Fallback text displayed while getLines() returns null (typically during
     * initial hologram creation before data is loaded).
     */
    List<String> getLoadingLines();

    /**
     * How often (in ticks) {@link PlayerHologramEngine} recalculates and pushes
     * this provider's text to active viewers. Defaults to 40 ticks (2s).
     * Override for content that animates and needs a faster, smoother cadence
     * (e.g. a flowing gradient title) - 10 ticks (0.5s) matches Multigainer's
     * TabListManager.
     */
    default long getRefreshIntervalTicks() {
        return 40L;
    }
}
