/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/*
 * Genere par tools/gen-icons.mjs — ne pas editer a la main.
 * Source : metrocore/src/controls/icons.ts
 */

package dev.metrocore.navbar

import androidx.annotation.DrawableRes

/**
 * Une glyphe disponible pour un bouton de la barre.
 *
 * [tintable] est faux pour les glyphes qui portent leurs propres couleurs : les teinter
 * les aplatirait en une seule teinte.
 */
data class NavIcon(val key: String, @DrawableRes val res: Int, val tintable: Boolean = true)

/** Toutes les glyphes, dans l'ordre d'affichage du selecteur. */
val NAV_ICONS: List<NavIcon> = listOf(
    NavIcon("backarrow", R.drawable.ic_mc_backarrow),
    NavIcon("windows", R.drawable.ic_mc_windows),
    NavIcon("search", R.drawable.ic_mc_search),
    NavIcon("back", R.drawable.ic_mc_back),
    NavIcon("metro", R.drawable.ic_mc_metro),
    NavIcon("metrocolor", R.drawable.ic_mc_metrocolor, tintable = false),
    NavIcon("add", R.drawable.ic_mc_add),
    NavIcon("attach", R.drawable.ic_mc_attach),
    NavIcon("bluetooth", R.drawable.ic_mc_bluetooth),
    NavIcon("calendar", R.drawable.ic_mc_calendar),
    NavIcon("camera", R.drawable.ic_mc_camera),
    NavIcon("check", R.drawable.ic_mc_check),
    NavIcon("clock", R.drawable.ic_mc_clock),
    NavIcon("close", R.drawable.ic_mc_close),
    NavIcon("delete", R.drawable.ic_mc_delete),
    NavIcon("down", R.drawable.ic_mc_down),
    NavIcon("edit", R.drawable.ic_mc_edit),
    NavIcon("forward", R.drawable.ic_mc_forward),
    NavIcon("globe", R.drawable.ic_mc_globe),
    NavIcon("heart", R.drawable.ic_mc_heart),
    NavIcon("location", R.drawable.ic_mc_location),
    NavIcon("lock", R.drawable.ic_mc_lock),
    NavIcon("mail", R.drawable.ic_mc_mail),
    NavIcon("message", R.drawable.ic_mc_message),
    NavIcon("mic", R.drawable.ic_mc_mic),
    NavIcon("minus", R.drawable.ic_mc_minus),
    NavIcon("more", R.drawable.ic_mc_more),
    NavIcon("music", R.drawable.ic_mc_music),
    NavIcon("pause", R.drawable.ic_mc_pause),
    NavIcon("people", R.drawable.ic_mc_people),
    NavIcon("phone", R.drawable.ic_mc_phone),
    NavIcon("photo", R.drawable.ic_mc_photo),
    NavIcon("pin", R.drawable.ic_mc_pin),
    NavIcon("play", R.drawable.ic_mc_play),
    NavIcon("refresh", R.drawable.ic_mc_refresh),
    NavIcon("save", R.drawable.ic_mc_save),
    NavIcon("settings", R.drawable.ic_mc_settings),
    NavIcon("share", R.drawable.ic_mc_share),
    NavIcon("star", R.drawable.ic_mc_star),
    NavIcon("store", R.drawable.ic_mc_store),
    NavIcon("swap", R.drawable.ic_mc_swap),
    NavIcon("unpin", R.drawable.ic_mc_unpin),
    NavIcon("up", R.drawable.ic_mc_up),
    NavIcon("video", R.drawable.ic_mc_video),
    NavIcon("volume", R.drawable.ic_mc_volume),
)

fun navIcon(key: String): NavIcon = NAV_ICONS.firstOrNull { it.key == key } ?: NAV_ICONS[0]
