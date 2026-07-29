package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.tier.TierManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Tier hologram.
 * Displays current tier, tier points, and progress to next tier.
 * <p>
 * Wording is a template read from hologram-groups.yml (group "Tier", first
 * TEXT display's "lines") with {placeholder} tokens - see
 * {@link #getDefaultTemplate()}. Edit config + /holoreload to change wording
 * without touching Java code.
 */
public class TierHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public TierHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
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
        return "Tier";
    }

    /**
     * Placeholders: {tier}, {tier_points}, {progress_cur}, {progress_next},
     * {tier_up} (renders to the "you can tier up" message, or empty string).
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§b§lTIER",
                "§fCurrent Tier §3{tier}",
                "§fTier Points §3{tier_points}",
                "§fProgress §3{progress_cur} §7/§3 {progress_next}",
                "{tier_up}"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        TierData data = getTierDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        // BigNumber má vlastní compareTo() (implementuje Comparable<BigNumber>) -
        // nelze použít >= přímo jako u double/primitiv.
        boolean canTierUp = data.curPoints.compareTo(data.nextCost) >= 0;

        Map<String, String> values = new HashMap<>();
        values.put("tier", String.valueOf(data.tier));
        values.put("tier_points", NumberFormatter.format(new BigNumber(data.tierPointsTotal)));
        values.put("progress_cur", NumberFormatter.format(data.curPoints));
        values.put("progress_next", NumberFormatter.format(data.nextCost));
        values.put("tier_up", canTierUp ? "§8>§7> §3§oYOU CAN TIER UP! §7<§8<" : "");

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§fLoading..."
        );
    }

    /**
     * Reads Tier data from MultiGainer player profile.
     * Returns null if profile is not yet loaded.
     */
    private TierData getTierDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Tier] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Tier] Profile is null for " + uuid);
            return null;
        }

        try {
            int tier = profile.getTier();
            // POZN: PlayerProfile používá BigNumber pro hodnoty, co mohou
            // narůst hodně vysoko (rebirth/tier points i next-tier cost),
            // takže tu necháváme BigNumber po celou cestu místo balení
            // do double - NumberFormatter to umí formátovat přímo.
            BigNumber nextCost = TierManager.getCostForTierBig(tier + 1);
            BigNumber curPoints = profile.getRebirthPoints();
            int tierPointsTotal = (int)profile.getTierPoints();

            return new TierData(tier, nextCost, curPoints, tierPointsTotal);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get tier data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static class TierData {
        int tier;
        BigNumber nextCost;
        BigNumber curPoints;
        int tierPointsTotal;

        TierData(int tier, BigNumber nextCost, BigNumber curPoints, int tierPointsTotal) {
            this.tier = tier;
            this.nextCost = nextCost;
            this.curPoints = curPoints;
            this.tierPointsTotal = tierPointsTotal;
        }
    }
}