/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.roundToInt

/**
 * Service d'accessibilite : affiche la barre en overlay et execute les actions systeme.
 *
 * On utilise TYPE_ACCESSIBILITY_OVERLAY : pas besoin de la permission "Affichage
 * par-dessus les autres applis", le service d'accessibilite suffit.
 *
 * La barre se reconstruit toute seule quand les reglages changent — l'ecran de reglages
 * ecrit dans les SharedPreferences, on ecoute, c'est tout. Pas de binder a maintenir.
 *
 * Deux raisons de s'effacer sans se detruire : le plein ecran et le verrouillage. Les
 * deux passent par [applyVisibility], jamais par [rebuild] — retirer puis rajouter la
 * vue a chaque changement d'application ferait clignoter la barre.
 */
class NavBarService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var store: ConfigStore
    private lateinit var haptics: Haptics

    private var windowManager: WindowManager? = null
    private var barView: View? = null
    private var config = NavBarConfig.DEFAULT

    /** Voir [isFullscreen] : on ne masque pas tant qu'on ne sait pas lire l'appareil. */
    private var sawNavBar = false

    /**
     * Le dernier bord ou l'on a vu la bande systeme, pour chaque rotation d'ecran. Sert a
     * tenir la position quand elle disparait — plein ecran, barre masquee. Voir
     * [measureBand].
     */
    private val edgeByRotation = mutableMapOf<Int, BarEdge>()

    /**
     * La bande systeme telle qu'elle etait au moment de construire la vue. Sert a
     * rattraper la rotation : voir [applyVisibility].
     */
    private var builtWith: Reserved? = null

    /** Garde-fou : [rebuild] appelle [applyVisibility], qui peut rappeler [rebuild]. */
    private var rebuilding = false

    /**
     * Le verrouillage ne produit pas toujours d'evenement d'accessibilite exploitable —
     * l'ecran s'eteint et rien ne bouge cote fenetres. On ecoute donc aussi l'ecran.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = applyVisibility()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ConfigStore(this)
        haptics = Haptics(this)
        store.observe(this)
        instance = this

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )

        rebuild()
    }

    /**
     * Le seul signal dont on a besoin : quelque chose a change au premier plan, on
     * reevalue s'il faut se montrer. Aucun contenu n'est lu — le service declare
     * `canRetrieveWindowContent="false"` et ca reste vrai.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> applyVisibility()
        }
    }

    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        rebuild()
    }

    /** La rotation change la largeur d'ecran, donc la hauteur de barre en automatique. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuild()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (::store.isInitialized) store.unobserve(this)
        runCatching { unregisterReceiver(screenReceiver) }
        hideBar()
        instance = null
    }

    private fun rebuild() {
        rebuilding = true
        hideBar()

        config = store.load()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // Le bord que le systeme reserve, et non le bas par principe : en paysage la
        // bande systeme passe souvent sur le cote, et suivre le referentiel veut dire
        // suivre le bord.
        // `builtWith` garde la mesure brute, pas le bord retenu : c'est elle qu'on
        // comparera pour rattraper une rotation, et elle ne depend pas du reglage.
        val reserved = measureBand()
        builtWith = reserved

        val edge = when (config.placement) {
            BarPlacement.SYSTEM -> reserved.edge
            BarPlacement.SCREEN_BOTTOM -> BarEdge.BOTTOM
        }

        val view = NavBarView.build(this, config, haptics, edge) { action, payload ->
            action.perform(this, payload)
        }

        val thicknessPx = NavBarView.dp(this, config.resolvedHeightDp(this, reserved))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            if (edge.isVertical) thicknessPx else WindowManager.LayoutParams.MATCH_PARENT,
            if (edge.isVertical) WindowManager.LayoutParams.MATCH_PARENT else thicknessPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // LEFT et RIGHT plutot que START et END : on vise un bord physique de
            // l'ecran, qu'aucun sens de lecture ne doit pouvoir retourner.
            gravity = when (edge) {
                BarEdge.BOTTOM -> Gravity.BOTTOM or Gravity.LEFT
                BarEdge.LEFT -> Gravity.LEFT or Gravity.TOP
                BarEdge.RIGHT -> Gravity.RIGHT or Gravity.TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching { wm.addView(view, params) }
            .onSuccess { barView = view }

        rebuilding = false
        applyVisibility()
    }

    private fun hideBar() {
        val view = barView ?: return
        runCatching { windowManager?.removeView(view) }
        barView = null
    }

    // ---------------------------------------------------------------- masquage

    private fun applyVisibility() {
        val view = barView ?: return

        // A la rotation, `onConfigurationChanged` arrive *avant* que les insets ne soient
        // a jour : on reconstruit alors sur l'ancien bord, et la barre reste du mauvais
        // cote. Les evenements de fenetre, eux, arrivent une fois la rotation posee — on
        // en profite pour rattraper l'ecart. Mesure a l'appui : sans ce rattrapage, un
        // passage de paysage a paysage inverse laissait la barre a droite.
        if (!rebuilding) {
            val now = measureBand()
            if (now != builtWith) {
                rebuild()
                return
            }
        }

        val hidden = (config.hideLockscreen && isLocked()) ||
            (config.hideFullscreen && isFullscreen()) ||
            (config.mode == BarMode.FITTED && builtWith?.isGesture == true)

        setHidden(view, hidden)
    }

    /**
     * Le passage se fait en fondu plutot que d'un coup.
     *
     * Une barre qui apparait sans transition apres un geste donne l'impression d'avoir
     * mis du temps a repondre — le retour visuel arrive apres coup, alors qu'un fondu
     * demarre immediatement. C'est le meme raisonnement que l'enfoncement des touches, et
     * ca reprend sa duree : [MetroTokens.DUR_BASE].
     *
     * On anime l'opacite et non la position : la fenetre de l'overlay fait exactement la
     * taille de la barre, un glissement serait donc rogne par ses propres bords.
     */
    private fun setHidden(view: View, hidden: Boolean) {
        val targetAlpha = if (hidden) 0f else 1f
        if (view.alpha == targetAlpha && (view.visibility == View.GONE) == hidden) return

        view.animate().cancel()
        if (hidden) {
            view.animate()
                .alpha(0f)
                .setDuration(MetroTokens.DUR_BASE)
                .withEndAction { view.visibility = View.GONE }
                .start()
        } else {
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(MetroTokens.DUR_BASE)
                .start()
        }
    }

    private fun isLocked(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isKeyguardLocked == true
    }

    /**
     * Plein ecran = la barre de navigation systeme n'est plus a l'ecran.
     *
     * C'est **la barre de navigation** qui sert de repere, et pas la barre d'etat : La
     * Barre occupe sa place, elle doit donc s'effacer exactement quand celle-ci
     * s'efface. Mesure a l'appui — l'appareil photo masque la barre d'etat mais garde
     * la barre de navigation, et se fier a la premiere faisait disparaitre La Barre
     * alors qu'il restait la place.
     *
     * Deux criteres plus simples ont ete essayes et mesures faux :
     *
     * - *comparer les limites de la fenetre active a l'ecran* — depuis le bord a bord
     *   impose, une application couvre tout l'ecran barres affichees. Sur l'ecran
     *   d'accueil deja : `type=1 Rect(0, 0 - 1080, 2400)` sur un ecran de 2400.
     * - *l'absence de toute fenetre TYPE_SYSTEM* — il en reste presque toujours une.
     *
     * `rootWindowInsets`, qui aurait ete l'API juste, rend `null` sur un overlay
     * d'accessibilite : le systeme ne lui dispatche pas d'insets.
     *
     * `getWindows()` ne donne que des types et des rectangles, jamais du contenu — mais
     * il exige la capacite de lecture, voir [navbar_service_config.xml].
     */
    private fun isFullscreen(): Boolean {
        val open = runCatching { windows }.getOrNull() ?: return false
        if (open.isEmpty()) return false

        val band = systemBand()
        if (DEBUG) logWindows(open, band)

        // Tant qu'on n'a jamais reconnu de barre de navigation ici, on ne sait pas lire
        // cet appareil — et on ne masque rien. Sans ce garde-fou, un fabricant qui
        // exposerait ses barres autrement verrait La Barre disparaitre en permanence, ce
        // qui est bien pire que de ne jamais se masquer.
        if (band != null) {
            sawNavBar = true
            return false
        }
        return sawNavBar
    }

    /**
     * Ou se trouve la bande systeme, lue de la liste des fenetres — bord et epaisseur.
     * `null` quand il n'y en a aucune, ce qui est le critere de plein ecran.
     *
     * **C'est la source de verite.** `currentWindowMetrics.windowInsets` s'est revele
     * inexploitable depuis un service : sur certains appareils il rend encore l'inset du
     * portrait apres une rotation, et ne se rafraichit qu'a l'apparition d'une fenetre
     * systeme. Symptome mesure chez un utilisateur : barre posee en bas en paysage, qui
     * sautait au bon bord des qu'on tirait les reglages rapides — la bonne valeur etait
     * atteignable, on la lisait juste trop tard.
     *
     * La liste des fenetres, elle, donne les bornes vraies au moment ou l'on regarde. Et
     * « ou est la bande » et « y en a-t-il une » deviennent la meme question, posee une
     * seule fois.
     */
    private fun systemBand(): Reserved? {
        // ECHAFAUDAGE DE TEST — a retirer. Simule un plein ecran : la bande disparait de
        // la liste sans qu'il faille lancer un jeu immersif.
        val open = runCatching { windows }.getOrNull() ?: return null

        val metrics = resources.displayMetrics
        val h = metrics.heightPixels
        val w = metrics.widthPixels
        val rect = Rect()

        for (window in open) {
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            window.getBoundsInScreen(rect)
            if (rect.isEmpty) continue

            // Une bande mince collee a un bord, et longue sur l'autre axe. Les deux
            // conditions comptent : le plafond d'epaisseur ecarte le volet de
            // notifications deploye et le menu marche/arret, qui touchent un bord en
            // couvrant l'ecran ; l'exigence de longueur ecarte les vignettes systeme.
            if (rect.bottom >= h - EDGE_SLACK &&
                rect.height() <= h / 4 &&
                rect.width() >= w - EDGE_SLACK
            ) {
                return Reserved(BarEdge.BOTTOM, dp(rect.height(), metrics.density))
            }
            if (rect.right >= w - EDGE_SLACK &&
                rect.width() <= w / 4 &&
                rect.height() >= h - EDGE_SLACK
            ) {
                return Reserved(BarEdge.RIGHT, dp(rect.width(), metrics.density))
            }
            if (rect.left <= EDGE_SLACK &&
                rect.width() <= w / 4 &&
                rect.height() >= h - EDGE_SLACK
            ) {
                return Reserved(BarEdge.LEFT, dp(rect.width(), metrics.density))
            }
        }
        return null
    }

    private fun dp(px: Int, density: Float) = (px / density).roundToInt()

    /**
     * La bande retenue, en prenant a chaque source ce qu'elle sait faire.
     *
     * **Le bord vient de la liste des fenetres** : c'est la seule lecture vivante, et
     * c'est le bord qui etait faux.
     *
     * **L'epaisseur vient des insets**, parce que la liste des fenetres oscille pendant
     * l'animation de rotation — mesure sur emulateur : 98 puis 51 puis 48 dp en 400 ms,
     * la barre suivant l'animation de la barre systeme. Comme [applyVisibility]
     * reconstruit des que la mesure change, chaque valeur intermediaire aurait provoque
     * une reconstruction : la barre aurait clignote a chaque rotation. L'epaisseur est de
     * toute facon peu sensible a la peremption — elle vaut 48 dp dans les deux sens.
     */
    private fun measureBand(): Reserved {
        val insets = SystemBars.reserved(this)
        val band = systemBand()

        if (band != null) {
            edgeByRotation[rotation()] = band.edge
            if (DEBUG) Log.d(TAG, "bande vue=$band memoire=$edgeByRotation insets=$insets")
            return Reserved(band.edge, if (insets.sizeDp > 0) insets.sizeDp else band.sizeDp)
        }

        // Aucune bande visible : l'application est en plein ecran, ou la barre systeme est
        // masquee. Il n'y a alors rien a suivre — mais le bord, lui, n'a pas change : la
        // barre systeme reviendra la ou elle etait. On garde donc le dernier bord observe
        // *dans cette rotation*.
        //
        // Sans cette memoire, on retombait sur les insets, et c'est exactement le cas qui
        // restait casse : dans un jeu en plein ecran en paysage, la barre se posait en bas
        // et ne rejoignait le bon bord qu'au moment ou l'on faisait apparaitre la barre
        // systeme d'un balayage.
        val remembered = edgeByRotation[rotation()] ?: insets.edge
        if (DEBUG) {
            Log.d(TAG, "bande absente — retenu=$remembered memoire=$edgeByRotation insets=$insets")
        }
        return Reserved(remembered, insets.sizeDp)
    }

    /**
     * La rotation de l'ecran, qui sert de cle a [edgeByRotation]. `defaultDisplay` est
     * deprecie mais reste la seule voie depuis un service : `getDisplay()` demande un
     * contexte visuel, et on a deja vu ce que ca donne avec `currentWindowMetrics`.
     */
    @Suppress("DEPRECATION")
    private fun rotation(): Int = runCatching {
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }.getOrDefault(Surface.ROTATION_0)

    private fun logWindows(open: List<AccessibilityWindowInfo>, band: Reserved?) {
        val rect = Rect()
        val inventory = open.joinToString(" | ") { window ->
            window.getBoundsInScreen(rect)
            "type=${window.type} $rect"
        }
        // Les deux sources cote a cote : c'est leur desaccord qui etait le bug.
        Log.d(TAG, "fenetres=$band insets=${SystemBars.reserved(this)} | $inventory")
    }

    companion object {
        /**
         * Passe a `true` pour tracer l'inventaire des fenetres a chaque evaluation, et
         * comprendre ce que l'appareil expose reellement :
         *
         * ```bash
         * adb logcat -s LaBarre
         * ```
         */
        private const val DEBUG = false
        private const val TAG = "LaBarre"

        /** Tolerance en px pour « collee au bord bas » : les bornes ne sont pas exactes. */
        private const val EDGE_SLACK = 8

        /** Non nul tant que le service est actif. */
        @Volatile
        var instance: NavBarService? = null
            private set
    }
}
