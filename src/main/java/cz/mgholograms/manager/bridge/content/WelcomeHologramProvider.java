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

/**
 * Content provider for the "Welcome" spawn hologram.
 * <p>
 * Purely informational and not backed by Multigainer at all (same idea as
 * {@link AfkHologramProvider}) - greets the viewing player using their own
 * name via the {player} placeholder.
 * <p>
 * The title (first line, {title}) is a smooth, continuously animated
 * hex-gradient ("MULTIGAINER 2", deep orange &lt;-&gt; gold yellow, brightest
 * in the middle) built by {@link GradientText}. This provider overrides
 * {@link #getRefreshIntervalTicks()} to 10 ticks (0.5s) - the same cadence
 * Multigainer's TabListManager uses - so the gradient flows smoothly instead
 * of jumping every 2s like the other holograms.
 * <p>
 * Wording is a template read from hologram-groups.yml (group "Welcome",
 * first TEXT display's "lines") with {@link #getDefaultTemplate()} used as a
 * built-in fallback. Edit config + /holoreload to change wording without
 * touching Java code ({title} must stay as-is to keep the animated gradient).
 */
public class WelcomeHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    public WelcomeHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
    }

    @Override
    public String getGroupId() {
        return "Welcome";
    }

    /**
     * Placeholders: {title} (generated animated gradient), {player}.
     */
    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "{title}",
                "&fWelcome {player}! For start follow tutorial on top of the screen.",
                "&fConstant events for big prizes on our DISCORD!",
                "&fIf you have any questions contact staff members."
        );
    }

    @Override
    public long getRefreshIntervalTicks() {
        return 10L; // matches Multigainer's TabListManager cadence for a smooth gradient flow
    }

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
}
