package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Giveaway hologram.
 * Displays giveaway information and live countdown timer.
 * Timer is calculated in the player's local timezone.
 */
public class GiveawayHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;

    public GiveawayHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
    }

    @Override
    public String getGroupId() {
        return "GIVEAWAY";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§5§lGIVEAWAY",
                "§f1 Pro GainerPass",
                "§ftotal 3 winners",
                "§f{giveaway_date}",
                "{giveaway_timer_line}"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        ZoneId serverZone = ZoneId.systemDefault();
        ZoneId playerZone = (player != null) ? ZoneId.systemDefault() : serverZone;

        ZonedDateTime now = ZonedDateTime.now(serverZone);
        ZonedDateTime giveawayEnd = now.plusDays(10);

        // Display giveaway end time in player's timezone
        ZonedDateTime giveawayPlayer = giveawayEnd.withZoneSameInstant(playerZone);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d.M. HH:mm");
        String giveawayDate = giveawayPlayer.format(fmt);

        long seconds = ChronoUnit.SECONDS.between(ZonedDateTime.now(playerZone), giveawayEnd.withZoneSameInstant(playerZone));
        String timerStr = formatTimer(seconds);

        Map<String, String> values = new HashMap<>();
        values.put("giveaway_date", giveawayDate);
        // If timer reached zero or negative, show END line instead of "End in X"
        if (seconds <= 0) {
            values.put("giveaway_timer_line", "§cEND");
        } else {
            values.put("giveaway_timer_line", "§fEnd in §5" + timerStr);
        }
        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§5§lGIVEAWAY",
                "§7Loading..."
        );
    }

    private long getTimeRemaining(ZoneId playerZone) {
        // Giveaway ends in 10 days from now in player's timezone
        ZonedDateTime now = ZonedDateTime.now(playerZone);
        ZonedDateTime giveawayEnd = now.plusDays(10);
        return ChronoUnit.SECONDS.between(now, giveawayEnd);
    }

    private String formatTimer(long seconds) {
        if (seconds <= 0) {
            return "§cENDED";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
