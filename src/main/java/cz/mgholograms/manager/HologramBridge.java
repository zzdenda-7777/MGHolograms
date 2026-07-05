package cz.mgholograms.manager;


import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.content.ProductionHologramProvider;
import cz.mgholograms.manager.bridge.content.TierHologramProvider;
import cz.mgholograms.manager.bridge.content.IncomeHologramProvider;
import cz.mgholograms.manager.bridge.content.RebirthHologramProvider;
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

    // Known ahead of time, independent of init() ordering - fixes a race condition where
    // HologramManager.init() (and its reload()) could run BEFORE HologramBridge.init()
    // populates the `engines` map below. If isManagedGroup() relied on `engines.containsKey()`,
    // it would incorrectly report false during that window and HologramManager would create
    // a StaticGroupEngine for Production/Tier/Income too - which then never gets cleaned up
    // because it uses different (per-player) hologram names than the legacy cleanup logic expects.
    private static final java.util.Set<String> MANAGED_GROUP_IDS =
            java.util.Set.of("Production", "Tier", "Income", "Rebirth");

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
        IncomeHologramProvider incomeProvider = new IncomeHologramProvider(plugin);
        RebirthHologramProvider rebirthProvider = new RebirthHologramProvider(plugin);

        // Ensure config entries exist for all groups
        ensureConfigEntry("Production", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Tier", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Income", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Rebirth", "voidworld", 0.0, 0.0, 0.0);

        // Check if multigainer is available
        if (productionProvider.getMultigainer() == null && tierProvider.getMultigainer() == null
                && incomeProvider.getMultigainer() == null && rebirthProvider.getMultigainer() == null) {
            plugin.getLogger().warning("Multigainer plugin not found - holograms disabled. "
                    + "Make sure 'multigainer' is installed and loaded before MGHolograms.");
            return;
        }

        // Create engines for each provider
        PlayerHologramEngine productionEngine = new PlayerHologramEngine(plugin, hologramManager, productionProvider);
        PlayerHologramEngine tierEngine = new PlayerHologramEngine(plugin, hologramManager, tierProvider);
        PlayerHologramEngine incomeEngine = new PlayerHologramEngine(plugin, hologramManager, incomeProvider);
        PlayerHologramEngine rebirthEngine = new PlayerHologramEngine(plugin, hologramManager, rebirthProvider);

        engines.put("Production", productionEngine);
        engines.put("Tier", tierEngine);
        engines.put("Income", incomeEngine);
        engines.put("Rebirth", rebirthEngine);

        // Initialize engines
        productionEngine.init();
        tierEngine.init();
        incomeEngine.init();
        rebirthEngine.init();

        plugin.getLogger().info("HologramBridge initialized with Production, Tier, Income and Rebirth engines");
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
        } else if (groupId.equals("Income")) {
            displayData.put("lines", List.of(
                    "&#9DFD3A&l&#84FC33&li&#6BFB2D&ln&#52FA26&lc&#3AF920&lo&#52FA26&lm&#6BFB2D&le",
                    "§7Income System"
            ));
        } else if (groupId.equals("Rebirth")) {
            displayData.put("lines", List.of(
                    "§5§lRebirth",
                    "§7Loading..."
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
     * Uses a fixed, always-known set rather than the `engines` map, since that map is
     * only populated inside init() - checking it here would be wrong (always false)
     * for any call that happens before init() runs, e.g. from HologramManager's first
     * reload() during its own init().
     */
    public boolean isManagedGroup(String groupId) {
        return MANAGED_GROUP_IDS.contains(groupId);
    }
}