package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.production.ProductionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * Content provider for the Production hologram.
 * Displays worker level, XP, production rate, and total energy.
 */
public class ProductionHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public ProductionHologramProvider(MGHolograms plugin) {
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
        return "Production";
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        ProductionData data = getProductionDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        return List.of(
                "&#FFBA00&lPRODUCTION",
                "&#FFB900&l―&#FFC834&l―&#FFD668&l―&#C8B070&l―&#A88E4A&l―&#886C23&l―&#624F1B&l―&#755E1F&l―&#886C23&l―&#A88E4A&l―&#C8B070&l―&#FFD668&l―&#FFC834&l―&#FFB900&l―",
                "",
                "&#E8C97A§lLevel §f" + data.level,
                "&#E8C97A§lWorker XP §f" + NumberFormatter.format(new BigNumber(data.workXp)) + " §7/ §f" + NumberFormatter.format(new BigNumber(data.xpForNext)),
                "&#E8C97A§lProduction §f" + NumberFormatter.format(new BigNumber(data.energyPerMin)) + " &#C9A85C/min",
                "&#FF0000R&#BC0000E&#790000Q&#690B0B: &#4A2121T&#5A1616I&#690B0BE&#790000R &#FF00003",
                "&#FFB900&l―&#FFC834&l―&#FFD668&l―&#C8B070&l―&#A88E4A&l―&#886C23&l―&#624F1B&l―&#755E1F&l―&#886C23&l―&#A88E4A&l―&#C8B070&l―&#FFD668&l―&#FFC834&l―&#FFB900&l―",
                "&#FFBA00&lTOTAL ENERGY",
                "&#FFD466&l⚡ §f" + NumberFormatter.format(new BigNumber(data.storedEnergy)) + " &#FFD466&l⚡"
        );
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "&#E9C463&l&#CFAE58&lr&#B5984D&lo&#9B8241&ld&#816C36&lu&#967E3F&lc&#AB8F48&lt&#BFA151&li&#D4B25A&lo&#E9C463&ln"
        );
    }

    /**
     * Reads Production data from MultiGainer player profile.
     * Returns null if profile is not yet loaded.
     */
    private ProductionData getProductionDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Production] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Production] Profile is null for " + uuid);
            return null;
        }

        try {
            int level = profile.getWorkerLevel();
            double workXp = profile.getWorkerXp();
            double xpForNext = ProductionManager.getXpForNextLevel(level);
            double energyPerMin = ProductionManager.getEnergyPerMinute(level);
            double storedEnergy = profile.getWorkerEnergy();

            return new ProductionData(level, workXp, xpForNext, energyPerMin, storedEnergy);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get production data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static class ProductionData {
        int level;
        double workXp;
        double xpForNext;
        double energyPerMin;
        double storedEnergy;

        ProductionData(int level, double workXp, double xpForNext, double energyPerMin, double storedEnergy) {
            this.level = level;
            this.workXp = workXp;
            this.xpForNext = xpForNext;
            this.energyPerMin = energyPerMin;
            this.storedEnergy = storedEnergy;
        }
    }
}