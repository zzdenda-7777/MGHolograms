package cz.mgholograms.command;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.model.HologramGroup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HoloCenterCommand implements CommandExecutor {
    private final MGHolograms plugin;
    private final HologramManager hologramManager;

    public HoloCenterCommand(MGHolograms plugin, HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mgholograms.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /holocenter <group_id>");
            return true;
        }

        Player player = (Player) sender;
        String groupId = args[0];

        hologramManager.centerGroup(groupId, player);
        sender.sendMessage("§a[MGHolograms] §fCentered hologram group '" + groupId + "' at your location.");

        return true;
    }
}