package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import multigainer.multigainer.Multigainer;
import multigainer.multigainer.data.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParkourLifetimeHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;
    private Multigainer multigainer;

    public ParkourLifetimeHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
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
        return "ParkourLifetime";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§5§l§nLIFETIME STATISTICS",
                "§6§l🥇§f {p1} - {s1} score",
                "§7§l🥈§f {p2} - {s2} score",
                "§c§l🥉§f {p3} - {s3} score",
                "§e4. {p4} - {s4} score",
                "§e5. {p5} - {s5} score",
                "§e6. {p6} - {s6} score",
                "§e7. {p7} - {s7} score",
                "§e8. {p8} - {s8} score",
                "§e9. {p9} - {s9} score",
                "§e10. {p10} - {s10} score"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        if (multigainer == null) {
            plugin.getLogger().warning("[ParkourLifetime] Multigainer is null!");
            return null;
        }

        try {
            Method gm = multigainer.getClass().getMethod("getLeaderboardManager");
            Object leaderboardManager = gm.invoke(multigainer);
            if (leaderboardManager == null) {
                plugin.getLogger().warning("[ParkourLifetime] LeaderboardManager is null");
                return getDefaultTemplate();
            }

            Class<?> lbClass = leaderboardManager.getClass();
            Class<?> categoryClass = Class.forName("multigainer.multigainer.leaderboard.LeaderboardManager$Category");
            Method getTop = lbClass.getMethod("getTop", categoryClass, int.class);

            // Heuristic: prefer Category that clearly represents parkour max/best score
            Object chosenCategory = null;
            String[] preferredKeys = new String[]{"parkour_max_score", "parkour_max", "parkour_best", "parkour_best_score", "park_max_score", "parkour_bestscore", "PARKOUR_MAX", "PARKOUR_BEST"};
            for (Object cat : categoryClass.getEnumConstants()) {
                // check any string-returning methods (possible DB key)
                for (Method m : categoryClass.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        try {
                            Object val = m.invoke(cat);
                            if (val != null) {
                                String s = String.valueOf(val).toLowerCase();
                                for (String pk : preferredKeys) {
                                    if (s.equals(pk.toLowerCase()) || s.contains(pk.toLowerCase())) { chosenCategory = cat; break; }
                                }
                                if (chosenCategory != null) break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (chosenCategory != null) break;

                // fallback: prefer enum name that mentions park and max/best/score
                try {
                    String name = (String) categoryClass.getMethod("name").invoke(cat);
                    if (name != null) {
                        String ln = name.toLowerCase();
                        if (ln.contains("park") && (ln.contains("max") || ln.contains("best") || ln.contains("score"))) { chosenCategory = cat; break; }
                    }
                } catch (Throwable ignored) {}
            }

            List<?> top = null;
            if (chosenCategory != null) {
                try { top = (List<?>) getTop.invoke(leaderboardManager, chosenCategory, 10); } catch (Throwable ignored) { top = null; }
            }

            // If still not found or empty, try categories that mention 'park' at all
            if (top == null || top.isEmpty()) {
                for (Object cat : categoryClass.getEnumConstants()) {
                    try {
                        String name = (String) categoryClass.getMethod("name").invoke(cat);
                        if (name != null && name.toLowerCase().contains("park")) {
                            List<?> t = (List<?>) getTop.invoke(leaderboardManager, cat, 10);
                            if (t != null && !t.isEmpty()) { chosenCategory = cat; top = t; break; }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Final fallback: anything non-empty
            if (top == null || top.isEmpty()) {
                for (Object cat : categoryClass.getEnumConstants()) {
                    try {
                        List<?> t = (List<?>) getTop.invoke(leaderboardManager, cat, 10);
                        if (t != null && !t.isEmpty()) { chosenCategory = cat; top = t; break; }
                    } catch (Throwable ignored) {}
                }
            }

            if (chosenCategory != null) {
                try { plugin.getLogger().info("[ParkourLifetime] selected Category: " + categoryClass.getMethod("name").invoke(chosenCategory)); } catch (Throwable ignored) {}
            } else {
                plugin.getLogger().warning("[ParkourLifetime] Could not find matching Category for lifetime parkour");
            }

            // Use template rendering so colors and formatting from hologram-groups.yml are preserved
            java.util.Map<String,String> values = new java.util.HashMap<>();

            if (top == null || top.isEmpty()) {
                for (int i = 1; i <= 10; i++) {
                    values.put("p" + i, "-");
                    values.put("s" + i, "0");
                }
                return render(values);
            }

            int idx = 1;
            for (Object entry : top) {
                if (idx > 10) break;

                java.util.UUID uid = extractUuid(entry);
                String name = null;
                if (uid != null) {
                    org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(uid);
                    if (off != null) name = off.getName();
                }
                if (name == null || name.isEmpty() || name.equals("-")) name = extractName(entry);

                String score = extractStat(entry, "getParkourMaxScore", "getValue", "getScore", "value", "score");
                if (score == null) {
                    if (entry instanceof Number) score = String.valueOf(entry);
                    else {
                        try {
                            for (java.lang.reflect.Field f : entry.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                Object v = f.get(entry);
                                if (v instanceof Number) { score = String.valueOf(v); break; }
                                if (v != null && String.valueOf(v).matches("^\\d+$")) { score = String.valueOf(v); break; }
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                values.put("p" + idx, name != null ? name : "-");
                values.put("s" + idx, formatAsInteger(score) != null ? formatAsInteger(score) : (score != null ? score : "0"));
                idx++;
            }

            while (idx <= 10) { values.put("p" + idx, "-"); values.put("s" + idx, "0"); idx++; }
            return render(values);

        } catch (Exception e) {
            plugin.getLogger().severe("[ParkourLifetime] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return getDefaultTemplate();
        }
    }

    private java.util.UUID extractUuid(Object entry) {
        try {
            String[] names = new String[]{"getUuid","getUniqueId","getPlayerUuid","getPlayerId","getId","uuid","uniqueId","getPlayer"};
            for (String n : names) {
                try {
                    Method m = entry.getClass().getMethod(n);
                    Object o = m.invoke(entry);
                    if (o == null) continue;
                    if (o instanceof java.util.UUID) return (java.util.UUID)o;
                    if (o instanceof String) {
                        try { return java.util.UUID.fromString((String)o); } catch (Exception ex) {}
                    }
                    try {
                        Method gu = o.getClass().getMethod("getUniqueId");
                        Object uid = gu.invoke(o);
                        if (uid instanceof java.util.UUID) return (java.util.UUID)uid;
                    } catch (Throwable ignored) {}
                } catch (NoSuchMethodException ignored) {}
            }
            for (java.lang.reflect.Field f : entry.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(entry);
                    if (v == null) continue;
                    if (v instanceof java.util.UUID) return (java.util.UUID)v;
                    if (v instanceof String) {
                        try { return java.util.UUID.fromString((String)v); } catch (Exception ex) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String extractName(Object profileLike) {
        try {
            for (String m : new String[]{"getDisplayName","getName","getUsername","getPlayerName","name"}) {
                try {
                    Method meth = profileLike.getClass().getMethod(m);
                    Object o = meth.invoke(profileLike);
                    if (o != null) return String.valueOf(o);
                } catch (NoSuchMethodException ignored) {}
            }
            java.util.UUID uid = extractUuid(profileLike);
            if (uid != null) {
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(uid);
                if (off != null) {
                    String n = off.getName();
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) {}
        return "-";
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

    private String extractStat(Object profileLike, String... candidateMethods) {
        try {
            for (String m : candidateMethods) {
                try {
                    Method meth = profileLike.getClass().getMethod(m);
                    Object o = meth.invoke(profileLike);
                    String formatted = formatAsInteger(o);
                    if (formatted != null) return formatted;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public List<String> getLoadingLines() {
        return List.of(
                "§fLIFETIME STATISTICS",
                "§7Loading..."
        );
    }
}
