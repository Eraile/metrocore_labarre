/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Retour haptique d'une touche.
 *
 * On privilegie les effets predefinis du systeme quand ils existent (API 29+) : ils sont
 * cales sur le moteur haptique reel de l'appareil, la ou une duree en ms donne un
 * bourdonnement different sur chaque telephone.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

    /** Le petit "tic" d'un appui court. */
    fun tap(level: Haptic) {
        if (level == Haptic.OFF || !hasVibrator) return

        // Les effets predefinis sont mis a l'echelle par le reglage systeme et restent
        // discrets sur beaucoup d'appareils : seul le niveau leger s'appuie encore sur
        // le plus marque d'entre eux ; au-dela, impulsion directe a forte amplitude.
        if (level == Haptic.LIGHT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // createPredefined peut echouer silencieusement si l'effet n'est pas
            // implemente ; on retombe alors sur une impulsion simple.
            runCatching {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }.onFailure { oneShot(level.ms, level.amplitude) }
            return
        }
        oneShot(level.ms, level.amplitude)
    }

    /** Un peu plus appuye, pour signaler qu'un appui long vient de partir. */
    fun hold(level: Haptic) {
        if (level == Haptic.OFF || !hasVibrator) return
        oneShot(level.holdMs, level.amplitude)
    }

    private fun oneShot(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
