package io.xstarrevival.core.replay

/**
 * Synthetic standards-only replay used to exercise the product without an
 * aircraft. The opaque candidate is intentionally not presented as an Autel
 * message and is never semantically decoded.
 */
object StandardMavlinkDemoCapture {
    val chunks: List<CaptureChunk> = listOf(
        CaptureChunk(0, "58535200".hexBytes()),
        CaptureChunk(300, "fe090a01010044332211020c8004033be9".hexBytes()),
        CaptureChunk(350, "fd1e00000b010118000015cd5b070000000012d8c9184e8038c819c404005a007800d2043930030e40f0".hexBytes()),
        CaptureChunk(350, "fd1c00000c01012100006ce20100fadbc918667c38c8f0ba040010a400002c01900183fff369a996".hexBytes()),
        CaptureChunk(350, "fe1c0d01011e6ce20100cdcccc3dcdcc4cbe000040400ad7233c0ad7a33c8fc2f5bcb7fd".hexBytes()),
        CaptureChunk(350, "fd1f00000e01010100000000000000000000000000005e013c3c59010000000000000000000000004d00d5".hexBytes()),
        CaptureChunk(350, "fd2400000f0101930000b0040000ffffffff220b0a0f140f000f1e0fffffffffffffffffffffffff9a010000034ceb98".hexBytes()),
        CaptureChunk(350, "fd06000010010110a400417574656c3f3412".hexBytes()),
        CaptureChunk(350, "fe091101010000000000020c00030314a5".hexBytes())
    )
}

private fun String.hexBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
