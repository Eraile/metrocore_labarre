/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Ce qu'un bouton declenche.
 *
 * Deux familles. Les actions systeme passent par `performGlobalAction`, la seule voie
 * qu'un service d'accessibilite a pour piloter le shell. Les autres lancent une
 * activite — c'est le seul moyen d'atteindre la recherche, qui n'est pas une action
 * globale.
 *
 * Sur la recherche : Windows Phone indexait localement contacts, messages, musique et
 * applications, et posait le web par-dessus, le tout derriere une seule touche. Android
 * n'a pas cet equivalent — l'index local appartient au lanceur et n'est pas expose.
 * [GLOBAL_SEARCH] est ce qui s'en approche le plus (l'ancienne Quick Search Box, encore
 * presente sur beaucoup d'appareils), [ASSISTANT] ouvre l'assistant, et [WEB_SEARCH] ne
 * fait que le web.
 */
enum class NavAction(
    val key: String,
    val labelRes: Int,
    private val globalAction: Int? = null,
    private val minSdk: Int = Build.VERSION_CODES.N,
    private val intents: List<() -> Intent> = emptyList(),
) {
    NONE("none", R.string.act_none),

    BACK("back", R.string.act_back, AccessibilityService.GLOBAL_ACTION_BACK),
    HOME("home", R.string.act_home, AccessibilityService.GLOBAL_ACTION_HOME),
    RECENTS("recents", R.string.act_recents, AccessibilityService.GLOBAL_ACTION_RECENTS),
    NOTIFICATIONS(
        "notifications",
        R.string.act_notifications,
        AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
    ),
    QUICK_SETTINGS(
        "quick_settings",
        R.string.act_quick_settings,
        AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
    ),
    POWER_DIALOG(
        "power_dialog",
        R.string.act_power_dialog,
        AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
    ),
    SPLIT_SCREEN(
        "split_screen",
        R.string.act_split_screen,
        AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,
    ),
    LOCK_SCREEN(
        "lock_screen",
        R.string.act_lock_screen,
        AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
        Build.VERSION_CODES.P,
    ),
    SCREENSHOT(
        "screenshot",
        R.string.act_screenshot,
        AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        Build.VERSION_CODES.P,
    ),

    /**
     * La recherche systeme. On tente d'abord la Quick Search Box — c'est elle qui
     * cherchait a la fois dans l'appareil et sur le web — puis l'assistant, puis le web
     * seul. Le premier qui repond gagne.
     */
    GLOBAL_SEARCH(
        "global_search",
        R.string.act_global_search,
        intents = listOf(
            { Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH) },
            { Intent(Intent.ACTION_ASSIST) },
            { Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "") },
        ),
    ),
    ASSISTANT(
        "assistant",
        R.string.act_assistant,
        intents = listOf({ Intent(Intent.ACTION_ASSIST) }),
    ),
    VOICE_SEARCH(
        "voice_search",
        R.string.act_voice_search,
        intents = listOf(
            { Intent(RecognizerIntent.ACTION_WEB_SEARCH) },
            { Intent(Intent.ACTION_VOICE_COMMAND) },
        ),
    ),
    WEB_SEARCH(
        "web_search",
        R.string.act_web_search,
        intents = listOf({ Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "") }),
    ),

    /** Ouvre une application choisie. La cible est dans la charge utile. */
    LAUNCH_APP("launch_app", R.string.act_launch_app),

    /**
     * Declenche une action rapide.
     *
     * Les raccourcis que le systeme affiche sous l'icone d'une application ne sont
     * lisibles que par le lanceur par defaut (`LauncherApps` exige ce role). On passe
     * donc par `ACTION_CREATE_SHORTCUT` : c'est le selecteur que l'application
     * elle-meme propose, et il rend un intent qu'on peut rejouer.
     */
    SHORTCUT("shortcut", R.string.act_shortcut),
    ;

    val available: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    /** Vrai si l'action a besoin qu'on lui designe une cible. */
    val needsPayload: Boolean get() = this == LAUNCH_APP || this == SHORTCUT

    /**
     * @param payload pour [LAUNCH_APP] et [SHORTCUT], l'intent serialise par
     *                `Intent.toUri(URI_INTENT_SCHEME)`. Ignore sinon.
     */
    fun perform(service: AccessibilityService, payload: String? = null): Boolean {
        if (!available) return false

        globalAction?.let { return service.performGlobalAction(it) }

        if (needsPayload) {
            val uri = payload ?: return false
            val intent = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }
                .getOrNull() ?: return false
            return launch(service, intent)
        }

        if (intents.isEmpty()) return false

        // On essaie sans interroger le PackageManager : la visibilite des paquets
        // (Android 11+) rend resolveActivity peu fiable, alors qu'un lancement rate
        // leve simplement ActivityNotFoundException.
        for (build in intents) {
            if (launch(service, build(), quiet = true)) return true
        }

        Toast.makeText(service, R.string.toast_no_handler, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun launch(
        service: AccessibilityService,
        intent: Intent,
        quiet: Boolean = false,
    ): Boolean {
        // Un service n'a pas de pile d'activites : sans NEW_TASK le lancement echoue.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            service.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (!quiet) Toast.makeText(service, R.string.toast_no_handler, Toast.LENGTH_SHORT).show()
            false
        } catch (_: SecurityException) {
            if (!quiet) Toast.makeText(service, R.string.toast_no_handler, Toast.LENGTH_SHORT).show()
            false
        }
    }

    companion object {
        fun from(key: String?): NavAction = entries.firstOrNull { it.key == key } ?: NONE

        /** Celles qu'on peut reellement proposer sur cet appareil. */
        fun selectable(): List<NavAction> = entries.filter { it.available }
    }
}
