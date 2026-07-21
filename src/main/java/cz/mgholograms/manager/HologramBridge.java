package cz.mgholograms.manager;


import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.content.ProductionHologramProvider;
import cz.mgholograms.manager.bridge.content.TierHologramProvider;
import cz.mgholograms.manager.bridge.content.IncomeHologramProvider;
import cz.mgholograms.manager.bridge.content.RebirthHologramProvider;
import cz.mgholograms.manager.bridge.content.AfkHologramProvider;
import cz.mgholograms.manager.bridge.content.WelcomeHologramProvider;
import cz.mgholograms.manager.bridge.content.GiveawayHologramProvider;
import cz.mgholograms.manager.bridge.content.EventHologramProvider;
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
            java.util.Set.of("Production", "Tier", "Income", "Rebirth", "AFK", "Welcome", "GIVEAWAY", "Event");

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
        shutdown();
        ProductionHologramProvider productionProvider = new ProductionHologramProvider(plugin, hologramManager);
        TierHologramProvider tierProvider = new TierHologramProvider(plugin, hologramManager);
        IncomeHologramProvider incomeProvider = new IncomeHologramProvider(plugin, hologramManager);
        RebirthHologramProvider rebirthProvider = new RebirthHologramProvider(plugin, hologramManager);
        AfkHologramProvider afkProvider = new AfkHologramProvider(plugin, hologramManager);
        WelcomeHologramProvider welcomeProvider = new WelcomeHologramProvider(plugin, hologramManager);
        GiveawayHologramProvider giveawayProvider = new GiveawayHologramProvider(plugin, hologramManager);
        EventHologramProvider eventProvider = new EventHologramProvider(plugin, hologramManager);

        // Ensure config entries exist for all groups
        ensureConfigEntry("Production", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Tier", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Income", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Rebirth", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("AFK", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Welcome", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("GIVEAWAY", "voidworld", 60.0, 175.0, 20.0);
        ensureConfigEntry("Event", "voidworld", 65.0, 175.0, 20.0);

        // AFK, Welcome and static holograms (GIVEAWAY, Event) have purely static/local text - don't depend
        // on Multigainer at all - so create/init their engines unconditionally,
        // before the Multigainer availability check below (which only gates the
        // money/xp-driven ones).
        PlayerHologramEngine afkEngine = new PlayerHologramEngine(plugin, hologramManager, afkProvider);
        engines.put("AFK", afkEngine);
        afkEngine.init();

        PlayerHologramEngine welcomeEngine = new PlayerHologramEngine(plugin, hologramManager, welcomeProvider);
        engines.put("Welcome", welcomeEngine);
        welcomeEngine.init();

        PlayerHologramEngine giveawayEngine = new PlayerHologramEngine(plugin, hologramManager, giveawayProvider);
        engines.put("GIVEAWAY", giveawayEngine);
        giveawayEngine.init();

        PlayerHologramEngine eventEngine = new PlayerHologramEngine(plugin, hologramManager, eventProvider);
        engines.put("Event", eventEngine);
        eventEngine.init();

        // Check if multigainer is available
        if (productionProvider.getMultigainer() == null && tierProvider.getMultigainer() == null
                && incomeProvider.getMultigainer() == null && rebirthProvider.getMultigainer() == null) {
            plugin.getLogger().warning("Multigainer plugin not found - Production/Tier/Income/Rebirth holograms disabled. "
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

        plugin.getLogger().info("HologramBridge initialized with Production, Tier, Income, Rebirth, AFK, Welcome, GIVEAWAY and Event engines");
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

        // Default template written into hologram-groups.yml on first run - matches
        // each provider's getDefaultTemplate() so admins immediately see (and can
        // edit) the full, real template instead of a stripped-down placeholder.
        if (groupId.equals("Production")) {
            displayData.put("lines", List.of(
                    "§e§lPRODUCTION",
                    "",
                    "§fYour level §e{level}",
                    "§fYour Worker's XP §e{work_xp} §f/ §e{xp_for_next}",
                    "§fYour production rate §e{energy_per_min} §f/min",
                    "§f§lTotal Energy §f{stored_energy} §e§l⚡",
                    "",
                    "§4§oRequires at least TIER 3"
            ));
        } else if (groupId.equals("Tier")) {
            displayData.put("lines", List.of(
                    "§b§lTIER",
                    "§fCurrent Tier §3{tier}",
                    "§fTier Points §3{tier_points}",
                    "§fProgress §3{progress_cur} §7/§3 {progress_next}",
                    "{tier_up}"
            ));
        } else if (groupId.equals("Income")) {
            displayData.put("lines", List.of(
                    "&#39FF14&lINCOME",
                    "",
                    "§fYour income is §a{income_per_second} §f/s",
                    "§fYour total multi is §a{money_multiplier}x",
                    "§fYour balance is §a{balance}",
                    "",
                    "§7 -- §7§oMoney is currency for buying §7--",
                    "§7§o/upgrades and /rebirth"
            ));
        } else if (groupId.equals("Rebirth")) {
            displayData.put("lines", List.of(
                    "§5§lREBIRTH",
                    "",
                    "§fYou will get §5",
                    "{points_on_rebirth} §frebirth points",
                    "§fYour rebirth multi §5{rebirth_multi}§fx",
                    "§fRebirth points §5{rebirth_points}"
            ));
        } else if (groupId.equals("AFK")) {
            displayData.put("lines", List.of(
                    "§e§l§nAFK",
                    "§eEarn 1 AFK POINT every 60 seconds",
                    "§eBut rank IMMORTAL earns 1.5x more AFK points"
            ));
        } else if (groupId.equals("Welcome")) {
            // {title} is generated in code (WelcomeHologramProvider) as an animated
            // hex gradient - keep it as-is unless you want to lose the animation.
            displayData.put("lines", List.of(
                    "{title}",
                    "&fWelcome {player}! For start follow tutorial on top of the screen.",
                    "&fConstant events for big prizes on our DISCORD!",
                    "&fIf you have any questions contact staff members."
            ));
        } else if (groupId.equals("GIVEAWAY")) {
            displayData.put("lines", List.of(
                    "§5§lGIVEAWAY",
                    "§f1 Pro GainerPass",
                    "§ftotal 3 winners",
                    "§f{giveaway_date}",
                    "{giveaway_timer_line}"
            ));
        } else if (groupId.equals("Event")) {
            displayData.put("lines", List.of(
                    "§9§l§nEVENT",
                    "§fColor event",
                    "§f{event_date}",
                    "",
                    "§9Upcoming events:",
                    "§ftnt run",
                    "§f28.7. 20:00"
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