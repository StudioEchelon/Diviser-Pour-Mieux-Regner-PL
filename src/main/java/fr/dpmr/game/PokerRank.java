package fr.dpmr.game;

/**
 * Hauteur de la carte : influe légèrement la durée du buff.
 */
public enum PokerRank {
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    JACK,
    QUEEN,
    KING,
    ACE;

    public String frShort() {
        return switch (this) {
            case ACE -> "As";
            case KING -> "Roi";
            case QUEEN -> "Dame";
            case JACK -> "Valet";
            case TEN -> "10";
            case NINE -> "9";
            case EIGHT -> "8";
            case SEVEN -> "7";
            case SIX -> "6";
            case FIVE -> "5";
            case FOUR -> "4";
            case THREE -> "3";
            case TWO -> "2";
        };
    }

    /**
     * Multiplicateur appliqué à la durée configurée (bonus-powerup-blocks.*.duration-seconds).
     */
    public double durationMultiplier() {
        return switch (this) {
            case ACE -> 1.3;
            case KING, QUEEN -> 1.15;
            case JACK -> 1.1;
            case TEN, NINE, EIGHT -> 1.0;
            case SEVEN, SIX, FIVE -> 0.95;
            case FOUR, THREE, TWO -> 0.9;
        };
    }
}
