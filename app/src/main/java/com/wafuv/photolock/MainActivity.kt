package com.wafuv.photolock

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var c: M3Colors
    private lateinit var card: FrameLayout
    private lateinit var image: ImageView
    private lateinit var placeholder: LinearLayout
    private var cardAnim: ValueAnimator? = null
    private var dp = 1f

    companion object {
        fun photoFile(c: Context) = File(c.filesDir, "photo.jpg")
        private const val REQ_PICK = 1
    }

    private fun px(v: Int) = (v * dp).roundToInt()

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        c = M3Colors(this)

        window.statusBarColor = c.surface
        window.navigationBarColor = c.surface
        window.decorView.systemUiVisibility =
            if (c.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
            setPadding(px(24), px(24), px(24), px(16))
        }

        // Заголовок: headline large (emphasized, Medium) + подзаголовок body medium
        root.addView(M3Type.style(TextView(this), 32f, 40f, 500, 0f).apply {
            text = "PhotoLock"; setTextColor(c.onSurface)
        })
        root.addView(
            M3Type.style(TextView(this), 14f, 20f, 400, 0.2f).apply {
                text = "Фото на весь экран блокирует касания. Пять нажатий подряд — зум или смена фото."
                setTextColor(c.onSurfaceVariant)
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) }
        )

        // Главный контейнер с фото: самый большой и яркий блок экрана, угол extra large increased (32dp)
        card = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh); cornerRadius = 32 * dp
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 32 * dp)
                }
            }
            clipToOutline = true
            isClickable = true
            setOnClickListener { pickPhoto() }
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> animateCard(0.97f)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateCard(1f)
                }
                false
            }
        }
        image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(image, FrameLayout.LayoutParams(-1, -1))

        placeholder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ImageView(context).apply {
                val d = context.getDrawable(R.drawable.ic_image)!!.mutate()
                d.setTint(c.onSurfaceVariant); setImageDrawable(d)
            }, LinearLayout.LayoutParams(px(48), px(48)))
            addView(M3Type.style(TextView(context), 22f, 28f, 500, 0f).apply {
                text = "Выбери фото"; setTextColor(c.onSurface); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(16) })
            addView(M3Type.style(TextView(context), 14f, 20f, 400, 0.2f).apply {
                text = "Нажми на карточку"; setTextColor(c.onSurfaceVariant); gravity = Gravity.CENTER
            })
        }
        card.addView(placeholder, FrameLayout.LayoutParams(-1, -1))

        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = px(24); bottomMargin = px(24)
        })

        // Кнопки (size M, 56dp): одна главная заливная (круглая) + тональная квадратная для контраста форм.
        // Менее важная кнопка слева, более важная справа.
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            M3Button(this, c, M3Kind.TONAL, M3Size.M, round = false, label = "Фото")
                .apply { setOnClickListener { pickPhoto() } },
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(12) }
        )
        row.addView(
            M3Button(this, c, M3Kind.FILLED, M3Size.M, round = true,
                label = "Заблокировать", iconRes = R.drawable.ic_lock)
                .apply { setOnClickListener { startLock() } },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        root.addView(row, LinearLayout.LayoutParams(-1, -2))

        root.addView(M3Type.style(TextView(this), 11f, 16f, 500, 0.5f).apply {
            text = "made by wafuv"; setTextColor(c.onSurfaceVariant); alpha = 0.7f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16) })

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        refresh()

        // «Hero»-момент: карточка появляется с отскоком (spatial) и проявляется (effects)
        if (savedInstanceState == null) {
            card.alpha = 0f
            Spring.start(Spring.DEFAULT_EFFECTS, 0f, 1f) { card.alpha = it.coerceIn(0f, 1f) }
            animateCard(1f, from = 0.9f, spec = Spring.DEFAULT_SPATIAL)
        }
    }

    private fun animateCard(to: Float, from: Float = card.scaleX, spec: Spring.Spec = Spring.FAST_SPATIAL) {
        cardAnim?.cancel()
        cardAnim = Spring.start(spec, from, to) { card.scaleX = it; card.scaleY = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh()
    }

    private fun refresh() {
        val f = photoFile(this)
        if (f.exists()) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            image.setImageBitmap(BitmapFactory.decodeFile(f.path, opts))
            image.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } else {
            image.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
        }
    }

    private fun pickPhoto() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PICK)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (req != REQ_PICK || res != RESULT_OK) return
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > 2560) {
                    val s = 2560f / m
                    d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
            }
            FileOutputStream(photoFile(this)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            refresh()
            animateCard(1f, from = 0.94f)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLock() {
        if (!photoFile(this).exists()) {
            Toast.makeText(this, "Сначала выбери фото", Toast.LENGTH_SHORT).show(); return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши показ поверх других приложений", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Блокировка включена. 5 нажатий — зум или смена фото", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
