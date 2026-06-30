package cz.mgholograms.manager;


import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.content.ProductionHologramProvider;
import cz.mgholograms.manager.bridge.content.TierHologramProvider;
import cz.mgholograms.manager.bridge.core.PlayerHologramEngine;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HologramBridge
 * ----------------
 * Wiring class that initializes and manages per-player hologram engines.
 * Delegates to PlayerHologramEngine instances for each content provider.
 */
public class HologramBridge {

    // Keep for backward compatibility with HologramManager
    public static final String GROUP_ID = "Production";

    private final MGHolograms plugin;
    private final cz.mgholograms.manager.HologramManager hologramManager;

    private final Map<String, PlayerHologramEngine> engines = new HashMap<>();

    public HologramBridge(MGHolograms plugin, cz.mgholograms.manager.HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
    }

    /**
     * Zavolat z MGHolograms#onEnable po hologramManager.init().
     */
    public void init() {
        // Create and register content providers
        ProductionHologramProvider productionProvider = new ProductionHologramProvider(plugin);
        TierHologramProvider tierProvider = new TierHologramProvider(plugin);

        // Ensure config entries exist for both groups
        ensureConfigEntry("Production", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Tier", "voidworld", 0.0, 0.0, 0.0);

        // Check if multigainer is available
        if (productionProvider.getMultigainer() == null && tierProvider.getMultigainer() == null) {
            plugin.getLogger().warning("Multigainer plugin not found - holograms disabled. "
                    + "Make sure 'multigainer' is installed and loaded before MGHolograms.");
            return;
        }

        // Create engines for each provider
        PlayerHologramEngine productionEngine = new PlayerHologramEngine(plugin, hologramManager, productionProvider);
        PlayerHologramEngine tierEngine = new PlayerHologramEngine(plugin, hologramManager, tierProvider);

        engines.put("Production", productionEngine);
        engines.put("Tier", tierEngine);

        // Initialize engines
        productionEngine.init();
        tierEngine.init();

        plugin.getLogger().info("HologramBridge initialized with Production and Tier engines");
    }

    public void shutdown() {
        for (PlayerHologramEngine engine : engines.values()) {
            engine.shutdown();
        }
        engines.clear();
    }

    /**
     * Zapíše výchozí šablonu skupiny do hologram-groups.yml, pokud tam ještě není.
     */
    private void ensureConfigEntry(String groupId, String world, double x, double y, double z) {
        Map<String, Object> displayData = new HashMap<>();
        displayData.put("type", "TEXT");
        displayData.put("x_offset", 0.0);
        displayData.put("y_offset", 0.0);
        displayData.put("z_offset", 0.0);
        displayData.put("scale", 1.0f);
        displayData.put("background", "transparent");
        
        // Different fallback text for different groups
        if (groupId.equals("Production")) {
            displayData.put("lines", List.of(
                    "&#E9C463&l&#CFAE58&lr&#B5984D&lo&#9B8241&ld&#816C36&lu&#967E3F&lc&#AB8F48&lt&#BFA151&li&#D4B25A&lo&#E9C463&ln",
                    "§7Worker System"
            ));
        } else if (groupId.equals("Tier")) {
            displayData.put("lines", List.of(
                    "&#E9C463&l&#CFAE58&lt&#B5984D&li&#9B8241&le&#816C36&lr&#967E3F&#AB8F48&#BFA151&#D4B25A&#E9C463",
                    "§7Tier System"
            ));
        }

        hologramManager.getConfigLoader().createGroupIfMissing(
                groupId,
                world,
                x, y, z,
                0.0f, 0.0f,
                List.of(displayData)
        );
    }

    /**
     * Returns the template location for the Production group (for backward compatibility).
     */
    public Location getTemplateLocation() {
        PlayerHologramEngine engine = engines.get(GROUP_ID);
        return engine != null ? engine.getTemplateLocation() : null;
    }

    /**
     * Called from HologramManager#teleportGroup() / reload() for a specific groupId.
     * Delegates to the appropriate engine.
     */
    public void createOrUpdateHologram(String groupId) {
        PlayerHologramEngine engine = engines.get(groupId);
        if (engine != null) {
            engine.createOrUpdateHologram();
        }
    }

    /**
     * Legacy method for backward compatibility - calls createOrUpdateHologram for Production.
     */
    public void createOrUpdateHologram() {
        createOrUpdateHologram(GROUP_ID);
    }

    /**
     * Checks if a groupId is managed by this bridge (i.e., it's a per-player hologram).
     */
    public boolean isManagedGroup(String groupId) {
        return engines.containsKey(groupId);
    }
}
