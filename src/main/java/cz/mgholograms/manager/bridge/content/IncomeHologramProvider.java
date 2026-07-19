package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Income hologram.
 * Displays current money/second (passive income), current total money
 * multiplier, and total balance.
 * <p>
 * incomePerSecond and moneyMultiplier are set every second in
 * IncomeManager#startPassiveIncomeTask() right after totalEarned/allMulti
 * are calculated - see multigainer.multigainer.income.IncomeManager:
 * <pre>
 *   profile.setLastIncomePerSecond(totalEarned);
 *   profile.setLastMoneyMultiplier(allMulti);
 * </pre>
 * <p>
 * NumberFormatter.format() takes (BigNumber value, UUID uid) - confirmed from
 * RebirthGUI.java / RebirthListener.java usage.
 * <p>
 * Wording is a template read from hologram-groups.yml (group "Income", first
 * TEXT display's "lines") with {placeholder} tokens - see
 * {@link #getDefaultTemplate()}. Edit config + /holoreload to change wording
 * without touching Java code.
 */
public class IncomeHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public IncomeHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
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
        return "Income";
    }

    /**
     * Placeholders: {income_per_second}, {money_multiplier}, {balance}.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "&#39FF14&lINCOME",
                "",
                "§fYour income is §a{income_per_second} §f/s",
                "§fYour total multi is §a{money_multiplier}x",
                "§fYour balance is §a{balance}",
                "",
                "§7 -- §7§oMoney is currency for buying §7--",
                "§7§o/upgrades and /rebirth"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        IncomeData data = getIncomeDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        Map<String, String> values = new HashMap<>();
        values.put("income_per_second", NumberFormatter.format(data.incomePerSecond, playerUuid));
        values.put("money_multiplier", NumberFormatter.format(data.moneyMultiplier, playerUuid));
        values.put("balance", NumberFormatter.format(data.money, playerUuid));

        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "&#9DFD3A&l&#84FC33&li&#6BFB2D&ln&#52FA26&lc&#3AF920&lo&#52FA26&lm&#6BFB2D&le",
                "§7Loading..."
        );
    }

    /**
     * Reads Income data from MultiGainer player profile.
     * Returns null if profile is not yet loaded.
     */
    private IncomeData getIncomeDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Income] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Income] Profile is null for " + uuid);
            return null;
        }

        try {
            BigNumber incomePerSecond = profile.getLastIncomePerSecond();
            BigNumber money = profile.getMoney();
            BigNumber moneyMultiplier = profile.getLastMoneyMultiplier();

            return new IncomeData(incomePerSecond, money, moneyMultiplier);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get income data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static class IncomeData {
        BigNumber incomePerSecond;
        BigNumber money;
        BigNumber moneyMultiplier;

        IncomeData(BigNumber incomePerSecond, BigNumber money, BigNumber moneyMultiplier) {
            this.incomePerSecond = incomePerSecond;
            this.money = money;
            this.moneyMultiplier = moneyMultiplier;
        }
    }
}