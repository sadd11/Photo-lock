package com.wafuv.photolock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.*
import android.widget.LinearLayout

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private lateinit var iv: ImageView
    private lateinit var bar: LinearLayout
    private lateinit var m3c: M3Colors
    private var barAnims = mutableListOf<ValueAnimator>()

    private var locked = true
    private var taps = 0
    private var lastTap = 0L

    private val m = Matrix()
    private var bw = 1f
    private var bh = 1f
    private var base = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var scaling = false
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (root == null && !showOverlay()) stopSelf()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("lock", "Блокировка", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "lock")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("PhotoLock активен")
            .setContentText("Нажми на экран 5 раз: зум или смена фото")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, 1 shl 30) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else startForeground(1, n)
    }

    private fun showOverlay(): Boolean {
        val bmp = BitmapFactory.decodeFile(MainActivity.photoFile(this).path) ?: return false
        bw = bmp.width.toFloat(); bh = bmp.height.toFloat()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dp = resources.displayMetrics.density

        iv = ImageView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            scaleType = ImageView.ScaleType.MATRIX
            setImageBitmap(bmp)
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) resetMatrix()
            }
            setOnTouchListener { _, e -> onTouch(e) }
        }

        // Плавающая панель M3 (floating toolbar): контейнер surface container, форма full, elevation level 3 (6dp).
        m3c = M3Colors(this)
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(m3c.surfaceContainer)
                cornerRadius = 100 * dp
            }
            elevation = 6 * dp
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            visibility = View.GONE
            // тональная кнопка (менее важная) — слева, заливная (главная) — справа
            addView(
                M3Button(context, m3c, M3Kind.TONAL, M3Size.S, round = true,
                    label = "Фото", iconRes = R.drawable.ic_image)
                    .apply { setOnClickListener { changePhoto() } }
            )
            addView(
                M3IconButton(context, m3c, R.drawable.ic_close, "Закрыть")
                    .apply { setOnClickListener { stopSelf() } },
                LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                    marginStart = (4 * dp).toInt(); marginEnd = (4 * dp).toInt()
                }
            )
            addView(
                M3Button(context, m3c, M3Kind.FILLED, M3Size.S, round = true, label = "Заблокировать")
                    .apply { setOnClickListener { setLocked(true) } }
            )
        }

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val cur = currentScale()
                val target = (cur * d.scaleFactor).coerceIn(base, base * 6f)
                val f = target / cur
                m.postScale(f, f, d.focusX, d.focusY)
                clampAndApply()
                return true
            }
        })

        val rootView = FrameLayout(this).apply {
            addView(iv, FrameLayout.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (40 * dp).toInt()
            })
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm!!.addView(rootView, lp)
        root = rootView
        return true
    }

    // ---------- режимы ----------

    private fun setLocked(value: Boolean) {
        locked = value
        taps = 0
        barAnims.forEach { it.cancel() }; barAnims.clear()
        val dp = resources.displayMetrics.density
        if (value) {
            // скрытие: только прозрачность, пружина fast effects (без отскока)
            val a = Spring.start(Spring.FAST_EFFECTS, bar.alpha, 0f) { bar.alpha = it.coerceIn(0f, 1f) }
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (locked) bar.visibility = View.GONE
                }
            })
            barAnims.add(a)
        } else {
            // появление: «выпрыгивает» снизу (default spatial, с отскоком) и проявляется (default effects)
            if (bar.visibility != View.VISIBLE) bar.alpha = 0f
            bar.visibility = View.VISIBLE
            barAnims.add(Spring.start(Spring.DEFAULT_SPATIAL, 120 * dp, 0f) { bar.translationY = it })
            barAnims.add(Spring.start(Spring.DEFAULT_EFFECTS, bar.alpha, 1f) { bar.alpha = it.coerceIn(0f, 1f) })
        }
    }

    private fun changePhoto() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        stopSelf()
    }

    // ---------- касания ----------

    private var lastAngle = 0f

    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    private fun onTouch(e: MotionEvent): Boolean {
        if (locked) {
            if (e.action == MotionEvent.ACTION_DOWN) onTap()
            return true // съедаем все касания
        }
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; scaling = false }
            MotionEvent.ACTION_POINTER_DOWN -> {
                scaling = true
                if (e.pointerCount == 2) lastAngle = angle(e)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // оставшийся палец продолжает двигать без прыжка
                val i = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(i); lastY = e.getY(i)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !scaling) {
                    m.postTranslate(e.x - lastX, e.y - lastY)
                    clampAndApply()
                } else if (e.pointerCount == 2) {
                    // вращение двумя пальцами вокруг точки между ними
                    val a = angle(e)
                    var d = a - lastAngle
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    lastAngle = a
                    m.postRotate(d, (e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f)
                    clampAndApply()
                }
                if (e.pointerCount == 1) { lastX = e.x; lastY = e.y }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scaling = false
        }
        return true
    }

    private fun onTap() {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap > 2500) 1 else taps + 1 // 5 нажатий подряд, пауза не больше 2.5 сек
        lastTap = now
        if (taps >= 5) setLocked(false)
    }

    // ---------- матрица (зум / перемещение) ----------

    private fun currentScale(): Float {
        val v = FloatArray(9); m.getValues(v)
        return hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y])
    }

    private fun currentRotation(): Float {
        val v = FloatArray(9); m.getValues(v)
        return Math.toDegrees(atan2(v[Matrix.MSKEW_Y].toDouble(), v[Matrix.MSCALE_X].toDouble())).toFloat()
    }

    private fun resetMatrix() {
        val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
        if (vw == 0f || vh == 0f) return
        base = maxOf(vw / bw, vh / bh) // как centerCrop
        m.reset()
        m.postScale(base, base)
        m.postTranslate((vw - bw * base) / 2f, (vh - bh * base) / 2f)
        iv.imageMatrix = m
    }

    private fun clampAndApply() {
        val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
        if (abs(currentRotation()) < 0.5f) {
            // без поворота: фото всегда закрывает экран
            val r = RectF(0f, 0f, bw, bh)
            m.mapRect(r)
            var dx = 0f; var dy = 0f
            if (r.width() <= vw) dx = (vw - r.width()) / 2f - r.left
            else { if (r.left > 0) dx = -r.left; if (r.right < vw) dx = vw - r.right }
            if (r.height() <= vh) dy = (vh - r.height()) / 2f - r.top
            else { if (r.top > 0) dy = -r.top; if (r.bottom < vh) dy = vh - r.bottom }
            m.postTranslate(dx, dy)
        } else {
            // с поворотом: центр фото не уходит за пределы экрана
            val c = floatArrayOf(bw / 2f, bh / 2f)
            m.mapPoints(c)
            m.postTranslate(c[0].coerceIn(0f, vw) - c[0], c[1].coerceIn(0f, vh) - c[1])
        }
        iv.imageMatrix = m
    }

    override fun onDestroy() {
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }
}
