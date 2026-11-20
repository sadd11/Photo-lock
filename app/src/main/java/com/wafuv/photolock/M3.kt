package com.wafuv.photolock

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.*

/**
 * Цветовые роли Material 3.
 * Android 12+: динамический цвет (Material You) из системной палитры.
 * Ниже: baseline-схема M3 (фиолетовая). Светлая и тёмная тема переключаются сами.
 */
@SuppressLint("NewApi")
class M3Colors(private val ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private fun pick(l: Int, d: Int, lh: Long, dh: Long): Int =
        if (Build.VERSION.SDK_INT >= 31) ctx.getColor(if (dark) d else l)
        else (if (dark) dh else lh).toInt()

    val primary = pick(android.R.color.system_accent1_600, android.R.color.system_accent1_200, 0xFF6750A4, 0xFFD0BCFF)
    val onPrimary = pick(android.R.color.system_accent1_0, android.R.color.system_accent1_800, 0xFFFFFFFF, 0xFF381E72)
    val secondaryContainer = pick(android.R.color.system_accent2_100, android.R.color.system_accent2_700, 0xFFE8DEF8, 0xFF4A4458)
    val onSecondaryContainer = pick(android.R.color.system_accent2_900, android.R.color.system_accent2_100, 0xFF1D192B, 0xFFE8DEF8)
    val surface = pick(android.R.color.system_neutral1_10, android.R.color.system_neutral1_900, 0xFFFEF7FF, 0xFF141218)
    val onSurface = pick(android.R.color.system_neutral1_900, android.R.color.system_neutral1_100, 0xFF1D1B20, 0xFFE6E0E9)
    val surfaceContainer = pick(android.R.color.system_neutral1_50, android.R.color.system_neutral1_800, 0xFFF3EDF7, 0xFF211F26)
    val surfaceContainerHigh = pick(android.R.color.system_neutral1_100, android.R.color.system_neutral1_700, 0xFFECE6F0, 0xFF2B2930)
    val onSurfaceVariant = pick(android.R.color.system_neutral2_700, android.R.color.system_neutral2_200, 0xFF49454F, 0xFFCAC4D0)
}

/** Пружины M3 Expressive: md.sys.motion.spring.* (expressive scheme). */
object Spring {
    class Spec(val damping: Float, val stiffness: Float) {
        private val w0 = sqrt(stiffness)
        /** до затухания огибающей до 0.0001, сек */
        val duration = ln(10000f) / (damping * w0)

        /** реакция пружины на «ступеньку» 0→1; для spatial перелетает через 1 */
        fun value(t: Float): Float =
            if (damping >= 1f) 1f - exp(-w0 * t) * (1f + w0 * t)
            else {
                val wd = w0 * sqrt(1f - damping * damping)
                1f - exp(-damping * w0 * t) * (cos(wd * t) + damping * w0 / wd * sin(wd * t))
            }
    }

    val FAST_SPATIAL = Spec(0.6f, 800f)      // кнопки, мелкие элементы: форма, размер, положение
    val DEFAULT_SPATIAL = Spec(0.8f, 380f)   // крупные элементы
    val FAST_EFFECTS = Spec(1.0f, 3800f)     // цвет и прозрачность мелких элементов, без отскока
    val DEFAULT_EFFECTS = Spec(1.0f, 1600f)

    fun start(spec: Spec, from: Float, to: Float, onUpdate: (Float) -> Unit): ValueAnimator {
        val total = spec.duration
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (total * 1000).toLong()
            interpolator = TimeInterpolator { f -> if (f >= 1f) 1f else spec.value(f * total) }
            addUpdateListener { onUpdate(from + (to - from) * (it.animatedValue as Float)) }
            start()
        }
    }
}

object M3Type {
    /** Применяет стиль шрифта: размер/высота строки (sp), вес, трекинг (sp). */
    fun style(tv: TextView, sp: Float, lineSp: Float, weight: Int, trackingSp: Float): TextView {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        tv.typeface = Typeface.create(Typeface.DEFAULT, weight, false)
        tv.letterSpacing = trackingSp / sp
        tv.lineHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, lineSp, tv.resources.displayMetrics
        ).roundToInt()
        return tv
    }
}

