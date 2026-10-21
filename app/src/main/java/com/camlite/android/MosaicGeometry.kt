package com.camlite.android

import kotlin.math.min
import kotlin.math.max

data class CellBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Only runs when dimensions/aspect ratios change. Four cameras always use a gapless 2×2. */
object MosaicGeometry {
    fun calculate(slots: Int, w: Int, h: Int, ratios: DoubleArray): List<CellBounds> {
        require(slots in 1..4)
        if (w <= 0 || h <= 0) return List(slots) { CellBounds(0, 0, 0, 0) }
        if (slots == 1) return listOf(CellBounds(0, 0, w, h))
        if (slots == 4) return List(4) { i -> CellBounds((i % 2) * w / 2, (i / 2) * h / 2, (i % 2 + 1) * w / 2, (i / 2 + 1) * h / 2) }
        val aspect = DoubleArray(slots) { i -> ratios.getOrNull(i)?.takeIf { it.isFinite() && it > 0 } ?: (16.0 / 9) }
        fun columns(split: Int) = listOf(CellBounds(0, 0, split, h), CellBounds(split, 0, w, h))
        fun fittedArea(cell: CellBounds, ratio: Double): Double {
            val width = min(cell.width.toDouble(), cell.height * ratio)
            return width * width / ratio
        }
        fun best(candidates: List<List<CellBounds>>): List<CellBounds> = candidates.maxWithOrNull(
            compareBy<List<CellBounds>> { cells -> cells.indices.minOf { fittedArea(cells[it], aspect[it]) } }
                .thenBy { cells -> cells.indices.sumOf { fittedArea(cells[it], aspect[it]) } }
        )!!
        if (slots == 2) {
            if (w < h) return List(2) { i -> CellBounds(0, i * h / 2, w, (i + 1) * h / 2) }
            val splits = listOf(w / 2, min(w / 2.0, h * aspect[0]).toInt(), max(w / 2.0, w - h * aspect[1]).toInt())
            return best(splits.map { columns(it.coerceIn(1, w.coerceAtLeast(2) - 1)) })
        }
        val top = min(h / 2.0, max(w / 2.0 / aspect[0], w / 2.0 / aspect[1])).toInt().coerceIn(1, h.coerceAtLeast(2) - 1)
        val left = min(w / 2.0, h * aspect[0]).toInt().coerceIn(1, w.coerceAtLeast(2) - 1)
        val candidates = listOf(
            listOf(CellBounds(0, 0, w / 2, top), CellBounds(w / 2, 0, w, top), CellBounds(0, top, w, h)),
            List(3) { i -> CellBounds(i * w / 3, 0, (i + 1) * w / 3, h) },
            List(3) { i -> CellBounds(0, i * h / 3, w, (i + 1) * h / 3) },
            listOf(CellBounds(0, 0, left, h), CellBounds(left, 0, w, h / 2), CellBounds(left, h / 2, w, h))
        )
        // Maximize the smallest complete image first, avoiding one huge tile and two tiny ones.
        return best(candidates)
    }
}
