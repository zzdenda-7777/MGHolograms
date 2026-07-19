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
 * Displays worker level, XP, production rate, and total energy.
 * <p>
 * Wording is a template read from hologram-groups.yml (group "Production",
 * first TEXT display's "lines") with {placeholder} tokens - see
 * {@link #getDefaultTemplate()}. Edit config + /holoreload to change wording
 * without touching Java code.
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
     * {stored_energy}.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§e§lPRODUCTION",
                "",
                "§fYour level §e{level}",
                "§fYour Worker's XP §e{work_xp} §f/ §e{xp_for_next}",
                "§fYour production rate §e{energy_per_min} §f/min",
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