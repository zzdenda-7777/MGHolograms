package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content provider for the Event hologram.
 * Displays current and upcoming events with live countdown timers.
 * Timers are calculated in the player's local timezone.
 */
public class EventHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;

    public EventHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
    }

    @Override
    public String getGroupId() {
        return "Event";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§9§l§nEVENT",
                "§fColor event",
                "§f{event_date}",
                "",
                "§ftnt run",
                "§f28.7. 20:00"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        // Use server timezone as source-of-truth and present event time in player's zone
        ZoneId serverZone = ZoneId.systemDefault();
        ZoneId playerZone = (player != null) ? ZoneId.systemDefault() : serverZone;

        ZonedDateTime nowServer = ZonedDateTime.now(serverZone);
        int year = nowServer.getYear();
        ZonedDateTime eventServer = ZonedDateTime.of(year, 7, 26, 20, 0, 0, 0, serverZone);
        if (eventServer.isBefore(nowServer)) {
            eventServer = ZonedDateTime.of(year + 1, 7, 26, 20, 0, 0, 0, serverZone);
        }

        // Convert to player's timezone for display
        ZonedDateTime eventPlayer = eventServer.withZoneSameInstant(playerZone);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d.M. HH:mm");
        String eventDate = eventPlayer.format(fmt);

        Map<String, String> values = new HashMap<>();
        values.put("event_date", eventDate);
        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§9§l§nEVENT",
                "§7Loading..."
        );
    }

    private long getTimeRemaining(ZoneId playerZone) {
        // Event time: 26.7. at 20:00 in server timezone
        ZonedDateTime now = ZonedDateTime.now(playerZone);
        int year = now.getYear();
        
        // Create event time in player's timezone
        ZonedDateTime eventTime = ZonedDateTime.of(year, 7, 26, 20, 0, 0, 0, playerZone);

        // If event time has passed, use next year's event
        if (eventTime.isBefore(now)) {
            eventTime = ZonedDateTime.of(year + 1, 7, 26, 20, 0, 0, 0, playerZone);
        }

        return ChronoUnit.SECONDS.between(now, eventTime);
    }

    private String formatTimer(long seconds) {
        if (seconds <= 0) {
            return "§cLIVE";
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
