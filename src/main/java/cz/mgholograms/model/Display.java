package cz.mgholograms.model;

import java.util.List;

public class Display {
    private final DisplayType type;
    private final double xOffset;
    private final double yOffset;
    private final double zOffset;
    private final float scale;
    private final List<String> lines; // For TEXT type
    private final String material; // For ITEM type
    private final String background; // For TEXT type
    private final Integer brightness; // Brightness level (0-15)
    private final Float yaw;
    private final Float pitch;

    public Display(DisplayType type, double xOffset, double yOffset, double zOffset, float scale, List<String> lines, String material, String background, Integer brightness, Float yaw, Float pitch) {
        this.type = type;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.scale = scale;
        this.lines = lines;
        this.material = material;
        this.background = background;
        this.brightness = brightness;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    // Backward compatibility constructor
    public Display(DisplayType type, double yOffset, float scale, List<String> lines, String material) {
        this(type, 0.0, yOffset, 0.0, scale, lines, material, null, null, null, null);
    }

    // Backward compatibility constructor with background
    public Display(DisplayType type, double yOffset, float scale, List<String> lines, String material, String background) {
        this(type, 0.0, yOffset, 0.0, scale, lines, material, background, null, null, null);
    }

    // Backward compatibility constructor with background and brightness
    public Display(DisplayType type, double yOffset, float scale, List<String> lines, String material, String background, Integer brightness) {
        this(type, 0.0, yOffset, 0.0, scale, lines, material, background, brightness, null, null);
    }

    public DisplayType getType() {
        return type;
    }

    public double getXOffset() {
        return xOffset;
    }

    public double getYOffset() {
        return yOffset;
    }

    public double getZOffset() {
        return zOffset;
    }

    public float getScale() {
        return scale;
    }

    public List<String> getLines() {
        return lines;
    }

    public String getMaterial() {
        return material;
    }

    public String getBackground() {
        return background;
    }

    public Integer getBrightness() {
        return brightness;
    }

    public Float getYaw() {
        return yaw;
    }

    public Float getPitch() {
        return pitch;
    }
}