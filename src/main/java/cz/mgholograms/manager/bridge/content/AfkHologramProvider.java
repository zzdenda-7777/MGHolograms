package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.MGHolograms;
import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.manager.bridge.core.HologramContentProvider;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Content provider for the AFK hologram.
 * <p>
 * Purely informational - the text is the same for every player (it just
 * explains the AFK point rules), so getLines() renders the template with no
 * per-player placeholders.
 * <p>
 * Wording is a template read from hologram-groups.yml (group "AFK", first
 * TEXT display's "lines") with {@link #getDefaultTemplate()} used as a
 * built-in fallback. Edit config + /holoreload to change wording without
 * touching Java code.
 */
public class AfkHologramProvider extends TemplateHologramProvider implements HologramContentProvider {

    private final MGHolograms plugin;

    public AfkHologramProvider(MGHolograms plugin, HologramManager hologramManager) {
        super(hologramManager);
        this.plugin = plugin;
    }

    @Override
    public String getGroupId() {
        return "AFK";
    }

    @Override
    protected List<String> getDefaultTemplate() {
        return List.of(
                "§e§l§nAFK",
                "§eEarn 1 AFK POINT every 60 seconds",
                "§eBut rank IMMORTAL earns 1.5x more AFK points"
        );
    }

    @Override
    public List<String> getLines(UUID playerUuid, Player player) {
        // No per-player data needed - just render the static template.
        return render(new HashMap<>());
    }

    @Override
    public List<String> getLoadingLines() {
        return getDefaultTemplate();
    }
}
