package fr.dpmr.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Texte "branding" (nom serveur) avec couleurs hex propres.
 */
public final class BrandingText {

    private BrandingText() {
    }

    /**
     * Titre pour la liste des joueurs (tab) : bleu foncé + "Conquer" en rouge.
     */
    public static Component divideAndConquerTab() {
        TextColor darkBlue = TextColor.fromHexString("#1B2F4A");
        return Component.empty()
                .append(Component.text("Divide & ", darkBlue, TextDecoration.BOLD))
                .append(Component.text("Conquer", NamedTextColor.RED, TextDecoration.BOLD));
    }

    public static Component serverName() {
        TextColor goldA = TextColor.fromHexString("#F6D365"); // doré doux
        TextColor goldB = TextColor.fromHexString("#FBB034"); // doré plus chaud
        TextColor blueGreyA = TextColor.fromHexString("#7B93A7"); // bleu-gris
        TextColor blueGreyB = TextColor.fromHexString("#4A6A86"); // bleu-gris foncé

        return Component.empty()
                .append(gradientWord("Diviser", goldA, goldB).decorate(TextDecoration.BOLD))
                .append(Component.text(" Pour Mieux ", blueGreyA))
                .append(gradientWord("Regner", goldB, goldA).decorate(TextDecoration.BOLD))
                .append(Component.text(" ", blueGreyB));
    }

    private static Component gradientWord(String word, TextColor from, TextColor to) {
        int n = Math.max(1, word.length());
        Component out = Component.empty();
        for (int i = 0; i < n; i++) {
            float t = (n == 1) ? 0f : (float) i / (float) (n - 1);
            int r = (int) Math.round(from.red() + (to.red() - from.red()) * t);
            int g = (int) Math.round(from.green() + (to.green() - from.green()) * t);
            int b = (int) Math.round(from.blue() + (to.blue() - from.blue()) * t);
            out = out.append(Component.text(String.valueOf(word.charAt(i)), TextColor.color(r, g, b)));
        }
        return out;
    }
}

