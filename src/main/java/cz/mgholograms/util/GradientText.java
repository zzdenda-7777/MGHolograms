package cz.mgholograms.util;

/**
 * Builds an animated, flowing hex-gradient bold title string in the legacy
 * §x§R§R§G§G§B§B format that Minecraft/Adventure text understands.
 * <p>
 * Produces a symmetric "triangle" gradient across a word (dark at both ends,
 * brightest in the middle) - same static look already used for other
 * gradient titles in hologram-groups.yml (e.g. BLOCKSTORAGE, GRINDING
 * POINTS) - but animates it over time by sliding the wave's phase using the
 * current wall-clock time, the same idea as Multigainer's TabListManager
 * (frame counter ticking every 10 ticks / 0.5s).
 */
public final class GradientText {

    private GradientText() {
    }

    // Palette stops for the MULTIGAINER title gradient: deep orange -> gold yellow.
    // t=0.0 -> C45100, t=0.2 -> D97200, t=0.4 -> EC9400, t=0.6 -> FBB700, t=0.8 -> FFD900, t=1.0 -> FFFF00
    private static final int[] STOPS = {0xC45100, 0xD97200, 0xEC9400, 0xFBB700, 0xFFD900, 0xFFFF00};

    // Full wave-travel cycle length - matches Multigainer's TabListManager cadence (10 ticks/0.5s per step).
    private static final long PERIOD_MILLIS = 12_000L;

    /**
     * Builds a bold, animated triangle-gradient string for {@code word}
     * (dark at both ends, brightest in the middle), followed by
     * {@code suffix} rendered without any new color code - it simply
     * inherits the last letter's color/bold, exactly like legacy Minecraft
     * text keeps the last formatting active until changed again.
     *
     * @param word       text to color letter by letter (spaces are kept plain)
     * @param suffix     appended right after {@code word} with no new color code
     * @param nowMillis  current time in milliseconds, driving the animation phase
     */
    public static String animatedTriangleGradient(String word, String suffix, long nowMillis) {
        int len = word.length();
        double phase = (nowMillis % PERIOD_MILLIS) / (double) PERIOD_MILLIS;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = word.charAt(i);
            if (c == ' ') {
                sb.append(' ');
                continue;
            }
            double t = (len <= 1) ? 0.0 : triangle((double) i / (len - 1) + phase);
            sb.append(legacyHex(colorAt(t))).append("§l").append(c);
        }
        sb.append(suffix);
        return sb.toString();
    }

    /** Triangle wave: 0 at x=0, 1 at x=0.5, 0 at x=1, repeating with period 1. */
    private static double triangle(double x) {
        double frac = x - Math.floor(x);
        return 1.0 - Math.abs(2.0 * frac - 1.0);
    }

    /** Piecewise-linear interpolation across {@link #STOPS} for t in [0,1]. */
    private static int colorAt(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double scaled = clamped * (STOPS.length - 1);
        int idx = (int) Math.floor(scaled);
        if (idx >= STOPS.length - 1) {
            return STOPS[STOPS.length - 1];
        }
        return lerpColor(STOPS[idx], STOPS[idx + 1], scaled - idx);
    }

    private static int lerpColor(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static String legacyHex(int rgb) {
        String hex = String.format("%06X", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(c);
        }
        return sb.toString();
    }
}
