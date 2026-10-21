package com.camlite.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Transparent touch shield above the mosaic; never changes its dimensions or players. */
class TouchLockView(context: Context, private val unlocked: () -> Unit) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = TouchUnlockGesture()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    private var barVisible = false
    private val hide = Runnable { barVisible = false; gesture.cancel(); invalidate() }
    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Monitor com toques bloqueados. Deslize a barra da esquerda para a direita para desbloquear. Use Voltar para sair do Monitor."
    }
    private fun dp(n: Int) = n * resources.displayMetrics.density
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val barWidth = minOf(dp(320), w - dp(24))
        val bottomInset = if (android.os.Build.VERSION.SDK_INT >= 30) rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0 else dp(24).toInt()
        bar.set((w - barWidth) / 2f, h - bottomInset - dp(68), (w + barWidth) / 2f, h - bottomInset - dp(16))
        gesture.cancel()
    }
    override fun onDraw(canvas: Canvas) {
        if (!barVisible) return
        paint.color = Color.argb(205, 20, 20, 20)
        canvas.drawRoundRect(bar, dp(8), dp(8), paint)
        paint.color = Color.WHITE; paint.textSize = dp(13); paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Deslize para desbloquear", bar.centerX() + dp(16), bar.centerY() + dp(5), paint)
        paint.color = Color.argb(230, 80, 110, 100)
        val x = bar.left + gesture.progress * (bar.width() - bar.height())
        canvas.drawRoundRect(RectF(x, bar.top, x + bar.height(), bar.bottom), dp(8), dp(8), paint)
        paint.color = Color.WHITE
        canvas.drawText(">>", x + bar.height() / 2, bar.centerY() + dp(5), paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        handler.removeCallbacks(hide)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                barVisible = true
                gesture.begin(event.x, event.y, bar.left, bar.top, bar.width(), bar.height())
            }
            MotionEvent.ACTION_MOVE -> gesture.move(event.x, event.y, bar.top, bar.width(), bar.height())
            MotionEvent.ACTION_UP -> {
                gesture.move(event.x, event.y, bar.top, bar.width(), bar.height())
                if (gesture.finish()) { unlocked(); return true }
                performClick()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> gesture.cancel()
        }
        invalidate()
        handler.postDelayed(hide, 5000)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true } // A click reveals, never unlocks.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Desbloquear toques (acessibilidade)"))
    }
    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) { unlocked(); return true }
        return super.performAccessibilityAction(action, arguments)
    }
    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); gesture.cancel(); super.onDetachedFromWindow() }
}
