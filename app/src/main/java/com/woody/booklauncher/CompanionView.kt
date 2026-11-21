package com.woody.booklauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Draws the reading companion. No bitmap assets: the whole pet is vector shapes. */
internal class PetView(context: Context) : View(context) {
    var stage: PetStage = PetStage.BABY
    var mood: Int = 70
    var energy: Int = 70

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2)
        color = Ui.BLACK
    }
    private val gray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0DC.toInt() }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = min(width, height).toFloat()
        val cx = width / 2f
        val base = height * 0.76f
        val scale = when (stage) {
            PetStage.EGG -> 0.80f
            PetStage.BABY -> 0.80f
            PetStage.CHILD -> 0.90f
            PetStage.TEEN -> 1.00f
            PetStage.ADULT -> 1.08f
            PetStage.ELDER -> 1.16f
        }
        val s = w * 0.46f * scale
        // rug
        gray.color = 0xFFE6E6E2.toInt()
        canvas.drawOval(cx - s * 0.75f, base + s * 0.02f, cx + s * 0.75f, base + s * 0.22f, gray)
        if (stage == PetStage.EGG) {
            drawEgg(canvas, cx, base, s)
            return
        }
        // tail
        line.strokeWidth = context.dpf(3)
        path.reset()
        path.moveTo(cx + s * 0.42f, base - s * 0.02f)
        path.cubicTo(cx + s * 0.95f, base - s * 0.15f, cx + s * 0.95f, base - s * 0.55f, cx + s * 0.62f, base - s * 0.52f)
        fill.color = Color.WHITE
        canvas.drawPath(path, line)
        line.strokeWidth = context.dpf(2)
        // body
        rect.set(cx - s * 0.46f, base - s * 0.56f, cx + s * 0.46f, base + s * 0.08f)
        canvas.drawOval(rect, fill)
        canvas.drawOval(rect, line)
        // feet
        canvas.drawOval(cx - s * 0.34f, base - s * 0.02f, cx - s * 0.02f, base + s * 0.12f, fill)
        canvas.drawOval(cx - s * 0.34f, base - s * 0.02f, cx - s * 0.02f, base + s * 0.12f, line)
        canvas.drawOval(cx + s * 0.02f, base - s * 0.02f, cx + s * 0.34f, base + s * 0.12f, fill)
        canvas.drawOval(cx + s * 0.02f, base - s * 0.02f, cx + s * 0.34f, base + s * 0.12f, line)
        // head
        val hy = base - s * 0.74f
        val hr = s * 0.37f
        // ears
        path.reset()
        path.moveTo(cx - hr * 0.85f, hy - hr * 0.30f)
        path.lineTo(cx - hr * 0.95f, hy - hr * 1.12f)
        path.lineTo(cx - hr * 0.20f, hy - hr * 0.80f)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
        path.reset()
        path.moveTo(cx + hr * 0.85f, hy - hr * 0.30f)
        path.lineTo(cx + hr * 0.95f, hy - hr * 1.12f)
        path.lineTo(cx + hr * 0.20f, hy - hr * 0.80f)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
        canvas.drawCircle(cx, hy, hr, fill)
        canvas.drawCircle(cx, hy, hr, line)
        // eyes
        val eyeY = hy - hr * 0.06f
        if (energy < 25) {
            line.strokeWidth = context.dpf(2.4f)
            canvas.drawLine(cx - hr * 0.46f, eyeY, cx - hr * 0.16f, eyeY, line)
            canvas.drawLine(cx + hr * 0.16f, eyeY, cx + hr * 0.46f, eyeY, line)
            line.strokeWidth = context.dpf(2)
        } else {
            canvas.drawCircle(cx - hr * 0.30f, eyeY, hr * 0.10f, dark)
            canvas.drawCircle(cx + hr * 0.30f, eyeY, hr * 0.10f, dark)
        }
        // nose and mouth
        path.reset()
        path.moveTo(cx - hr * 0.10f, hy + hr * 0.22f)
        path.lineTo(cx + hr * 0.10f, hy + hr * 0.22f)
        path.lineTo(cx, hy + hr * 0.34f)
        path.close()
        canvas.drawPath(path, dark)
        line.strokeWidth = context.dpf(1.8f)
        if (mood >= 35) {
            canvas.drawArc(cx - hr * 0.34f, hy + hr * 0.28f, cx - hr * 0.02f, hy + hr * 0.55f, 0f, 180f, false, line)
            canvas.drawArc(cx + hr * 0.02f, hy + hr * 0.28f, cx + hr * 0.34f, hy + hr * 0.55f, 0f, 180f, false, line)
        } else {
            canvas.drawArc(cx - hr * 0.34f, hy + hr * 0.44f, cx - hr * 0.02f, hy + hr * 0.67f, 180f, 180f, false, line)
            canvas.drawArc(cx + hr * 0.02f, hy + hr * 0.44f, cx + hr * 0.34f, hy + hr * 0.67f, 180f, 180f, false, line)
        }
        line.strokeWidth = context.dpf(1.2f)
        for (i in 0 until 2) {
            val y = hy + hr * (0.18f + i * 0.12f)
            canvas.drawLine(cx - hr * 0.95f, y, cx - hr * 0.55f, y - hr * 0.03f, line)
            canvas.drawLine(cx + hr * 0.55f, y - hr * 0.03f, cx + hr * 0.95f, y, line)
        }
        line.strokeWidth = context.dpf(2)
    }

    private fun drawEgg(canvas: Canvas, cx: Float, base: Float, s: Float) {
        rect.set(cx - s * 0.42f, base - s * 0.75f, cx + s * 0.42f, base + s * 0.05f)
        canvas.drawOval(rect, fill)
        canvas.drawOval(rect, line)
        gray.color = 0xFFCFCFCA.toInt()
        canvas.drawCircle(cx - s * 0.14f, base - s * 0.42f, s * 0.08f, gray)
        canvas.drawCircle(cx + s * 0.17f, base - s * 0.25f, s * 0.06f, gray)
        canvas.drawCircle(cx - s * 0.03f, base - s * 0.15f, s * 0.045f, gray)
    }
}
