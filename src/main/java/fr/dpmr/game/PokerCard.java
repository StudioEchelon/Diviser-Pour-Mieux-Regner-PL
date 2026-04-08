package fr.dpmr.game;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Carte à jouer liée à un effet {@link PowerupBlockManager.Kind} :
 * la couleur choisit le buff, Valet et Jokers donnent la furtivité.
 */
public final class PokerCard {

    private final PokerSuit suit;
    private final PokerRank rank;
    /** {@code true} pour les deux jokers (suit/rank inutilisés). */
    private final boolean joker;
    private final boolean redJoker;

    private PokerCard(PokerSuit suit, PokerRank rank, boolean joker, boolean redJoker) {
        this.suit = suit;
        this.rank = rank;
        this.joker = joker;
        this.redJoker = redJoker;
    }

    public static PokerCard of(PokerSuit suit, PokerRank rank) {
        return new PokerCard(suit, rank, false, false);
    }

    public static PokerCard redJoker() {
        return new PokerCard(null, null, true, true);
    }

    public static PokerCard blackJoker() {
        return new PokerCard(null, null, true, false);
    }

    public boolean isJoker() {
        return joker;
    }

    public PokerSuit suit() {
        return suit;
    }

    public PokerRank rank() {
        return rank;
    }

    public PowerupBlockManager.Kind powerupKind() {
        if (joker) {
            return PowerupBlockManager.Kind.STEALTH;
        }
        if (rank == PokerRank.JACK) {
            return PowerupBlockManager.Kind.STEALTH;
        }
        return switch (suit) {
            case HEARTS -> PowerupBlockManager.Kind.RAPID_FIRE;
            case DIAMONDS -> PowerupBlockManager.Kind.KILL_COINS;
            case CLUBS -> PowerupBlockManager.Kind.BULLET_SHIELD;
            case SPADES -> PowerupBlockManager.Kind.INVULNERABILITY;
        };
    }

    public double durationMultiplier() {
        if (joker) {
            return 1.25;
        }
        return rank.durationMultiplier();
    }

    /**
     * Clé stable pour PDC / commandes (ex. {@code HEARTS_ACE}, {@code JOKER_RED}).
     */
    public String storageKey() {
        if (joker) {
            return redJoker ? "JOKER_RED" : "JOKER_BLACK";
        }
        return suit.name() + "_" + rank.name();
    }

    public static PokerCard fromStorageKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("JOKER_RED".equals(u) || "RED_JOKER".equals(u)) {
            return redJoker();
        }
        if ("JOKER_BLACK".equals(u) || "BLACK_JOKER".equals(u)) {
            return blackJoker();
        }
        int us = u.indexOf('_');
        if (us < 1) {
            return null;
        }
        String sPart = u.substring(0, us);
        String rPart = u.substring(us + 1);
        try {
            PokerSuit suit = PokerSuit.valueOf(sPart);
            PokerRank rank = PokerRank.valueOf(rPart);
            return of(suit, rank);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Tirage uniforme sur le jeu 52 cartes + 2 jokers (54).
     */
    public static PokerCard randomFullDeck(ThreadLocalRandom r) {
        int n = r.nextInt(54);
        if (n == 52) {
            return redJoker();
        }
        if (n == 53) {
            return blackJoker();
        }
        PokerSuit s = PokerSuit.values()[n / 13];
        PokerRank rank = PokerRank.values()[n % 13];
        return of(s, rank);
    }
}
