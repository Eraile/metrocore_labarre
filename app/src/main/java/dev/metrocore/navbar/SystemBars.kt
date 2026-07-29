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

/** Le bord de l'ecran ou le systeme reserve sa bande de navigation. */
enum class BarEdge {
    BOTTOM,
    LEFT,
    RIGHT,
    ;

    val isVertical: Boolean get() = this != BOTTOM
}

/** Le bord occupe et l'epaisseur de la bande, en dp. */
data class Reserved(val edge: BarEdge, val sizeDp: Int)

/**
 * Ce que le systeme reserve deja pour sa propre barre de navigation.
 *
 * Aucun overlay ne peut reserver d'inset : ni TYPE_ACCESSIBILITY_OVERLAY, ni
 * TYPE_APPLICATION_OVERLAY. Seul le systeme le fait, pour sa propre barre. La Barre se
 * pose donc toujours *par-dessus*, et la seule facon de ne pas mordre sur le contenu est
 * de se caler exactement sur la zone que le systeme a deja mise de cote.
 *
 * Cette zone n'est pas toujours en bas. Beaucoup d'appareils font passer leur barre de
 * navigation sur le cote en paysage — a droite quand le bas naturel du telephone se
 * retrouve a droite, a gauche dans l'autre sens. Suivre le referentiel veut donc dire
 * suivre le bord, et pas seulement l'epaisseur : voir [reserved].
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
     * ou barre systeme masquee. Il n'y a alors aucune place a prendre, et tout ce qu'on
     * dessine est pris sur le contenu.
     */
    fun isGestureNav(context: Context): Boolean = reserved(context).sizeDp < USABLE_MIN_DP

    /**
     * Le bord et l'epaisseur de la bande systeme. `sizeDp` vaut 0 si le systeme ne
     * reserve rien ou si la mesure echoue — le bord est alors [BarEdge.BOTTOM], qui est
     * le cas de tous les telephones en portrait et le repli le plus sur.
     *
     * Seuls les insets savent de quel cote se trouve la bande : la rotation ne suffit
     * pas, beaucoup d'appareils gardent leur barre en bas meme en paysage. Quand les
     * insets ne repondent pas, on retombe sur la ressource systeme, qui ne donne qu'une
     * hauteur — donc le bord bas.
     */
    fun reserved(context: Context): Reserved {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = (px / density).roundToInt()

        // currentWindowMetrics veut un contexte visuel. Un service n'en est pas un au
        // sens strict, meme s'il tient un WindowManager pour son overlay : selon les
        // versions ca journalise, ca renvoie zero ou ca leve. On tente, et on retombe
        // sur la ressource systeme — qui existe depuis toujours.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.navigationBars())
            }.getOrNull()

            if (insets != null) {
                // L'ordre compte : un appareil qui garde sa barre en bas en paysage a
                // aussi des insets lateraux non nuls a cause de l'encoche.
                when {
                    insets.bottom > 0 -> return Reserved(BarEdge.BOTTOM, dp(insets.bottom))
                    insets.right > 0 -> return Reserved(BarEdge.RIGHT, dp(insets.right))
                    insets.left > 0 -> return Reserved(BarEdge.LEFT, dp(insets.left))
                }
            }
        }

        @SuppressLint("DiscouragedApi")
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val px = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return Reserved(BarEdge.BOTTOM, if (px > 0) dp(px) else 0)
    }
}
