package fr.dpmr.trophy;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Achievements unlocked from kills, points, or clan activity — English names for ego / flex.
 */
public enum Trophy {

    FIRST_BLOOD("first_blood", "First Blood", "The hunt begins.",
            (ctx) -> ctx.kills >= 1),
    BLOOD_TRAIL("blood_trail", "Blood Trail", "Ten names crossed off your list.",
            (ctx) -> ctx.kills >= 10),
    EXECUTION_ARTIST("execution_artist", "Execution Artist", "Fifty eliminations. Still hungry.",
            (ctx) -> ctx.kills >= 50),
    CENTURION("centurion", "Centurion", "A hundred kills. The lobby knows your name.",
            (ctx) -> ctx.kills >= 100),
    REAPER_SUPREME("reaper_supreme", "Reaper Supreme", "Five hundred souls. You are the statistic.",
            (ctx) -> ctx.kills >= 500),
    OBLIVION_WALKER("oblivion_walker", "Oblivion Walker", "A thousand kills. They debate whether you're human.",
            (ctx) -> ctx.kills >= 1000),

    WAR_CHEST("war_chest", "War Chest", "A thousand points earned — fortune favors you.",
            (ctx) -> ctx.points >= 1000),
    TYCOON_OF_CARNAGE("tycoon_of_carnage", "Tycoon of Carnage", "Ten thousand points. Power looks good on you.",
            (ctx) -> ctx.points >= 10_000),
    GOLDEN_EMPIRE("golden_empire", "Golden Empire", "Fifty thousand points. You bought the throne.",
            (ctx) -> ctx.points >= 50_000),

    CLAN_LIFEBLADE("clan_lifeblade", "Clan Lifeblade", "Fifty kills pledged to a banner. True loyalty cuts deep.",
            (ctx) -> ctx.inClan && ctx.kills >= 50),

    PEAK_PREDATOR("peak_predator", "Peak Predator", "Two hundred kills and ten thousand points. Total dominance.",
            (ctx) -> ctx.kills >= 200 && ctx.points >= 10_000);

    private final String id;
    private final String title;
    private final String subtitle;
    private final java.util.function.Predicate<UnlockContext> qualifies;

    Trophy(String id, String title, String subtitle, java.util.function.Predicate<UnlockContext> qualifies) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.qualifies = qualifies;
    }

    public String storageId() {
        return id;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public boolean qualifies(UnlockContext ctx) {
        return qualifies.test(ctx);
    }

    /** Context passed to unlock predicates (no Bukkit types). */
    public record UnlockContext(int kills, int points, boolean inClan) {

        public static UnlockContext forPlayer(UUID uuid, ToIntFunction<UUID> killsFn, ToIntFunction<UUID> pointsFn,
                Function<UUID, String> clanTag) {
            int k = killsFn.applyAsInt(uuid);
            int p = pointsFn.applyAsInt(uuid);
            String c = clanTag.apply(uuid);
            return new UnlockContext(k, p, c != null);
        }
    }
}
