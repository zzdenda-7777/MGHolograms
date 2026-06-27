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
        org.bukkit.Location hologramLoc = plugin.getHologramBridge().getTemplateLocation();
        
        if (hologramLoc == null) {
            return;
        }
        
        // Musíš být v MC reach vzdálenosti (cca 4.5 bloku) a koukat na hologram
        double distance = player.getLocation().distance(hologramLoc);
        if (distance <= 4.5 && player.hasLineOfSight(hologramLoc)) {
            plugin.getLogger().info("[DEBUG] Production GUI opened for " + player.getName() + " from " + distance + " blocks");
            openProductionGUI(player);
        }
    }

    private void openProductionGUI(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.performCommand("production");
        });
    }
}




