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
    private var sawStatusBar = false

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
        hideBar()

        config = store.load()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = NavBarView.build(this, config, haptics) { action, payload ->
            action.perform(this, payload)
        }

        val heightPx = NavBarView.dp(this, config.resolvedHeightDp(this))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                barView = view
                applyVisibility()
            }
    }

    private fun hideBar() {
        val view = barView ?: return
        runCatching { windowManager?.removeView(view) }
        barView = null
    }

    // ---------------------------------------------------------------- masquage

    private fun applyVisibility() {
        val view = barView ?: return

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
     * Plein ecran = la barre d'etat n'est plus a l'ecran.
     *
     * Comparer les limites de la fenetre active a celles de l'ecran ne discrimine rien :
     * depuis Android 15 les applications dessinent bord a bord par defaut, et leur
     * fenetre couvre tout l'ecran meme barres systeme affichees.
     *
     * « Aucune fenetre TYPE_SYSTEM » ne marchait pas non plus : ce type couvre bien plus
     * que les barres — poignee de geste, fenetres systeme flottantes — et il en reste
     * presque toujours une, donc la condition n'etait jamais vraie. On cherche donc la
     * barre d'etat *nommement* : la seule fenetre systeme collee au bord haut et basse.
     * Le plafond de hauteur ecarte le volet de notifications deploye, qui part aussi du
     * haut mais couvre l'ecran.
     *
     * `getWindows()` ne donne que des types et des rectangles, pas du contenu ; il ne
     * demande que `flagRetrieveInteractiveWindows`, deja declare.
     */
    private fun isFullscreen(): Boolean {
        val open = runCatching { windows }.getOrNull() ?: return false
        if (open.isEmpty()) return false

        val screenHeight = resources.displayMetrics.heightPixels
        val rect = Rect()

        val statusBarShown = open.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            window.getBoundsInScreen(rect)
            !rect.isEmpty && rect.top <= 0 && rect.height() <= screenHeight / 4
        }

        if (DEBUG) logWindows(open, statusBarShown)

        // Tant qu'on n'a jamais reconnu de barre d'etat ici, on ne sait pas lire cet
        // appareil — et on ne masque rien. Sans ce garde-fou, un fabricant qui exposerait
        // ses barres autrement verrait La Barre disparaitre en permanence, ce qui est
        // bien pire que de ne jamais se masquer. L'ecran de reglages suffit a l'amorcer.
        if (statusBarShown) {
            sawStatusBar = true
            return false
        }
        return sawStatusBar
    }

    private fun logWindows(open: List<AccessibilityWindowInfo>, statusBarShown: Boolean) {
        val rect = Rect()
        val inventory = open.joinToString(" | ") { window ->
            window.getBoundsInScreen(rect)
            "type=${window.type} $rect"
        }
        Log.d(TAG, "statusBar=$sawStatusBar/$statusBarShown fenetres: $inventory")
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

        /** Non nul tant que le service est actif. */
        @Volatile
        var instance: NavBarService? = null
            private set
    }
}
