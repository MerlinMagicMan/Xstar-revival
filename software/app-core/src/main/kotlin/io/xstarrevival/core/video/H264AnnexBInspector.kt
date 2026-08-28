package io.xstarrevival.core.video

/** Small, strict checks for standard H.264 Annex-B callback payloads. */
object H264AnnexBInspector {
    private val codecSetupTypes = setOf(6, 7, 8, 9)
    private val parameterSetTypes = setOf(7, 8)

    /**
     * True only when the whole payload is an Annex-B sequence containing SPS
     * or PPS and no picture or unknown NAL units. Opaque payloads return false.
     */
    fun isCodecSetup(payload: ByteArray): Boolean {
        val types = nalTypes(payload) ?: return false
        return types.any { it in parameterSetTypes } && types.all { it in codecSetupTypes }
    }

    /** Returns NAL types only when the payload starts with a valid Annex-B start code. */
    fun nalTypes(payload: ByteArray): Set<Int>? {
        if (startCodeLength(payload, 0) == 0) return null
        val types = linkedSetOf<Int>()
        var offset = 0
        while (offset < payload.size) {
            val startCodeLength = startCodeLength(payload, offset)
            if (startCodeLength == 0) return null
            val header = offset + startCodeLength
            if (header >= payload.size) return null
            types += payload[header].toInt() and 0x1f

            val next = findStartCode(payload, header + 1)
            if (next < 0) break
            offset = next
        }
        return types.takeIf { it.isNotEmpty() }
    }

    private fun findStartCode(bytes: ByteArray, from: Int): Int {
        var index = from.coerceAtLeast(0)
        while (index <= bytes.size - 3) {
            if (startCodeLength(bytes, index) != 0) return index
            index++
        }
        return -1
    }

    private fun startCodeLength(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 >= bytes.size) return 0
        if (bytes[offset] != 0.toByte() || bytes[offset + 1] != 0.toByte()) return 0
        if (bytes[offset + 2] == 1.toByte()) return 3
        return if (
            offset + 3 < bytes.size &&
            bytes[offset + 2] == 0.toByte() &&
            bytes[offset + 3] == 1.toByte()
        ) 4 else 0
    }
}
