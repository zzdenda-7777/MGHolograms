package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import cz.mgholograms.util.GradientText;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WelcomeHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    public WelcomeHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
    }

    @Override
    public String getGroupId() {
        return "Welcome";
    }

    /**
     * Fallback used only if hologram-groups.yml has no TEXT displays with
     * lines configured for this group.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "{title}",
                "&7Welcome §e§l{player}§f!",
                "&8§m                       ",
                "&fYour goal is to reach as much",
                "§a§lMONEY§f as possible.",
                "&8§m                       ",
                "&fNeed help? Do §6§l/guide§f",
                "&a§oTo start, follow §a§lTUTORIAL§a§o on top of the screen."
        );
    }

    @Override
    public long getRefreshIntervalTicks() {
        return 1L; // matches Multigainer's TabListManager cadence for a smooth gradient flow
    }
    @Override
    public java.util.Set<Integer> getDynamicDisplayIndices() {
        return java.util.Set.of(0); // jen title (gradient) se skutečně mění
    }

    /**
     * Required by the interface, but no longer used for rendering by
     * PlayerHologramEngine (which now calls getLinesPerDisplay() instead).
     * Kept as a flattened equivalent for compatibility with any other code
     * that might still call getLines() directly.
     */
    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        Map<String, String> values = new HashMap<>();
        values.put("title", GradientText.animatedTriangleGradient("MULTIGAINER", " 2", System.currentTimeMillis()));
        values.put("player", player.getName());
        return render(values);
    }

    @Override
    public List<String> getLoadingLines() {
        Map<String, String> values = new HashMap<>();
        values.put("title", GradientText.animatedTriangleGradient("MULTIGAINER", " 2", System.currentTimeMillis()));
        values.put("player", "Player");
        return render(values);
    }

    /**
     * Actually used by PlayerHologramEngine - returns lines split per TEXT
     * display block, matching the group's structure in hologram-groups.yml.
     */
    @Override
    public List<List<String>> getLinesPerDisplay(UUID playerUuid, Player player) {
        Map<String, String> values = new HashMap<>();
        values.put("title", GradientText.animatedTriangleGradient("MULTIGAINER", " 2", System.currentTimeMillis()));
        values.put("player", player.getName());
        return renderPerDisplay(values);
    }

    @Override
    public List<List<String>> getLoadingLinesPerDisplay() {
        Map<String, String> values = new HashMap<>();
        values.put("title", GradientText.animatedTriangleGradient("MULTIGAINER", " 2", System.currentTimeMillis()));
        values.put("player", "Player");
        return renderPerDisplay(values);
    }
}