package cz.mgholograms.manager;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.config.HologramConfigLoader;
import cz.mgholograms.model.HologramGroup;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HologramManager {
    private final MGHolograms plugin;
    private final HologramConfigLoader configLoader;
    private List<HologramGroup> loadedGroups;

    // Per-player engines for STATIC (config-only) groups, e.g. cobblestone, goldore.
    // These now behave exactly like the Production/Tier holograms from HologramBridge:
    // visible up to view-distance AND only with direct line of sight.
    private final Map<String, StaticGroupEngine> staticEngines = new HashMap<>();

    // Nastaveno zvenčí (z MGHolograms#onEnable) po vytvoření HologramBridge.
    // Umožňuje teleportGroup()/reload() delegovat groupId "money_balance"
    // na bridge a vyhnout se vytváření obecného (veřejného) hologramu pro ni.
    private HologramBridge hologramBridge;

    public HologramManager(MGHolograms plugin) {
        this.plugin = plugin;
        this.configLoader = new HologramConfigLoader(plugin);
    }

    public void setHologramBridge(HologramBridge hologramBridge) {
        this.hologramBridge = hologramBridge;
    }

    public HologramConfigLoader getConfigLoader() {
        return configLoader;
    }

    public void init() {
        // Initialize config loader
        configLoader.init();

        // Load hologram groups
        reload();

        plugin.getLogger().info("HologramManager initialized");
    }

    public void shutdown() {
        // Stop and clean up per-player static engines
        for (StaticGroupEngine engine : staticEngines.values()) {
            engine.shutdown();
        }
        staticEngines.clear();

        // FancyHolograms handles persistence automatically
        plugin.getLogger().info("HologramManager shutdown");
    }

    public void reload() {
        // Reload config
        configLoader.reload();
        plugin.reloadConfig();

        // Load groups
        loadedGroups = configLoader.loadGroups();

        // Create/refresh per-player engines for static (config-only) groups
        // (Production/Tier are skipped - HologramBridge handles those)
        syncStaticEngines();

        plugin.getLogger().info("HologramManager reloaded - " + loadedGroups.size() + " groups loaded");
    }

    /**
     * Ensures every non-bridge-managed group has a running {@link StaticGroupEngine}
     * with up-to-date data, and stops engines for groups that were removed from config.
     * Replaces the old approach of creating one shared, always-visible-through-walls
     * hologram per display via the FancyHolograms API directly.
     */
    private void syncStaticEngines() {
        int viewDistance = plugin.getConfig().getInt("view-distance", 100);
        plugin.getLogger().info("View distance from config: " + viewDistance);

        Set<String> currentGroupIds = new HashSet<>();

        for (HologramGroup group : loadedGroups) {
            String groupId = group.getGroupId();

            // Groups managed by HologramBridge (Production/Tier) are skipped here
            // - they create their own per-player holograms via PlayerHologramEngine
            if (hologramBridge != null && hologramBridge.isManagedGroup(groupId)) {
                continue;
            }

            currentGroupIds.add(groupId);

            StaticGroupEngine engine = staticEngines.get(groupId);
            if (engine == null) {
                engine = new StaticGroupEngine(plugin, group);
                staticEngines.put(groupId, engine);
                engine.init();
                plugin.getLogger().info("Created static hologram engine for group: " + groupId);
            } else {
                engine.updateGroup(group);
            }
        }

        // Stop and remove engines for groups no longer present in config
        Iterator<Map.Entry<String, StaticGroupEngine>> it = staticEngines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StaticGroupEngine> entry = it.next();
            if (!currentGroupIds.contains(entry.getKey())) {
                entry.getValue().shutdown();
                it.remove();
                plugin.getLogger().info("Removed static hologram engine for deleted group: " + entry.getKey());
            }
        }
    }

    public void centerGroup(String groupId, Player player) {
        teleportGroup(groupId, player);
    }

    public void teleportGroup(String groupId, Player player) {
        plugin.getLogger().info("=== TELEPORTING GROUP '" + groupId + "' ===");
        plugin.getLogger().info("Player: " + player.getName());
        plugin.getLogger().info("Player location: " + player.getLocation());

        HologramGroup group = findGroupById(groupId);
        if (group == null) {
            plugin.getLogger().warning("Hologram group '" + groupId + "' not found");
            return;
        }

        plugin.getLogger().info("Current group location: " + group.getWorld() + ", X=" + group.getX() + ", Y=" + group.getY() + ", Z=" + group.getZ());

        // Remove any leftover legacy shared holograms for this group.
        // Groups managed by HologramBridge or by a StaticGroupEngine handle their
        // own per-player holograms and should skip this.
        boolean isManagedOrStatic = (hologramBridge != null && hologramBridge.isManagedGroup(groupId))
                || staticEngines.containsKey(groupId);
        if (!isManagedOrStatic) {
            removeGroupHolograms(groupId);
        }

        // Update position in config
        configLoader.updateGroupPosition(
                groupId,
                player.getWorld().getName(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );
        plugin.getLogger().info("Updated position in config");

        // Reload the group to get new position
        List<HologramGroup> reloadedGroups = configLoader.loadGroups();
        HologramGroup updatedGroup = null;
        for (HologramGroup g : reloadedGroups) {
            if (g.getGroupId().equals(groupId)) {
                updatedGroup = g;
                break;
            }
        }

        if (updatedGroup != null) {
            plugin.getLogger().info("Reloaded group from config");
            plugin.getLogger().info("New group location: " + updatedGroup.getWorld() + ", X=" + updatedGroup.getX() + ", Y=" + updatedGroup.getY() + ", Z=" + updatedGroup.getZ());

            // Update the group in our loaded list
            for (int i = 0; i < loadedGroups.size(); i++) {
                if (loadedGroups.get(i).getGroupId().equals(groupId)) {
                    loadedGroups.set(i, updatedGroup);
                    break;
                }
            }
            plugin.getLogger().info("Updated group in loaded list");

            // Groups managed by HologramBridge (per-player holograms) delegate to the bridge
            // After position change, delegate to bridge - it will remove active per-player
            // holograms, and checkAllPlayers() will recreate them at the new position
            // for players who are in range.
            if (hologramBridge != null && hologramBridge.isManagedGroup(groupId)) {
                hologramBridge.createOrUpdateHologram(groupId);
                plugin.getLogger().info("=== TELEPORT COMPLETE FOR GROUP '" + groupId + "' (via HologramBridge) ===");
                return;
            }

            // Static (config-only) groups are per-player too now - delegate to their engine.
            // It removes currently active per-player holograms; checkAllPlayers() will
            // recreate them at the new position for whichever players are in range.
            StaticGroupEngine engine = staticEngines.get(groupId);
            if (engine != null) {
                engine.updateGroup(updatedGroup);
                engine.createOrUpdateHologram();
                plugin.getLogger().info("=== TELEPORT COMPLETE FOR GROUP '" + groupId + "' (via StaticGroupEngine) ===");
            } else {
                // Shouldn't normally happen, but create the engine now rather than
                // silently doing nothing.
                engine = new StaticGroupEngine(plugin, updatedGroup);
                staticEngines.put(groupId, engine);
                engine.init();
                plugin.getLogger().warning("No StaticGroupEngine existed for group '" + groupId + "' - created a new one");
                plugin.getLogger().info("=== TELEPORT COMPLETE FOR GROUP '" + groupId + "' (created new StaticGroupEngine) ===");
            }
        } else {
            plugin.getLogger().severe("Failed to reload group '" + groupId + "' after position update");
        }
    }

    private void removeGroupHolograms(String groupId) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        // Remove all holograms for this group
        for (int i = 0; i < 100; i++) { // Reasonable limit
            String hologramName = groupId + "_" + i;
            Optional<Hologram> hologram = manager.getHologram(hologramName);
            if (hologram.isPresent()) {
                manager.removeHologram(hologram.get());
                plugin.getLogger().info("Removed hologram: " + hologramName);
            } else {
                break; // No more holograms for this group
            }
        }
    }

    private HologramGroup findGroupById(String groupId) {
        for (HologramGroup group : loadedGroups) {
            if (group.getGroupId().equals(groupId)) {
                return group;
            }
        }
        return null;
    }

    public List<HologramGroup> getLoadedGroups() {
        return loadedGroups;
    }
}