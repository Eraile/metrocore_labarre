package dev.metrocore.navbar

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Les briques d'interface Metro.
 *
 * Les metriques viennent de metrocore (`src/styles/controls.css`) et sont exprimees
 * dans l'unite d'origine — le pixel logique WVGA. [Scale] les ramene a l'ecran reel en
 * mettant la page entiere a l'echelle plutot qu'en re-derivant chaque valeur : c'est
 * ainsi que WP procedait, et les proportions restent exactes.
 */
class MetroUi(
    val context: Context,
    accent: Int,
    /** La palette de l'ecran. La barre elle-meme garde la sienne, voir NavBarConfig. */
    val palette: MetroTokens.Palette = MetroTokens.LIGHT,
) {

    val bg: Int get() = palette.bg

    /**
     * Fond des pages plein ecran (les ListPicker). On prend le haut du degrade du
     * panorama plutot que le blanc pur de la palette : une page blanche ouverte
     * par-dessus un panorama creme se verrait comme un raccord rate.
     */
    val pageBg: Int get() = MetroTokens.PANORAMA_LIGHT.first()
    val fg: Int get() = palette.fg
    val sub: Int get() = palette.sub
    val faint: Int get() = palette.faint
    val line: Int get() = palette.line
    val chrome: Int get() = palette.chrome
    val chrome2: Int get() = palette.chrome2

    var accent: Int = accent
        set(value) {
            field = value
            accentListeners.forEach { it(value) }
        }

    private val accentListeners = mutableListOf<(Int) -> Unit>()

    fun onAccentChanged(block: (Int) -> Unit) {
        accentListeners += block
    }

    /** Facteur d'echelle WVGA -> ecran courant. */
    val scale: Float =
        (context.resources.configuration.screenWidthDp / MetroTokens.SCREEN_W)
            .coerceIn(0.75f, 1.25f)

    fun px(wvga: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        wvga * scale,
        context.resources.displayMetrics,
    ).toInt()

    /** Largeur utile d'une page, marges de page deduites. */
    fun contentWidthPx(): Int =
        context.resources.displayMetrics.widthPixels - px(MetroTokens.MARGIN_TITLE) * 2

    private fun TextView.sized(wvga: Float) =
        setTextSize(TypedValue.COMPLEX_UNIT_SP, wvga * scale)

    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    // ------------------------------------------------------------------ texte

    /** Le sur-titre en petites capitales, au-dessus du titre de page. */
    fun overline(text: String) = TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(fg)
        typeface = regular
        sized(MetroTokens.FS_SMALL * 0.72f)
        letterSpacing = 0.12f
    }

    fun pageTitle(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(fg)
        typeface = light
        sized(MetroTokens.FS_XL)
        includeFontPadding = false
        setPadding(0, px(2f), 0, px(10f))
    }

    fun sectionHead(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(fg)
        typeface = light
        sized(MetroTokens.FS_LARGE)
        setPadding(0, px(22f), 0, px(6f))
    }

    fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(fg)
        typeface = regular
        sized(MetroTokens.FS_NORMAL)
    }

    fun sub(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(sub)
        typeface = regular
        sized(MetroTokens.FS_SMALL)
        setPadding(0, px(2f), 0, 0)
    }

    // ---------------------------------------------------------------- toggle

    /**
     * L'interrupteur WP : un rail rectangulaire de 74x30 borde de 2 px, un curseur de
     * 16x20 qui parcourt 48 px. Rien n'est arrondi — c'est la moitie de son identite.
     */
    inner class Toggle(
        labelText: String,
        private val initial: Boolean,
        private val onChange: (Boolean) -> Unit,
    ) : LinearLayout(context) {

        private var checked = initial
        private val track = TrackView(context)
        private val state = sub(stateText(initial))

        init {
            orientation = VERTICAL
            setPadding(0, px(6f), 0, px(10f))

            addView(label(labelText).apply { setPadding(0, 0, 0, px(6f)) })
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(track, LayoutParams(px(74f), px(30f)))
                addView(
                    state,
                    LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = px(12f) },
                )
            })

            setOnClickListener { toggle() }
            isClickable = true
            onAccentChanged { track.invalidate() }
        }

        private fun toggle() {
            checked = !checked
            state.text = stateText(checked)
            track.animateTo(checked)
            onChange(checked)
        }

        private fun stateText(on: Boolean) =
            context.getString(if (on) R.string.state_on else R.string.state_off)

        /** Le rail et son curseur, dessines a la main : aucun widget Android n'a cette forme. */
        private inner class TrackView(context: Context) : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var progress = if (initial) 1f else 0f
            private var animator: ValueAnimator? = null

            fun animateTo(on: Boolean) {
                animator?.cancel()
                animator = ValueAnimator.ofFloat(progress, if (on) 1f else 0f).apply {
                    duration = MetroTokens.DUR_BASE
                    addUpdateListener {
                        progress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }

            override fun onDraw(canvas: Canvas) {
                val border = px(2f).toFloat()
                val on = progress > 0.5f

                // Rail : plein en accent quand actif, simple contour sinon.
                paint.style = Paint.Style.FILL
                paint.color = if (on) accent else Color.TRANSPARENT
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = border
                paint.color = if (on) accent else fg
                canvas.drawRect(
                    border / 2, border / 2,
                    width - border / 2, height - border / 2, paint,
                )

                // Curseur : 16x20 a 3 px du bord, course de 48 px.
                val kw = px(16f).toFloat()
                val kh = px(20f).toFloat()
                val inset = px(3f).toFloat()
                val travel = px(48f).toFloat()
                val left = inset + travel * progress

                paint.style = Paint.Style.FILL
                paint.color = if (on) Color.WHITE else fg
                canvas.drawRect(left, inset, left + kw, inset + kh, paint)
            }
        }
    }

    // -------------------------------------------------------------- swatches

    /**
     * La grille de pastilles de couleur, cinq par ligne comme la page
     * personnalisation. La selection est un liseré blanc, pas une coche.
     */
    fun swatchGrid(
        colors: List<Pair<String, Int>>,
        selectedKey: String,
        widthPx: Int = contentWidthPx(),
        onPick: (String) -> Unit,
    ): GridLayout {
        val columns = 5
        val grid = GridLayout(context).apply {
            columnCount = columns
            setPadding(0, px(4f), 0, px(4f))
        }

        val gap = px(4f)
        val cell = (widthPx - gap * (columns - 1)) / columns

        var current = selectedKey
        val cells = mutableMapOf<String, SwatchView>()

        colors.forEach { (key, color) ->
            val view = SwatchView(context, color, key == selectedKey).apply {
                setOnClickListener {
                    cells[current]?.setSelectedSwatch(false)
                    current = key
                    setSelectedSwatch(true)
                    onPick(key)
                }
            }
            cells[key] = view
            grid.addView(view, GridLayout.LayoutParams().apply {
                width = cell
                height = (cell * 0.66f).toInt()
                setMargins(0, 0, gap, gap)
            })
        }
        return grid
    }

    inner class SwatchView(
        context: Context,
        private val color: Int,
        selected: Boolean,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var isSelectedSwatch = selected

        init {
            isClickable = true
        }

        fun setSelectedSwatch(value: Boolean) {
            isSelectedSwatch = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Une pastille noire sur fond noir n'existerait pas : chaque case est
            // toujours cernee d'un filet, seule sa couleur depend du fond.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = px(1f).toFloat()
            paint.color = if (NavBarConfig.isLight(color)) line else faint
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            if (isSelectedSwatch) {
                val w = px(3f).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w
                paint.color = if (NavBarConfig.isLight(color)) fg else Color.WHITE
                canvas.drawRect(w / 2, w / 2, width - w / 2, height - w / 2, paint)
            }
        }
    }

    // ------------------------------------------------------------ ligne-choix

    /**
     * Une ligne qui ouvre une liste plein ecran. C'est le ListPicker de WP : le choix
     * ne se fait jamais dans une petite boite flottante, la page entiere bascule.
     */
    fun pickerRow(labelText: String, valueText: String, onClick: () -> Unit): LinearLayout {
        val value = TextView(context).apply {
            text = valueText
            setTextColor(accent)
            typeface = regular
            setTextSize(TypedValue.COMPLEX_UNIT_SP, MetroTokens.FS_MEDIUM * scale)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(8f), 0, px(8f))
            minimumHeight = px(MetroTokens.LIST_ROW_1)
            addView(label(labelText).apply { setTextColor(sub) })
            addView(value)
            isClickable = true
            setOnClickListener { onClick() }
            setTag(R.id.tag_picker_value, value)
        }
    }

    /**
     * Une adresse cliquable. Meme traitement qu'une valeur de ListPicker — texte en
     * accent, pas de soulignement : Metro ne souligne pas ses liens.
     */
    fun linkRow(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(accent)
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_SP, MetroTokens.FS_MEDIUM * scale)
        setPadding(0, px(2f), 0, px(2f))
        minHeight = px(MetroTokens.TOUCH_MIN)
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun updatePickerRow(row: View, valueText: String) {
        (row.getTag(R.id.tag_picker_value) as? TextView)?.apply {
            text = valueText
            setTextColor(accent)
        }
    }

    /** La liste plein ecran ouverte par [pickerRow]. */
    fun <T> showPicker(
        title: String,
        options: List<T>,
        selected: T?,
        labelOf: (T) -> String,
        onPick: (T) -> Unit,
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
            setPadding(px(MetroTokens.MARGIN_TITLE), px(28f), px(MetroTokens.MARGIN_TITLE), px(24f))
            addView(pageTitle(title))
        }

        options.forEach { option ->
            column.addView(radioRow(labelOf(option), option == selected) {
                onPick(option)
                dialog.dismiss()
            })
        }

        dialog.setContentView(ScrollView(context).apply {
            setBackgroundColor(pageBg)
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        })
        dialog.show()
    }

    /**
     * La meme liste plein ecran, mais avec l'icone de chaque application a gauche.
     * C'est la seule liste ou une vignette est justifiee : sans elle on ne reconnait
     * pas une application dans un inventaire de deux cents lignes.
     */
    fun showAppPicker(
        title: String,
        apps: List<AppTarget>,
        onPick: (AppTarget) -> Unit,
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
            setPadding(px(MetroTokens.MARGIN_TITLE), px(28f), px(MetroTokens.MARGIN_TITLE), px(24f))
            addView(pageTitle(title))
        }

        if (apps.isEmpty()) {
            column.addView(sub(context.getString(R.string.picker_empty)))
        }

        apps.forEach { app ->
            column.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(8f), 0, px(8f))
                    minimumHeight = px(MetroTokens.LIST_ROW_1)

                    addView(
                        ImageView(context).apply {
                            setImageDrawable(app.icon)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        },
                        LinearLayout.LayoutParams(px(34f), px(34f)),
                    )
                    addView(
                        label(app.label),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = px(12f) },
                    )

                    isClickable = true
                    setOnClickListener {
                        onPick(app)
                        dialog.dismiss()
                    }
                },
            )
        }

        dialog.setContentView(ScrollView(context).apply {
            setBackgroundColor(pageBg)
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        })
        dialog.show()
    }

    /** Cercle vide, point d'accent au centre quand il est choisi. */
    fun radioRow(text: String, selected: Boolean, onClick: () -> Unit) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8f), 0, px(8f))
            minimumHeight = px(MetroTokens.TOUCH_MIN)
            addView(RadioDot(context, selected), LinearLayout.LayoutParams(px(26f), px(26f)))
            addView(label(text), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = px(12f) })
            isClickable = true
            setOnClickListener { onClick() }
        }

    inner class RadioDot(context: Context, private val selected: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val border = px(2f).toFloat()
            val r = (width - border) / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = fg
            canvas.drawCircle(width / 2f, height / 2f, r, paint)
            if (selected) {
                paint.style = Paint.Style.FILL
                paint.color = accent
                canvas.drawCircle(width / 2f, height / 2f, r - px(5f), paint)
            }
        }
    }

    // ---------------------------------------------------------------- slider

    /**
     * Rail plat, remplissage en accent. [autoLabel] est le libelle affiche quand la
     * valeur retombe au minimum, ou le reglage repasse en automatique.
     */
    fun slider(
        labelText: String,
        min: Int,
        max: Int,
        value: Int,
        autoLabel: String?,
        format: (Int) -> String,
        onChange: (Int) -> Unit,
    ): LinearLayout {
        val readout = sub(if (value <= min && autoLabel != null) autoLabel else format(value))

        val bar = SeekBar(context).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressDrawable = context.getDrawable(R.drawable.metro_seekbar_track)
            thumb = context.getDrawable(R.drawable.metro_seekbar_thumb)
            splitTrack = false
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(fg)
            progressBackgroundTintList = ColorStateList.valueOf(chrome2)
            setPadding(px(6f), px(8f), px(6f), px(8f))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + min
                    readout.text = if (v <= min && autoLabel != null) autoLabel else format(v)
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(8f), 0, px(10f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(label(labelText).apply { setTextColor(sub) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(readout)
            })
            addView(bar)
        }
    }

    // ------------------------------------------------------------ grille icones

    /** Une case du selecteur de glyphes : fond chrome, cadre en accent si choisie. */
    inner class IconCell(context: Context, iconRes: Int, tint: Int?, chosen: Boolean) :
        FrameLayout(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var chosen: Boolean = chosen
            set(value) {
                field = value
                invalidate()
            }

        private val tintable = tint != null

        private val image = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = tint?.let { ColorStateList.valueOf(it) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        init {
            setWillNotDraw(false)
            isClickable = true
            addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        fun setTint(color: Int) {
            if (tintable) image.imageTintList = ColorStateList.valueOf(color)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val pad = (w * 0.24f).toInt()
            image.setPadding(pad, pad, pad, pad)
        }

        override fun dispatchDraw(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            paint.color = chrome
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            if (chosen) {
                val w = px(3f).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w
                paint.color = accent
                canvas.drawRect(w / 2, w / 2, width - w / 2, height - w / 2, paint)
            }
            super.dispatchDraw(canvas)
        }
    }

    /** La grille de glyphes ; celle qui est choisie porte un cadre en accent. */
    fun iconGrid(
        selectedKey: String,
        tint: Int,
        widthPx: Int = contentWidthPx(),
        onPick: (String) -> Unit,
    ): GridLayout {
        val columns = 5
        val grid = GridLayout(context).apply {
            columnCount = columns
            setPadding(0, px(4f), 0, px(4f))
        }

        val gap = px(4f)
        val cell = (widthPx - gap * (columns - 1)) / columns

        val cells = mutableMapOf<String, IconCell>()

        NAV_ICONS.forEach { icon ->
            val box = IconCell(
                context,
                icon.res,
                tint.takeIf { icon.tintable },
                icon.key == selectedKey,
            ).apply {
                setOnClickListener {
                    cells.forEach { (key, view) -> view.chosen = key == icon.key }
                    onPick(icon.key)
                }
            }
            cells[icon.key] = box
            grid.addView(box, GridLayout.LayoutParams().apply {
                width = cell
                height = cell
                setMargins(0, 0, gap, gap)
            })
        }

        // Retinte toute la grille quand la couleur des glyphes change en direct.
        grid.setTag(R.id.tag_icon_cells, cells)
        return grid
    }

    @Suppress("UNCHECKED_CAST")
    fun retintIconGrid(grid: View, color: Int) {
        (grid.getTag(R.id.tag_icon_cells) as? Map<String, IconCell>)
            ?.values?.forEach { it.setTint(color) }
    }
}
