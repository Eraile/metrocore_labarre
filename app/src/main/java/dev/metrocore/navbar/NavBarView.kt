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
    fun build(
        context: Context,
        config: NavBarConfig,
        haptics: Haptics,
        onAction: (NavAction, String?) -> Unit,
    ): LinearLayout {
        val widthDp = context.resources.configuration.screenWidthDp
        val tint = ColorStateList.valueOf(config.iconColor(context))

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(config.barColor(context))
            isBaselineAligned = false
        }

        for (slot in Slot.entries) {
            val sc = config.slots.getValue(slot)
            val hold = sc.hold.takeIf { it != NavAction.NONE }
            val icon = navIcon(sc.iconKey)
            val iconPx = dp(context, config.resolvedIconDp(widthDp, slot))

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
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )

            // La glyphe est carree et centree : on borne par le padding vertical.
            key.post {
                val pad = ((key.height - iconPx) / 2).coerceAtLeast(0)
                key.setPadding(0, pad, 0, pad)
            }
        }

        return bar
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
