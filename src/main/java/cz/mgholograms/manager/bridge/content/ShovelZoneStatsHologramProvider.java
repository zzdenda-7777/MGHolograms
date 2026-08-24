package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.shoveling.ShovelManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the ShovelZoneStats holograms.
 * Shows zone-specific block destruction progress for each of the 5 zones.
 */
public class ShovelZoneStatsHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private final int zoneNumber;
    private Multigainer multigainer;

    public ShovelZoneStatsHologramProvider(MGHolograms plugin, HologramManager hologramManager, int zoneNumber) {
        super(hologramManager);
        this.plugin = plugin;
        this.zoneNumber = zoneNumber;
        hookMultigainer();
    }

    private void hookMultigainer() {
        Plugin found = org.bukkit.Bukkit.getPluginManager().getPlugin("multigainer");
        if (found instanceof Multigainer mg) {
            this.multigainer = mg;
        } else {
            this.multigainer = null;
        }
    }

    public Multigainer getMultigainer() {
        return multigainer;
    }

    @Override
    public String getGroupId() {
        return "ShovelZoneStats" + zoneNumber;
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§6§l§nSHOVEL ZONE " + zoneNumber + " STATS",
                "§8§m                    ",
                "§fBlocks Destroyed: §6{zone" + zoneNumber + "_blocks_line}",
                "§fRaw destroyed count: §6{zone" + zoneNumber + "_current_destroyed}",
                "§8§m                    ",
                "§7§oZone " + zoneNumber + " Progress"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        ZoneStatsData data = getZoneStatsDataFor(playerUuid);
        if (data == null) {
            return getDefaultTemplate();
        }

        Map<String, String> values = new HashMap<>();
        values.put("zone" + zoneNumber + "_blocks_line", data.blocksLine);
        values.put("zone" + zoneNumber + "_current_destroyed", data.currentDestroyed);

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        Map<String, String> values = new HashMap<>();
        values.put("zone" + zoneNumber + "_blocks_line", "&7-");
        values.put("zone" + zoneNumber + "_current_destroyed", "&7-");
        return render(values);
    }

    @Override
    public java.util.List<java.util.List<String>> getLinesPerDisplay(java.util.UUID playerUuid, org.bukkit.entity.Player player) {
        ZoneStatsData data = getZoneStatsDataFor(playerUuid);
        Map<String, String> values = new HashMap<>();
        if (data != null) {
            values.put("zone" + zoneNumber + "_blocks_line", data.blocksLine);
            values.put("zone" + zoneNumber + "_current_destroyed", data.currentDestroyed);
        }
        return renderPerDisplay(values);
    }

    @Override
    public java.util.List<java.util.List<String>> getLoadingLinesPerDisplay() {
        Map<String, String> values = new HashMap<>();
        values.put("zone" + zoneNumber + "_blocks_line", "&7-");
        values.put("zone" + zoneNumber + "_current_destroyed", "&7-");
        return renderPerDisplay(values);
    }

    private ZoneStatsData getZoneStatsDataFor(UUID uuid) {
        if (multigainer == null) {
            return null;
        }

        try {
            ShovelManager shovelManager = ShovelManager.getInstance();
            if (shovelManager == null) {
                return null;
            }

            String blocksLine = shovelManager.getZoneBlocksLine(zoneNumber);
            String currentDestroyed = String.valueOf(shovelManager.getZoneCurrentDestroyed(zoneNumber));

            return new ZoneStatsData(blocksLine, currentDestroyed);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get zone stats for zone " + zoneNumber + ": " + e.getMessage());
            return null;
        }
    }

    private static class ZoneStatsData {
        final String blocksLine;
        final String currentDestroyed;

        ZoneStatsData(String blocksLine, String currentDestroyed) {
            this.blocksLine = blocksLine;
            this.currentDestroyed = currentDestroyed;
        }
    }
}