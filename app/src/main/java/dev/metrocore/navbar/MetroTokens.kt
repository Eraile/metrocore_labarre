/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.graphics.Color

/**
 * Constantes portees depuis metrocore (`spec/tokens.json` / `spec/out/Tokens.kt`).
 *
 * Les metriques sont en pixels logiques WVGA (l'ecran de reference WP8.1 fait 480x800) :
 * c'est l'unite que le systeme utilisait lui-meme, et la garder rend les ratios exacts.
 * Voir [barHeightDpFor] pour la conversion vers un ecran Android reel.
 */
object MetroTokens {

    // ---- chassis ----
    const val SCREEN_W = 480f

    /** La bande capacitive precedent / demarrer / rechercher. */
    const val NAVKEYS_H = 60f

    /** Taille de la glyphe dans cette bande (navkeys.ts : icon(..., size = 20)). */
    const val NAVKEY_ICON = 20f

    /** Epaisseur de trait des glyphes, dans la boite de 24 unites. */
    const val ICON_WEIGHT = 1.8f

    // ---- page ----
    const val MARGIN = 12f
    const val MARGIN_TITLE = 24f
    const val LIST_ROW = 68f
    const val LIST_ROW_1 = 48f
    const val TOUCH_MIN = 34f

    // ---- type (les huit tailles exposees par le systeme) ----
    const val FS_SMALL = 18.667f
    const val FS_NORMAL = 20f
    const val FS_MEDIUM = 22.667f
    const val FS_MEDIUM_LARGE = 25.333f
    const val FS_LARGE = 32f
    const val FS_XL = 40f

    /** Le titre de panorama, coupe par les deux bords de l'ecran. */
    const val FS_HUGE = 186.667f

    // ---- motion ----
    /** Enfoncement. */
    const val DUR_TILT = 100L

    /** Retour apres relachement. */
    const val DUR_TILT_OUT = 200L
    const val DUR_FAST = 150L
    const val DUR_BASE = 250L

    /** Appui long avant declenchement. */
    const val HOLD_MS = 550L

    /** Debordement maximal d'une surface tiree au-dela de sa fin (physics). */
    const val RUBBER_LIMIT = 90f

    /** Echelle appliquee a pleine pression (tilt WP). */
    const val TILT_DEPRESS = 0.985f

    /** Opacite d'une touche capacitive enfoncee (shell.css : `.mc-navkey.is-down`). */
    const val NAVKEY_DOWN_ALPHA = 0.45f

    // ---- themes ----

    /**
     * Une palette complete. Le theme clair etait un reglage systeme a part entiere sur
     * WP, pas une arriere-pensee : les deux sont donc definis pareil (metrocore,
     * `spec/tokens.json` → themes).
     */
    data class Palette(
        val bg: Int,
        val fg: Int,
        val sub: Int,
        val faint: Int,
        val line: Int,
        val chrome: Int,
        val chrome2: Int,
    )

    val DARK = Palette(
        bg = Color.parseColor("#FF000000"),
        fg = Color.parseColor("#FFFFFFFF"),
        sub = Color.parseColor("#99FFFFFF"),
        faint = Color.parseColor("#57FFFFFF"),
        line = Color.parseColor("#21FFFFFF"),
        chrome = Color.parseColor("#FF1F1F1F"),
        chrome2 = Color.parseColor("#FF2B2B2B"),
    )

    val LIGHT = Palette(
        bg = Color.parseColor("#FFFFFFFF"),
        fg = Color.parseColor("#FF000000"),
        sub = Color.parseColor("#99000000"),
        faint = Color.parseColor("#5C000000"),
        line = Color.parseColor("#1F000000"),
        chrome = Color.parseColor("#FFF2F2F2"),
        chrome2 = Color.parseColor("#FFE6E6E6"),
    )

    /**
     * Le degrade de fond du panorama : creme vers or pale, en diagonale.
     *
     * Il garde une composante horizontale, sans quoi le parallaxe ne se verrait pas.
     */
    val PANORAMA_LIGHT = intArrayOf(
        Color.parseColor("#FFFAF6EC"),
        Color.parseColor("#FFF3E6C4"),
        Color.parseColor("#FFE8C86A"),
    )

    /** Le fond de la bande capacitive — pas tout a fait noir (shell.css). */
    val NAVKEYS_BG = Color.parseColor("#FF0A0A0A")

    /** Sa couleur de glyphe par defaut : blanc a 75 %. */
    val NAVKEYS_FG = Color.parseColor("#BFFFFFFF")

    /**
     * Les vingt accents fournis par WP8.1. Le systeme n'exposait que ceux-la et aucun
     * selecteur libre, donc on fait pareil.
     */
    val ACCENTS: List<Pair<String, Int>> = listOf(
        "lime" to Color.parseColor("#FFA4C400"),
        "green" to Color.parseColor("#FF60A917"),
        "emerald" to Color.parseColor("#FF008A00"),
        "teal" to Color.parseColor("#FF00ABA9"),
        "cyan" to Color.parseColor("#FF1BA1E2"),
        "cobalt" to Color.parseColor("#FF0050EF"),
        "indigo" to Color.parseColor("#FF6A00FF"),
        "violet" to Color.parseColor("#FFAA00FF"),
        "pink" to Color.parseColor("#FFF472D0"),
        "magenta" to Color.parseColor("#FFD80073"),
        "crimson" to Color.parseColor("#FFA20025"),
        "red" to Color.parseColor("#FFE51400"),
        "orange" to Color.parseColor("#FFFA6800"),
        "amber" to Color.parseColor("#FFF0A30A"),
        "yellow" to Color.parseColor("#FFE3C800"),
        "brown" to Color.parseColor("#FF825A2C"),
        "olive" to Color.parseColor("#FF6D8764"),
        "steel" to Color.parseColor("#FF647687"),
        "mauve" to Color.parseColor("#FF76608A"),
        "taupe" to Color.parseColor("#FF87794E"),
    )

    const val DEFAULT_ACCENT = "cobalt"

    fun accent(key: String): Int =
        ACCENTS.firstOrNull { it.first == key }?.second
            ?: ACCENTS.first { it.first == DEFAULT_ACCENT }.second

    /**
     * Hauteur de barre pour un ecran donne.
     *
     * WP posait 60 px de bande sur 480 px de large, soit un huitieme de la largeur.
     * On ancre sur la largeur et non sur la hauteur : les telephones actuels sont
     * bien plus allonges que le 5:3 du WVGA, et un ratio pris sur la hauteur
     * donnerait une bande enorme.
     */
    fun barHeightDpFor(screenWidthDp: Int): Int =
        (screenWidthDp * NAVKEYS_H / SCREEN_W).toInt().coerceIn(36, 80)

    /** Taille de glyphe par defaut : le tiers de la bande (20 sur 60). */
    fun iconDpFor(barHeightDp: Int): Int =
        (barHeightDp * NAVKEY_ICON / NAVKEYS_H).toInt().coerceAtLeast(8)
}
