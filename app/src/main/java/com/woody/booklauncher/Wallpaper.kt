package com.woody.booklauncher

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 홈 배경 사진(설정 › 화면 › 배경 사진). 고른 사진을 기기 보호 저장소 `files/wallpaper.jpg` 에 복사해 두고(잠금 해제 전에도 홈에 보이게,
 * 원본 파일을 지우거나 옮겨도 그대로), 홈 전체 바탕에 화면을 채워(가운데 자르기) 흰색과 섞어 진하기([HomePrefs.wallpaperStrength], 0~100%)만큼 그린다.
 * 흑백 테마면 회색으로, 컬러 테마면 원래 색으로. 글자는 그 위에 그대로 그린다(읽기 어려우면 사용자가 진하기를 낮춘다).
 * 크레마는 직접 그린 그림도 화면 대비만큼 어둡게 보여 미리 밝힌다([Crema.undoContrast], 표지와 같다).
 * 그림은 배경(background)이 아니라 홈의 맨 바깥 뷰가 흰 바탕과 함께 직접 그린다(크레마가 배경 있는 뷰에 명암 필터를 다시 거는 것을 피한다).
 */
object Wallpaper {
    private const val FILE = "wallpaper.jpg"
    private const val QUALITY = 90

    private fun file(context: Context) = File(Storage.of(context).filesDir, FILE)

    fun exists(context: Context) = file(context).isFile

    /**
     * [uri] 의 사진을 복사한다(뒤에서 부른다). 화면을 어느 쪽으로 돌려도 채울 수 있게 화면 긴 변 크기의 정사각형을 덮는 데까지만 줄인다(키우지 않는다).
     * 사진의 회전 정보는 [ImageDecoder] 가 맞춘다. 투명한 곳은 흰색으로. 읽지 못하면 예외(있던 사진은 그대로).
     * 크기는 고르는 창이 아니라 화면 전체로 잰다(분할 화면에서 골라도 홈 전체를 채우게).
     */
    fun save(context: Context, uri: Uri) {
        val long = Resources.getSystem().displayMetrics.let { max(it.widthPixels, it.heightPixels) }
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = info.size.width to info.size.height
            val s = min(1f, max(long.toFloat() / w, long.toFloat() / h))
            if (s < 1f) decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
        }
        // JPEG 은 투명을 담지 못해 투명한 곳이 검게 남는다(투명 배경 PNG): 흰 바탕에 얹는다
        val bmp = if (!decoded.hasAlpha()) decoded else Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
            decoded.recycle()
        }
        // 반쯤 쓴 파일이 남지 않게 다른 이름으로 다 쓴 뒤 바꾼다(실패하면 지운다)
        val dst = file(context)
        val tmp = File(dst.parentFile, "$FILE.tmp")
        try {
            tmp.outputStream().use { if (!bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)) error("compress") }
            if (!tmp.renameTo(dst)) error("rename")
        } finally {
            bmp.recycle()
            tmp.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** 그릴 배경: 사진 파일([stamp], 바꾸면 달라진다)·진하기·컬러·크레마 화면 대비. 같으면 만들어 둔 그림을 그대로 쓴다([Cache]). */
    data class Spec(val stamp: String, val strength: Int, val color: Boolean, val tone: Float)

    /** 지금 그릴 배경. 사진이 없거나 진하기 0 이면 null(흰 바탕). */
    fun spec(context: Context, strength: Int, color: Boolean): Spec? {
        if (strength <= 0) return null
        val f = file(context)
        if (!f.isFile) return null
        return Spec("${f.lastModified()}/${f.length()}", strength, color, Crema.contrastTone(context))
    }

    /** 만들어 둔 배경 그림 하나(홈·설정 미리 보기): [Spec] 과 크기가 같으면 그대로, 다르면 그 자리에서 다시 만든다. */
    class Cache {
        private var key: Triple<Spec, Int, Int>? = null
        private var bmp: Bitmap? = null

        fun get(context: Context, spec: Spec?, w: Int, h: Int): Bitmap? {
            if (spec == null) { key = null; bmp = null; return null }
            val k = Triple(spec, w, h)
            if (k != key) { key = k; bmp = render(context, spec, w, h) }
            return bmp
        }
    }

    /** [w]×[h] 를 채운 배경 그림. 사진을 읽지 못하면 null. */
    private fun render(context: Context, spec: Spec, w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val src = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(context))) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // 채우는 데 필요한 크기까지만 읽는다
                val s = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height)
                if (s < 1f) decoder.setTargetSize(ceil(info.size.width * s).toInt(), ceil(info.size.height * s).toInt())
            }
        }.getOrNull() ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(wash(spec.strength, spec.color)) }
        Canvas(out).drawBitmap(src, crop(src.width, src.height, w, h), Rect(0, 0, w, h), paint)
        src.recycle()
        Crema.undoContrast(out, spec.tone)
        return out
    }

    /** 사진 [sw]×[sh] 에서 [w]×[h] 를 비율 그대로 채우는 가운데 부분 */
    internal fun crop(sw: Int, sh: Int, w: Int, h: Int): Rect {
        val k = max(w.toFloat() / sw, h.toFloat() / sh)
        val cw = min(sw, (w / k).roundToInt())
        val ch = min(sh, (h / k).roundToInt())
        val x = (sw - cw) / 2
        val y = (sh - ch) / 2
        return Rect(x, y, x + cw, y + ch)
    }

    /**
     * 흰색과 섞기: 사진이 [strength]% 남는다(밝기 c → 255 − a(255 − c)). 흑백이면 먼저 회색으로(밝기 가중 0.299·0.587·0.114).
     */
    internal fun wash(strength: Int, color: Boolean): ColorMatrix {
        val a = strength.coerceIn(0, 100) / 100f
        val t = 255f * (1 - a)
        val m = if (color) floatArrayOf(
            a, 0f, 0f, 0f, t,
            0f, a, 0f, 0f, t,
            0f, 0f, a, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ) else {
            val (r, g, b) = Triple(0.299f * a, 0.587f * a, 0.114f * a)
            floatArrayOf(
                r, g, b, 0f, t,
                r, g, b, 0f, t,
                r, g, b, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        }
        return ColorMatrix(m)
    }
}
