/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.widget.LinearLayout

/**
 * La barre elle-meme : trois touches de largeur egale sur un fond plein.
 *
 * Construite en code plutot qu'en XML parce que les memes vues servent a deux endroits
 * — l'overlay du service et l'apercu de l'ecran de reglages — et que tout y est pilote
 * par [NavBarConfig].
 */
object NavBarView {

    /**
     * @param onAction recoit l'action et sa cible eventuelle. Le service la joue
     *                 vraiment ; l'apercu des reglages se contente de l'annoncer.
     */
    /**
     * @param edge le bord de l'ecran occupe. En paysage la bande systeme passe souvent
     *             sur le cote, et La Barre la suit : elle devient verticale, et l'ordre
     *             des touches suit le telephone plutot que l'ecran — voir [ordered].
     */
    fun build(
        context: Context,
        config: NavBarConfig,
        haptics: Haptics,
        edge: BarEdge = BarEdge.BOTTOM,
        onAction: (NavAction, String?) -> Unit,
    ): LinearLayout {
        val tint = ColorStateList.valueOf(config.iconColor(context))

        val bar = LinearLayout(context).apply {
            orientation =
                if (edge.isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setBackgroundColor(config.barColor(context))
            isBaselineAligned = false
        }

        for (slot in ordered(edge)) {
            val sc = config.slots.getValue(slot)
            val hold = sc.hold.takeIf { it != NavAction.NONE }
            val icon = navIcon(sc.iconKey)
            val iconPx = dp(context, config.resolvedIconDp(context, slot))

            val key = NavKeyView(
                context = context,
                haptics = haptics,
                onTap = { onAction(sc.tap, sc.tapPayload) },
                onHold = hold?.let { action -> { onAction(action, sc.holdPayload) } },
            ).apply {
                hapticLevel = config.haptic
                pressFeedback = config.pressFeedback
                setImageResource(icon.res)
                // Une glyphe qui porte ses propres couleurs serait aplatie par un tint.
                imageTintList = if (icon.tintable) tint else null
                contentDescription = context.getString(sc.tap.labelRes)
            }

            bar.addView(
                key,
                if (edge.isVertical) {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                } else {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                },
            )

            // La glyphe est carree et centree : on la borne sur les deux axes, la touche
            // etant large et basse en horizontal, haute et etroite en vertical.
            key.post {
                val padV = ((key.height - iconPx) / 2).coerceAtLeast(0)
                val padH = ((key.width - iconPx) / 2).coerceAtLeast(0)
                key.setPadding(padH, padV, padH, padV)
            }
        }

        return bar
    }

    /**
     * L'ordre des touches le long de la bande.
     *
     * Les touches de Windows Phone etaient capacitives : elles ne bougeaient pas avec
     * l'ecran, elles restaient sur le bord bas de l'appareil. C'est la regle qu'on
     * reproduit — en paysage, le bas de l'appareil est un cote de l'ecran, et les
     * touches gardent leur position *physique*.
     *
     * Telephone tourne dans le sens antihoraire : le bas naturel arrive a droite, et
     * « precedent », qui etait a gauche de ce bord, se retrouve donc **en bas** de la
     * bande. Dans l'autre sens, il se retrouve en haut.
     *
     * C'est aussi pour cette raison que les glyphes ne pivotent pas : une touche
     * capacitive est serigraphiee sur la coque, elle ne tourne pas avec l'affichage.
     */
    private fun ordered(edge: BarEdge): List<Slot> = when (edge) {
        BarEdge.BOTTOM -> Slot.entries.toList()
        BarEdge.RIGHT -> Slot.entries.reversed()
        BarEdge.LEFT -> Slot.entries.toList()
    }

    fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    fun dpF(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()
}
