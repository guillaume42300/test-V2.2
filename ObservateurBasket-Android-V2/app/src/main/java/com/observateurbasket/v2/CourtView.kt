package com.observateurbasket.v2

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CourtView(
    context: Context,
    private val actors: MutableList<Actor>
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected: Actor? = null
    private var downDx = 0f
    private var downDy = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val margin = 18f
        val left = margin
        val top = margin
        val right = width - margin
        val bottom = height - margin

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(222, 184, 120)
        canvas.drawRect(left, top, right, bottom, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        canvas.drawRect(left, top, right, bottom, paint)

        val midX = (left + right) / 2f
        canvas.drawLine(midX, top, midX, bottom, paint)
        canvas.drawCircle(midX, (top + bottom) / 2f, min(width, height) * .11f, paint)

        val laneW = (right - left) * .19f
        val laneH = (bottom - top) * .30f
        val laneTop = (top + bottom - laneH) / 2f

        paint.color = Color.WHITE
        canvas.drawRect(left, laneTop, left + laneW, laneTop + laneH, paint)
        canvas.drawRect(right - laneW, laneTop, right, laneTop + laneH, paint)

        val restricted = min(width, height) * .045f
        canvas.drawCircle(left + laneW, (top + bottom) / 2f, restricted, paint)
        canvas.drawCircle(right - laneW, (top + bottom) / 2f, restricted, paint)

        val arcR = (right - left) * .27f
        val cy = (top + bottom) / 2f
        val rectLeft = RectF(left - arcR * .58f, cy - arcR, left + arcR * .58f, cy + arcR)
        val rectRight = RectF(right - arcR * .58f, cy - arcR, right + arcR * .58f, cy + arcR)
        canvas.drawArc(rectLeft, -65f, 130f, false, paint)
        canvas.drawArc(rectRight, 115f, 130f, false, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.DKGRAY
        canvas.drawRect(left - 4f, cy - 35f, left + 8f, cy + 35f, paint)
        canvas.drawRect(right - 8f, cy - 35f, right + 4f, cy + 35f, paint)

        actors.forEach { actor ->
            val x = left + actor.x.coerceIn(0f, 1f) * (right - left)
            val y = top + actor.y.coerceIn(0f, 1f) * (bottom - top)

            paint.color = when {
                actor.id.startsWith("A") -> Color.rgb(198, 40, 40)
                actor.id.startsWith("B") -> Color.rgb(21, 101, 192)
                else -> Color.rgb(46, 125, 50)
            }
            canvas.drawCircle(x, y, 28f, paint)

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(actor.id, x, y + 7f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val margin = 18f
        val w = max(1f, width - margin * 2f)
        val h = max(1f, height - margin * 2f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selected = actors.minByOrNull { actor ->
                    val x = margin + actor.x * w
                    val y = margin + actor.y * h
                    (x - event.x) * (x - event.x) + (y - event.y) * (y - event.y)
                }?.takeIf {
                    val x = margin + it.x * w
                    val y = margin + it.y * h
                    (x - event.x) * (x - event.x) + (y - event.y) * (y - event.y) < 65f * 65f
                }
                if (selected != null) {
                    downDx = event.x - (margin + selected!!.x * w)
                    downDy = event.y - (margin + selected!!.y * h)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                selected?.let {
                    it.x = ((event.x - downDx - margin) / w).coerceIn(0f, 1f)
                    it.y = ((event.y - downDy - margin) / h).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selected = null
                return true
            }
        }
        return true
    }
}
