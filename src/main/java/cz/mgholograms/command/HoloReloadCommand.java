package cz.mgholograms.command;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HoloReloadCommand implements CommandExecutor {
    private final MGHolograms plugin;
    private final HologramManager hologramManager;

    public HoloReloadCommand(MGHolograms plugin, HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mgholograms.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        sender.sendMessage("§e[MGHolograms] §fReloading hologram configuration...");
        
        hologramManager.reload();
        MGHolograms.getInstance().getHologramBridge().init();
        
        sender.sendMessage("§a[MGHolograms] §fHologram configuration reloaded successfully!");
        
        return true;
    }
}