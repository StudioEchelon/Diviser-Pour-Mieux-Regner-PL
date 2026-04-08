package fr.dpmr.game;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * Styles de launchpad (bloc slime) : impulsion verticale + horizontale selon le regard.
 */
public enum LaunchpadStyle {

    DOUX("DOUX", "Light", NamedTextColor.GREEN, 0.38, 0.18, 0, 0.35f, 1.35f),
    STANDARD("STANDARD", "Balanced", NamedTextColor.YELLOW, 0.55, 0.45, 0, 0.45f, 1.2f),
    BOOST("BOOST", "Strong", NamedTextColor.GOLD, 0.72, 0.72, 0, 0.55f, 1.05f),
    FUSEE("FUSEE", "Vertical", NamedTextColor.RED, 1.08, 0.1, 25, 0.65f, 0.85f),
    DASH("DASH", "Horizontal", NamedTextColor.AQUA, 0.26, 1.12, 0, 0.5f, 1.45f),
    ORBITAL("ORBITAL", "Max arc", NamedTextColor.LIGHT_PURPLE, 1.18, 0.48, 45, 0.7f, 0.95f),
    MEGA("MEGA", "Ultimate", NamedTextColor.DARK_PURPLE, 0.92, 0.95, 15, 0.75f, 0.75f),
    /**
     * Propulsion tres longue portee + slow falling long + effet parachute (particules).
     */
    PARACHUTE("PARACHUTE", "Parachute", NamedTextColor.WHITE, 0.48, 2.75, 280, 0.9f, 1.05f);

    private final String id;
    private final String displayFr;
    private final NamedTextColor color;
    private final double vertical;
    private final double horizontal;
    /** Chute lente (ticks) apres impulsion ; 0 = aucune */
    private final int slowFallTicks;
    private final float soundVolume;
    private final float soundPitch;

    LaunchpadStyle(String id, String displayFr, NamedTextColor color,
                   double vertical, double horizontal, int slowFallTicks,
                   float soundVolume, float soundPitch) {
        this.id = id;
        this.displayFr = displayFr;
        this.color = color;
        this.vertical = vertical;
        this.horizontal = horizontal;
        this.slowFallTicks = slowFallTicks;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    public String id() {
        return id;
    }

    public String displayFr() {
        return displayFr;
    }

    public NamedTextColor color() {
        return color;
    }

    public double vertical() {
        return vertical;
    }

    public double horizontal() {
        return horizontal;
    }

    public int slowFallTicks() {
        return slowFallTicks;
    }

    public float soundVolume() {
        return soundVolume;
    }

    public float soundPitch() {
        return soundPitch;
    }

    public static LaunchpadStyle fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Style avec effet voile / particules prolongees au-dessus du joueur. */
    public boolean isParachuteStyle() {
        return this == PARACHUTE;
    }
}
