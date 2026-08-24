package cz.mgholograms.manager;


import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.content.WelcomeHologramProvider;
import cz.mgholograms.manager.bridge.content.GiveawayHologramProvider;
import cz.mgholograms.manager.bridge.content.EventHologramProvider;
import cz.mgholograms.manager.bridge.content.ParkourPersonalHologramProvider;
import cz.mgholograms.manager.bridge.content.ParkourWeeklyHologramProvider;
import cz.mgholograms.manager.bridge.content.ParkourLifetimeHologramProvider;
import cz.mgholograms.manager.bridge.content.MinesHologramProvider;
import cz.mgholograms.manager.bridge.content.ShovelZoneStatsHologramProvider;
import cz.mgholograms.manager.bridge.content.ShovelPlayerStatsHologramProvider;
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

    // Known ahead of time, independent of init() ordering - fixes a race condition where
    // HologramManager.init() (and its reload()) could run BEFORE HologramBridge.init()
    // populates the `engines` map below. If isManagedGroup() relied on `engines.containsKey()`,
    // it would incorrectly report false during that window and HologramManager would create
    // a StaticGroupEngine for Production/Tier/Income too - which then never gets cleaned up
    // because it uses different (per-player) hologram names than the legacy cleanup logic expects.
    private static final java.util.Set<String> MANAGED_GROUP_IDS =
            java.util.Set.of("Welcome", "GIVEAWAY", "Event",
                    "ParkourPersonal", "ParkourWeekly", "ParkourLifetime", "Mines",
                    "ShovelZoneStats1", "ShovelZoneStats2", "ShovelZoneStats3", "ShovelZoneStats4", "ShovelZoneStats5",
                    "ShovelPlayerStats");

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
        ParkourPersonalHologramProvider parkourPersonalProvider = new ParkourPersonalHologramProvider(plugin, hologramManager);
        ParkourWeeklyHologramProvider parkourWeeklyProvider = new ParkourWeeklyHologramProvider(plugin, hologramManager);
        ParkourLifetimeHologramProvider parkourLifetimeProvider = new ParkourLifetimeHologramProvider(plugin, hologramManager);
        MinesHologramProvider minesProvider = new MinesHologramProvider(plugin, hologramManager);
        ShovelZoneStatsHologramProvider shovelZone1Provider = new ShovelZoneStatsHologramProvider(plugin, hologramManager, 1);
        ShovelZoneStatsHologramProvider shovelZone2Provider = new ShovelZoneStatsHologramProvider(plugin, hologramManager, 2);
        ShovelZoneStatsHologramProvider shovelZone3Provider = new ShovelZoneStatsHologramProvider(plugin, hologramManager, 3);
        ShovelZoneStatsHologramProvider shovelZone4Provider = new ShovelZoneStatsHologramProvider(plugin, hologramManager, 4);
        ShovelZoneStatsHologramProvider shovelZone5Provider = new ShovelZoneStatsHologramProvider(plugin, hologramManager, 5);
        ShovelPlayerStatsHologramProvider shovelPlayerStatsProvider = new ShovelPlayerStatsHologramProvider(plugin, hologramManager);
        WelcomeHologramProvider welcomeProvider = new WelcomeHologramProvider(plugin, hologramManager);
        GiveawayHologramProvider giveawayProvider = new GiveawayHologramProvider(plugin, hologramManager);
        EventHologramProvider eventProvider = new EventHologramProvider(plugin, hologramManager);

        // Ensure config entries exist for all groups
        ensureConfigEntry("ParkourPersonal", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("ParkourWeekly", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("ParkourLifetime", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("Mines", "voidworld", 398.49, 217.0, 182.48);
        ensureConfigEntry("ShovelZoneStats1", "voidworld", 6.175383741426156, 172.7340583097124, -0.4542485831274084);
        ensureConfigEntry("ShovelZoneStats2", "voidworld", 12.0, 175.0, 0.0);
        ensureConfigEntry("ShovelZoneStats3", "voidworld", 18.0, 175.0, 0.0);
        ensureConfigEntry("ShovelZoneStats4", "voidworld", 24.0, 175.0, 0.0);
        ensureConfigEntry("ShovelZoneStats5", "voidworld", 30.0, 175.0, 0.0);
        ensureConfigEntry("ShovelPlayerStats", "voidworld", 0.500968794345261, 173.4780117216745, -2.0868706433239765);
        ensureConfigEntry("Welcome", "voidworld", 0.0, 0.0, 0.0);
        ensureConfigEntry("GIVEAWAY", "voidworld", 60.0, 175.0, 20.0);
        ensureConfigEntry("Event", "voidworld", 65.0, 175.0, 20.0);

        // Welcome and static holograms (GIVEAWAY, Event) have purely static/local text - don't depend
        // on Multigainer at all - so create/init their engines unconditionally,
        // before the Multigainer availability check below (which only gates the
        // money/xp-driven ones).
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
        if (minesProvider.getMultigainer() == null && shovelZone1Provider.getMultigainer() == null
                && shovelPlayerStatsProvider.getMultigainer() == null) {
            plugin.getLogger().warning("Multigainer plugin not found - Mines/Shovel holograms disabled. "
                    + "Make sure 'multigainer' is installed and loaded before MGHolograms.");
            return;
        }

        // Create engines for each provider
        PlayerHologramEngine parkourPersonalEngine = new PlayerHologramEngine(plugin, hologramManager, parkourPersonalProvider);
        PlayerHologramEngine parkourWeeklyEngine = new PlayerHologramEngine(plugin, hologramManager, parkourWeeklyProvider);
        PlayerHologramEngine parkourLifetimeEngine = new PlayerHologramEngine(plugin, hologramManager, parkourLifetimeProvider);
        PlayerHologramEngine minesEngine = new PlayerHologramEngine(plugin, hologramManager, minesProvider);
        PlayerHologramEngine shovelZone1Engine = new PlayerHologramEngine(plugin, hologramManager, shovelZone1Provider);
        PlayerHologramEngine shovelZone2Engine = new PlayerHologramEngine(plugin, hologramManager, shovelZone2Provider);
        PlayerHologramEngine shovelZone3Engine = new PlayerHologramEngine(plugin, hologramManager, shovelZone3Provider);
        PlayerHologramEngine shovelZone4Engine = new PlayerHologramEngine(plugin, hologramManager, shovelZone4Provider);
        PlayerHologramEngine shovelZone5Engine = new PlayerHologramEngine(plugin, hologramManager, shovelZone5Provider);
        PlayerHologramEngine shovelPlayerStatsEngine = new PlayerHologramEngine(plugin, hologramManager, shovelPlayerStatsProvider);

        engines.put("ParkourPersonal", parkourPersonalEngine);
        engines.put("ParkourWeekly", parkourWeeklyEngine);
        engines.put("ParkourLifetime", parkourLifetimeEngine);
        engines.put("Mines", minesEngine);
        engines.put("ShovelZoneStats1", shovelZone1Engine);
        engines.put("ShovelZoneStats2", shovelZone2Engine);
        engines.put("ShovelZoneStats3", shovelZone3Engine);
        engines.put("ShovelZoneStats4", shovelZone4Engine);
        engines.put("ShovelZoneStats5", shovelZone5Engine);
        engines.put("ShovelPlayerStats", shovelPlayerStatsEngine);

        // Initialize engines
        parkourPersonalEngine.init();
        parkourWeeklyEngine.init();
        parkourLifetimeEngine.init();
        minesEngine.init();
        shovelZone1Engine.init();
        shovelZone2Engine.init();
        shovelZone3Engine.init();
        shovelZone4Engine.init();
        shovelZone5Engine.init();
        shovelPlayerStatsEngine.init();

        plugin.getLogger().info("HologramBridge initialized with ParkourPersonal, ParkourWeekly, ParkourLifetime, Mines, ShovelZoneStats1-5, ShovelPlayerStats, Welcome, GIVEAWAY and Event engines");
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
        if (groupId.equals("Welcome")) {
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
        } else if (groupId.equals("ParkourPersonal")) {
            displayData.put("lines", List.of(
                    "§fYOUR PARKOUR STATS",
                    "",
                    "§fHighest score §3{parkour_max_score}",
                    "§fTotal jumps §3{parkour_total_jumps}",
                    "§fFails §3{parkour_fails}"
            ));
        } else if (groupId.equals("ParkourWeekly")) {
            displayData.put("lines", List.of(
                    "§fWEEKLY STATISTICS",
                    "§f1. {p1} - {s1} jumps",
                    "§f2. {p2} - {s2} jumps",
                    "§f3. {p3} - {s3} jumps",
                    "§f4. {p4} - {s4} jumps",
                    "§f5. {p5} - {s5} jumps",
                    "§f6. {p6} - {s6} jumps",
                    "§f7. {p7} - {s7} jumps",
                    "§f8. {p8} - {s8} jumps",
                    "§f9. {p9} - {s9} jumps",
                    "§f10. {p10} - {s10} jumps"
            ));
        } else if (groupId.equals("Mines")) {
            // Note: "Mines" group actually has 6 separate TEXT displays in
            // hologram-groups.yml (name / "Multipliers" label / separator /
            // gems+xp / separator / next-mine+requirements / footer). This
            // single-block fallback is only used if the group is entirely
            // missing from the config - edit hologram-groups.yml directly to
            // change the real per-display template.
            displayData.put("lines", List.of(
                    "{mine_name}",
                    "&fMultipliers",
                    "&8§m                    ",
                    "&7 Gems: {mine_gems} ",
                    "&7 XP: {mine_xp} ",
                    "&8§m                    ",
                    "{next_mine_line}",
                    "{next_mine_req_line}",
                    "&7§oChange your Mines in &f§l/mines"
            ));
        } else if (groupId.equals("ParkourLifetime")) {
            displayData.put("lines", List.of(
                    "§fLIFETIME STATISTICS",
                    "§f1. {p1} - {s1} jumps",
                    "§f2. {p2} - {s2} jumps",
                    "§f3. {p3} - {s3} jumps",
                    "§f4. {p4} - {s4} jumps",
                    "§f5. {p5} - {s5} jumps",
                    "§f6. {p6} - {s6} jumps",
                    "§f7. {p7} - {s7} jumps",
                    "§f8. {p8} - {s8} jumps",
                    "§f9. {p9} - {s9} jumps",
                    "§f10. {p10} - {s10} jumps"
            ));
        } else if (groupId.equals("ShovelZoneStats1")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL ZONE 1 STATS",
                    "§8§m                    ",
                    "§fBlocks Destroyed: §6{zone1_blocks_line}",
                    "§fRaw destroyed count: §6{zone1_current_destroyed}",
                    "§8§m                    ",
                    "§7§oZone 1 Progress"
            ));
        } else if (groupId.equals("ShovelZoneStats2")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL ZONE 2 STATS",
                    "§8§m                    ",
                    "§fBlocks Destroyed: §6{zone2_blocks_line}",
                    "§fRaw destroyed count: §6{zone2_current_destroyed}",
                    "§8§m                    ",
                    "§7§oZone 2 Progress"
            ));
        } else if (groupId.equals("ShovelZoneStats3")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL ZONE 3 STATS",
                    "§8§m                    ",
                    "§fBlocks Destroyed: §6{zone3_blocks_line}",
                    "§fRaw destroyed count: §6{zone3_current_destroyed}",
                    "§8§m                    ",
                    "§7§oZone 3 Progress"
            ));
        } else if (groupId.equals("ShovelZoneStats4")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL ZONE 4 STATS",
                    "§8§m                    ",
                    "§fBlocks Destroyed: §6{zone4_blocks_line}",
                    "§fRaw destroyed count: §6{zone4_current_destroyed}",
                    "§8§m                    ",
                    "§7§oZone 4 Progress"
            ));
        } else if (groupId.equals("ShovelZoneStats5")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL ZONE 5 STATS",
                    "§8§m                    ",
                    "§fBlocks Destroyed: §6{zone5_blocks_line}",
                    "§fRaw destroyed count: §6{zone5_current_destroyed}",
                    "§8§m                    ",
                    "§7§oZone 5 Progress"
            ));
        } else if (groupId.equals("ShovelPlayerStats")) {
            displayData.put("lines", List.of(
                    "§6§l§nSHOVEL PLAYER STATS",
                    "§8§m                    ",
                    "§fTotal Blocks Destroyed: §6{lifetime_total_blocks_str}",
                    "§fTotal Blocks (raw): §6{lifetime_total_blocks_raw}",
                    "§8§m                    ",
                    "§fTotal Treasure Found: §6{lifetime_total_treasure_str}",
                    "§fTotal Treasure (raw): §6{lifetime_total_treasure_raw}",
                    "§8§m                    ",
                    "§7§oLifetime Statistics"
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