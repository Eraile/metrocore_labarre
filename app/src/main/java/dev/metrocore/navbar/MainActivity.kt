/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * L'ecran de reglages, en panorama.
 *
 * Une seule toile plus large que l'ecran, dont chaque section tient une facette du
 * reglage. C'est la forme que WP reservait a la porte d'entree d'un hub, et c'est ce
 * qu'est cet ecran : pas une liste de preferences, mais l'endroit d'ou l'on regle la
 * barre. Le titre surdimensionne defile moins vite que le contenu et se fait couper
 * par les deux bords — voir [PanoramaView].
 *
 * Tout est construit en code : les listes de glyphes, d'actions et d'accents viennent
 * de donnees, et un XML statique ne ferait que les repeter.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: ConfigStore
    private lateinit var ui: MetroUi
    private lateinit var haptics: Haptics

    private var config = NavBarConfig.DEFAULT
    private var panorama: PanoramaView? = null

    private var previewHost: FrameLayout? = null
    private var statusLine: TextView? = null

    /** Le bouton en attente pendant que l'application affiche son selecteur. */
    private var pendingShortcut: Triple<Slot, Trigger, () -> Unit>? = null

    /** La version trouvee sur GitHub, tant qu'elle n'est pas installee. */
    private var pendingUpdate: Update? = null

    /**
     * `ACTION_CREATE_SHORTCUT` rend l'intent choisi dans `EXTRA_SHORTCUT_INTENT`.
     * Le contrat est ancien mais c'est le seul qu'une application non-lanceur puisse
     * utiliser pour recuperer une action rapide.
     */
    private val shortcutLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val (slot, trigger, onDone) = pendingShortcut ?: return@registerForActivityResult
        pendingShortcut = null

        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult

        @Suppress("DEPRECATION")
        val target = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

        @Suppress("DEPRECATION")
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)

        if (target == null) {
            Toast.makeText(this, R.string.toast_no_shortcut, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        setTrigger(
            slot,
            trigger,
            NavAction.SHORTCUT,
            target.toUri(Intent.URI_INTENT_SCHEME),
            name ?: getString(R.string.act_shortcut),
        )
        onDone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = ConfigStore(this)
        haptics = Haptics(this)
        config = store.load()
        ui = MetroUi(this, MetroTokens.accent(MetroTokens.DEFAULT_ACCENT))

        buildPanorama()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // -------------------------------------------------------------- panorama

    private fun buildPanorama() {
        val keep = panorama?.currentIndex ?: 0

        panorama = PanoramaView(this, ui, getString(R.string.title_navbar)).also { pano ->
            pano.setBackgroundColor(ui.bg)
            setContentView(pano)

            addPreviewSection(pano)
            addAppearanceSection(pano)
            addBehaviourSection(pano)
            addFeedbackSection(pano)
            Slot.entries.forEach { addSlotSection(pano, it) }
            addAboutSection(pano)

            pano.commit()
            pano.go(keep, animate = false)
        }
        refreshStatus()
    }

    // --------------------------------------------------------------- apercu

    /**
     * La premiere section : l'apercu, l'etat du service, et la remise a zero. C'est ce
     * qu'on veut voir en ouvrant l'application.
     */
    private fun addPreviewSection(pano: PanoramaView) {
        val body = pano.addSection(getString(R.string.section_overview))

        previewHost = FrameLayout(this).also { host ->
            body.addView(
                host,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = ui.px(4f) },
            )
        }
        body.addView(ui.sub(getString(R.string.preview_hint)))
        renderPreview()

        statusLine = ui.label("").also {
            body.addView(it, marginTop(ui.px(20f)))
        }

        // Le mode de navigation explique a lui seul la moitie des surprises — barre qui
        // rogne le bas de l'ecran, barre absente. Autant le dire la ou on regarde en
        // premier, plutot que d'attendre que la question remonte.
        if (SystemBars.isGestureNav(this)) {
            body.addView(ui.sub(getString(R.string.hint_gesture_nav)))
        }

        body.addView(
            ui.actionRow(
                getString(R.string.row_accessibility),
                getString(R.string.row_accessibility_value),
            ) { openAccessibilitySettings() },
        )

        // Le mur le plus frequent a l'installation : depuis Android 13, l'interrupteur
        // d'accessibilite est verrouille pour toute application installee hors magasin.
        // Rien ne le dit a l'ecran ou l'utilisateur se trouve, il voit juste un
        // interrupteur qui ne repond pas — autant l'expliquer ici, avant qu'il n'y aille.
        body.addView(ui.sub(getString(R.string.hint_restricted)))

        body.addView(
            ui.actionRow(getString(R.string.row_reset), getString(R.string.row_reset_value)) {
                store.reset()
                config = store.load()
                buildPanorama()
                Toast.makeText(this, R.string.toast_reset, Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun renderPreview() {
        val host = previewHost ?: return
        host.removeAllViews()
        val bar = NavBarView.build(this, config, haptics) { action, _ ->
            // Dans l'apercu on montre ce que ferait le bouton sans le faire : appliquer
            // "precedent" ou "accueil" ici quitterait l'ecran de reglages.
            Toast.makeText(this, getString(action.labelRes), Toast.LENGTH_SHORT).show()
        }
        host.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // Le contexte, et pas la largeur seule : c'est lui qui sait ce que le
                // systeme reserve, quand la hauteur est calee dessus.
                NavBarView.dp(this, config.resolvedHeightDp(this)),
            ),
        )
    }

    private fun refreshStatus() {
        val line = statusLine ?: return
        val enabled = isServiceEnabled(this)
        line.text = getString(if (enabled) R.string.status_on else R.string.status_off)
        line.setTextColor(
            if (enabled) MetroTokens.accent("emerald") else MetroTokens.accent("red"),
        )
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onSuccess { Toast.makeText(this, R.string.toast_hint, Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show() }
    }

    // ------------------------------------------------------------- apparence

    private fun addAppearanceSection(pano: PanoramaView) {
        val body = pano.addSection(getString(R.string.section_appearance))

        val gridWidth = pano.sectionContentWidthPx()

        // Material You n'existe qu'a partir d'Android 12 : ailleurs, on ne propose pas
        // un reglage qui ne ferait rien.
        if (MetroTokens.dynamicAvailable) {
            body.addView(
                ui.Toggle(
                    getString(R.string.label_dynamic_color),
                    config.dynamicColor,
                ) { on -> update { it.copy(dynamicColor = on) } },
            )
            body.addView(ui.sub(getString(R.string.label_dynamic_color_hint)))
        }

        body.addView(
            ui.sub(getString(R.string.label_bar_color)),
            marginTop(ui.px(if (MetroTokens.dynamicAvailable) 18f else 0f)),
        )
        body.addView(
            ui.swatchGrid(NavBarConfig.BAR_COLORS, config.barColorKey, gridWidth) { key ->
                update { it.copy(barColorKey = key) }
            },
        )

        body.addView(ui.sub(getString(R.string.label_icon_color)), marginTop(ui.px(10f)))
        body.addView(
            ui.swatchGrid(NavBarConfig.ICON_COLORS, config.iconColorKey, gridWidth) { key ->
                update { it.copy(iconColorKey = key) }
            },
        )

        // « auto » ne veut plus dire la proportion WP mais la place que prend la barre
        // systeme : on demande donc sa valeur au meme calcul que la barre reelle, plutot
        // que de refaire le sien ici.
        val autoHeight = config.copy(barHeightDp = 0).resolvedHeightDp(this)

        body.addView(
            ui.slider(
                labelText = getString(R.string.label_bar_height),
                min = 0,
                max = 80,
                value = config.barHeightDp,
                autoLabel = getString(R.string.value_auto, autoHeight),
                format = { getString(R.string.value_dp, it) },
            ) { v -> update { it.copy(barHeightDp = if (v == 0) 0 else v.coerceAtLeast(28)) } },
        )

        body.addView(
            ui.slider(
                labelText = getString(R.string.label_icon_size),
                min = 0,
                max = 70,
                value = config.iconSizePct,
                autoLabel = getString(R.string.value_auto_pct, NavBarConfig.DEFAULT_ICON_PCT),
                format = { getString(R.string.value_pct, it) },
            ) { v -> update { it.copy(iconSizePct = if (v == 0) 0 else v.coerceAtLeast(12)) } },
        )
    }

    // --------------------------------------------------------- comportement

    /**
     * Quand la barre s'efface d'elle-meme.
     *
     * Aucun overlay ne peut reserver d'espace a l'ecran (voir [SystemBars]), donc la
     * seule reponse au « elle recouvre le contenu » est de disparaitre la ou ca gene :
     * le plein ecran et le verrouillage. Les deux sont actifs par defaut — c'est une
     * correction, pas une preference.
     */
    private fun addBehaviourSection(pano: PanoramaView) {
        val body = pano.addSection(getString(R.string.section_behaviour))

        // Le mode se propose toujours, meme en 3 boutons ou il ne change rien : c'est la
        // reponse a « pourquoi elle recouvre le bas de l'ecran », et une question qu'on
        // ne se pose qu'apres avoir change de mode de navigation.
        lateinit var modeRow: View
        modeRow = ui.pickerRow(
            getString(R.string.label_bar_mode),
            getString(config.mode.labelRes),
        ) {
            ui.showPicker(
                title = getString(R.string.label_bar_mode),
                options = BarMode.entries.toList(),
                selected = config.mode,
                labelOf = { getString(it.labelRes) },
            ) { picked ->
                update { it.copy(mode = picked) }
                ui.updatePickerRow(modeRow, getString(picked.labelRes))
            }
        }
        body.addView(modeRow)
        body.addView(ui.sub(getString(R.string.label_bar_mode_hint)))

        // Sans effet en portrait : les deux valeurs y donnent le bas. Le reglage se
        // propose quand meme, sinon on ne le trouve que par hasard — et c'est en paysage,
        // ecran tourne, qu'on a le moins envie de partir a sa recherche.
        lateinit var placementRow: View
        placementRow = ui.pickerRow(
            getString(R.string.label_placement),
            getString(config.placement.labelRes),
        ) {
            ui.showPicker(
                title = getString(R.string.label_placement),
                options = BarPlacement.entries.toList(),
                selected = config.placement,
                labelOf = { getString(it.labelRes) },
            ) { picked ->
                update { it.copy(placement = picked) }
                ui.updatePickerRow(placementRow, getString(picked.labelRes))
            }
        }
        body.addView(placementRow, marginTop(ui.px(18f)))
        body.addView(ui.sub(getString(R.string.label_placement_hint)))

        body.addView(
            ui.Toggle(
                getString(R.string.label_hide_fullscreen),
                config.hideFullscreen,
            ) { on -> update { it.copy(hideFullscreen = on) } },
            marginTop(ui.px(18f)),
        )
        body.addView(ui.sub(getString(R.string.label_hide_fullscreen_hint)))

        body.addView(
            ui.Toggle(
                getString(R.string.label_hide_lockscreen),
                config.hideLockscreen,
            ) { on -> update { it.copy(hideLockscreen = on) } },
            marginTop(ui.px(18f)),
        )
        body.addView(ui.sub(getString(R.string.label_hide_lockscreen_hint)))
    }

    // ---------------------------------------------------------------- retour

    private fun addFeedbackSection(pano: PanoramaView) {
        val body = pano.addSection(getString(R.string.section_feedback))

        lateinit var row: View
        row = ui.pickerRow(
            getString(R.string.label_haptic),
            getString(config.haptic.labelRes),
        ) {
            ui.showPicker(
                title = getString(R.string.label_haptic),
                options = Haptic.entries.toList(),
                selected = config.haptic,
                labelOf = { getString(it.labelRes) },
            ) { picked ->
                update { it.copy(haptic = picked) }
                ui.updatePickerRow(row, getString(picked.labelRes))
                haptics.tap(picked)
            }
        }
        body.addView(row)

        body.addView(
            ui.Toggle(
                getString(R.string.label_press_feedback),
                config.pressFeedback,
            ) { on -> update { it.copy(pressFeedback = on) } },
        )
    }

    // --------------------------------------------------------------- boutons

    private fun addSlotSection(pano: PanoramaView, slot: Slot) {
        val body = pano.addSection(getString(slot.labelRes))
        val current = { config.slots.getValue(slot) }

        // Ce que fait le bouton d'abord, a quoi il ressemble ensuite : on regle une
        // touche pour son action, et la grille de 45 glyphes repousserait tout le reste
        // hors de vue si elle ouvrait la section.
        Trigger.entries.forEach { trigger -> body.addView(triggerRow(slot, trigger)) }

        body.addView(
            ui.slider(
                labelText = getString(R.string.label_icon_scale),
                min = 60,
                max = 180,
                value = current().iconScalePct,
                autoLabel = null,
                format = { getString(R.string.value_pct, it) },
            ) { v -> updateSlot(slot) { it.copy(iconScalePct = v) } },
        )

        body.addView(ui.sub(getString(R.string.label_icon)))
        // Teinte de la page, pas celle de la barre : cette grille sert a choisir une
        // forme. Une glyphe blanche — la couleur par defaut de la barre — serait
        // invisible sur le fond clair des cases. La couleur reelle se choisit dans
        // « couleur des glyphes », et l'apercu au-dessus la montre pour de vrai.
        body.addView(
            ui.iconGrid(current().iconKey, ui.fg, pano.sectionContentWidthPx()) { key ->
                updateSlot(slot) { it.copy(iconKey = key) }
            },
        )
    }

    /**
     * Une ligne « appui court » / « appui long ».
     *
     * Choisir une action qui vise quelque chose (ouvrir une application, une action
     * rapide) enchaine directement sur le choix de la cible : demander l'action puis
     * laisser l'utilisateur deviner qu'il faut retoucher la ligne serait une impasse.
     */
    private fun triggerRow(slot: Slot, trigger: Trigger): View {
        val current = { config.slots.getValue(slot) }

        lateinit var row: View
        row = ui.pickerRow(getString(trigger.labelRes), describe(current(), trigger)) {
            ui.showPicker(
                title = getString(trigger.labelRes),
                options = NavAction.selectable(),
                selected = current().actionFor(trigger),
                labelOf = { getString(it.labelRes) },
            ) { picked ->
                if (picked.needsPayload) {
                    chooseTarget(slot, trigger, picked) {
                        ui.updatePickerRow(row, describe(current(), trigger))
                    }
                } else {
                    setTrigger(slot, trigger, picked, null, null)
                    ui.updatePickerRow(row, describe(current(), trigger))
                }
            }
        }
        return row
    }

    /** Le libelle affiche : le nom de la cible s'il y en a une, l'action sinon. */
    private fun describe(sc: SlotConfig, trigger: Trigger): String {
        val action = sc.actionFor(trigger)
        val target = sc.payloadLabelFor(trigger)
        return if (action.needsPayload && target != null) {
            getString(R.string.value_action_target, getString(action.labelRes), target)
        } else {
            getString(action.labelRes)
        }
    }

    private fun chooseTarget(
        slot: Slot,
        trigger: Trigger,
        action: NavAction,
        onDone: () -> Unit,
    ) {
        when (action) {
            NavAction.LAUNCH_APP -> ui.showAppPicker(
                title = getString(R.string.picker_app),
                apps = AppTargets.launchable(this),
            ) { app ->
                setTrigger(slot, trigger, action, app.uri, app.label)
                onDone()
            }

            NavAction.SHORTCUT -> ui.showAppPicker(
                title = getString(R.string.picker_shortcut),
                apps = AppTargets.shortcutProviders(this),
            ) { app ->
                // L'application ouvre son propre selecteur et nous rend un intent ;
                // c'est lui qu'on enregistre, pas celui du selecteur.
                val intent = runCatching {
                    Intent.parseUri(app.uri, Intent.URI_INTENT_SCHEME)
                }.getOrNull() ?: return@showAppPicker

                pendingShortcut = Triple(slot, trigger, onDone)
                runCatching { shortcutLauncher.launch(intent) }
                    .onFailure {
                        pendingShortcut = null
                        Toast.makeText(this, R.string.toast_no_handler, Toast.LENGTH_SHORT).show()
                    }
            }

            else -> Unit
        }
    }

    private fun setTrigger(
        slot: Slot,
        trigger: Trigger,
        action: NavAction,
        payload: String?,
        payloadLabel: String?,
    ) {
        updateSlot(slot) { sc ->
            if (trigger == Trigger.TAP) {
                sc.copy(tap = action, tapPayload = payload, tapPayloadLabel = payloadLabel)
            } else {
                sc.copy(hold = action, holdPayload = payload, holdPayloadLabel = payloadLabel)
            }
        }
    }

    // --------------------------------------------------------------- metrocore

    /** La derniere section : d'ou vient tout ce qu'il y a devant. */
    private fun addAboutSection(pano: PanoramaView) {
        // La section fournit son propre en-tete : le logo et le mot-symbole vont
        // ensemble, sur une meme ligne.
        val body = pano.addSection(getString(R.string.section_about), showTitle = false)

        body.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_mc_metrocolor)
                    },
                    LinearLayout.LayoutParams(ui.px(54f), ui.px(54f)),
                )
                addView(
                    ui.sectionHead("").apply {
                        text = wordmark(getString(R.string.section_about))
                        setPadding(0, 0, 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = ui.px(10f) },
                )
            },
            marginTop(ui.px(4f)),
        )

        body.addView(
            ui.label("").apply { text = wordmark(getString(R.string.about_blurb)) },
            marginTop(ui.px(14f)),
        )

        body.addView(ui.sub(getString(R.string.about_project)), marginTop(ui.px(22f)))
        body.addView(ui.linkRow(getString(R.string.about_site)) { open(SITE_PROJECT) })

        // La ligne porte son propre etat : « rechercher » → « recherche… » → la version
        // trouvee, puis le meme appui telecharge et installe. Un seul endroit a regarder,
        // et rien ne part sur le reseau sans appui.
        body.addView(ui.sub(getString(R.string.label_update)), marginTop(ui.px(22f)))
        lateinit var updateButton: TextView
        updateButton = ui.button(getString(R.string.update_check)) {
            onUpdateRowTapped(updateButton)
        }
        body.addView(
            updateButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = ui.px(8f) },
        )

        // La porte de sortie : si la mise a jour integree ne peut pas aboutir — pas
        // d'autorisation d'installer, reseau capricieux, envie de lire les notes de
        // version — la page des Releases reste atteignable.
        body.addView(
            ui.linkRow(getString(R.string.update_releases)) { open(SITE_RELEASES) },
            marginTop(ui.px(6f)),
        )

        body.addView(ui.sub(getString(R.string.about_footer)), marginTop(ui.px(30f)))

        // Le drapeau et la mention sur leur propre ligne. Un seul TextView avec le
        // drapeau en ImageSpan, et non deux vues cote a cote : en ligne, le texte se
        // replie normalement, la ou une rangee de vues coincait la derniere et la
        // cassait mot par mot.
        body.addView(
            ui.sub("").apply {
                text = madeInLine()
                contentDescription = "${getString(R.string.about_flag_desc)} — " +
                    getString(R.string.about_made_in)
            },
            marginTop(ui.px(2f)),
        )
    }

    /** Met « core » en gras dans chaque occurrence de « metrocore ». */
    private fun wordmark(text: String): CharSequence {
        val out = SpannableStringBuilder(text)
        var from = 0
        while (true) {
            val at = text.indexOf(WORDMARK, from, ignoreCase = true)
            if (at < 0) break
            val coreAt = at + WORDMARK.length - CORE.length
            out.setSpan(
                StyleSpan(Typeface.BOLD),
                coreAt,
                coreAt + CORE.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            from = at + WORDMARK.length
        }
        return out
    }

    private fun madeInLine(): CharSequence {
        val flag = ContextCompat.getDrawable(this, R.drawable.basque_flag)
            ?: return getString(R.string.about_made_in)

        // Le drapeau est cale sur la hauteur de la ligne, ratio conserve.
        val h = ui.px(13f)
        flag.setBounds(0, 0, (h * flag.intrinsicWidth / flag.intrinsicHeight), h)

        return SpannableStringBuilder()
            .apply {
                append(" ")
                setSpan(
                    ImageSpan(flag, ImageSpan.ALIGN_BASELINE),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            .append("  ")
            .append(getString(R.string.about_made_in))
    }

    // ------------------------------------------------------------ mise a jour

    /**
     * Deux temps sur la meme ligne : le premier appui cherche, le second installe ce qui
     * a ete trouve. Le reseau se fait sur un fil a part — [Updates] bloque, et il n'y a
     * pas de coroutines dans ce projet.
     */
    private fun onUpdateRowTapped(row: TextView) {
        val found = pendingUpdate
        if (found != null) {
            installUpdate(found, row)
            return
        }

        row.text = getString(R.string.update_checking)
        Thread {
            val update = Updates.latest(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                pendingUpdate = update
                row.text = if (update == null) {
                    getString(R.string.update_current)
                } else {
                    getString(R.string.update_available, update.version)
                }
            }
        }.start()
    }

    private fun installUpdate(update: Update, row: TextView) {
        row.text = getString(R.string.update_downloading)
        Thread {
            val apk = Updates.download(this, update)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (apk == null) {
                    row.text = getString(R.string.update_failed)
                    return@runOnUiThread
                }
                row.text = getString(R.string.update_available, update.version)
                // Faux veut dire que l'autorisation d'installer manque : l'ecran systeme
                // vient de s'ouvrir, on dit pourquoi.
                if (!Updates.install(this, apk)) {
                    Toast.makeText(this, R.string.update_needs_permission, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, R.string.toast_no_handler, Toast.LENGTH_SHORT).show() }
    }

    // ------------------------------------------------------------------ etat

    /**
     * Applique une modification, la persiste et rafraichit l'apercu.
     *
     * Le service ecoute les memes preferences : il se reconstruit de lui-meme, sans
     * qu'on ait a lui parler.
     */
    private fun update(block: (NavBarConfig) -> NavBarConfig) {
        config = block(config)
        store.save(config)
        renderPreview()
    }

    private fun updateSlot(slot: Slot, block: (SlotConfig) -> SlotConfig) {
        update { cfg ->
            cfg.copy(slots = cfg.slots + (slot to block(cfg.slots.getValue(slot))))
        }
    }

    private fun marginTop(px: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = px }

    companion object {
        private const val SITE_PROJECT = "https://metrocore.dev"
        private const val SITE_RELEASES =
            "https://github.com/Eraile/metrocore_labarre/releases"

        /** Le mot-symbole, et la partie qu'on met en gras dedans. */
        private const val WORDMARK = "metrocore"
        private const val CORE = "core"

        /**
         * Verifie dans les reglages systeme si le service est active. Plus fiable que
         * de regarder [NavBarService.instance] : ca survit a un kill du process.
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${NavBarService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            return splitter.any { it.equals(expected, ignoreCase = true) }
        }
    }
}
