package cz.mgholograms.config;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.model.Display;
import cz.mgholograms.model.DisplayType;
import cz.mgholograms.model.HologramGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HologramConfigLoader {
    private final MGHolograms plugin;
    private final File configFile;
    private FileConfiguration config;

    public HologramConfigLoader(MGHolograms plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "hologram-groups.yml");
    }

    public void init() {
        if (!configFile.exists()) {
            plugin.saveResource("hologram-groups.yml", false);
        }
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save hologram-groups.yml: " + e.getMessage());
        }
    }

    public List<HologramGroup> loadGroups() {
        List<HologramGroup> groups = new ArrayList<>();
        ConfigurationSection groupsSection = config.getConfigurationSection("groups");

        if (groupsSection == null) {
            plugin.getLogger().warning("No groups section found in hologram-groups.yml");
            return groups;
        }

        int nextEntityId = 1000; // Starting entity ID for holograms

        for (String groupId : groupsSection.getKeys(false)) {
            try {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
                if (groupSection == null) continue;

                String world = groupSection.getString("world");
                double x = groupSection.getDouble("x");
                double y = groupSection.getDouble("y");
                double z = groupSection.getDouble("z");
                float yaw = (float) groupSection.getDouble("yaw");
                float pitch = (float) groupSection.getDouble("pitch");

                List<Display> displays = new ArrayList<>();

                List<?> displayList = groupSection.getList("displays");
                if (displayList != null) {
                    for (Object displayObj : displayList) {
                        if (displayObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> displayMap = (Map<String, Object>) displayObj;

                            String typeStr = (String) displayMap.get("type");
                            DisplayType type = DisplayType.valueOf(typeStr.toUpperCase());

                            double xOffset = displayMap.containsKey("x_offset") ? ((Number) displayMap.get("x_offset")).doubleValue() : 0.0;
                            double yOffset = ((Number) displayMap.get("y_offset")).doubleValue();
                            double zOffset = displayMap.containsKey("z_offset") ? ((Number) displayMap.get("z_offset")).doubleValue() : 0.0;
                            float scale = ((Number) displayMap.get("scale")).floatValue();

                            List<String> lines = null;
                            String material = null;
                            String background = null;
                            Integer brightness = null;
                            Float displayYaw = displayMap.containsKey("yaw") ? ((Number) displayMap.get("yaw")).floatValue() : null;
                            Float displayPitch = displayMap.containsKey("pitch") ? ((Number) displayMap.get("pitch")).floatValue() : null;

                            if (type == DisplayType.TEXT) {
                                lines = (List<String>) displayMap.get("lines");
                                background = (String) displayMap.get("background");
                            } else if (type == DisplayType.ITEM) {
                                material = (String) displayMap.get("material");
                            }

                            // Read brightness if present
                            Object brightnessObj = displayMap.get("brightness");
                            if (brightnessObj != null) {
                                if (brightnessObj instanceof Number) {
                                    brightness = ((Number) brightnessObj).intValue();
                                } else if (brightnessObj instanceof String) {
                                    try {
                                        brightness = Integer.parseInt((String) brightnessObj);
                                    } catch (NumberFormatException e) {
                                        plugin.getLogger().warning("Invalid brightness value: " + brightnessObj);
                                    }
                                }
                            }

                            Display display = new Display(type, xOffset, yOffset, zOffset, scale, lines, material, background, brightness, displayYaw, displayPitch);
                            displays.add(display);
                        }
                    }
                }

                HologramGroup group = new HologramGroup(
                        groupId, world, x, y, z, yaw, pitch, displays
                );
                groups.add(group);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load hologram group '" + groupId + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + groups.size() + " hologram groups");
        return groups;
    }

    public void updateGroupPosition(String groupId, String world, double x, double y, double z, float yaw, float pitch) {
        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        if (groupsSection == null) {
            plugin.getLogger().warning("No groups section found in hologram-groups.yml");
            return;
        }

        ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
        if (groupSection == null) {
            plugin.getLogger().warning("Hologram group '" + groupId + "' not found");
            return;
        }

        groupSection.set("world", world);
        groupSection.set("x", x);
        groupSection.set("y", y);
        groupSection.set("z", z);
        groupSection.set("yaw", yaw);
        groupSection.set("pitch", pitch);

        save();
    }

    /**
     * @return true pokud groupId už existuje v config souboru
     */
    public boolean groupExists(String groupId) {
        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        if (groupsSection == null) {
            return false;
        }
        return groupsSection.contains(groupId);
    }

    /**
     * Zapíše novou skupinu do hologram-groups.yml, pokud groupId ještě neexistuje.
     * Pokud existuje, nic nedělá (existující pozice/úpravy zůstanou zachovány).
     */
    public void createGroupIfMissing(String groupId, String world, double x, double y, double z,
                                     float yaw, float pitch, List<Map<String, Object>> displaysData) {
        if (groupExists(groupId)) {
            return;
        }

        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        if (groupsSection == null) {
            groupsSection = config.createSection("groups");
        }

        ConfigurationSection groupSection = groupsSection.createSection(groupId);
        groupSection.set("world", world);
        groupSection.set("x", x);
        groupSection.set("y", y);
        groupSection.set("z", z);
        groupSection.set("yaw", yaw);
        groupSection.set("pitch", pitch);
        groupSection.set("displays", displaysData);

        save();
        plugin.getLogger().info("Created default hologram group '" + groupId + "' in config");
    }
}