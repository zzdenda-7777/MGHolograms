package cz.mgholograms.model;

import java.util.List;

public class HologramGroup {
    private final String groupId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final List<Display> displays;

    public HologramGroup(String groupId, String world, double x, double y, double z, 
                        float yaw, float pitch, List<Display> displays) {
        this.groupId = groupId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.displays = displays;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public List<Display> getDisplays() {
        return displays;
    }

    /**
     * Get the chunk coordinates for this group's position
     */
    public int getChunkX() {
        return (int) Math.floor(x / 16.0);
    }

    public int getChunkZ() {
        return (int) Math.floor(z / 16.0);
    }
}