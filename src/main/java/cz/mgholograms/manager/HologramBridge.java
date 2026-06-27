package cz.mgholograms.manager;


import cz.mgholograms.MGHolograms;
import cz.mgholograms.model.Display;
import cz.mgholograms.model.DisplayType;
import cz.mgholograms.model.HologramGroup;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import multigainer.multigainer.formatting.NumberFormatter;
import multigainer.multigainer.math.BigNumber;
import multigainer.multigainer.production.ProductionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * HologramBridge
 * ----------------
 * Propojuje MultiGainer (přímé Java API, bez Vault/PlaceholderAPI) s FancyHolograms.
 * <p>
 * MultiGainer NEMÁ Vault economy hook ani PlaceholderAPI expansion - peníze
 * (BigNumber mantissa/exponent) se čtou přímo přes
 * {@code multigainer.getPlayerDataManager().getProfile(uuid).getMoney()}.
 * <p>
 * Protože FancyHolograms text je sdílený pro všechny viewery jednoho hologramu,
 * a my chceme aby každý hráč viděl SVŮJ zůstatek, vytváříme pro každého hráče
 * v dosahu jeho VLASTNÍ hologram entitu (název "money_balance_&lt;uuid&gt;"),
 * viditelnou jen jemu (Visibility.MANUAL + showHologram(player)).
 * <p>
 * Pozice šablony (odkud se klonují per-player hologramy) je uložena ve stejném
 * hologram-groups.yml jako ostatní hologramy, pod groupId "money_balance" -
 * lze ji tedy posouvat přes /holotp money_balance a /holocenter money_balance.
 */
public class HologramBridge {

    public static final String GROUP_ID = "Production";
    private static final long CHECK_INTERVAL_TICKS = 100L; // jak často kontrolujeme vzdálenost (5s)
    private static final long TEXT_REFRESH_INTERVAL_TICKS = 40L; // jak často refreshujeme částku (2s)

    private final MGHolograms plugin;
    private final cz.mgholograms.manager.HologramManager hologramManager;

    private Multigainer multigainer;
    private final Set<UUID> activeViewers = new HashSet<>();
    private final Map<UUID, Integer> hologramCreationCount = new HashMap<>();

    private org.bukkit.scheduler.BukkitTask distanceCheckTask;
    private org.bukkit.scheduler.BukkitTask textRefreshTask;

    public HologramBridge(MGHolograms plugin, cz.mgholograms.manager.HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
    }

    /**
     * Zavolat z MGHolograms#onEnable po hologramManager.init().
     */
    public void init() {
        hookMultigainer();
        ensureConfigEntry();

        if (multigainer == null) {
            plugin.getLogger().warning("Multigainer plugin not found - money hologram disabled. "
                    + "Make sure 'multigainer' is installed and loaded before MGHolograms.");
            return;
        }

        // Force cleanup všech starých Production hologramů při startu
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (int i = 0; i < 100; i++) {
            if (manager.getHologram("Production_" + i).isPresent()) {
                manager.removeHologram(manager.getHologram("Production_" + i).get());
            }
        }

        startTasks();
    }

    public void shutdown() {
        if (distanceCheckTask != null) distanceCheckTask.cancel();
        if (textRefreshTask != null) textRefreshTask.cancel();
        removeAllPlayerHolograms();
    }

    private void hookMultigainer() {
        Plugin found = Bukkit.getPluginManager().getPlugin("multigainer");
        if (found instanceof Multigainer mg) {
            this.multigainer = mg;
        } else {
            this.multigainer = null;
        }
    }

    /**
     * Zapíše výchozí šablonu skupiny do hologram-groups.yml, pokud tam ještě není.
     * Vyžaduje doplněnou metodu createGroupIfMissing v HologramConfigLoader.
     */
    private void ensureConfigEntry() {
        Map<String, Object> displayData = new HashMap<>();
        displayData.put("type", "TEXT");
        displayData.put("x_offset", 0.0);
        displayData.put("y_offset", 0.0);
        displayData.put("z_offset", 0.0);
        displayData.put("scale", 1.0f);
        displayData.put("background", "transparent");
        // "lines" se zde nepoužívá jako finální text (ten je per-player),
        // jen jako fallback/needitovaný popis pro případ, že by ho někdo
        // zobrazil bez aktivního MultiGainer profilu.
        displayData.put("lines", List.of(
                "&#E9C463&l&#CFAE58&lr&#B5984D&lo&#9B8241&ld&#816C36&lu&#967E3F&lc&#AB8F48&lt&#BFA151&li&#D4B25A&lo&#E9C463&ln",
                "§7Worker System"
        ));

        hologramManager.getConfigLoader().createGroupIfMissing(
                GROUP_ID,
                "voidworld",
                0.0, 0.0, 0.0,
                0.0f, 0.0f,
                List.of(displayData)
        );
    }

