/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

/**
 * Une touche capacitive.
 *
 * Le comportement est celui de metrocore (`src/shell/navkeys.ts` + `.mc-navkey` dans
 * shell.css) : l'appui fait tomber l'opacite a 45 %, un maintien de 550 ms declenche
 * l'action longue et *annule* l'appui court, et sortir du bouton annule tout.
 *
 * On n'utilise pas `selectableItemBackground` : l'ondulation Material est exactement
 * le genre de detail qui trahit la reconstruction.
 */
@SuppressLint("ViewConstructor")
class NavKeyView(
    context: Context,
    private val haptics: Haptics,
    private val onTap: () -> Unit,
    private val onHold: (() -> Unit)?,
) : ImageView(context) {

    /** Recharges a chaque reconstruction de la barre. */
    var hapticLevel: Haptic = Haptic.LIGHT
    var pressFeedback: Boolean = true

    private var holdFired = false

    private val holdRunnable = Runnable {
        holdFired = true
        release()
        haptics.hold(hapticLevel)
        onHold?.invoke()
    }

    init {
        isClickable = true
        isFocusable = false
        scaleType = ScaleType.FIT_CENTER
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                holdFired = false
                depress()
                haptics.tap(hapticLevel)
                if (onHold != null) postDelayed(holdRunnable, MetroTokens.HOLD_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Un doigt qui glisse hors de la touche annule : c'est la porte de
                // sortie attendue quand on s'est trompe de bouton.
                if (!inside(event)) cancel()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasInside = inside(event)
                val fired = holdFired
                cancel()
                if (!fired && wasInside) {
                    performClick()
                    onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancel()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun inside(event: MotionEvent): Boolean =
        event.x >= 0 && event.y >= 0 && event.x <= width && event.y <= height

    private fun cancel() {
        removeCallbacks(holdRunnable)
        release()
    }

    private fun depress() {
        if (!pressFeedback) return
        animate().cancel()
        animate()
            .alpha(MetroTokens.NAVKEY_DOWN_ALPHA)
            .scaleX(MetroTokens.TILT_DEPRESS)
            .scaleY(MetroTokens.TILT_DEPRESS)
            .setDuration(MetroTokens.DUR_TILT)
            .start()
    }

    private fun release() {
        if (!pressFeedback) {
            alpha = 1f
            return
        }
        animate().cancel()
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(MetroTokens.DUR_TILT_OUT)
            .start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(holdRunnable)
        super.onDetachedFromWindow()
    }
}

/** Raccourci de lisibilite pour la construction de la barre. */
internal fun View.setSquarePadding(px: Int) = setPadding(px, px, px, px)
