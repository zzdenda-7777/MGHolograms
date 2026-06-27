package cz.mgholograms.manager;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.config.HologramConfigLoader;
import cz.mgholograms.model.Display;
import cz.mgholograms.model.DisplayType;
import cz.mgholograms.model.HologramGroup;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.DisplayHologramData;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public class HologramManager {
    private final MGHolograms plugin;
    private final HologramConfigLoader configLoader;
    private List<HologramGroup> loadedGroups;

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
        // FancyHolograms handles persistence automatically
        plugin.getLogger().info("HologramManager shutdown");
    }

    public void reload() {
        // Reload config
        configLoader.reload();
        plugin.reloadConfig();

        // Load groups
        loadedGroups = configLoader.loadGroups();

        // Create holograms via FancyHolograms API
        createHologramsFromConfig();

        plugin.getLogger().info("HologramManager reloaded - " + loadedGroups.size() + " groups loaded");
    }

    private void createHologramsFromConfig() {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        int viewDistance = plugin.getConfig().getInt("view-distance", 100);
        plugin.getLogger().info("View distance from config: " + viewDistance);

        for (HologramGroup group : loadedGroups) {
            // money_balance je šablona pro HologramBridge (per-player hologramy) -
            // obecná logika by z ní vytvořila jeden veřejný hologram "money_balance_0",
            // viditelný všem se stejným textem, což nechceme.
            if (hologramBridge != null && group.getGroupId().equals(HologramBridge.GROUP_ID)) {
                continue;
            }

            Location location = new Location(
                    plugin.getServer().getWorld(group.getWorld()),
                    group.getX(),
                    group.getY(),
                    group.getZ(),
                    group.getYaw(),
                    group.getPitch()
            );

            for (int i = 0; i < group.getDisplays().size(); i++) {
                Display display = group.getDisplays().get(i);
                double displayX = group.getX() + display.getXOffset();
                double displayY = group.getY() + display.getYOffset();
                double displayZ = group.getZ() + display.getZOffset();
                float displayYaw = display.getYaw() != null ? display.getYaw() : location.getYaw();
                float displayPitch = display.getPitch() != null ? display.getPitch() : location.getPitch();
                Location displayLocation = new Location(
                        location.getWorld(),
                        displayX,
                        displayY,
                        displayZ,
                        displayYaw,
                        displayPitch
                );

                String hologramName = group.getGroupId() + "_" + i;
                DisplayHologramData hologramData = createHologramData(hologramName, displayLocation, display);

                if (hologramData != null) {
                    Hologram hologram = manager.create(hologramData);
                    hologram.getData().setPersistent(false); // Runtime holograms managed by our config
                    hologram.getData().setVisibilityDistance(viewDistance); // Set view distance from config
                    manager.addHologram(hologram);
                    plugin.getLogger().info("Created hologram: " + hologramName + " with view distance: " + viewDistance);
                }
            }
        }
    }

    private DisplayHologramData createHologramData(String name, Location location, Display display) {
        try {
            if (display.getType() == DisplayType.TEXT) {
                TextHologramData textData = new TextHologramData(name, location);

                // Set text content
                if (display.getLines() != null && !display.getLines().isEmpty()) {
                    textData.setText(display.getLines());
                }

                // Set background transparency
                if (display.getBackground() != null && !display.getBackground().isEmpty()) {
                    if (display.getBackground().equalsIgnoreCase("transparent")) {
                        textData.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                        textData.setSeeThrough(true);
                    }
                }

                // Set billboard
                textData.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

                // Set scale
                float scale = display.getScale();
                textData.setScale(new Vector3f(scale, scale, scale));

                // Set brightness if specified
                if (display.getBrightness() != null) {
                    try {
                        org.bukkit.entity.Display.Brightness brightness =
                                new org.bukkit.entity.Display.Brightness(0, display.getBrightness()); // 0 = block
                        textData.setBrightness(brightness);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to set brightness: " + e.getMessage());
                    }
                }

                return textData;

            } else if (display.getType() == DisplayType.ITEM) {
                ItemHologramData itemData = new ItemHologramData(name, location);

                // Set item stack
                if (display.getMaterial() != null && !display.getMaterial().isEmpty()) {
                    try {
                        Material material = Material.matchMaterial(display.getMaterial());
                        if (material != null) {
                            ItemStack itemStack = new ItemStack(material);
                            itemData.setItemStack(itemStack);
                        } else {
                            plugin.getLogger().warning("Invalid material: " + display.getMaterial());
                            return null;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to set item: " + e.getMessage());
                        return null;
                    }
                }

                // Set billboard
                itemData.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

                // Set scale
                float scale = display.getScale();
                itemData.setScale(new Vector3f(scale, scale, scale));

                // Set brightness if specified
                if (display.getBrightness() != null) {
                    try {
                        org.bukkit.entity.Display.Brightness brightness =
                                new org.bukkit.entity.Display.Brightness(0, display.getBrightness()); // 0 = block
                        itemData.setBrightness(brightness);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to set brightness: " + e.getMessage());
                    }
                }

                return itemData;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create hologram data: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
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

        // Remove existing holograms for this group
        // (money_balance je řízeno HologramBridge per-player hologramy -
        // ty se "money_balance_0" stylem nejmenují, takže by zde stejně
        // nic nenašlo, ale pro jasnost a bezpečnost to explicitně přeskočíme)
        if (!(hologramBridge != null && groupId.equals(HologramBridge.GROUP_ID))) {
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

            // money_balance je šablona řízená HologramBridge (per-player hologramy).
            // Po přesunu pozice deleguj na bridge - ten zruší aktivní per-player
            // hologramy, a checkAllPlayers() je při nejbližším běhu znovu vytvoří
            // na nové pozici pro hráče, kteří jsou v dosahu.
            if (hologramBridge != null && groupId.equals(HologramBridge.GROUP_ID)) {
                hologramBridge.createOrUpdateHologram();
                plugin.getLogger().info("=== TELEPORT COMPLETE FOR GROUP '" + groupId + "' (via HologramBridge) ===");
                return;
            }

            // Recreate holograms at new position
            Location location = new Location(
                    plugin.getServer().getWorld(updatedGroup.getWorld()),
                    updatedGroup.getX(),
                    updatedGroup.getY(),
                    updatedGroup.getZ(),
                    updatedGroup.getYaw(),
                    updatedGroup.getPitch()
            );

            de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            int viewDistance = plugin.getConfig().getInt("view-distance", 100);
            plugin.getLogger().info("View distance from config: " + viewDistance);

            for (int i = 0; i < updatedGroup.getDisplays().size(); i++) {
                Display display = updatedGroup.getDisplays().get(i);
                double displayX = updatedGroup.getX() + display.getXOffset();
                double displayY = updatedGroup.getY() + display.getYOffset();
                double displayZ = updatedGroup.getZ() + display.getZOffset();
                float displayYaw = display.getYaw() != null ? display.getYaw() : location.getYaw();
                float displayPitch = display.getPitch() != null ? display.getPitch() : location.getPitch();
                Location displayLocation = new Location(
                        location.getWorld(),
                        displayX,
                        displayY,
                        displayZ,
                        displayYaw,
                        displayPitch
                );

                String hologramName = groupId + "_" + i;
                DisplayHologramData hologramData = createHologramData(hologramName, displayLocation, display);

                if (hologramData != null) {
                    Hologram hologram = manager.create(hologramData);
                    hologram.getData().setPersistent(false);
                    hologram.getData().setVisibilityDistance(viewDistance); // Set view distance from config
                    manager.addHologram(hologram);
                    plugin.getLogger().info("Recreated hologram: " + hologramName + " with view distance: " + viewDistance);
                }
            }

            plugin.getLogger().info("=== TELEPORT COMPLETE FOR GROUP '" + groupId + "' ===");
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