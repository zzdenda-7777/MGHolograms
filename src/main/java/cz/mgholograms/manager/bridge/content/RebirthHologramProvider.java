package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.artifacts.ArtifactManager;
import multigainer.multigainer.artifacts.ArtifactType;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.rebirth.RebirthManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Rebirth hologram.
 * Displays how many rebirth points the player would receive right now,
 * their current rebirth points, and their current rebirth money multiplier.
 * <p>
 * Formula/sources match RebirthGUI.java exactly:
 * - potential points on rebirth = RebirthManager.calculateRebirthPoints(profile.getMoney())
 *                                  .multiply(new BigNumber(ArtifactManager.getMultiplierDouble(profile, ArtifactType.REBIRTH_POINTS)))
 * - current rebirth points       = profile.getRebirthPoints()
 * - current rebirth multiplier   = RebirthManager.calculateMoneyMultiplier(profile.getRebirthPoints())
 * <p>
 * Colors: §5 (dark purple) as the main rebirth color, §d (light purple / pink)
 * as the secondary accent, matching the in-game rebirth color scheme (RebirthGUI).
 * <p>
 * Wording is a template read from hologram-groups.yml (group "Rebirth",
 * first TEXT display's "lines") with {placeholder} tokens - see
 * {@link #getDefaultTemplate()}. Edit config + /holoreload to change wording
 * without touching Java code.
 */
public class RebirthHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public RebirthHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
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
        return "Rebirth";
    }

    /**
     * Placeholders: {points_on_rebirth}, {rebirth_multi}, {rebirth_points}.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§5§lREBIRTH",
                "",
                "§fYou will get §5",
                "{points_on_rebirth} §frebirth points",
                "§fYour rebirth multi §5{rebirth_multi}§fx",
                "§fRebirth points §5{rebirth_points}"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        RebirthData data = getRebirthDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        values.put("points_on_rebirth", NumberFormatter.format(data.pointsOnRebirth, playerUuid));
        values.put("rebirth_multi", NumberFormatter.format(data.rebirthMulti, playerUuid));
        values.put("rebirth_points", NumberFormatter.format(data.rebirthPoints, playerUuid));

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§5§lRebirth",
                "§7Loading..."
        );
    }

    /**
     * Reads Rebirth data from MultiGainer player profile.
     * Returns null if profile is not yet loaded.
     */
    private RebirthData getRebirthDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Rebirth] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Rebirth] Profile is null for " + uuid);
            return null;
        }

        try {
            BigNumber rebirthPoints = profile.getRebirthPoints();
            BigNumber rebirthMulti = RebirthManager.calculateMoneyMultiplier(rebirthPoints);

            BigNumber pointsOnRebirth = RebirthManager.calculateRebirthPoints(profile.getMoney())
                    .multiply(new BigNumber(ArtifactManager.getMultiplierDouble(profile, ArtifactType.REBIRTH_POINTS)));

            return new RebirthData(pointsOnRebirth, rebirthPoints, rebirthMulti);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get rebirth data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static class RebirthData {
        BigNumber pointsOnRebirth;
        BigNumber rebirthPoints;
        BigNumber rebirthMulti;

        RebirthData(BigNumber pointsOnRebirth, BigNumber rebirthPoints, BigNumber rebirthMulti) {
            this.pointsOnRebirth = pointsOnRebirth;
            this.rebirthPoints = rebirthPoints;
            this.rebirthMulti = rebirthMulti;
        }
    }
}