package io.xstarrevival.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H264AnnexBInspectorTest {
    @Test
    fun `recognizes only parameter-set setup sequences`() {
        val setup = annexB(nal(7), nal(8))

        assertEquals(setOf(7, 8), H264AnnexBInspector.nalTypes(setup))
        assertTrue(H264AnnexBInspector.isCodecSetup(setup))
        assertTrue(H264AnnexBInspector.isCodecSetup(annexB(nal(6), nal(7), nal(9))))
        assertFalse(H264AnnexBInspector.isCodecSetup(annexB(nal(7), nal(5))))
        assertFalse(H264AnnexBInspector.isCodecSetup(annexB(nal(6), nal(9))))
    }

    @Test
    fun `keeps opaque and malformed payloads unclassified`() {
        assertNull(H264AnnexBInspector.nalTypes(byteArrayOf(0x58, 0x53, 0x52)))
        assertNull(H264AnnexBInspector.nalTypes(byteArrayOf(0x58, 0, 0, 1, 0x67)))
        assertNull(H264AnnexBInspector.nalTypes(byteArrayOf(0, 0, 1)))
        assertNull(H264AnnexBInspector.nalTypes(byteArrayOf(0, 0, 1, 0x67, 1, 2, 0, 0, 1)))
    }

    private fun annexB(vararg nals: ByteArray): ByteArray = nals.fold(ByteArray(0)) { all, nal ->
        all + byteArrayOf(0, 0, 0, 1) + nal
    }

    private fun nal(type: Int): ByteArray = byteArrayOf((0x60 or type).toByte(), 0x80.toByte())
}
