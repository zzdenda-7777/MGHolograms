package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.production.ProductionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Production hologram.
 * Displays worker level, XP, production rate (with multipliers), and total energy.
 */
public class ProductionHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public ProductionHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
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
        return "Production";
    }

    /**
     * Placeholders: {level}, {work_xp}, {xp_for_next}, {energy_per_min},
     * {stored_energy}, {multiplier}.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§e§lPRODUCTION",
                "",
                "§fYour level §e{level}",
                "§fYour Worker's XP §e{work_xp} §f/ §e{xp_for_next}",
                "§fYour production rate §e{energy_per_min} §f/min §7(x{multiplier})",
                "§f§lTotal Energy §f{stored_energy} §e§l⚡",
                "",
                "§4§oRequires at least TIER 3"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        ProductionData data = getProductionDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        values.put("level", String.valueOf(data.level));
        values.put("work_xp", NumberFormatter.format(new BigNumber(data.workXp)));
        values.put("xp_for_next", NumberFormatter.format(new BigNumber(data.xpForNext)));
        values.put("energy_per_min", NumberFormatter.format(new BigNumber(data.energyPerMin)));
        values.put("stored_energy", NumberFormatter.format(new BigNumber(data.storedEnergy)));

        // Zobrazí celkový násobič např. jako "3.39"
        values.put("multiplier", String.format("%.2f", data.multiplier));

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§e§lPRODUCTION",
                "§7Loading..."
        );
    }

    /**
     * Reads Production data from MultiGainer player profile.
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

            // 1. Získání základní produkce
            double baseEnergy = ProductionManager.getEnergyPerMinute(level);

            // 2. Získání PLNÉ produkce (včetně všech multiplierů)
            double fullEnergyPerMin = ProductionManager.getFullEnergyPerMinute(profile);

            // 3. Výpočet samotného násobiče (pro nový placeholder {multiplier})
            double multiplier = baseEnergy > 0 ? (fullEnergyPerMin / baseEnergy) : 1.0;

            double storedEnergy = profile.getWorkerEnergy();

            return new ProductionData(level, workXp, xpForNext, fullEnergyPerMin, storedEnergy, multiplier);
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
        double multiplier;

        ProductionData(int level, double workXp, double xpForNext, double energyPerMin, double storedEnergy, double multiplier) {
            this.level = level;
            this.workXp = workXp;
            this.xpForNext = xpForNext;
            this.energyPerMin = energyPerMin;
            this.storedEnergy = storedEnergy;
            this.multiplier = multiplier;
        }
    }
}