package cz.mgholograms.listener;

import cz.mgholograms.util.PlayerJoinTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Records when each player joins/leaves, so PlayerHologramEngine and
 * StaticGroupEngine can skip a player's first hologram check for a couple
 * of seconds after they log in.
 */
public class PlayerJoinTrackerListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerJoinTracker.markJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerJoinTracker.clear(event.getPlayer().getUniqueId());
    }
}