private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
    (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).roundToInt(),
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt()
)

enum class M3Kind { FILLED, TONAL }

/** Размеры кнопок M3 Expressive: высота, отступ, иконка, зазор, размер текста, квадратный угол, угол при нажатии (dp). */
enum class M3Size(val h: Int, val pad: Int, val icon: Int, val gap: Int, val sp: Float, val line: Float, val square: Int, val pressed: Int) {
    S(40, 16, 20, 8, 14f, 20f, 12, 8),
    M(56, 24, 24, 8, 16f, 24f, 16, 12)
}

/**
 * Кнопка M3 Expressive: круглая (full) или квадратная форма.
 * При нажатии форма «перетекает» в pressed-форму пружиной fast spatial (с отскоком),
 * а слой состояния (10%) меняется пружиной fast effects (без отскока).
 */
@SuppressLint("ViewConstructor")
class M3Button(
    ctx: Context,
    c: M3Colors,
    private val kind: M3Kind,
    private val size: M3Size,
    private val round: Boolean,
    label: String,
    iconRes: Int = 0
) : TextView(ctx) {
    private val dp = resources.displayMetrics.density
    private val container = if (kind == M3Kind.FILLED) c.primary else c.secondaryContainer
    private val content = if (kind == M3Kind.FILLED) c.onPrimary else c.onSecondaryContainer
    private val bg = GradientDrawable()
    private var radius = restRadius()
    private var layer = 0f
    private var radiusAnim: ValueAnimator? = null
    private var layerAnim: ValueAnimator? = null

    init {
        text = label
        setTextColor(content)
        // label: у заметных кнопок emphasized (Bold), у остальных Medium
        M3Type.style(this, size.sp, size.line, if (kind == M3Kind.FILLED) 700 else 500, 0.15f)
        isAllCaps = false
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setHeight((size.h * dp).roundToInt())
        val p = (size.pad * dp).roundToInt()
        setPadding(p, 0, p, 0)
        if (iconRes != 0) {
            val d = ctx.getDrawable(iconRes)!!.mutate()
            d.setTint(content)
            val s = (size.icon * dp).roundToInt()
            d.setBounds(0, 0, s, s)
            setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = (size.gap * dp).roundToInt()
        }
        background = bg
        render()
        isClickable = true
        isFocusable = true
    }

    private fun restRadius() = if (round) size.h * dp / 2f else size.square * dp

    private fun render() {
        bg.setColor(blend(container, content, layer * 0.10f))
        bg.cornerRadius = max(0f, radius)
    }

    private fun press(down: Boolean) {
        radiusAnim?.cancel(); layerAnim?.cancel()
        val toR = if (down) size.pressed * dp else restRadius()
        radiusAnim = Spring.start(Spring.FAST_SPATIAL, radius, toR) { radius = it; render() }
        layerAnim = Spring.start(Spring.FAST_EFFECTS, layer, if (down) 1f else 0f) { layer = it.coerceIn(0f, 1f); render() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(false)
        }
        return super.onTouchEvent(e)
    }
}

/** Стандартная иконка-кнопка (40dp, иконка 20dp), слой состояния — пружина effects. */
@SuppressLint("ViewConstructor")
class M3IconButton(ctx: Context, c: M3Colors, iconRes: Int, desc: String) : ImageView(ctx) {
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
    private val tint = c.onSurfaceVariant
    private var layer = 0f
    private var anim: ValueAnimator? = null

    init {
        val dp = resources.displayMetrics.density
        val d = ctx.getDrawable(iconRes)!!.mutate()
        d.setTint(tint)
        setImageDrawable(d)
        val p = (10 * dp).roundToInt()
        setPadding(p, p, p, p)
        background = bg
        contentDescription = desc
        isClickable = true
        isFocusable = true
    }

    private fun press(down: Boolean) {
        anim?.cancel()
        anim = Spring.start(Spring.FAST_EFFECTS, layer, if (down) 1f else 0f) {
            layer = it.coerceIn(0f, 1f)
            bg.setColor(Color.argb((layer * 0.10f * 255).roundToInt(), Color.red(tint), Color.green(tint), Color.blue(tint)))
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(false)
        }
        return super.onTouchEvent(e)
    }
}
