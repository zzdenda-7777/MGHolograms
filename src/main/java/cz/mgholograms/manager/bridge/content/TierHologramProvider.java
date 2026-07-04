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
                "§b§lTIER",
                "&#55FFFF&l―&#4DE8E8&l―&#45D6D6&l―&#3AB8B8&l―&#2E9999&l―&#227A7A&l―&#156060&l―&#1D6B6B&l―&#227A7A&l―&#2E9999&l―&#3AB8B8&l―&#45D6D6&l―&#4DE8E8&l―&#55FFFF&l―",
                "",
                "§3Current Tier §f" + data.tier,
                "§3Tier Points §f" + NumberFormatter.format(new BigNumber(data.tierPointsTotal)),
                "§3Progress §f" + NumberFormatter.format(data.curPoints) + " §7/§f " + NumberFormatter.format(data.nextCost),
                ""
        ));

        if (canTierUp) {
            lines.set(6, "&#9DFD3A&lY&#ACFC56&lO&#BAFB73&lU &#CBF997&lC&#CDF89D&lA&#CEF7A4&lN &#CFF7A5&lT&#CDF8A0&lI&#CCF89A&lE&#CAF995&lR &#BAFB73&lU&#ACFC56&lP");
        }

        lines.add("&#55FFFF&l―&#4DE8E8&l―&#45D6D6&l―&#3AB8B8&l―&#2E9999&l―&#227A7A&l―&#156060&l―&#1D6B6B&l―&#227A7A&l―&#2E9999&l―&#3AB8B8&l―&#45D6D6&l―&#4DE8E8&l―&#55FFFF&l―");

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