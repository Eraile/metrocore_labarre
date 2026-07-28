package dev.metrocore.navbar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilite : affiche la barre en overlay et execute les actions systeme.
 *
 * On utilise TYPE_ACCESSIBILITY_OVERLAY : pas besoin de la permission "Affichage
 * par-dessus les autres applis", le service d'accessibilite suffit.
 *
 * La barre se reconstruit toute seule quand les reglages changent — l'ecran de reglages
 * ecrit dans les SharedPreferences, on ecoute, c'est tout. Pas de binder a maintenir.
 */
class NavBarService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var store: ConfigStore
    private lateinit var haptics: Haptics

    private var windowManager: WindowManager? = null
    private var barView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ConfigStore(this)
        haptics = Haptics(this)
        store.observe(this)
        instance = this
        rebuild()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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
        hideBar()
        instance = null
    }

    private fun rebuild() {
        hideBar()

        val config = store.load()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = NavBarView.build(this, config, haptics) { action, payload ->
            action.perform(this, payload)
        }

        val widthDp = resources.configuration.screenWidthDp
        val heightPx = NavBarView.dp(this, config.resolvedHeightDp(widthDp))

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
            .onSuccess { barView = view }
    }

    private fun hideBar() {
        val view = barView ?: return
        runCatching { windowManager?.removeView(view) }
        barView = null
    }

    companion object {
        /** Non nul tant que le service est actif. */
        @Volatile
        var instance: NavBarService? = null
            private set
    }
}
