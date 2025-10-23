package com.inncome.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var scanRect: RectF? = null

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        isAntiAlias = true
    }
    private val cornerRadiusPx = 12f * resources.displayMetrics.density

    private val LASER_HEIGHT = 20f
    private var laserY: Float = 0f
    private val ANIMATION_DURATION = 2000L
    private val laserPaint = Paint().apply {
        isAntiAlias = true
    }
    private val rectClip = Rect()

    init {
        startLaserAnimation()
    }

    fun startLaserAnimation() {
        val frame = scanRect ?: return

        val animator = ValueAnimator.ofFloat(frame.bottom, frame.top)

        animator.duration = ANIMATION_DURATION
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()

        animator.addUpdateListener { animation ->
            laserY = animation.animatedValue as Float

            invalidate()
        }

        animator.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLaserAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
     }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame = scanRect ?: return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawRoundRect(frame, cornerRadiusPx, cornerRadiusPx, clearPaint)

        canvas.drawRoundRect(frame, cornerRadiusPx, cornerRadiusPx, borderPaint)

        frame.round(rectClip)
        canvas.clipRect(rectClip)

        val shader = LinearGradient(
            frame.left,
            laserY - LASER_HEIGHT / 2,
            frame.right,
            laserY + LASER_HEIGHT / 2,
            intArrayOf(
                Color.parseColor("#00FF0000"),
                Color.parseColor("#AA00FF00"),
                Color.parseColor("#00FF0000")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val laserShader = LinearGradient(
            frame.left, laserY - LASER_HEIGHT / 2,
            frame.right, laserY + LASER_HEIGHT / 2,
            intArrayOf(
                Color.parseColor("#0000FF00"),
                Color.parseColor("#AAFF00"),
                Color.parseColor("#0000FF00")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        laserPaint.shader = laserShader

        canvas.drawRect(
            frame.left,
            laserY - LASER_HEIGHT / 2,
            frame.right,
            laserY + LASER_HEIGHT / 2,
            laserPaint
        )
    }
}