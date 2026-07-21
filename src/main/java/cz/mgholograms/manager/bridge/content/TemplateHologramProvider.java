package cz.mgholograms.manager.bridge.content;

import cz.mgholograms.manager.HologramManager;
import cz.mgholograms.model.Display;
import cz.mgholograms.model.DisplayType;
import cz.mgholograms.model.HologramGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for per-player content providers whose displayed text comes from
 * a template defined in hologram-groups.yml (with {placeholder} tokens),
 * instead of being hardcoded in Java.
 * <p>
 * This lets admins change the wording/formatting of Production/Tier/Income/
 * Rebirth holograms by editing the config and running /holoreload, without
 * touching Java code, rebuilding the plugin, or restarting the server.
 * <p>
 * If the group/display isn't configured yet (or has no "lines"), {@link
 * #getDefaultTemplate()} is used as a built-in fallback so the hologram still
 * works out of the box.
 */
public abstract class TemplateHologramProvider {

    protected final HologramManager hologramManager;

    protected TemplateHologramProvider(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public abstract String getGroupId();

    /**
     * Hardcoded fallback template (list of lines, may contain {placeholder}
     * tokens) used when hologram-groups.yml doesn't define "lines" for this
     * group's TEXT display yet.
     */
    protected abstract List<String> getDefaultTemplate();

    /**
     * Reads the template lines from this group's first TEXT display in
     * hologram-groups.yml, falling back to {@link #getDefaultTemplate()} if
     * not configured (missing group/display, or empty "lines").
     */
    protected List<String> getTemplate() {
        if (hologramManager != null && hologramManager.getLoadedGroups() != null) {
            for (HologramGroup group : hologramManager.getLoadedGroups()) {
                if (!group.getGroupId().equals(getGroupId())) continue;
                for (Display display : group.getDisplays()) {
                    if (display.getType() == DisplayType.TEXT
                            && display.getLines() != null
                            && !display.getLines().isEmpty()) {
                        List<String> lines = new ArrayList<>();
                        for (Object lineObj : display.getLines()) {
                            lines.add(lineObj.toString());
                        }
                        return lines;
                    }
                }
            }
        }
        return getDefaultTemplate();
    }

    /**
     * Renders the template by replacing every {key} occurrence in each line
     * with its value from the given map. Ignores placeholders without values.
     */
    protected List<String> render(Map<String, String> values) {
        List<String> result = new ArrayList<>();
        for (Object lineObj : getTemplate()) {
            String line = lineObj.toString();
            String rendered = line;
            
            // Replace only placeholders that have values in the map
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() != null) {
                    rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            
            // Remove any remaining unreplaced placeholders
            rendered = rendered.replaceAll("\\{[^}]*\\}", "").trim();
            
            result.add(rendered);
        }
        return result;
    }
}
