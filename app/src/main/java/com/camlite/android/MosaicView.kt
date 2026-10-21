package com.camlite.android

import android.content.Context
import android.view.ViewGroup

/** Cell bounds only; video uses FIT independently of the layout. */
class MosaicView(context: Context, private val slots: Int) : ViewGroup(context) {
    private val ratios = DoubleArray(slots) { 16.0 / 9 }
    private var cells: List<CellBounds> = emptyList()
    private var dirty = true
    fun setVideoAspectRatio(index: Int, ratio: Double) {
        if (index !in ratios.indices || !ratio.isFinite() || ratio <= 0 || kotlin.math.abs(ratios[index] - ratio) < 0.001) return
        ratios[index] = ratio
        if (slots in 2..3) { dirty = true; requestLayout() }
    }
    var expanded: Int = -1
        private set
    fun toggleCamera(index: Int) {
        if (index !in 0 until childCount) return
        expanded = if (expanded == index) -1 else index
        for (i in 0 until childCount) getChildAt(i).visibility = if (expanded < 0 || i == expanded) VISIBLE else INVISIBLE
        requestLayout()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (dirty || w != measuredWidth || h != measuredHeight || cells.isEmpty()) {
            cells = MosaicGeometry.calculate(slots, w, h, ratios); dirty = false
        }
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            val r = bounds(i, w, h)
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(r.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(r.height, MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) { val rect = bounds(i, width, height); getChildAt(i).layout(rect.left, rect.top, rect.right, rect.bottom) }
    }
    private fun bounds(i: Int, w: Int, h: Int): CellBounds = if (expanded == i) CellBounds(0, 0, w, h) else cells[i]
}
