/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlin.math.roundToInt

/** Un des trois emplacements de la barre. */
enum class Slot(val key: String, val labelRes: Int) {
    LEFT("left", R.string.slot_left),
    CENTER("center", R.string.slot_center),
    RIGHT("right", R.string.slot_right),
}

/** Intensite du retour haptique. La duree est en ms, l'amplitude sur 255. */
enum class Haptic(val key: String, val labelRes: Int, val ms: Long, val amplitude: Int) {
    OFF("off", R.string.haptic_off, 0, 0),
    LIGHT("light", R.string.haptic_light, 10, 60),
    MEDIUM("medium", R.string.haptic_medium, 18, 130),
    STRONG("strong", R.string.haptic_strong, 30, 220),
    ;

    companion object {
        fun from(key: String?): Haptic = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

/**
 * Quand la barre s'affiche, face a la place disponible.
 *
 * Il n'y a que deux reponses possibles au fait qu'aucun overlay ne peut reserver
 * d'espace (voir [SystemBars]) : ne se montrer que la ou le systeme en laisse, ou se
 * montrer quand meme et recouvrir. Autant les nommer.
 */
enum class BarMode(val key: String, val labelRes: Int) {
    /** Affichee seulement la ou le systeme reserve une bande — navigation a 3 boutons. */
    FITTED("fitted", R.string.mode_fitted),

    /** Affichee partout, recouvrement assume la ou il n'y a pas de place. */
    ALWAYS("always", R.string.mode_always),
    ;

    companion object {
        fun from(key: String?): BarMode = entries.firstOrNull { it.key == key } ?: ALWAYS
    }
}

/**
 * Reglage d'un bouton : sa glyphe, son appui court, son appui long.
 *
 * [iconScalePct] ajuste la glyphe par rapport a la taille commune. Le logo central est
 * plus grand par defaut : c'est une forme pleine, la ou les deux autres sont des traits
 * fins, et a taille geometrique egale il paraitrait plus petit.
 */
data class SlotConfig(
    val iconKey: String,
    val tap: NavAction,
    val hold: NavAction,
    val iconScalePct: Int = 100,
    /** Cible de [NavAction.LAUNCH_APP] / [NavAction.SHORTCUT] : un intent serialise. */
    val tapPayload: String? = null,
    val holdPayload: String? = null,
    /** Ce qu'on affiche a la place du nom d'action generique. */
    val tapPayloadLabel: String? = null,
    val holdPayloadLabel: String? = null,
) {
    fun payloadFor(trigger: Trigger): String? =
        if (trigger == Trigger.TAP) tapPayload else holdPayload

    fun payloadLabelFor(trigger: Trigger): String? =
        if (trigger == Trigger.TAP) tapPayloadLabel else holdPayloadLabel

    fun actionFor(trigger: Trigger): NavAction =
        if (trigger == Trigger.TAP) tap else hold
}

/** Les deux facons d'actionner une touche. */
enum class Trigger(val labelRes: Int) {
    TAP(R.string.label_tap),
    HOLD(R.string.label_hold),
}

/**
 * L'etat complet de la barre.
 *
 * [barHeightDp] et [iconSizePct] valent 0 quand ils sont laisses en automatique : la
 * valeur est alors derivee de la largeur de l'ecran par [MetroTokens.barHeightDpFor],
 * ce qui reproduit les proportions WP quel que soit l'appareil.
 */
data class NavBarConfig(
    val barColorKey: String,
    val iconColorKey: String,
    val barHeightDp: Int,
    val iconSizePct: Int,
    val haptic: Haptic,
    val pressFeedback: Boolean,
    val slots: Map<Slot, SlotConfig>,
    /**
     * Material You : le fond de la barre suit l'accent que le systeme tire du fond
     * d'ecran, et se remet a jour tout seul quand celui-ci change. Prend le pas sur
     * [barColorKey]. Faux par defaut — les vingt accents WP restent le reglage
     * d'origine, sinon l'app ne ressemble plus a ce qu'elle reconstruit.
     */
    val dynamicColor: Boolean = false,
    /**
     * S'afficher partout, ou seulement la ou le systeme laisse la place. Ne change rien
     * en navigation a 3 boutons : la bande est reservee, les deux modes s'y valent.
     */
    val mode: BarMode = BarMode.ALWAYS,
    /** S'effacer quand l'application au premier plan passe en plein ecran. */
    val hideFullscreen: Boolean = true,
    /** S'effacer sur l'ecran de verrouillage. */
    val hideLockscreen: Boolean = true,
) {
    /** Hauteur reelle, en dp, pour un ecran de [screenWidthDp] de large. */
    fun resolvedHeightDp(screenWidthDp: Int): Int =
        if (barHeightDp > 0) barHeightDp else MetroTokens.barHeightDpFor(screenWidthDp)

    /**
     * La hauteur reelle. C'est celle-ci qui fait foi : le referentiel n'est plus la
     * proportion WP mais **la place que prend la barre systeme**.
     *
     * En navigation a 3 boutons le systeme reserve sa bande et La Barre vient s'y poser
     * exactement : rien du contenu n'est recouvert. L'ecart avec la proportion WP est
     * d'ailleurs minime — 60/480 donne 51 dp sur un ecran de 411 dp, la barre Android
     * en fait 48 — donc on ne perd a peu pres rien de la fidelite en se calant dessus.
     *
     * En navigation gestuelle le systeme ne reserve que la poignee. Se caler dessus
     * donnerait une barre de 24 dp : sous n'importe quel minimum tactile, et posee pile
     * sur la zone du geste. Il n'y a plus de place a prendre, donc plus de referentiel —
     * on revient a la proportion WP, et le recouvrement est assume. [showInGestureNav]
     * decide si on affiche quand meme.
     */
    fun resolvedHeightDp(context: Context): Int {
        if (barHeightDp > 0) return barHeightDp

        val system = SystemBars.reserved(context).sizeDp
        if (system >= SystemBars.USABLE_MIN_DP) return system

        // La proportion WP s'ancre sur la largeur d'ecran ; en paysage, `screenWidthDp`
        // est le grand cote et donnerait une bande enorme. On prend donc toujours le
        // petit cote, qui est la largeur du telephone quel que soit son sens.
        val config = context.resources.configuration
        val shortSide = minOf(config.screenWidthDp, config.screenHeightDp)
        return MetroTokens.barHeightDpFor(shortSide)
    }

    /** Taille de glyphe commune, en dp. */
    fun resolvedIconDp(screenWidthDp: Int): Int =
        iconDp(screenWidthDp, scalePct = 100)

    /** Taille de glyphe d'un bouton donne, echelle propre appliquee. */
    fun resolvedIconDp(screenWidthDp: Int, slot: Slot): Int =
        iconDp(screenWidthDp, slots.getValue(slot).iconScalePct)

    /**
     * La meme, calee sur la hauteur reelle de la barre — celle que le systeme dicte.
     * Sans ca, la glyphe se dimensionnerait sur une hauteur theorique differente de la
     * bande effectivement dessinee.
     */
    fun resolvedIconDp(context: Context, slot: Slot): Int =
        scaleIcon(resolvedHeightDp(context), slots.getValue(slot).iconScalePct)

    /**
     * Tout le calcul se fait en flottant et n'arrondit qu'a la fin : enchainer deux
     * pourcentages sur des entiers tronques perdait plusieurs pour cent en route.
     */
    private fun iconDp(screenWidthDp: Int, scalePct: Int): Int =
        scaleIcon(resolvedHeightDp(screenWidthDp), scalePct)

    private fun scaleIcon(barDp: Int, scalePct: Int): Int {
        val pct = if (iconSizePct > 0) iconSizePct else DEFAULT_ICON_PCT
        return (barDp * pct / 100f * scalePct / 100f).roundToInt().coerceAtLeast(8)
    }

    /**
     * Le fond de la barre. Demande un contexte car Material You lit une couleur du
     * systeme, qui change avec le fond d'ecran.
     */
    fun barColor(context: Context): Int {
        if (dynamicColor) {
            MetroTokens.dynamicAccent(context)?.let { return it }
        }
        return resolveBarColor(barColorKey)
    }

    /** En automatique, la glyphe prend le contraste du fond, a l'opacite WP (75 %). */
    fun iconColor(context: Context): Int = when (iconColorKey) {
        "auto" ->
            if (prefersDarkForeground(barColor(context))) Color.parseColor("#BF000000")
            else MetroTokens.NAVKEYS_FG
        "white" -> Color.WHITE
        "black" -> Color.BLACK
        else -> MetroTokens.accent(iconColorKey)
    }

    companion object {
        /** 20 sur 60, le ratio WP. */
        const val DEFAULT_ICON_PCT = 33

        /** Fonds proposes en plus des vingt accents. */
        val BAR_COLORS: List<Pair<String, Int>> = listOf(
            "shell" to MetroTokens.NAVKEYS_BG,
            "black" to Color.BLACK,
            "white" to Color.WHITE,
        ) + MetroTokens.ACCENTS

        val ICON_COLORS: List<Pair<String, Int>> = listOf(
            "auto" to MetroTokens.NAVKEYS_FG,
            "white" to Color.WHITE,
            "black" to Color.BLACK,
        ) + MetroTokens.ACCENTS

        fun resolveBarColor(key: String): Int =
            BAR_COLORS.firstOrNull { it.first == key }?.second ?: MetroTokens.NAVKEYS_BG

        private fun luminance(color: Int): Double =
            0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)

        /** Assez clair pour se perdre sur un fond noir : sert a cerner les pastilles. */
        fun isLight(color: Int): Boolean = luminance(color) > 140

        /**
         * Faut-il passer la glyphe en noir ?
         *
         * Le seuil est volontairement haut. Les vingt accents WP sont concus pour porter
         * du blanc — l'ambre et le citron sont clairs au sens de la luminance, mais le
         * systeme y posait quand meme du blanc. Seul un fond quasi blanc bascule.
         */
        fun prefersDarkForeground(color: Int): Boolean = luminance(color) > 220

        /** La configuration d'origine : la barre WP telle quelle. */
        val DEFAULT = NavBarConfig(
            barColorKey = "shell",
            iconColorKey = "auto",
            barHeightDp = 0,
            iconSizePct = 0,
            haptic = Haptic.LIGHT,
            pressFeedback = true,
            slots = mapOf(
                // Les trois roles d'origine : precedent, demarrer, rechercher. L'appui
                // long sur "precedent" ouvrait le selecteur de taches, comme sur WP.
                Slot.LEFT to SlotConfig("backarrow", NavAction.BACK, NavAction.RECENTS),
                Slot.CENTER to SlotConfig("metrocolor", NavAction.HOME, NavAction.NONE, 118),
                Slot.RIGHT to SlotConfig("search", NavAction.GLOBAL_SEARCH, NavAction.NONE),
            ),
        )
    }
}

/**
 * Persistance. Un seul fichier de preferences, relu a chaque acces : la barre vit dans
 * un service et l'ecran de reglages dans une activite, donc deux process-locaux
 * differents qui doivent voir la meme chose sans se synchroniser a la main.
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): NavBarConfig {
        val d = NavBarConfig.DEFAULT
        return NavBarConfig(
            barColorKey = prefs.getString("bar_color", d.barColorKey) ?: d.barColorKey,
            iconColorKey = prefs.getString("icon_color", d.iconColorKey) ?: d.iconColorKey,
            barHeightDp = prefs.getInt("bar_height", d.barHeightDp),
            iconSizePct = prefs.getInt("icon_pct", d.iconSizePct),
            haptic = Haptic.from(prefs.getString("haptic", d.haptic.key)),
            pressFeedback = prefs.getBoolean("press_feedback", d.pressFeedback),
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            mode = BarMode.from(prefs.getString("bar_mode", d.mode.key)),
            hideFullscreen = prefs.getBoolean("hide_fullscreen", d.hideFullscreen),
            hideLockscreen = prefs.getBoolean("hide_lockscreen", d.hideLockscreen),
            slots = Slot.entries.associateWith { slot ->
                val fallback = d.slots.getValue(slot)
                SlotConfig(
                    iconKey = prefs.getString("${slot.key}_icon", fallback.iconKey)
                        ?: fallback.iconKey,
                    tap = NavAction.from(prefs.getString("${slot.key}_tap", fallback.tap.key)),
                    hold = NavAction.from(prefs.getString("${slot.key}_hold", fallback.hold.key)),
                    iconScalePct = prefs.getInt("${slot.key}_scale", fallback.iconScalePct),
                    tapPayload = prefs.getString("${slot.key}_tap_payload", null),
                    holdPayload = prefs.getString("${slot.key}_hold_payload", null),
                    tapPayloadLabel = prefs.getString("${slot.key}_tap_payload_label", null),
                    holdPayloadLabel = prefs.getString("${slot.key}_hold_payload_label", null),
                )
            },
        )
    }

    fun save(config: NavBarConfig) {
        prefs.edit().apply {
            putString("bar_color", config.barColorKey)
            putString("icon_color", config.iconColorKey)
            putInt("bar_height", config.barHeightDp)
            putInt("icon_pct", config.iconSizePct)
            putString("haptic", config.haptic.key)
            putBoolean("press_feedback", config.pressFeedback)
            putBoolean("dynamic_color", config.dynamicColor)
            putString("bar_mode", config.mode.key)
            putBoolean("hide_fullscreen", config.hideFullscreen)
            putBoolean("hide_lockscreen", config.hideLockscreen)
            config.slots.forEach { (slot, sc) ->
                putString("${slot.key}_icon", sc.iconKey)
                putString("${slot.key}_tap", sc.tap.key)
                putString("${slot.key}_hold", sc.hold.key)
                putInt("${slot.key}_scale", sc.iconScalePct)
                putString("${slot.key}_tap_payload", sc.tapPayload)
                putString("${slot.key}_hold_payload", sc.holdPayload)
                putString("${slot.key}_tap_payload_label", sc.tapPayloadLabel)
                putString("${slot.key}_hold_payload_label", sc.holdPayloadLabel)
            }
        }.apply()
    }

    fun reset() = prefs.edit().clear().apply()

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val FILE = "navbar"
    }
}
