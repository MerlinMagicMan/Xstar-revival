package io.xstarrevival.app

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/** Private-file sink kept separate from the receive-only USB descriptor owner. */
internal class ControllerProbeCaptureSink(
    file: File,
    private val maxBytes: Long
) : Closeable {
    private val output = BufferedOutputStream(FileOutputStream(file))
    var bytesWritten: Long = 0
        private set

    fun append(bytes: ByteArray, count: Int): Int {
        val keep = ControllerProbeBounds.bytesToKeep(bytesWritten, count, maxBytes)
        if (keep > 0) {
            output.write(bytes, 0, keep)
            bytesWritten += keep
        }
        return keep
    }

    override fun close() = output.close()
}