    private void startTasks() {
        distanceCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAllPlayers, 0L, CHECK_INTERVAL_TICKS);
        textRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAllTexts, 20L, TEXT_REFRESH_INTERVAL_TICKS);
    }

    /**
     * Pro každého online hráče zkontroluje vzdálenost k pozici šablony.
     * V dosahu -&gt; vytvoří/zviditelní jeho osobní hologram.
     * Mimo dosah -&gt; skryje/smaže jeho osobní hologram.
     */
    private void checkAllPlayers() {
        HologramGroup group = findGroup(GROUP_ID);
        if (group == null) return;
        if (multigainer == null) return;

        Location templateLocation = templateLocation(group);
        if (templateLocation == null || templateLocation.getWorld() == null) return;

        int viewDistance = plugin.getConfig().getInt("view-distance", 100);
        double maxDistSq = (double) viewDistance * viewDistance;

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean sameWorld = player.getWorld().equals(templateLocation.getWorld());
            boolean inRange = sameWorld && player.getLocation().distanceSquared(templateLocation) <= maxDistSq;
            boolean hasLineOfSight = inRange && player.hasLineOfSight(templateLocation);
            boolean currentlyActive = activeViewers.contains(player.getUniqueId());

            if (hasLineOfSight && !currentlyActive) {
                createPlayerHologram(player, templateLocation, group);
                activeViewers.add(player.getUniqueId());
            } else if (!hasLineOfSight && currentlyActive) {
                removePlayerHologram(player.getUniqueId());
                activeViewers.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Pro každého aktuálně aktivního viewera přepočítá text z MultiGainer
     * profilu a aktualizuje jeho osobní hologram.
     */
    private void refreshAllTexts() {
        if (multigainer == null || activeViewers.isEmpty()) return;

        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (UUID uuid : new HashSet<>(activeViewers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                removePlayerHologram(uuid);
                activeViewers.remove(uuid);
                continue;
            }

            ProductionData data = getProductionDataFor(uuid);
            if (data == null) {
                plugin.getLogger().warning("[Production] Data is null for player " + player.getName());
                continue;
            }

            HologramGroup group = findGroup(GROUP_ID);
            if (group != null) {
                updatePlayerHologramText(player, uuid, group, data);
            }
        }
    }

    private void updatePlayerHologramText(Player player, UUID uuid, HologramGroup group, ProductionData data) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (int i = 0; i < group.getDisplays().size(); i++) {
            Display display = group.getDisplays().get(i);
            if (display.getType() != DisplayType.TEXT) continue;

            String name = hologramName(uuid) + "_" + i;
            Optional<Hologram> holoOpt = manager.getHologram(name);

            if (holoOpt.isEmpty()) {
                Location templateLocation = templateLocation(group);
                if (templateLocation != null) {
                    createPlayerHologram(player, templateLocation, group);
                }
                return;
            }

            Hologram hologram = holoOpt.get();
            if (hologram.getData() instanceof TextHologramData textData) {
                List<String> textLines = List.of(
                        "&#FFBA00&lPRODUCTION",
                        "",
                        "&#F5DFA4Level: §f" + data.level,
                        "&#F5DFA4Worker XP §f" + String.format("%.0f", data.workXp) + " / §f" + NumberFormatter.format(new BigNumber(data.xpForNext)),
                        "&#F5DFA4Production: §f" + String.format("%.2f", data.energyPerMin) + "&#F5DFA4/min",
                        "",
                        "&#FFBA00&lYOU HAVE TOTAL §f" + String.format("%.2f", data.storedEnergy) + " &#FFBA00&lENERGY"
                );
                textData.setText(textLines);
                hologram.refreshHologram(player);
            }
        }
    }

    /**
    * Přečte Production data z MultiGainer profilu hráče.
     * Vrací null, pokud profil ještě není načtený.
     */
    private ProductionData getProductionDataFor(UUID uuid) {
        if (multigainer == null) {
            plugin.getLogger().warning("[Production] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(uuid);
        if (profile == null) {
            plugin.getLogger().warning("[Production] Profile is null for " + uuid);
            return null;
        }

        try {
            int level = profile.getWorkerLevel();
            double workXp = profile.getWorkerXp();
            double xpForNext = ProductionManager.getXpForNextLevel(level);
            double energyPerMin = ProductionManager.getEnergyPerMinute(level);
            double storedEnergy = profile.getWorkerEnergy();

            return new ProductionData(level, workXp, xpForNext, energyPerMin, storedEnergy);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get production data for " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static class ProductionData {
        int level;
        double workXp;
        double xpForNext;
        double energyPerMin;
        double storedEnergy;

        ProductionData(int level, double workXp, double xpForNext, double energyPerMin, double storedEnergy) {
            this.level = level;
            this.workXp = workXp;
            this.xpForNext = xpForNext;
            this.energyPerMin = energyPerMin;
            this.storedEnergy = storedEnergy;
        }
    }

    private void createPlayerHologram(Player player, Location templateLocation, HologramGroup group) {
        int count = hologramCreationCount.getOrDefault(player.getUniqueId(), 0);
        hologramCreationCount.put(player.getUniqueId(), count + 1);

        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (int i = 0; i < group.getDisplays().size(); i++) {
            Display display = group.getDisplays().get(i);
            String name = hologramName(player.getUniqueId()) + "_" + i;

            manager.getHologram(name).ifPresent(manager::removeHologram);

            float scale = display.getScale() * 1.2f;
            boolean transparent = "transparent".equalsIgnoreCase(display.getBackground());

            Location displayLocation = new Location(
                    templateLocation.getWorld(),
                    templateLocation.getX() + display.getXOffset(),
                    templateLocation.getY() + display.getYOffset(),
                    templateLocation.getZ() + display.getZOffset(),
                    templateLocation.getYaw(),
                    templateLocation.getPitch()
            );

            if (display.getType() == DisplayType.TEXT) {
                ProductionData data = getProductionDataFor(player.getUniqueId());
                List<String> textLines = data != null ? List.of(
                        "&#FFBA00&lPRODUCTION",
                        "",
                        "&#F5DFA4Level: §f" + data.level,
                        "&#F5DFA4Worker XP §f" + String.format("%.0f", data.workXp) + " / §f" + NumberFormatter.format(new BigNumber(data.xpForNext)),
                        "&#F5DFA4Production: §f" + String.format("%.2f", data.energyPerMin) + "§f/min",
                        "",
                        "&#FFBA00&lYOU HAVE TOTAL §f" + String.format("%.2f", data.storedEnergy) + " &#FFBA00&lENERGY"
                ) : List.of(
                        "&#E9C463&l&#CFAE58&lr&#B5984D&lo&#9B8241&ld&#816C36&lu&#967E3F&lc&#AB8F48&lt&#BFA151&li&#D4B25A&lo&#E9C463&ln",
                        "§7Loading..."
                );

                TextHologramData textData = new TextHologramData(name, displayLocation);
                textData.setText(textLines);
                textData.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                textData.setScale(new Vector3f(scale, scale, scale));
                if (transparent) {
                    textData.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    textData.setSeeThrough(true);
                }
                textData.setVisibility(Visibility.MANUAL);
                textData.setVisibilityDistance(plugin.getConfig().getInt("view-distance", 100));

                Hologram hologram = manager.create(textData);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    hologram.forceShowHologram(player);
                }, 2L);

            } else if (display.getType() == DisplayType.ITEM) {
                ItemHologramData data = new ItemHologramData(name, displayLocation);
                if (display.getMaterial() != null && !display.getMaterial().isEmpty()) {
                    Material material = Material.matchMaterial(display.getMaterial());
                    if (material != null) data.setItemStack(new ItemStack(material));
                }
                data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                data.setScale(new Vector3f(scale, scale, scale));
                data.setVisibility(Visibility.MANUAL);
                data.setVisibilityDistance(plugin.getConfig().getInt("view-distance", 100));

                Hologram hologram = manager.create(data);
                hologram.getData().setPersistent(false);
                manager.addHologram(hologram);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    hologram.forceShowHologram(player);
                }, 2L);
            }
        }
    }

    private void removePlayerHologram(UUID uuid) {
        de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        // Smažeme všechny displays pro hráče (money_balance_uuid_0, money_balance_uuid_1, ...)
        for (int i = 0; i < 10; i++) { // Max 10 displays by mělo stačit
            String name = hologramName(uuid) + "_" + i;
            manager.getHologram(name).ifPresent(manager::removeHologram);
        }
    }

    private void removeAllPlayerHolograms() {
        for (UUID uuid : new HashSet<>(activeViewers)) {
            removePlayerHologram(uuid);
        }
        activeViewers.clear();
    }

    private String hologramName(UUID uuid) {
        return GROUP_ID + "_" + uuid;
    }

    private Location templateLocation(HologramGroup group) {
        Display display = group.getDisplays().isEmpty() ? null : group.getDisplays().get(0);
        double xOff = display != null ? display.getXOffset() : 0.0;
        double yOff = display != null ? display.getYOffset() : 0.0;
        double zOff = display != null ? display.getZOffset() : 0.0;

        return new Location(
                Bukkit.getWorld(group.getWorld()),
                group.getX() + xOff,
                group.getY() + yOff,
                group.getZ() + zOff,
                group.getYaw(),
                group.getPitch()
        );
    }

    private HologramGroup findGroup(String groupId) {
        if (hologramManager.getLoadedGroups() == null) {
            return null;
        }
        for (HologramGroup group : hologramManager.getLoadedGroups()) {
            if (group.getGroupId().equals(groupId)) {
                return group;
            }
        }
        return null;
    }

    public Location getTemplateLocation() {
        HologramGroup group = findGroup(GROUP_ID);
        return group != null ? templateLocation(group) : null;
    }

    /**
     * Voláno z HologramManager#teleportGroup() / reload() pro groupId == GROUP_ID.
     * Protože pozice šablony se mezitím změnila v configu, smažeme aktivní
     * per-player hologramy - checkAllPlayers() je při nejbližším běhu znovu
     * vytvoří na nové pozici (nebo je smaže, pokud hráč už není v dosahu).
     */
    public void createOrUpdateHologram() {
        removeAllPlayerHolograms();
    }
}