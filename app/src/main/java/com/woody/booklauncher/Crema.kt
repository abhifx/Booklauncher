package com.woody.booklauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import kotlin.math.pow

/** 크레마 기기에서만 쓰는 기능 */
object Crema {
    private const val LAUNCHER_PKG = "com.wetao.cremalauncher"

    val isCrema: Boolean get() = Build.DEVICE.startsWith("CREMA", ignoreCase = true)

    /** 페블 패널은 1456px 이지만 맨 아래 8px 쯤은 테두리에 가려 보이지 않는다(같은 6인치 패널의 보통 높이는 1448px). */
    private const val HIDDEN_BOTTOM_PX = 8

    /**
     * 화면 끝에 붙는 [view] 에서 패널의 가려지는 줄 쪽을 그만큼 비워, 하단 줄이 보이는 영역의 가운데에 오게 한다.
     * 가려지는 줄은 패널의 아래 끝이라 화면을 돌리면 자리가 바뀐다: 세로 아래, 왼쪽으로 돌리면(ROTATION_90) 오른쪽, 오른쪽으로 돌리면 왼쪽, 거꾸로면 위.
     */
    @Suppress("DEPRECATION")
    fun reserveHiddenBottom(view: android.view.View) {
        if (!isCrema) return
        val rotation = runCatching { view.context.getSystemService(WindowManager::class.java).defaultDisplay.rotation }.getOrDefault(Surface.ROTATION_0)
        val px = HIDDEN_BOTTOM_PX
        with(view) {
            when (rotation) {
                Surface.ROTATION_90 -> setPadding(paddingLeft, paddingTop, paddingRight + px, paddingBottom)
                Surface.ROTATION_270 -> setPadding(paddingLeft + px, paddingTop, paddingRight, paddingBottom)
                Surface.ROTATION_180 -> setPadding(paddingLeft, paddingTop + px, paddingRight, paddingBottom)
                else -> setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + px)
            }
        }
    }

    /**
     * 크레마 런처의 `crema Pebble` 글자 로고. 상표 이미지를 이 앱에 넣지 않으려고 기기에 설치된 크레마 런처에서 불러온다.
     * 없으면 null (로고 자리를 비운다).
     */
    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    fun logo(context: Context): Drawable? = runCatching {
        val res = context.packageManager.getResourcesForApplication(LAUNCHER_PKG)
        val id = res.getIdentifier("ic_icon_crema", "drawable", LAUNCHER_PKG)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    /**
     * 크레마 '화면 대비'의 감마 s. 크레마는 글자를 진하게 하려고 앱 화면 전체를 밝기^(1/s) 로 어둡게 그리고
     * ImageView 그림만 같은 만큼 다시 밝힌다(SystemUI `EinkSettingsProvider`: s = 1 − 대비 × 90 / 8000, 대비 56 → 0.37).
     * 대비를 끄면(0) 또는 크레마가 아니면 1.
     */
    fun contrastTone(context: Context): Float {
        if (!isCrema) return 1f
        val contrast = runCatching { Settings.System.getInt(context.contentResolver, "hq_contrast", 0) }.getOrDefault(0)
        return (1f - contrast * 90f / 8000f).coerceIn(0.1f, 1f)
    }

    /**
     * 직접 그리는 그림(PictureView, 크레마가 밝혀 주지 않음)을 미리 밝기^s 로 밝혀, 화면 대비로 어두워진 뒤
     * 원래 밝기로 보이게 한다. [bmp] 를 그 자리에서 바꾼다(빨강·초록·파랑 따로, 흑백 그림이면 밝기를 밝히는 것과 같다).
     */
    fun undoContrast(bmp: Bitmap, tone: Float) {
        if (tone >= 1f) return
        val lut = IntArray(256) { (255 * (it / 255f).pow(tone) + 0.5f).toInt() }
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            px[i] = (p and 0xFF000000.toInt()) or (lut[p shr 16 and 0xFF] shl 16) or (lut[p shr 8 and 0xFF] shl 8) or lut[p and 0xFF]
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
