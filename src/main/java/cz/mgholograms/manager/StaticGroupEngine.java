package cz.mgholograms.manager;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player engine for STATIC (config-only) hologram groups - e.g. cobblestone,
 * goldore, and any other group not backed by a HologramContentProvider.
 * <p>
 * Uses the exact same distance + line-of-sight logic as
 * {@link cz.mgholograms.manager.bridge.core.PlayerHologramEngine}, so these
 * holograms now behave identically to Production/Tier: visible up to
 * view-distance AND only with direct line of sight (they hide behind walls).
 * <p>
 * The only difference is that the text/item content comes straight from the
 * group's config ({@link Display#getLines()} / {@link Display#getMaterial()})
 * instead of a dynamic per-player content provider, since static holograms
 * show the same content to everyone.
 */
public class StaticGroupEngine {

    private static final long CHECK_INTERVAL_TICKS = 100L; // 5s, same as PlayerHologramEngine
    private static final int MAX_DISPLAYS_CLEANUP = 20; // safety margin for name-based cleanup

    private final MGHolograms plugin;
    private HologramGroup group;

    private final Set<UUID> activeViewers = new HashSet<>();
    private org.bukkit.scheduler.BukkitTask distanceCheckTask;

    public StaticGroupEngine(MGHolograms plugin, HologramGroup group) {
        this.plugin = plugin;
        this.group = group;
    }

    public String getGroupId() {
        return group.getGroupId();
    }

    /**
     * Called after a config reload/teleport so the engine uses the latest
     * position/displays without needing to be recreated.
     */
    public void updateGroup(HologramGroup newGroup) {
        this.group = newGroup;
    }

    public void init() {
        // Clean up any leftover *shared/global* holograms that older versions of
        // HologramManager created directly (named "<groupId>_<index>"), since this
        // engine now creates per-player holograms instead.
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (int i = 0; i < MAX_DISPLAYS_CLEANUP; i++) {
            String legacyName = group.getGroupId() + "_" + i;
            manager.getHologram(legacyName).ifPresent(manager::removeHologram);
        }

        distanceCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAllPlayers, 0L, CHECK_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (distanceCheckTask != null) distanceCheckTask.cancel();
        removeAllPlayerHolograms();
    }

    private void checkAllPlayers() {
        Location templateLocation = templateLocation();
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
                createPlayerHologram(player);
                activeViewers.add(player.getUniqueId());
            } else if (!hasLineOfSight && currentlyActive) {
                removePlayerHologram(player.getUniqueId());
                activeViewers.remove(player.getUniqueId());
            }
        }
    }

    private void createPlayerHologram(Player player) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        int viewDistance = plugin.getConfig().getInt("view-distance", 100);

        for (int i = 0; i < group.getDisplays().size(); i++) {
            Display display = group.getDisplays().get(i);
            String name = hologramName(player.getUniqueId()) + "_" + i;

            manager.getHologram(name).ifPresent(manager::removeHologram);

            Location displayLocation = new Location(
                    Bukkit.getWorld(group.getWorld()),
                    group.getX() + display.getXOffset(),
                    group.getY() + display.getYOffset(),
                    group.getZ() + display.getZOffset(),
                    display.getYaw() != null ? display.getYaw() : group.getYaw(),
                    display.getPitch() != null ? display.getPitch() : group.getPitch()
            );

            float scale = display.getScale();

            if (display.getType() == DisplayType.TEXT) {
                TextHologramData textData = new TextHologramData(name, displayLocation);

                if (display.getLines() != null && !display.getLines().isEmpty()) {
                    textData.setText(display.getLines());
                }

                if (display.getBackground() != null && display.getBackground().equalsIgnoreCase("transparent")) {
                    textData.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    textData.setSeeThrough(true);
                }

                textData.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                textData.setScale(new Vector3f(scale, scale, scale));

                if (display.getBrightness() != null) {
                    try {
                        textData.setBrightness(new org.bukkit.entity.Display.Brightness(15, display.getBrightness()));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to set brightness for " + name + ": " + e.getMessage());
                    }
                }

                textData.setVisibility(Visibility.MANUAL);
                textData.setVisibilityDistance(viewDistance);

                Hologram hologram = manager.create(textData);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> hologram.forceShowHologram(player), 2L);

            } else if (display.getType() == DisplayType.ITEM) {
                ItemHologramData data = new ItemHologramData(name, displayLocation);

                if (display.getMaterial() != null && !display.getMaterial().isEmpty()) {
                    Material material = Material.matchMaterial(display.getMaterial());
                    if (material != null) data.setItemStack(new ItemStack(material));
                }

                data.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                data.setScale(new Vector3f(scale, scale, scale));

                if (display.getBrightness() != null) {
                    try {
                        data.setBrightness(new org.bukkit.entity.Display.Brightness(15, display.getBrightness()));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to set brightness for " + name + ": " + e.getMessage());
                    }
                }

                data.setVisibility(Visibility.MANUAL);
                data.setVisibilityDistance(viewDistance);

                Hologram hologram = manager.create(data);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> hologram.forceShowHologram(player), 2L);
            }
        }
    }

    private void removePlayerHologram(UUID uuid) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (int i = 0; i < MAX_DISPLAYS_CLEANUP; i++) {
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
        return group.getGroupId() + "_" + uuid;
    }

    private Location templateLocation() {
        return new Location(
                Bukkit.getWorld(group.getWorld()),
                group.getX(),
                group.getY(),
                group.getZ(),
                group.getYaw(),
                group.getPitch()
        );
    }

    public Location getTemplateLocation() {
        return templateLocation();
    }

    /**
     * Called after the group's position changes (teleport/reload). Removes
     * currently active per-player holograms - checkAllPlayers() will recreate
     * them at the new position on the next tick (or not, if out of range).
     */
    public void createOrUpdateHologram() {
        removeAllPlayerHolograms();
    }
}