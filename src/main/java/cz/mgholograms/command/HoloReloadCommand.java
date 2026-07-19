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

        cz.mgholograms.manager.HologramBridge hologramBridge = MGHolograms.getInstance().getHologramBridge();

        // 1) Úplné vypnutí - zruší všechny běžící tasky (static i per-player engines)
        //    a smaže VŠECHNY aktuálně zobrazené hologram entity u všech hráčů.
        //    Bez tohoto kroku by staré entity/tasky mohly zůstat viset a
        //    překrývat se s nově vytvořenými (duplicitní text/čísla).
        hologramBridge.shutdown();
        hologramManager.shutdown();

        // 2) Úplné znovu-vytvoření - načte config-y čerstvě z disku a vytvoří
        //    úplně nové engines i hologram entity, přesně jako při startu serveru,
        //    ale bez nutnosti server restartovat.
        hologramManager.init();
        hologramBridge.init();

        sender.sendMessage("§a[MGHolograms] §fHologram configuration reloaded successfully!");

        return true;
    }
}