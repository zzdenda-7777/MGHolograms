package cz.mgholograms;

import cz.mgholograms.command.HoloCenterCommand;
import cz.mgholograms.command.HoloReloadCommand;
import cz.mgholograms.command.HoloTpCommand;
import cz.mgholograms.listener.PlayerJoinTrackerListener;
import cz.mgholograms.manager.HologramBridge;
import cz.mgholograms.manager.HologramManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MGHolograms extends JavaPlugin {

    private static MGHolograms instance;
    private HologramManager hologramManager;
    private HologramBridge hologramBridge;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize hologram manager - bridge musí být nastaven PŘED init(),
        // jinak createHologramsFromConfig() vytvoří veřejný hologram money_balance
        // s textem "Loading..." viditelný všem hráčům.
        hologramManager = new HologramManager(this);
        hologramBridge = new HologramBridge(this, hologramManager);
        hologramManager.setHologramBridge(hologramBridge);
        hologramManager.init();

        // Spusť bridge (tasky, hooky na multigainer) až po init()
        hologramBridge.init();

        // Register commands
        getCommand("holotp").setExecutor(new HoloTpCommand(this, hologramManager));
        getCommand("holoreload").setExecutor(new HoloReloadCommand(this, hologramManager));
        getCommand("holocenter").setExecutor(new HoloCenterCommand(this, hologramManager));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinTrackerListener(), this);

        getLogger().info("MGHolograms enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (hologramBridge != null) {
            hologramBridge.shutdown();
        }

        if (hologramManager != null) {
            hologramManager.shutdown();
        }

        getLogger().info("MGHolograms disabled!");
    }

    public static MGHolograms getInstance() {
        return instance;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public HologramBridge getHologramBridge() {
        return hologramBridge;
    }
}