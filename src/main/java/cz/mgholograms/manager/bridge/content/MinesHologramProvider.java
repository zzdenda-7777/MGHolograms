package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.listeners.MiningListener;
import multigainer.multigainer.tools.PickaxeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the "Mines" (Personal Mine) hologram.
 * <p>
 * Backs the group "Mines" in hologram-groups.yml, which has 6 TEXT display
 * blocks (in this order): name, "Multipliers" label (static), separator
 * (static), gems/xp line, separator (static), next-mine/requirement lines,
 * footer (static). Only the dynamic blocks (0, 3, 5) are actually
 * recalculated - see {@link #getDynamicDisplayIndices()}.
 * <p>
 * Placeholders (see {@link #getDefaultTemplate()}):
 * {mine_name}          - colored name of the player's active personal mine block, e.g. "&#D97C5ECOPPER MINES"
 * {mine_gems}           - colored gems multiplier of the active block, e.g. "&#D97C5E4x"
 * {mine_xp}             - colored xp multiplier of the active block, e.g. "&#D97C5E3x"
 * {next_mine_line}      - "Next mine" or, once unlocked, "You can buy next mine"
 * {next_mine_req_line}  - lock icon + pickaxe-tier/tier requirement checklist for the next block
 */
public class MinesHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    // Same order as PickaxeManager.BLOCKS / MiningListener.BLOCK_TIER_REQUIREMENTS
    private static final String[] MINE_NAMES = {
            "COBBLESTONE MINES", "COBBLED DEEPSLATE MINES", "COPPER MINES", "COPPER DEEPSLATE MINES",
            "COAL MINES", "COAL DEEPSLATE MINES", "IRON MINES", "IRON DEEPSLATE MINES",
            "REDSTONE MINES", "REDSTONE DEEPSLATE MINES", "LAPIS MINES", "LAPIS DEEPSLATE MINES",
            "GOLD MINES", "GOLD DEEPSLATE MINES", "DIAMOND MINES", "REINFORCED DIAMOND MINES", "NETHERITE MINES"
    };

    private static final String[] MINE_HEX = {
            "A3A3A3", "7F7F7F", "D97C5E", "4B5320",
            "222222", "1A1A1A", "D8D8D8", "505050",
            "FF2222", "990000", "1B3984", "0D1B42",
            "E5C158", "B89728", "55FFFF", "00AAAA", "1A1A1A"
    };

    public MinesHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
        hookMultigainer();
    }

    public Multigainer getMultigainer() {
        return multigainer;
    }

    private void hookMultigainer() {
        Plugin found = org.bukkit.Bukkit.getPluginManager().getPlugin("multigainer");
        if (found instanceof Multigainer mg) {
            this.multigainer = mg;
        } else {
            this.multigainer = null;
        }
    }

    @Override
    public String getGroupId() {
        return "Mines";
    }

    /**
     * Only display blocks 0 (name), 3 (gems/xp) and 5 (next mine + requirements)
     * are ever dynamic. 1, 2, 4, 6 are static labels/separators/footer.
     */
    @Override
    public java.util.Set<Integer> getDynamicDisplayIndices() {
        return java.util.Set.of(0, 3, 5);
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "{mine_name}",
                "&fMultipliers",
                "&8§m                    ",
                "&7 Gems: {mine_gems} ",
                "&7 XP: {mine_xp} ",
                "&8§m                    ",
                "{next_mine_line}",
                "{next_mine_req_line}",
                "&7§oChange your Mines in &f§l/mines"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        MinesData data = getMinesDataFor(playerUuid);
        if (data == null) return null;
        return render(data.values);
    }

    @Override
    public List<List<String>> getLinesPerDisplay(UUID playerUuid, Player player) {
        MinesData data = getMinesDataFor(playerUuid);
        if (data == null) return null;
        return renderPerDisplay(data.values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of("§fLoading...");
    }

    @Override
    public List<List<String>> getLoadingLinesPerDisplay() {
        Map<String, String> values = new HashMap<>();
        values.put("mine_name", "&7Loading...");
        values.put("mine_gems", "&7-");
        values.put("mine_xp", "&7-");
        values.put("next_mine_line", "");
        values.put("next_mine_req_line", "");
        return renderPerDisplay(values);
    }

    // ── Data pull from Multigainer ──────────────────────────────────────────

    private MinesData getMinesDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Mines] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Mines] Profile is null for " + uuid);
            return null;
        }

        try {
            int index = profile.getPersonalMineBlock();
            if (index < 0 || index >= PickaxeManager.BLOCKS.length) index = 0;

            String hex = MINE_HEX[index];
            String name = MINE_NAMES[index];

            Material blockMat = PickaxeManager.BLOCKS[index];
            double gemsMulti = MiningListener.getBlockGemsMultiplier(blockMat);
            double xpMulti = MiningListener.getBlockXpMultiplier(blockMat);

            Map<String, String> values = new HashMap<>();
            values.put("mine_name", "&#" + hex + name);
            values.put("mine_gems", "&#" + hex + formatMulti(gemsMulti) + "x");
            values.put("mine_xp", "&#" + hex + formatMulti(xpMulti) + "x");

            int nextIndex = index + 1;
            if (nextIndex >= PickaxeManager.BLOCKS.length) {
                // Already on the last (Netherite) mine - nothing further to unlock.
                values.put("next_mine_line", "§a§l✔ §aMax mine unlocked!");
                values.put("next_mine_req_line", "");
            } else {
                int requiredPickaxeTier = PickaxeManager.getMinTierForBlock(nextIndex);
                String requiredPickaxeName = PickaxeManager.TIER_NAMES[requiredPickaxeTier] + " Pickaxe";
                boolean pickaxeMet = profile.getPickaxeTier() >= requiredPickaxeTier;

                int requiredTier = MiningListener.BLOCK_TIER_REQUIREMENTS[nextIndex];
                boolean tierMet = profile.getTier() >= requiredTier;

                boolean bothMet = pickaxeMet && tierMet;

                values.put("next_mine_line", bothMet
                        ? "§a§l✔ §aYou can buy next mine"
                        : "§fNext mine");

                String lockIcon = bothMet ? "§a§l🔓" : "§c§l🔒";
                String pickaxeCheck = pickaxeMet ? "§a§l✔" : "§c§l✖";
                String tierCheck = tierMet ? "§a§l✔" : "§c§l✖";

                values.put("next_mine_req_line", lockIcon + "§f§c Req: "
                        + pickaxeCheck + " §f" + requiredPickaxeName
                        + " &  " + tierCheck + " §fTier " + requiredTier);
            }

            return new MinesData(values);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get mines data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String formatMulti(double v) {
        if (v >= 1_000_000) return trimZero(v / 1_000_000) + "M";
        if (v >= 1_000) return trimZero(v / 1_000) + "K";
        return trimZero(v);
    }

    private static String trimZero(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }

    private static class MinesData {
        final Map<String, String> values;
        MinesData(Map<String, String> values) { this.values = values; }
    }
}