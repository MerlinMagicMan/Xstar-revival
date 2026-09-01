package io.xstarrevival.app.gs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaModelsTest {
    private val photo = PersistedMediaItem(
        id = "photo",
        origin = MediaOrigin.AIRCRAFT,
        kind = MediaKind.PHOTO,
        fileName = "photo.jpg",
        createdAtEpochMs = 1L,
        sizeBytes = 10L
    )
    private val video = PersistedMediaItem(
        id = "video",
        origin = MediaOrigin.LOCAL,
        kind = MediaKind.VIDEO,
        fileName = "video.mp4",
        createdAtEpochMs = 2L,
        sizeBytes = 20L,
        favorite = true
    )

    @Test
    fun filtersSeparateOriginAndKindAndPrioritizeFavorites() {
        assertEquals(listOf("video", "photo"), filterMedia(listOf(photo, video), MediaFilter.ALL).map { it.id })
        assertEquals(listOf("photo"), filterMedia(listOf(photo, video), MediaFilter.AIRCRAFT).map { it.id })
        assertEquals(listOf("video"), filterMedia(listOf(photo, video), MediaFilter.VIDEOS).map { it.id })
    }

    @Test
    fun normalizationRejectsInvalidTransferMetadata() {
        val normalized = video.copy(fileName = " ", sizeBytes = -1L, durationSeconds = Double.NaN, frameRateFps = 999).normalized()

        assertEquals("XSTAR_VIDEO.MP4", normalized.fileName)
        assertEquals(0L, normalized.sizeBytes)
        assertNull(normalized.durationSeconds)
        assertEquals(240, normalized.frameRateFps)
    }
}
