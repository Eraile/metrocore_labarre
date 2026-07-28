/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Une version publiee sur GitHub, et l'APK qui va avec. */
data class Update(val version: String, val apkUrl: String)

/**
 * La mise a jour depuis les Releases GitHub.
 *
 * La Barre se distribue hors Play (voir SIGNING.md), donc personne ne pousse les mises a
 * jour a sa place : sans ce mecanisme, une correction n'atteint que ceux qui repassent
 * d'eux-memes sur la page du depot.
 *
 * Rien n'est automatique — ni verification au demarrage, ni telechargement en fond. On
 * ne va sur le reseau que si l'utilisateur appuie sur la ligne, et l'installation reste
 * celle d'Android, avec son ecran de confirmation. Une application qui s'installe toute
 * seule est exactement ce dont il faut se mefier.
 */
object Updates {

    private const val LATEST =
        "https://api.github.com/repos/Eraile/metrocore_labarre/releases/latest"

    private const val TIMEOUT_MS = 15_000

    /** Le nom de version de l'APK installe, lu du paquet et non d'une constante. */
    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /**
     * La derniere version publiee, ou `null` s'il n'y a rien de plus recent — et aussi
     * si le reseau, l'API ou le format ne repondent pas. L'appelant ne distingue pas les
     * deux : dans les deux cas il n'y a rien a proposer.
     *
     * A appeler hors du fil principal.
     */
    fun latest(context: Context): Update? = runCatching {
        val body = get(LATEST) ?: return null
        val json = JSONObject(body)

        // Les tags du depot n'ont pas de prefixe (« 1.0.0 »), mais on accepte « v1.0.0 »
        // au cas ou une release serait taguee autrement un jour.
        val tag = json.getString("tag_name").removePrefix("v").trim()
        if (!isNewer(tag, currentVersion(context))) return null

        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return Update(tag, asset.getString("browser_download_url"))
            }
        }
        null
    }.getOrNull()

    /**
     * Compare deux versions champ par champ — « 1.10 » est plus recent que « 1.9 », ce
     * qu'une comparaison de chaines aurait inverse. Les champs manquants valent zero,
     * pour que « 1.1 » et « 1.1.0 » soient la meme version.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = fields(candidate)
        val b = fields(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun fields(version: String): List<Int> =
        version.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /**
     * Telecharge l'APK dans le cache de l'application. Retourne `null` en cas d'echec —
     * le fichier partiel est alors supprime, pour ne pas garder de quoi installer un
     * binaire tronque.
     *
     * A appeler hors du fil principal.
     */
    fun download(context: Context, update: Update): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, "la-barre-${update.version}.apk")

        return runCatching {
            connect(update.apkUrl).use { stream ->
                apk.outputStream().use { out -> stream.copyTo(out) }
            }
            apk
        }.getOrElse {
            apk.delete()
            null
        }
    }

    /**
     * Passe la main a l'installeur d'Android. Depuis API 26 il faut en plus que
     * l'utilisateur ait autorise l'application a installer des paquets ; le cas echeant
     * on l'emmene au bon ecran plutot que d'echouer sans rien dire.
     *
     * @return faux si l'autorisation manque, ou si l'installeur n'a pas pu etre ouvert —
     *         l'ecran de reglages a alors ete propose. Rien ne remonte en exception : un
     *         echec ici ne doit pas emporter l'ecran de reglages avec lui.
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
                    )
                },
            )
            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ reseau

    private fun get(url: String): String? = runCatching {
        connect(url).use { it.reader().readText() }
    }.getOrNull()

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        // L'API GitHub repond du JSON sans cle ni jeton pour les depots publics ; elle
        // exige en revanche un User-Agent, et refuse la requete sans.
        setRequestProperty("User-Agent", "la-barre")
        setRequestProperty("Accept", "application/vnd.github+json")
    }.inputStream
}
