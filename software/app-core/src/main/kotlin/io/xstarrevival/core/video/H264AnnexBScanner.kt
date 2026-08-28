package io.xstarrevival.core.video

/**
 * One complete H.264 access unit in Annex-B byte-stream form.
 *
 * The bytes retain start codes so they can be queued directly to an Android
 * AVC decoder. This model deliberately says nothing about Autel USB framing.
 */
data class H264AccessUnit(
    val bytes: ByteArray,
    val presentationTimeUs: Long,
    val keyFrame: Boolean,
    val nalTypes: Set<Int>
)

/**
 * Incrementally groups standard H.264 Annex-B NAL units into pictures.
 *
 * Boundaries are taken from access-unit delimiters when present. Streams that
 * omit delimiters are split only when a VCL NAL starts at macroblock zero, as
 * defined by the standard slice header. Unknown NAL types are preserved.
 */
class H264AnnexBScanner(
    frameRate: Int = 15,
    private val maxBufferedBytes: Int = 2 * 1024 * 1024
) {
    private val frameDurationUs = 1_000_000L / frameRate.coerceAtLeast(1)
    private var byteBuffer = ByteArray(0)
    private val pendingNals = mutableListOf<ByteArray>()
    private var nextPresentationTimeUs = 0L

    fun push(chunk: ByteArray): List<H264AccessUnit> {
        if (chunk.isEmpty()) return emptyList()
        require(byteBuffer.size + chunk.size <= maxBufferedBytes) {
            "H.264 scanner buffer exceeded $maxBufferedBytes bytes without a complete NAL boundary"
        }

        byteBuffer += chunk
        val output = mutableListOf<H264AccessUnit>()
        var start = findStartCode(byteBuffer, 0)
        if (start < 0) {
            byteBuffer = byteBuffer.takeLast(3).toByteArray()
            return output
        }

        while (true) {
            val next = findStartCode(byteBuffer, start + startCodeLength(byteBuffer, start))
            if (next < 0) break
            acceptNal(byteBuffer.copyOfRange(start, next), output)
            start = next
        }
        byteBuffer = byteBuffer.copyOfRange(start, byteBuffer.size)
        return output
    }

    fun endOfStream(): List<H264AccessUnit> {
        val output = mutableListOf<H264AccessUnit>()
        val start = findStartCode(byteBuffer, 0)
        if (start >= 0 && start + startCodeLength(byteBuffer, start) < byteBuffer.size) {
            acceptNal(byteBuffer.copyOfRange(start, byteBuffer.size), output)
        }
        byteBuffer = ByteArray(0)
        emitPending(output)
        return output
    }

    fun reset() {
        byteBuffer = ByteArray(0)
        pendingNals.clear()
        nextPresentationTimeUs = 0L
    }

    private fun acceptNal(nal: ByteArray, output: MutableList<H264AccessUnit>) {
        val headerIndex = startCodeLength(nal, 0)
        if (headerIndex >= nal.size) return
        val type = nal[headerIndex].toInt() and 0x1f

        if (type == NAL_ACCESS_UNIT_DELIMITER && pendingNals.isNotEmpty()) {
            emitPending(output)
        } else if (type in VCL_NAL_TYPES && pendingNals.any(::isVclNal)) {
            if (firstMacroblockInSlice(nal, headerIndex + 1) == 0) emitPending(output)
        }
        pendingNals += nal
    }

    private fun emitPending(output: MutableList<H264AccessUnit>) {
        if (pendingNals.none(::isVclNal)) return
        val types = pendingNals.mapTo(linkedSetOf()) { nalType(it) }
        output += H264AccessUnit(
            bytes = pendingNals.fold(ByteArray(0)) { all, nal -> all + nal },
            presentationTimeUs = nextPresentationTimeUs,
            keyFrame = NAL_IDR_SLICE in types,
            nalTypes = types
        )
        nextPresentationTimeUs += frameDurationUs
        pendingNals.clear()
    }

    private fun isVclNal(nal: ByteArray): Boolean = nalType(nal) in VCL_NAL_TYPES

    private fun nalType(nal: ByteArray): Int {
        val header = startCodeLength(nal, 0)
        return if (header < nal.size) nal[header].toInt() and 0x1f else -1
    }

    private fun firstMacroblockInSlice(nal: ByteArray, payloadIndex: Int): Int? {
        if (payloadIndex >= nal.size) return null
        val rbsp = ArrayList<Int>(nal.size - payloadIndex)
        var zeros = 0
        for (index in payloadIndex until nal.size) {
            val value = nal[index].toInt() and 0xff
            if (zeros >= 2 && value == 0x03) {
                zeros = 0
                continue
            }
            rbsp += value
            zeros = if (value == 0) zeros + 1 else 0
        }
        return ExpGolombReader(rbsp).readUnsigned()
    }

    private class ExpGolombReader(private val bytes: List<Int>) {
        private var bitIndex = 0

        fun readUnsigned(): Int? {
            var leadingZeros = 0
            while (true) {
                val bit = readBit() ?: return null
                if (bit == 1) break
                leadingZeros++
                if (leadingZeros > 30) return null
            }
            var suffix = 0
            repeat(leadingZeros) { suffix = (suffix shl 1) or (readBit() ?: return null) }
            return (1 shl leadingZeros) - 1 + suffix
        }

        private fun readBit(): Int? {
            if (bitIndex >= bytes.size * 8) return null
            val value = (bytes[bitIndex / 8] shr (7 - bitIndex % 8)) and 1
            bitIndex++
            return value
        }
    }

    private companion object {
        const val NAL_IDR_SLICE = 5
        const val NAL_ACCESS_UNIT_DELIMITER = 9
        val VCL_NAL_TYPES = 1..5

        fun findStartCode(bytes: ByteArray, from: Int): Int {
            var index = from.coerceAtLeast(0)
            while (index <= bytes.size - 3) {
                if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
                    if (bytes[index + 2] == 1.toByte()) return index
                    if (index <= bytes.size - 4 && bytes[index + 2] == 0.toByte() && bytes[index + 3] == 1.toByte()) return index
                }
                index++
            }
            return -1
        }

        fun startCodeLength(bytes: ByteArray, offset: Int): Int =
            if (offset + 3 < bytes.size && bytes[offset + 2] == 0.toByte() && bytes[offset + 3] == 1.toByte()) 4 else 3
    }
}
