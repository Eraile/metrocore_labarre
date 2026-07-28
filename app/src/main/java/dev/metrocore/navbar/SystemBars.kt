/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Ce que le systeme reserve deja en bas de l'ecran.
 *
 * Aucun overlay ne peut reserver d'inset : ni TYPE_ACCESSIBILITY_OVERLAY, ni
 * TYPE_APPLICATION_OVERLAY. Seul le systeme le fait, pour sa propre barre. La Barre se
 * pose donc toujours *par-dessus*, et la seule facon de ne pas mordre sur le contenu est
 * de se caler exactement sur la zone que le systeme a deja mise de cote — ce que rend
 * [navigationBarHeightDp].
 *
 * En navigation a 3 boutons cette zone fait la hauteur de la barre systeme : La Barre la
 * recouvre au pixel pres et rien n'est perdu. En navigation gestuelle elle se reduit a
 * la poignee (~24 dp), et une barre de cette hauteur est petite mais honnete : c'est
 * tout ce qui est libre.
 */
object SystemBars {

    /**
     * En dessous, l'espace reserve n'est plus une bande de boutons mais la poignee de
     * la navigation gestuelle. 40 dp separe proprement les deux cas : une barre systeme
     * a 3 boutons fait 48 dp, une poignee gestuelle 16 a 24.
     *
     * C'est aussi la limite du raisonnable pour une cible tactile — meme [MetroTokens
     * .TOUCH_MIN], deja bas a 34 dp, est au-dessus d'une poignee.
     */
    const val USABLE_MIN_DP = 40

    /**
     * Vrai quand le systeme ne reserve pas de bande de boutons : navigation gestuelle,
     * ou barre systeme masquee. Il n'y a alors aucune place a prendre en bas de l'ecran,
     * et tout ce qu'on y dessine est pris sur le contenu.
     */
    fun isGestureNav(context: Context): Boolean =
        navigationBarHeightDp(context) < USABLE_MIN_DP

    /** 0 si le systeme ne reserve rien, ou si la mesure echoue. */
    fun navigationBarHeightDp(context: Context): Int {
        val px = navigationBarHeightPx(context)
        if (px <= 0) return 0
        return (px / context.resources.displayMetrics.density).roundToInt()
    }

    private fun navigationBarHeightPx(context: Context): Int {
        // currentWindowMetrics veut un contexte visuel. Un service n'en est pas un au
        // sens strict, meme s'il tient un WindowManager pour son overlay : selon les
        // versions ca journalise, ca renvoie zero ou ca leve. On tente, et on retombe
        // sur la ressource systeme — qui existe depuis toujours et suffit ici.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val measured = runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.navigationBars())
                    .bottom
            }.getOrDefault(0)
            if (measured > 0) return measured
        }

        @SuppressLint("DiscouragedApi")
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
