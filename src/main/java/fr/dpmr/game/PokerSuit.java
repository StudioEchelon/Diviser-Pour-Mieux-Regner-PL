package fr.dpmr.game;

/**
 * Couleur de la carte : détermine l'effet power-up (sauf Valet / Jokers → furtivité).
 */
public enum PokerSuit {
    HEARTS,
    DIAMONDS,
    CLUBS,
    SPADES;

    public String frName() {
        return switch (this) {
            case HEARTS -> "cœur";
            case DIAMONDS -> "carreau";
            case CLUBS -> "trèfle";
            case SPADES -> "pique";
        };
    }

    public String symbol() {
        return switch (this) {
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
            case SPADES -> "♠";
        };
    }
}
