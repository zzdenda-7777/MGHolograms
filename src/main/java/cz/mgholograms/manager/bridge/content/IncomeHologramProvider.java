package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
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
 */
public class IncomeHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public IncomeHologramProvider(MGHolograms plugin) {
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

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        IncomeData data = getIncomeDataFor(playerUuid);
        if (data == null) {
            return null;
        }

        return List.of(
                "&#39FF14&lINCOME",
                "&#B9FF00&l―&#C8FF34&l―&#D6FF68&l―&#B0C870&l―&#8EA84A&l―&#6C8823&l―&#4F621B&l―&#5E751F&l―&#6C8823&l―&#8EA84A&l―&#B0C870&l―&#D6FF68&l―&#C8FF34&l―&#B9FF00&l―",
                "",
                "&#7CFC00&lIncome §f" + NumberFormatter.format(data.incomePerSecond, playerUuid) + " &#8FCB6B/s",
                "&#7CFC00&lMulti §f" + NumberFormatter.format(data.moneyMultiplier, playerUuid) + "x",
                "&#7CFC00&lBalance §f" + NumberFormatter.format(data.money, playerUuid),
                "&#B9FF00&l―&#C8FF34&l―&#D6FF68&l―&#B0C870&l―&#8EA84A&l―&#6C8823&l―&#4F621B&l―&#5E751F&l―&#6C8823&l―&#8EA84A&l―&#B0C870&l―&#D6FF68&l―&#C8FF34&l―&#B9FF00&l―"
        );
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