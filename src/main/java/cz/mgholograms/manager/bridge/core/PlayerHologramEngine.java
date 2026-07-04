package cz.mgholograms.manager.bridge.core;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.model.Display;
import cz.mgholograms.model.DisplayType;
import cz.mgholograms.model.HologramGroup;
import cz.mgholograms.util.PlayerJoinTracker;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * General engine for per-player holograms.
 * Handles distance checking, hologram creation/removal, and text refreshing
 * for a single HologramContentProvider instance.
 */
public class PlayerHologramEngine {

    private static final long CHECK_INTERVAL_TICKS = 100L; // 5s
    private static final long TEXT_REFRESH_INTERVAL_TICKS = 40L; // 2s

    private final MGHolograms plugin;
    private final cz.mgholograms.manager.HologramManager hologramManager;
    private final HologramContentProvider contentProvider;

    private final Set<UUID> activeViewers = new HashSet<>();
    private final Map<UUID, Integer> hologramCreationCount = new HashMap<>();

    private org.bukkit.scheduler.BukkitTask distanceCheckTask;
    private org.bukkit.scheduler.BukkitTask textRefreshTask;

    public PlayerHologramEngine(MGHolograms plugin, cz.mgholograms.manager.HologramManager hologramManager, HologramContentProvider contentProvider) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
        this.contentProvider = contentProvider;
    }

    public void init() {
        // Force cleanup of old holograms for this group at startup
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (int i = 0; i < 100; i++) {
            String name = contentProvider.getGroupId() + "_" + i;
            if (manager.getHologram(name).isPresent()) {
                manager.removeHologram(manager.getHologram(name).get());
            }
        }

        startTasks();
    }

    public void shutdown() {
        if (distanceCheckTask != null) distanceCheckTask.cancel();
        if (textRefreshTask != null) textRefreshTask.cancel();
        removeAllPlayerHolograms();
    }

    private void startTasks() {
        distanceCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAllPlayers, 0L, CHECK_INTERVAL_TICKS);
        textRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAllTexts, 20L, TEXT_REFRESH_INTERVAL_TICKS);
    }

    /**
     * For each online player, checks distance to template location.
     * In range -> creates/shows their personal hologram.
     * Out of range -> hides/removes their personal hologram.
     */
    private void checkAllPlayers() {
        HologramGroup group = findGroup(contentProvider.getGroupId());
        if (group == null) return;

        Location templateLocation = templateLocation(group);
        if (templateLocation == null || templateLocation.getWorld() == null) return;

        int viewDistance = plugin.getConfig().getInt("view-distance", 100);
        double maxDistSq = (double) viewDistance * viewDistance;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!PlayerJoinTracker.isReady(player.getUniqueId())) {
                continue; // Player joined less than 2s ago - wait before showing/checking their hologram
            }

            boolean sameWorld = player.getWorld().equals(templateLocation.getWorld());
            boolean inRange = sameWorld && player.getLocation().distanceSquared(templateLocation) <= maxDistSq;
            boolean hasLineOfSight = inRange && player.hasLineOfSight(templateLocation);
            boolean currentlyActive = activeViewers.contains(player.getUniqueId());

            if (hasLineOfSight && !currentlyActive) {
                createPlayerHologram(player, templateLocation, group);
                activeViewers.add(player.getUniqueId());
            } else if (!hasLineOfSight && currentlyActive) {
                removePlayerHologram(player.getUniqueId());
                activeViewers.remove(player.getUniqueId());
            }
        }
    }

    /**
     * For each currently active viewer, recalculates text from the content provider
     * and updates their personal hologram.
     */
    private void refreshAllTexts() {
        if (activeViewers.isEmpty()) return;

        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (UUID uuid : new HashSet<>(activeViewers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                removePlayerHologram(uuid);
                activeViewers.remove(uuid);
                continue;
            }

            HologramGroup group = findGroup(contentProvider.getGroupId());
            if (group != null) {
                updatePlayerHologramText(player, uuid, group);
            }
        }
    }

    private void updatePlayerHologramText(Player player, UUID uuid, HologramGroup group) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (int i = 0; i < group.getDisplays().size(); i++) {
            Display display = group.getDisplays().get(i);
            if (display.getType() != DisplayType.TEXT) continue;

            String name = hologramName(uuid) + "_" + i;
            Optional<Hologram> holoOpt = manager.getHologram(name);

            if (holoOpt.isEmpty()) {
                Location templateLocation = templateLocation(group);
                if (templateLocation != null) {
                    createPlayerHologram(player, templateLocation, group);
                }
                return;
            }

            Hologram hologram = holoOpt.get();
            if (hologram.getData() instanceof TextHologramData textData) {
                List<String> textLines = contentProvider.getLines(uuid, player);
                if (textLines == null) {
                    textLines = contentProvider.getLoadingLines();
                }
                textData.setText(textLines);
                hologram.refreshHologram(player);
            }
        }
    }

    private void createPlayerHologram(Player player, Location templateLocation, HologramGroup group) {
        int count = hologramCreationCount.getOrDefault(player.getUniqueId(), 0);
        hologramCreationCount.put(player.getUniqueId(), count + 1);

        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (int i = 0; i < group.getDisplays().size(); i++) {
            Display display = group.getDisplays().get(i);
            String name = hologramName(player.getUniqueId()) + "_" + i;

            manager.getHologram(name).ifPresent(manager::removeHologram);

            float scale = display.getScale() * 1.2f;
            boolean transparent = "transparent".equalsIgnoreCase(display.getBackground());

            Location displayLocation = new Location(
                    templateLocation.getWorld(),
                    templateLocation.getX() + display.getXOffset(),
                    templateLocation.getY() + display.getYOffset(),
                    templateLocation.getZ() + display.getZOffset(),
                    templateLocation.getYaw(),
                    templateLocation.getPitch()
            );

            if (display.getType() == DisplayType.TEXT) {
                List<String> textLines = contentProvider.getLines(player.getUniqueId(), player);
                if (textLines == null) {
                    textLines = contentProvider.getLoadingLines();
                }

                TextHologramData textData = new TextHologramData(name, displayLocation);
                textData.setText(textLines);
                textData.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                textData.setScale(new Vector3f(scale, scale, scale));
                if (transparent) {
                    textData.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    textData.setSeeThrough(true);
                }
                textData.setVisibility(Visibility.MANUAL);
                textData.setVisibilityDistance(plugin.getConfig().getInt("view-distance", 100));

                Hologram hologram = manager.create(textData);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    hologram.forceShowHologram(player);
                }, 2L);

            } else if (display.getType() == DisplayType.ITEM) {
                ItemHologramData data = new ItemHologramData(name, displayLocation);
                if (display.getMaterial() != null && !display.getMaterial().isEmpty()) {
                    Material material = Material.matchMaterial(display.getMaterial());
                    if (material != null) data.setItemStack(new ItemStack(material));
                }
                data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                data.setScale(new Vector3f(scale, scale, scale));
                data.setVisibility(Visibility.MANUAL);
                data.setVisibilityDistance(plugin.getConfig().getInt("view-distance", 100));

                Hologram hologram = manager.create(data);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    hologram.forceShowHologram(player);
                }, 2L);
            }
        }
    }

    private void removePlayerHologram(UUID uuid) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        // Remove all displays for the player (groupId_uuid_0, groupId_uuid_1, ...)
        for (int i = 0; i < 10; i++) { // Max 10 displays should be enough
            String name = hologramName(uuid) + "_" + i;
            manager.getHologram(name).ifPresent(manager::removeHologram);
        }
    }

    private void removeAllPlayerHolograms() {
        for (UUID uuid : new HashSet<>(activeViewers)) {
            removePlayerHologram(uuid);
        }
        activeViewers.clear();
    }

    private String hologramName(UUID uuid) {
        return contentProvider.getGroupId() + "_" + uuid;
    }

    private Location templateLocation(HologramGroup group) {
        Display display = group.getDisplays().isEmpty() ? null : group.getDisplays().get(0);
        double xOff = display != null ? display.getXOffset() : 0.0;
        double yOff = display != null ? display.getYOffset() : 0.0;
        double zOff = display != null ? display.getZOffset() : 0.0;

        return new Location(
                Bukkit.getWorld(group.getWorld()),
                group.getX() + xOff,
                group.getY() + yOff,
                group.getZ() + zOff,
                group.getYaw(),
                group.getPitch()
        );
    }

    private HologramGroup findGroup(String groupId) {
        if (hologramManager.getLoadedGroups() == null) {
            return null;
        }
        for (HologramGroup group : hologramManager.getLoadedGroups()) {
            if (group.getGroupId().equals(groupId)) {
                return group;
            }
        }
        return null;
    }

    public Location getTemplateLocation() {
        HologramGroup group = findGroup(contentProvider.getGroupId());
        return group != null ? templateLocation(group) : null;
    }

    /**
     * Called from HologramManager#teleportGroup() / reload() for this groupId.
     * Since the template position has changed in the config, we remove active
     * per-player holograms - checkAllPlayers() will recreate them at the new
     * position on the next run (or remove them if the player is no longer in range).
     */
    public void createOrUpdateHologram() {
        removeAllPlayerHolograms();
    }

    public String getGroupId() {
        return contentProvider.getGroupId();
    }
}