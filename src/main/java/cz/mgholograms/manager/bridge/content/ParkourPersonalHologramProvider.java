package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParkourPersonalHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public ParkourPersonalHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
        hookMultigainer();
    }

    public Multigainer getMultigainer() {
        return multigainer;
    }

    private void hookMultigainer() {
        Plugin found = org.bukkit.Bukkit.getPluginManager().getPlugin("multigainer");
        if (found instanceof Multigainer mg) {
            this.multigainer = mg;
        } else {
            this.multigainer = null;
        }
    }

    @Override
    public String getGroupId() {
        return "ParkourPersonal";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§fYOUR PARKOUR STATS",
                "",
                "§fHighest score §3{parkour_max_score}",
                "§fTotal jumps §3{parkour_total_jumps}",
                "§fFails §3{parkour_fails}"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        if (multigainer == null) {
            plugin.getLogger().warning("[ParkourPersonal] Multigainer is null!");
            return null;
        }

        PlayerProfile profile = multigainer.getPlayerDataManager().getProfile(playerUuid);
        if (profile == null) {
            plugin.getLogger().warning("[ParkourPersonal] Profile is null for " + playerUuid);
            return null;
        }

        Map<String, String> values = new HashMap<>();
        try {
            Method mTotal = profile.getClass().getMethod("getParkourTotalJumps");
            Method mFails = profile.getClass().getMethod("getParkourFails");
            Method mMax = profile.getClass().getMethod("getParkourMaxScore");

            Object total = mTotal.invoke(profile);
            Object fails = mFails.invoke(profile);
            Object max = mMax.invoke(profile);

            values.put("parkour_total_jumps", formatAsInteger(total) != null ? formatAsInteger(total) : "0");
            values.put("parkour_fails", formatAsInteger(fails) != null ? formatAsInteger(fails) : "0");
            values.put("parkour_max_score", formatAsInteger(max) != null ? formatAsInteger(max) : "0");
        } catch (NoSuchMethodException nsme) {
            plugin.getLogger().warning("[ParkourPersonal] PlayerProfile missing parkour getters: " + nsme.getMessage());
            return null;
        } catch (Exception e) {
            plugin.getLogger().severe("[ParkourPersonal] Failed to read parkour data for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        return render(values);
    }

    private String formatAsInteger(Object o) {
        try {
            if (o == null) return null;
            if (o instanceof Number) {
                Number n = (Number) o;
                if (n instanceof Float || n instanceof Double) {
                    long v = Math.round(n.doubleValue());
                    return String.valueOf(v);
                } else {
                    return String.valueOf(n.longValue());
                }
            }
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return null;
            if (s.matches("^-?\\d+\\.\\d+$")) {
                try { double d = Double.parseDouble(s); return String.valueOf(Math.round(d)); } catch (Throwable ignored) {}
            }
            if (s.matches("^-?\\d+$")) return s;
            try { double d = Double.parseDouble(s); return String.valueOf(Math.round(d)); } catch (Throwable ignored) {}
            return s;
        } catch (Throwable ex) { return String.valueOf(o); }
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§fYOUR PARKOUR STATS",
                "§7Loading..."
        );
    }
}
