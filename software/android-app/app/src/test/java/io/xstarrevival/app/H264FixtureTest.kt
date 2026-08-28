package io.xstarrevival.app

import io.xstarrevival.core.video.H264AccessUnit
import io.xstarrevival.core.video.H264AnnexBScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class H264FixtureTest {
    @Test
    fun `bundled clip is a deterministic four second Annex B replay`() {
        val fixture = File(checkNotNull(System.getProperty("xstar.videoFixture"))).readBytes()
        val scanner = H264AnnexBScanner(frameRate = 15)
        val units = mutableListOf<H264AccessUnit>()
        var offset = 0
        val chunkSizes = intArrayOf(1, 7, 511, 2048, 4093)
        var chunkIndex = 0

        while (offset < fixture.size) {
            val chunkSize = chunkSizes[chunkIndex % chunkSizes.size]
            val end = (offset + chunkSize).coerceAtMost(fixture.size)
            units += scanner.push(fixture.copyOfRange(offset, end))
            offset = end
            chunkIndex++
        }
        units += scanner.endOfStream()

        assertEquals(60, units.size)
        assertEquals(4, units.count { it.keyFrame })
        assertTrue(units.first().nalTypes.containsAll(setOf(7, 8, 5)))
        assertEquals(3_933_294L, units.last().presentationTimeUs)
    }

}
