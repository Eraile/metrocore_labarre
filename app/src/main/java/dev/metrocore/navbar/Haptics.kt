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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (level) {
                Haptic.LIGHT -> VibrationEffect.EFFECT_TICK
                Haptic.MEDIUM -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
            // createPredefined peut echouer silencieusement si l'effet n'est pas
            // implemente ; on retombe alors sur une impulsion simple.
            runCatching { vibrator?.vibrate(VibrationEffect.createPredefined(effect)) }
                .onFailure { oneShot(level) }
            return
        }
        oneShot(level)
    }

    /** Un peu plus appuye, pour signaler qu'un appui long vient de partir. */
    fun hold(level: Haptic) {
        if (level == Haptic.OFF || !hasVibrator) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }.onFailure { oneShot(level) }
            return
        }
        oneShot(level)
    }

    private fun oneShot(level: Haptic) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(level.ms, level.amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(level.ms)
        }
    }
}
