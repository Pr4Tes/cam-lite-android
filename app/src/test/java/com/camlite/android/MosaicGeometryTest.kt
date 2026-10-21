package com.camlite.android

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.min

class MosaicGeometryTest {
    @Test fun fourCellsCover1280x800WithoutMarginsGapsOrOverlap() {
        val cells = MosaicGeometry.calculate(4, 1280, 800, doubleArrayOf(16.0/9, 4.0/3, 9.0/16, 2.0))
        assertEquals(listOf(CellBounds(0,0,640,400), CellBounds(640,0,1280,400), CellBounds(0,400,640,800), CellBounds(640,400,1280,800)), cells)
        assertEquals(1280 * 800, cells.sumOf { it.width * it.height })
    }
    @Test fun layoutsAdaptToRealWindowDimensionsAndRetainSlotOrder() {
        for ((w,h) in listOf(1280 to 800, 1920 to 1080, 801 to 1281, 641 to 401)) {
            for (slots in 1..4) {
                val cells = MosaicGeometry.calculate(slots, w, h, DoubleArray(slots) { 16.0/9 })
                assertEquals(slots, cells.size)
                cells.forEach { check(it.left >= 0 && it.top >= 0 && it.right <= w && it.bottom <= h && it.width > 0 && it.height > 0) }
                assertEquals(w * h, cells.sumOf { it.width * it.height })
                for (i in cells.indices) for (j in i+1 until cells.size) {
                    val a = cells[i]; val b = cells[j]
                    assertTrue(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
                }
            }
        }
    }
    @Test fun threeLandscapeCamerasGainImageAreaAndThirdCanUseWholeRow() {
        val ratio = 16.0/9
        val cells = MosaicGeometry.calculate(3,1280,800,DoubleArray(3) { ratio })
        assertEquals(1280, cells[2].width)
        fun fitArea(c: CellBounds): Double { val width=min(c.width.toDouble(), c.height*ratio); return width*width/ratio }
        val old = listOf(CellBounds(0,0,640,400),CellBounds(640,0,1280,400),CellBounds(320,400,960,800))
        assertTrue(cells.sumOf { fitArea(it) } > old.sumOf { fitArea(it) })
        assertTrue(cells.minOf { fitArea(it) } >= old.minOf { fitArea(it) })
    }
    @Test fun portraitAndUltrawideStreamsChooseUsefulThreeCameraArrangement() {
        val portrait = MosaicGeometry.calculate(3,1280,800,DoubleArray(3) { 9.0/16 })
        assertTrue(portrait.all { it.height == 800 })
        val wide = MosaicGeometry.calculate(3,1280,800,DoubleArray(3) { 32.0/9 })
        assertTrue(wide.all { it.width == 1280 })
        val two = MosaicGeometry.calculate(2,1280,800,doubleArrayOf(9.0/16,16.0/9))
        assertEquals(450, two[0].width)
        assertEquals(830, two[1].width)
    }
}
