package io.xstarrevival.app.gs

import android.content.Context
import io.xstarrevival.core.model.CameraState
import org.json.JSONArray
import org.json.JSONObject

enum class MediaOrigin { AIRCRAFT, LOCAL }
enum class MediaKind { PHOTO, VIDEO }
enum class MediaFilter { ALL, AIRCRAFT, LOCAL, PHOTOS, VIDEOS }

data class PersistedMediaItem(
    val id: String,
    val origin: MediaOrigin,
    val kind: MediaKind,
    val fileName: String,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val durationSeconds: Double? = null,
    val resolution: String? = null,
    val frameRateFps: Int? = null,
    val favorite: Boolean = false,
    val sourceMediaId: String? = null
) {
    fun normalized() = copy(
        fileName = fileName.trim().take(80).ifEmpty { if (kind == MediaKind.PHOTO) "XSTAR_PHOTO.JPG" else "XSTAR_VIDEO.MP4" },
        sizeBytes = sizeBytes.coerceAtLeast(0L),
        durationSeconds = durationSeconds?.takeIf { it.isFinite() && it >= 0.0 },
        frameRateFps = frameRateFps?.coerceIn(1, 240)
    )
}

data class MediaTransferState(
    val mediaId: String,
    val fileName: String,
    val progressPercent: Int,
    val bytesPerSecond: Long,
    val completed: Boolean = false
)

internal fun filterMedia(items: List<PersistedMediaItem>, filter: MediaFilter): List<PersistedMediaItem> =
    items.filter { item ->
        when (filter) {
            MediaFilter.ALL -> true
            MediaFilter.AIRCRAFT -> item.origin == MediaOrigin.AIRCRAFT
            MediaFilter.LOCAL -> item.origin == MediaOrigin.LOCAL
            MediaFilter.PHOTOS -> item.kind == MediaKind.PHOTO
            MediaFilter.VIDEOS -> item.kind == MediaKind.VIDEO
        }
    }.sortedWith(compareByDescending<PersistedMediaItem> { it.favorite }.thenByDescending { it.createdAtEpochMs })

class GsMediaStore(context: Context) {
    private val prefs = context.getSharedPreferences("xstar_media_library", Context.MODE_PRIVATE)

    fun load(): List<PersistedMediaItem> {
        val array = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]") ?: "[]") }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.toMediaItemOrNull()?.let(::add)
        }.sortedByDescending { it.createdAtEpochMs }
    }

    fun captureSimulatorPhotos(camera: CameraState, timestampEpochMs: Long): Boolean {
        val observed = prefs.getInt(KEY_PHOTO_COUNTER, 0)
        if (camera.photosTaken < observed) {
            prefs.edit().putInt(KEY_PHOTO_COUNTER, camera.photosTaken).apply()
            return false
        }
        if (camera.photosTaken == observed) return false
        val items = load().toMutableList()
        for (sequence in observed + 1..camera.photosTaken) {
            items += PersistedMediaItem(
                id = "sim-photo-$timestampEpochMs-$sequence",
                origin = MediaOrigin.AIRCRAFT,
                kind = MediaKind.PHOTO,
                fileName = "XSTAR_IMG_%04d.JPG".format(sequence),
                createdAtEpochMs = timestampEpochMs + sequence - camera.photosTaken,
                sizeBytes = 8L * 1024L * 1024L,
                resolution = camera.photoResolution ?: "12 MP"
            )
        }
        save(items)
        prefs.edit().putInt(KEY_PHOTO_COUNTER, camera.photosTaken).apply()
        return true
    }

    fun captureSimulatorVideos(camera: CameraState, timestampEpochMs: Long): Boolean {
        val observed = prefs.getInt(KEY_VIDEO_COUNTER, 0)
        if (camera.videosTaken < observed) {
            prefs.edit().putInt(KEY_VIDEO_COUNTER, camera.videosTaken).apply()
            return false
        }
        if (camera.videosTaken == observed) return false
        val items = load().toMutableList()
        for (sequence in observed + 1..camera.videosTaken) {
            val duration = if (sequence == camera.videosTaken) camera.lastVideoDurationSeconds ?: 0.0 else 0.0
            items += PersistedMediaItem(
                id = "sim-video-$timestampEpochMs-$sequence",
                origin = MediaOrigin.AIRCRAFT,
                kind = MediaKind.VIDEO,
                fileName = "XSTAR_VID_%04d.MP4".format(sequence),
                createdAtEpochMs = timestampEpochMs + sequence - camera.videosTaken,
                sizeBytes = (
                    duration.coerceAtLeast(1.0) * videoMegabytesPerSecond(camera.videoResolution) * 1024.0 * 1024.0
                    ).toLong(),
                durationSeconds = duration,
                resolution = camera.videoResolution ?: "4K",
                frameRateFps = camera.frameRateFps
            )
        }
        save(items)
        prefs.edit().putInt(KEY_VIDEO_COUNTER, camera.videosTaken).apply()
        return true
    }

    fun download(itemIds: Set<String>) {
        val items = load().toMutableList()
        val existingSources = items.mapNotNullTo(mutableSetOf()) { it.sourceMediaId }
        items.filter { it.id in itemIds && it.origin == MediaOrigin.AIRCRAFT && it.id !in existingSources }
            .forEach { source ->
                items += source.copy(
                    id = "local:${source.id}",
                    origin = MediaOrigin.LOCAL,
                    sourceMediaId = source.id
                )
            }
        save(items)
    }

    fun delete(itemIds: Set<String>) = save(load().filterNot { it.id in itemIds })

    fun toggleFavorite(itemId: String) = save(load().map { if (it.id == itemId) it.copy(favorite = !it.favorite) else it })

    private fun save(items: List<PersistedMediaItem>) {
        val unique = items.associateBy { it.id }.values.map(PersistedMediaItem::normalized)
        val array = JSONArray().also { encoded -> unique.forEach { encoded.put(it.toJson()) } }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_PHOTO_COUNTER = "simulator_photo_counter"
        const val KEY_VIDEO_COUNTER = "simulator_video_counter"
    }
}

private fun PersistedMediaItem.toJson() = JSONObject()
    .put("id", id)
    .put("origin", origin.name)
    .put("kind", kind.name)
    .put("name", fileName)
    .put("created", createdAtEpochMs)
    .put("size", sizeBytes)
    .putNullable("duration", durationSeconds)
    .putNullable("resolution", resolution)
    .putNullable("fps", frameRateFps)
    .put("favorite", favorite)
    .putNullable("source", sourceMediaId)

private fun JSONObject.toMediaItemOrNull(): PersistedMediaItem? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    val origin = runCatching { MediaOrigin.valueOf(optString("origin")) }.getOrNull() ?: return null
    val kind = runCatching { MediaKind.valueOf(optString("kind")) }.getOrNull() ?: return null
    return PersistedMediaItem(
        id = id,
        origin = origin,
        kind = kind,
        fileName = optString("name"),
        createdAtEpochMs = optLong("created"),
        sizeBytes = optLong("size"),
        durationSeconds = optNullableDouble("duration"),
        resolution = optNullableString("resolution"),
        frameRateFps = optNullableInt("fps"),
        favorite = optBoolean("favorite"),
        sourceMediaId = optNullableString("source")
    ).normalized()
}

private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun videoMegabytesPerSecond(resolution: String?): Double = when (resolution?.uppercase()) {
    "4K" -> 8.0
    "2.7K" -> 5.0
    else -> 3.0
}
