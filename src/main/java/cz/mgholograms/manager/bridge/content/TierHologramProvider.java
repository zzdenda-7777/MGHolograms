package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.tier.TierManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * Content provider for the Tier hologram.
 * Displays current tier, tier points, and progress to next tier.
 */
public class TierHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public TierHologramProvider(MGHolograms plugin) {
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

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        TierData data = getTierDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        // BigNumber má vlastní compareTo() (implementuje Comparable<BigNumber>) -
        // nelze použít >= přímo jako u double/primitiv.
        boolean canTierUp = data.curPoints.compareTo(data.nextCost) >= 0;

        java.util.ArrayList<String> lines = new java.util.ArrayList<>(List.of(
                "&#FFD466&lTIER ADVANCEMENT",
                "&#FFB900&l―&#FFC834&l―&#FFD668&l―&#C8B070&l―&#A88E4A&l―&#886C23&l―&#624F1B&l―&#755E1F&l―&#886C23&l―&#A88E4A&l―&#C8B070&l―&#FFD668&l―&#FFC834&l―&#FFB900&l―",
                "",
                "&#E8C97A§lCurrent Tier §f" + data.tier,
                "&#E8C97A§lTier Points §d" + NumberFormatter.format(new BigNumber(data.tierPointsTotal)),
                "&#E8C97A§lProgress §b" + NumberFormatter.format(data.curPoints) + " §7/§6 " + NumberFormatter.format(data.nextCost),
                ""
        ));

        if (canTierUp) {
            lines.set(6, "&#55FF55&l✔ YOU CAN TIER UP!");
        }

        lines.add("&#FFB900&l―&#FFC834&l―&#FFD668&l―&#C8B070&l―&#A88E4A&l―&#886C23&l―&#624F1B&l―&#755E1F&l―&#886C23&l―&#A88E4A&l―&#C8B070&l―&#FFD668&l―&#FFC834&l―&#FFB900&l―");

        return lines;
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "&#E9C463&l&#CFAE58&lt&#B5984D&li&#9B8241&le&#816C36&lr&#967E3F&#AB8F48&#BFA151&#D4B25A&#E9C463",
                "§7Loading..."
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
            int tierPointsTotal = profile.getTierPoints();

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