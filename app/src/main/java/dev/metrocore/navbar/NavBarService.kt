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
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

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
        val reserved = SystemBars.reserved(this)
        builtWith = reserved

        val edge = when (config.placement) {
            BarPlacement.SYSTEM -> reserved.edge
            BarPlacement.SCREEN_BOTTOM -> BarEdge.BOTTOM
        }

        val view = NavBarView.build(this, config, haptics, edge) { action, payload ->
            action.perform(this, payload)
        }

        val thicknessPx = NavBarView.dp(this, config.resolvedHeightDp(this))

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
            val now = SystemBars.reserved(this)
            if (now != builtWith) {
                rebuild()
                return
            }
        }

        val hidden = (config.hideLockscreen && isLocked()) ||
            (config.hideFullscreen && isFullscreen()) ||
            (config.mode == BarMode.FITTED && SystemBars.isGestureNav(this))

        val target = if (hidden) View.GONE else View.VISIBLE
        if (view.visibility != target) view.visibility = target
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

        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        val rect = Rect()

        val navBarShown = open.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            window.getBoundsInScreen(rect)
            if (rect.isEmpty) return@any false

            // Une bande mince collee a un bord. En paysage elle passe sur le cote, donc
            // les trois cas comptent. Le plafond d'epaisseur ecarte les fenetres systeme
            // qui touchent un bord en couvrant l'ecran — volet de notifications deploye,
            // menu marche/arret.
            val bottom = rect.bottom >= screenHeight - EDGE_SLACK &&
                rect.height() <= screenHeight / 4
            val right = rect.right >= screenWidth - EDGE_SLACK &&
                rect.width() <= screenWidth / 4
            val left = rect.left <= EDGE_SLACK && rect.width() <= screenWidth / 4

            bottom || right || left
        }

        if (DEBUG) logWindows(open, navBarShown)

        // Tant qu'on n'a jamais reconnu de barre de navigation ici, on ne sait pas lire
        // cet appareil — et on ne masque rien. Sans ce garde-fou, un fabricant qui
        // exposerait ses barres autrement verrait La Barre disparaitre en permanence, ce
        // qui est bien pire que de ne jamais se masquer.
        if (navBarShown) {
            sawNavBar = true
            return false
        }
        return sawNavBar
    }

    private fun logWindows(open: List<AccessibilityWindowInfo>, navBarShown: Boolean) {
        val rect = Rect()
        val inventory = open.joinToString(" | ") { window ->
            window.getBoundsInScreen(rect)
            "type=${window.type} $rect"
        }
        Log.d(TAG, "navBar=$sawNavBar/$navBarShown fenetres: $inventory")
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
