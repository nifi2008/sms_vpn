package com.example.vpnpay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/** A large on/off switch: the track changes colour and the thumb slides with an animation. */
class AnimatedToggle(context: Context) : View(context) {

    var checked = false
        private set

    var onToggle: ((Boolean) -> Unit)? = null

    private var progress = 0f
    private val animator = ValueAnimator().apply {
        duration = 320
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }
    private val trackOff = Color.rgb(203, 213, 225)
    private val trackOn = Color.rgb(14, 124, 134)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(3f), 0f, dp(1.5f), 0x40000000)
    }
    private val rect = RectF()
    private val argb = ArgbEvaluator()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for the thumb's shadow
        isClickable = true
        setOnClickListener { setChecked(!checked, animate = true, notify = true) }
    }

    fun setChecked(value: Boolean, animate: Boolean = true, notify: Boolean = false) {
        checked = value
        val target = if (value) 1f else 0f
        animator.cancel()
        if (animate) {
            animator.setFloatValues(progress, target)
            animator.start()
        } else {
            progress = target
            invalidate()
        }
        if (notify) onToggle?.invoke(value)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(64f).toInt(), dp(36f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(3f)
        rect.set(0f, 0f, w, h)
        trackPaint.color = argb.evaluate(progress, trackOff, trackOn) as Int
        canvas.drawRoundRect(rect, h / 2, h / 2, trackPaint)
        val r = (h - 2 * pad) / 2
        val cx = pad + r + (w - 2 * (pad + r)) * progress
        canvas.drawCircle(cx, h / 2, r, thumbPaint)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
