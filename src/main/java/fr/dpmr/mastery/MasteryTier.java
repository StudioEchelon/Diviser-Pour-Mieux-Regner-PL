package fr.dpmr.mastery;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Combat mastery titles (English) — shown in chat; progression is kill-weighted with a mild points bonus.
 */
public enum MasteryTier {

    FRESH_BLOOD(0, "Fresh Blood", NamedTextColor.DARK_GRAY),
    RISING_MENACE(25, "Rising Menace", NamedTextColor.GRAY),
    FEARED_OPPONENT(80, "Feared Opponent", NamedTextColor.GREEN),
    WALKING_CALAMITY(200, "Walking Calamity", NamedTextColor.DARK_GREEN),
    SOVEREIGN_OF_RUIN(450, "Sovereign of Ruin", NamedTextColor.LIGHT_PURPLE),
    MYTH_MADE_FLESH(900, "Myth Made Flesh", NamedTextColor.LIGHT_PURPLE),
    THE_ONE_THEY_FEAR(1800, "The One They Fear", NamedTextColor.GOLD),
    UNTOUCHABLE_LEGEND(3500, "Untouchable Legend", NamedTextColor.GOLD),
    ABSOLUTE_APEX(6500, "Absolute Apex", NamedTextColor.RED);

    private final int minScore;
    private final String chatTitle;
    private final TextColor accent;

    MasteryTier(int minScore, String chatTitle, TextColor accent) {
        this.minScore = minScore;
        this.chatTitle = chatTitle;
        this.accent = accent;
    }

    public String chatTitle() {
        return chatTitle;
    }

    public TextColor accent() {
        return accent;
    }

    /**
     * Kills weighted heavily; points add a soft tail so economy grinders still climb a little.
     */
    public static int masteryScore(int kills, int points) {
        return kills * 10 + (int) Math.sqrt(Math.max(0, points));
    }

    public static MasteryTier fromProgress(int kills, int points) {
        int score = masteryScore(kills, points);
        MasteryTier best = FRESH_BLOOD;
        for (MasteryTier t : values()) {
            if (score >= t.minScore) {
                best = t;
            }
        }
        return best;
    }
}
