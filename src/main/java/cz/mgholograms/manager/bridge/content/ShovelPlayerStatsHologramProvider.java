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
 * Content provider for the ShovelPlayerStats hologram.
 * Shows lifetime shovel statistics including total blocks destroyed and treasures found.
 */
public class ShovelPlayerStatsHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public ShovelPlayerStatsHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
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
        return "ShovelPlayerStats";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§6§l§nSHOVEL PLAYER STATS",
                "§8§m                    ",
                "§fTotal Blocks Destroyed: §6{lifetime_total_blocks_str}",
                "§fTotal Blocks (raw): §6{lifetime_total_blocks_raw}",
                "§8§m                    ",
                "§fTotal Treasure Found: §6{lifetime_total_treasure_str}",
                "§fTotal Treasure (raw): §6{lifetime_total_treasure_raw}",
                "§8§m                    ",
                "§7§oLifetime Statistics"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        PlayerStatsData data = getPlayerStatsDataFor(playerUuid);
        if (data == null) {
            return getDefaultTemplate();
        }

        Map<String, String> values = new HashMap<>();
        values.put("lifetime_total_blocks_str", data.lifetimeTotalBlocksStr);
        values.put("lifetime_total_blocks_raw", data.lifetimeTotalBlocksRaw);
        values.put("lifetime_total_treasure_str", data.lifetimeTotalTreasureStr);
        values.put("lifetime_total_treasure_raw", data.lifetimeTotalTreasureRaw);

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        Map<String, String> values = new HashMap<>();
        values.put("lifetime_total_blocks_str", "&7-");
        values.put("lifetime_total_blocks_raw", "&7-");
        values.put("lifetime_total_treasure_str", "&7-");
        values.put("lifetime_total_treasure_raw", "&7-");
        return render(values);
    }

    @Override
    public java.util.List<java.util.List<String>> getLinesPerDisplay(java.util.UUID playerUuid, org.bukkit.entity.Player player) {
        PlayerStatsData data = getPlayerStatsDataFor(playerUuid);
        Map<String, String> values = new HashMap<>();
        if (data != null) {
            values.put("lifetime_total_blocks_str", data.lifetimeTotalBlocksStr);
            values.put("lifetime_total_blocks_raw", data.lifetimeTotalBlocksRaw);
            values.put("lifetime_total_treasure_str", data.lifetimeTotalTreasureStr);
            values.put("lifetime_total_treasure_raw", data.lifetimeTotalTreasureRaw);
        }
        return renderPerDisplay(values);
    }

    @Override
    public java.util.List<java.util.List<String>> getLoadingLinesPerDisplay() {
        Map<String, String> values = new HashMap<>();
        values.put("lifetime_total_blocks_str", "&7-");
        values.put("lifetime_total_blocks_raw", "&7-");
        values.put("lifetime_total_treasure_str", "&7-");
        values.put("lifetime_total_treasure_raw", "&7-");
        return renderPerDisplay(values);
    }

    private PlayerStatsData getPlayerStatsDataFor(UUID uuid) {
        if (multigainer == null) {
            return null;
        }

        try {
            ShovelManager shovelManager = ShovelManager.getInstance();
            if (shovelManager == null) {
                return null;
            }

            String lifetimeTotalBlocksStr = shovelManager.getLifetimeTotalBlocksStr();
            String lifetimeTotalBlocksRaw = String.valueOf(shovelManager.getLifetimeTotalBlocksRaw());
            String lifetimeTotalTreasureStr = shovelManager.getLifetimeTotalTreasureStr();
            String lifetimeTotalTreasureRaw = String.valueOf(shovelManager.getLifetimeTotalTreasureRaw());

            return new PlayerStatsData(lifetimeTotalBlocksStr, lifetimeTotalBlocksRaw, 
                                    lifetimeTotalTreasureStr, lifetimeTotalTreasureRaw);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get player stats for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private static class PlayerStatsData {
        final String lifetimeTotalBlocksStr;
        final String lifetimeTotalBlocksRaw;
        final String lifetimeTotalTreasureStr;
        final String lifetimeTotalTreasureRaw;

        PlayerStatsData(String lifetimeTotalBlocksStr, String lifetimeTotalBlocksRaw,
                       String lifetimeTotalTreasureStr, String lifetimeTotalTreasureRaw) {
            this.lifetimeTotalBlocksStr = lifetimeTotalBlocksStr;
            this.lifetimeTotalBlocksRaw = lifetimeTotalBlocksRaw;
            this.lifetimeTotalTreasureStr = lifetimeTotalTreasureStr;
            this.lifetimeTotalTreasureRaw = lifetimeTotalTreasureRaw;
        }
    }
}