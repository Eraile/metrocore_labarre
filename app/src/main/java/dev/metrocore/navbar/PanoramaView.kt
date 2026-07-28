/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.metrocore.navbar

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Le Panorama.
 *
 * Une toile unique plus large que l'ecran, regardee a travers lui. Trois couches
 * defilent a trois vitesses, et c'est ce rapport qui fait tout le controle
 * (metrocore, `src/nav/panorama.ts`) :
 *
 *   fond      0.30x   une bande large qui ne finit jamais tout a fait de defiler
 *   titre     0.60x   le nom en minuscules surdimensionne, coupe par les deux bords
 *   sections  1.00x   le contenu, chacune plus etroite que l'ecran pour que la
 *                     suivante depasse toujours a droite
 *
 * Le titre est coupe a gauche ET a droite volontairement. Le rentrer dans la marge est
 * la facon la plus courante de rater un panorama.
 *
 * Un ecart assume avec metrocore : ici on borne l'index au lieu de le faire boucler.
 * Avec six sections, revenir de la derniere a la premiere produirait un glissement de
 * cinq ecrans — spectaculaire mais illisible.
 */
@SuppressLint("ViewConstructor")
class PanoramaView(
    context: Context,
    private val ui: MetroUi,
    titleText: String,
) : FrameLayout(context) {

    /** Fraction de la largeur d'ecran occupee par une section. WP utilisait 0.8. */
    private val sectionFraction = 0.8f

    private val bg = View(context)
    private val titleWrap = FrameLayout(context)
    private val titleView = TextView(context)
    private val track = FrameLayout(context)

    private val panes = mutableListOf<Pane>()
    private var offsets = IntArray(0)
    private var total = 0
    private var current = 0
    private var assignedFor = -1

    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private class Pane(val holder: LinearLayout, val widthFraction: Float)

    init {
        clipChildren = true

        // Le meme degrade est pose deux fois : sur la couche mobile, et sur le panorama
        // lui-meme. Tirer au-dela des extremites decale la couche mobile et decouvrirait
        // sinon le fond nu — ici on retombe sur la meme teinte, creme a gauche, or a
        // droite, et le debordement ne se voit pas.
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            MetroTokens.PANORAMA_LIGHT,
        )
        bg.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            MetroTokens.PANORAMA_LIGHT,
        )
        addView(bg, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        titleView.apply {
            text = titleText.lowercase()
            setTextColor(ui.fg)
            typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.035f
            includeFontPadding = false
            maxLines = 1
            // Sans ca, un titre de deux mots se replie sur l'espace et maxLines=1
            // supprime la seconde ligne : « la barre » ne montrait plus que « la ».
            setHorizontallyScrolling(true)
        }

        val titlePx = fitTitle()

        titleWrap.addView(
            titleView,
            // Largeur explicite : le titre est plus large que l'ecran, et un
            // WRAP_CONTENT dans un parent borne se ferait plafonner a l'ecran — il ne
            // resterait plus rien a reveler en defilant.
            LayoutParams(titleWidthPx(), LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ui.px(MetroTokens.MARGIN)
                // Android place le texte sur sa boite ascendante/descendante, plus haute
                // que le line-height 0.82 du CSS : il faut remonter pour cadrer sur la
                // meme bande de glyphe. Proportionnel, puisque la taille s'ajuste.
                topMargin = -(titlePx * TITLE_RISE).roundToInt()
            },
        )
        // La bande de titre est aussi large que le titre, pour la meme raison que le
        // rail : bornee a l'ecran, elle rognerait tout ce qui depasse.
        addView(
            titleWrap,
            LayoutParams(LayoutParams.WRAP_CONTENT, (titlePx * TITLE_BAND).roundToInt()),
        )

        // Le rail est aussi large que TOUT le panorama, pas que l'ecran.
        //
        // Un parent qui decoupe ses enfants les decoupe a leurs propres bornes. Un rail
        // large d'un ecran se ferait donc rogner a un ecran, et une section posee a
        // 864 px ne serait dessinee que sur 1080 - 864 px : la coupure tombe pile sur la
        // largeur d'ecran. Sa largeur reelle est fixee dans assignWidths.
        track.clipChildren = false
        track.clipToPadding = false
        addView(
            track,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = (titlePx * TITLE_BAND).roundToInt() + ui.px(6f)
            },
        )
    }

    /**
     * Ajoute une section. Son titre vit *dans* la section — un panorama n'a pas
     * d'en-tetes, c'est ce qui le distingue d'un pivot.
     */
    fun addSection(
        title: String,
        widthFraction: Float = sectionFraction,
        /** A false, la section fournit son propre en-tete dans son corps. */
        showTitle: Boolean = true,
    ): LinearLayout {
        // Largeur donnee explicitement plutot que par MATCH_PARENT : la chaine
        // section -> ScrollView -> corps ne propage pas la largeur de facon fiable
        // quand la section est redimensionnee apres coup, et le contenu reste alors
        // calibre sur une mesure obsolete. Elle est de toute facon deterministe.
        val contentWidth = sectionContentWidthPx()

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, ui.px(30f))
        }

        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.px(MetroTokens.MARGIN), 0, ui.px(18f), 0)
            if (showTitle) {
                addView(
                    ui.sectionHead(title).apply { setPadding(0, 0, 0, ui.px(4f)) },
                    LinearLayout.LayoutParams(
                        contentWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            addView(
                ScrollView(context).apply {
                    isFillViewport = false
                    overScrollMode = OVER_SCROLL_NEVER
                    addView(
                        body,
                        ViewGroup.LayoutParams(
                            contentWidth,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(contentWidth, 0, 1f),
            )
        }

        track.addView(holder, LayoutParams(0, LayoutParams.MATCH_PARENT))
        panes += Pane(holder, widthFraction)
        return body
    }

    /**
     * Regle la taille du titre et rend la taille retenue, en pixels.
     *
     * Un panorama coupe son titre par les deux bords : c'est voulu, et le rentrer dans
     * la marge est la facon la plus courante de rater l'effet. Mais a la taille brute de
     * metrocore (fs-huge), un titre de deux mots ne montre plus que sa premiere syllabe
     * et devient illisible. On le reduit donc juste assez pour qu'il tienne en
     * [TITLE_MAX_OVERFLOW] largeurs d'ecran — encore coupe, toujours lisible.
     */
    private fun fitTitle(): Float {
        val full = MetroTokens.FS_HUGE * ui.scale
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, full)

        val screen = screenWidthPx().toFloat()
        val measured = titleView.paint.measureText(titleView.text.toString())
        val budget = screen * TITLE_MAX_OVERFLOW - ui.px(MetroTokens.MARGIN)

        val sizePx = titleView.textSize
        if (measured <= budget || measured <= 0f) return sizePx

        // La largeur d'un texte est proportionnelle a sa taille : une regle de trois
        // suffit, pas besoin de chercher par dichotomie.
        val fitted = full * (budget / measured)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fitted)
        return titleView.textSize
    }

    /** Largeur reelle du titre, une fois sa taille arretee. */
    private fun titleWidthPx(): Int =
        ceil(titleView.paint.measureText(titleView.text.toString())).toInt() + ui.px(4f)

    /** A appeler une fois toutes les sections ajoutees. */
    fun commit() = requestLayout()

    /** La section affichee — a conserver quand la page se reconstruit. */
    val currentIndex: Int get() = current

    /**
     * Largeur utile a l'interieur d'une section, marges comprises.
     *
     * Une section est plus etroite que l'ecran : toute grille posee dedans doit compter
     * avec cette largeur-la, sinon ses colonnes debordent et retombent l'une sous
     * l'autre. On lit l'ecran plutot que [getWidth] car les sections sont construites
     * avant la premiere mesure.
     */
    fun sectionContentWidthPx(): Int =
        (screenWidthPx() * sectionFraction).roundToInt() -
            ui.px(MetroTokens.MARGIN) - ui.px(18f)

    /**
     * Les largeurs sont posees ici, avant que `super` ne mesure les enfants.
     *
     * On les calcule sur la largeur de l'ecran et *non* sur celle du MeasureSpec recu :
     * le systeme envoie des passes de mesure intermediaires avec des largeurs qui ne
     * sont pas celles de la fenetre, et une seule suffisait a recalculer toutes les
     * sections a une fraction de leur taille.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        assignWidths(screenWidthPx())
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun screenWidthPx(): Int = resources.displayMetrics.widthPixels

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        go(current, animate = false)
    }

    private fun assignWidths(width: Int) {
        if (width == assignedFor && offsets.size == panes.size) return
        assignedFor = width

        var x = 0
        offsets = IntArray(panes.size)
        panes.forEachIndexed { i, pane ->
            val paneWidth = (width * pane.widthFraction).roundToInt()
            (pane.holder.layoutParams as LayoutParams).width = paneWidth
            // Ecrire dans les LayoutParams ne suffit pas : sans requestLayout, la vue
            // garde la mesure de la passe precedente et son contenu reste calibre sur
            // l'ancienne largeur, meme si le conteneur, lui, s'elargit bien.
            pane.holder.requestLayout()
            pane.holder.translationX = x.toFloat()
            offsets[i] = x
            x += paneWidth
        }
        total = x

        // Le rail doit contenir toutes les sections, sinon son parent le rogne a la
        // largeur d'ecran et emporte tout ce qui depasse avec lui.
        (track.layoutParams as LayoutParams).width = total
        track.requestLayout()

        // Le fond est dimensionne pour que sa course sur tout le panorama vaille celle
        // du contenu multipliee par BG_RATE : il defile donc encore sur la derniere
        // section au lieu d'etre arrive au bout.
        (bg.layoutParams as LayoutParams).width =
            width + ((total - width) * BG_RATE).roundToInt()
    }

    /** Position horizontale de la section [index], bornee a la fin du panorama. */
    private fun offsetOf(index: Int): Int =
        offsets.getOrElse(index) { 0 }.coerceAtMost((total - width).coerceAtLeast(0))

    fun go(index: Int, animate: Boolean = true) {
        if (panes.isEmpty()) return
        current = index.coerceIn(0, panes.size - 1)
        val target = offsetOf(current).toFloat()

        animator?.cancel()
        if (!animate) {
            apply(target)
            return
        }
        animator = ValueAnimator.ofFloat(-track.translationX, target).apply {
            duration = MetroTokens.DUR_BASE
            interpolator = android.view.animation.PathInterpolator(0.1f, 0.9f, 0.2f, 1f)
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Resistance aux extremites.
     *
     * Au-dela du premier ou du dernier ecran, le deplacement s'ecrase et tend vers
     * [MetroTokens.RUBBER_LIMIT] sans jamais l'atteindre : la toile suit le doigt de
     * moins en moins, au lieu de partir librement hors de l'ecran.
     */
    private fun rubberBand(x: Float): Float {
        val max = (total - width).toFloat().coerceAtLeast(0f)
        val limit = ui.px(MetroTokens.RUBBER_LIMIT).toFloat()

        val over = when {
            x < 0f -> x
            x > max -> x - max
            else -> return x
        }
        val squashed = limit * over / (over.absoluteValue + limit)
        return if (x < 0f) squashed else max + squashed
    }

    private fun apply(x: Float) {
        track.translationX = -x
        titleView.translationX = -x * titleRate()
        bg.translationX = -x * BG_RATE
    }

    /**
     * Vitesse du titre.
     *
     * Le titre doit finir sa course exactement quand le contenu finit la sienne : son
     * propre debordement se consomme sur toute la longueur du panorama. C'est ce qui
     * fait qu'il bouge encore sur la derniere section au lieu d'avoir disparu depuis
     * longtemps — un taux fixe de 0.60x emmenerait un titre court hors de l'ecran des
     * la troisieme. On plafonne quand meme a 0.60x, la valeur de metrocore.
     */
    private fun titleRate(): Float {
        val travel = total - width
        if (travel <= 0) return 0f
        val overflow = (titleView.width - width).toFloat()
        if (overflow <= 0f) return 0f
        return (overflow / travel).coerceAtMost(TITLE_RATE)
    }

    // ------------------------------------------------------------------ gestes

    /**
     * L'horizontal nous appartient, le vertical revient a la section sous le doigt.
     * On n'intercepte donc que lorsque le geste s'est declare horizontal.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
                velocity?.addMovement(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocity?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                animator?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) <= touchSlop || abs(dx) <= abs(dy)) return true
                    dragging = true
                }
                apply(rubberBand(offsetOf(current) - (event.x - downX)))
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - downX
                velocity?.computeCurrentVelocity(1) // px/ms, comme les tokens
                val vx = velocity?.xVelocity ?: 0f
                velocity?.recycle()
                velocity = null

                if (dragging) {
                    val paneWidth = panes.getOrNull(current)?.holder?.width ?: width
                    go(if (committed(dx, vx, paneWidth)) current - sign(dx).toInt() else current)
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Un glissement bascule s'il a franchi 30 % de la surface, ou s'il est parti assez
     * vite pour que la distance ne compte plus (tokens `commit-distance` / `-velocity`).
     */
    private fun committed(dx: Float, vx: Float, size: Int): Boolean =
        abs(dx) > size * COMMIT_DISTANCE || abs(vx) > COMMIT_VELOCITY


    private companion object {
        const val BG_RATE = 0.30f
        const val TITLE_RATE = 0.60f
        /** Debordement tolere du titre, en largeurs d'ecran. */
        const val TITLE_MAX_OVERFLOW = 1.12f

        /** Remontee et hauteur de la bande de titre, en fraction de la taille du texte. */
        const val TITLE_RISE = 0.13f
        const val TITLE_BAND = 0.80f
        const val COMMIT_DISTANCE = 0.3f
        const val COMMIT_VELOCITY = 0.55f
    }
}
