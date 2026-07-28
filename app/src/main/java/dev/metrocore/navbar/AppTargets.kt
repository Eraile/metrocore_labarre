/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Une application lancable, telle qu'elle apparait dans le selecteur. */
data class AppTarget(
    val label: String,
    val icon: Drawable?,
    /** L'intent de lancement, serialise — c'est ce qu'on stocke et rejoue. */
    val uri: String,
)

/**
 * Inventaire des applications lancables.
 *
 * Depuis Android 11 la liste des paquets installes est cloisonnee : il faut declarer
 * dans le manifeste ce qu'on cherche. Le bloc `<queries>` de AndroidManifest.xml
 * demande explicitement les activites MAIN/LAUNCHER, c'est ce qui rend cette requete
 * complete au lieu de ne rendre que nos propres activites.
 */
object AppTargets {

    fun launchable(context: Context): List<AppTarget> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(query, 0)

        return resolved
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(activity.packageName, activity.name)

                AppTarget(
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    uri = intent.toUri(Intent.URI_INTENT_SCHEME),
                )
            }
            .distinctBy { it.uri }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Les applications qui savent proposer une action rapide.
     *
     * `ACTION_CREATE_SHORTCUT` est l'ancien contrat de raccourci : l'application ouvre
     * son propre selecteur et rend un intent. C'est le seul chemin accessible ici — les
     * raccourcis affiches sous l'icone d'une application (`LauncherApps`) sont reserves
     * au lanceur par defaut.
     */
    fun shortcutProviders(context: Context): List<AppTarget> {
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)

        return resolved
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
                    .setClassName(activity.packageName, activity.name)

                AppTarget(
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    uri = intent.toUri(Intent.URI_INTENT_SCHEME),
                )
            }
            .distinctBy { it.uri }
            .sortedBy { it.label.lowercase() }
    }

    /** Le nom lisible d'une cible enregistree, ou null si l'app a disparu depuis. */
    fun labelOf(context: Context, uri: String?): String? {
        if (uri == null) return null
        val intent = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }
            .getOrNull() ?: return null

        val pm = context.packageManager
        val component = intent.component ?: return null
        return runCatching {
            pm.getActivityInfo(component, PackageManager.GET_META_DATA).loadLabel(pm).toString()
        }.getOrNull()
    }
}
