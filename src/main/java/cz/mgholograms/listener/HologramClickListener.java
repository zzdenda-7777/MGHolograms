package cz.mgholograms.listener;

import cz.mgholograms.MGHolograms;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class HologramClickListener implements Listener {
    private final MGHolograms plugin;

    public HologramClickListener(MGHolograms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT")) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Musíš koukat na hologram (line-of-sight)
        if (player.hasLineOfSight(plugin.getHologramBridge().getTemplateLocation())) {
            openProductionGUI(player);
        }
    }

    private void openProductionGUI(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.performCommand("production");
        });
    }
}




