package io.xstarrevival.app.gs

import android.content.Intent
import android.os.storage.StorageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date

@Composable
fun GsMediaScreen(
    state: XStarState,
    source: TelemetrySource,
    mediaItems: List<PersistedMediaItem>,
    transfers: List<MediaTransferState>,
    onDownload: (Set<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<PersistedMediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visible = filterMedia(mediaItems, filter)
    val selected = mediaItems.filter { it.id in selectedIds }
    val downloadableIds = selected.filter { it.origin == MediaOrigin.AIRCRAFT }.mapTo(mutableSetOf()) { it.id }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MEDIA", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("${mediaItems.count { it.origin == MediaOrigin.AIRCRAFT }} onboard · ${mediaItems.count { it.origin == MediaOrigin.LOCAL }} local", color = GsColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("AIRCRAFT ${state.camera.storageRemainingMb?.let(::formatMegabytes) ?: "—"}", color = GsColors.White, fontSize = 11.sp)
                    Text("LOCAL ${formatBytes(availableLocalBytes(context))} available", color = GsColors.Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            val capability = when {
                source == TelemetrySource.SIMULATOR -> "Simulator captures are listed as onboard media and can be downloaded locally. Previews and shares represent synthetic capture metadata."
                state.camera.connected == true -> "Live camera telemetry is connected; onboard listing and mutations remain unavailable on the receive-only protocol."
                else -> "Connect the simulator to create media. Live onboard listing degrades safely when unsupported."
            }
            GsSectionCard("MEDIA SOURCE") { Text(capability, color = GsColors.Muted, fontSize = 11.sp) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaFilter.entries.forEach { option ->
                    if (option == filter) Button(onClick = { filter = option }, modifier = Modifier.weight(1f)) { Text(option.name, fontSize = 9.sp) }
                    else OutlinedButton(onClick = { filter = option }, modifier = Modifier.weight(1f)) { Text(option.name, fontSize = 9.sp) }
                }
            }
        }
        if (selectedIds.isNotEmpty()) {
            item {
                GsSectionCard("${selectedIds.size} SELECTED") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownload(downloadableIds) }, enabled = downloadableIds.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DOWNLOAD") }
                        OutlinedButton(onClick = { shareMedia(context, selected) }, modifier = Modifier.weight(1f)) { Text("SHARE") }
                        OutlinedButton(onClick = { pendingDelete = selectedIds }, modifier = Modifier.weight(1f)) { Text("DELETE") }
                        OutlinedButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.weight(1f)) { Text("CLEAR") }
                    }
                }
            }
        }
        if (transfers.isNotEmpty()) {
            item {
                GsSectionCard("TRANSFER QUEUE") {
                    transfers.takeLast(6).forEach { transfer ->
                        GsSettingLine(transfer.fileName, "${transfer.progressPercent}% · ${formatBytes(transfer.bytesPerSecond)}/s")
                        LinearProgressIndicator(
                            progress = { transfer.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                GsSectionCard("NO MEDIA") {
                    Text("Capture a simulator photo or finish a simulator recording to populate the onboard library.", color = GsColors.Muted)
                }
            }
        }
        items(visible.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    Card(
                        Modifier.weight(1f).clickable {
                            if (selectedIds.isEmpty()) preview = item
                            else selectedIds = selectedIds.toggle(item.id)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.id in selectedIds) GsColors.Orange.copy(alpha = .22f) else GsColors.Panel2
                        )
                    ) {
                        Column {
                            MediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Column(Modifier.padding(10.dp)) {
                                Text(item.fileName, color = GsColors.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${item.origin.name} · ${item.kind.name} · ${formatBytes(item.sizeBytes)}", color = GsColors.Muted, fontSize = 9.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { onToggleFavorite(item.id) }) {
                                        Text(if (item.favorite) "FAVORITED" else "FAVORITE", color = GsColors.Amber, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = { selectedIds = selectedIds.toggle(item.id) }) {
                                        Text(if (item.id in selectedIds) "SELECTED" else "SELECT", color = GsColors.Orange, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    preview?.let { item ->
        Dialog(onDismissRequest = { preview = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(item.fileName, color = GsColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    GsSettingLine("Source", item.origin.name)
                    GsSettingLine("Type", item.kind.name)
                    GsSettingLine("Captured", DateFormat.getDateTimeInstance().format(Date(item.createdAtEpochMs)))
                    GsSettingLine("Size", formatBytes(item.sizeBytes))
                    GsSettingLine("Resolution", item.resolution ?: "Unavailable")
                    GsSettingLine("Duration", item.durationSeconds?.let(::formatDuration) ?: "—")
                    GsSettingLine("Frame rate", item.frameRateFps?.let { "$it fps" } ?: "—")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownload(setOf(item.id)) }, enabled = item.origin == MediaOrigin.AIRCRAFT, modifier = Modifier.weight(1f)) { Text("DOWNLOAD") }
                        OutlinedButton(onClick = { shareMedia(context, listOf(item)) }, modifier = Modifier.weight(1f)) { Text("SHARE") }
                        OutlinedButton(onClick = { pendingDelete = setOf(item.id); preview = null }, modifier = Modifier.weight(1f)) { Text("DELETE") }
                    }
                    OutlinedButton(onClick = { preview = null }, modifier = Modifier.fillMaxWidth()) { Text("CLOSE") }
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        Dialog(onDismissRequest = { pendingDelete = emptySet() }) {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DELETE ${pendingDelete.size} MEDIA ITEM${if (pendingDelete.size == 1) "" else "S"}?", color = GsColors.White, fontWeight = FontWeight.Bold)
                    Text("This removes the selected records from this local library. Simulator onboard records cannot be recovered after deletion.", color = GsColors.Muted, fontSize = 11.sp)
                    Button(onClick = {
                        onDelete(pendingDelete)
                        selectedIds = selectedIds - pendingDelete
                        pendingDelete = emptySet()
                    }, modifier = Modifier.fillMaxWidth()) { Text("CONFIRM DELETE") }
                    OutlinedButton(onClick = { pendingDelete = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: PersistedMediaItem, modifier: Modifier = Modifier) {
    val seed = item.id.hashCode()
    val accent = if (item.kind == MediaKind.PHOTO) GsColors.Blue else GsColors.Orange
    Canvas(modifier.background(GsColors.Ink)) {
        drawRect(accent.copy(alpha = .28f))
        val horizon = size.height * (.45f + ((seed ushr 3) and 7) / 50f)
        drawRect(GsColors.Panel2, topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(size.width, size.height - horizon))
        drawCircle(accent.copy(alpha = .85f), size.minDimension * .10f, Offset(size.width * .75f, size.height * .28f))
        val ridge = listOf(
            Offset(0f, size.height * .72f),
            Offset(size.width * .28f, size.height * .42f),
            Offset(size.width * .52f, size.height * .68f),
            Offset(size.width * .72f, size.height * .50f),
            Offset(size.width, size.height * .74f)
        )
        for (index in 0 until ridge.lastIndex) drawLine(GsColors.Faint, ridge[index], ridge[index + 1], strokeWidth = 4f)
        if (item.kind == MediaKind.VIDEO) {
            val previewCenter = center
            drawCircle(Color.Black.copy(alpha = .55f), size.minDimension * .16f, previewCenter)
            drawLine(Color.White, Offset(previewCenter.x - 8f, previewCenter.y - 12f), Offset(previewCenter.x + 12f, previewCenter.y), 5f)
            drawLine(Color.White, Offset(previewCenter.x + 12f, previewCenter.y), Offset(previewCenter.x - 8f, previewCenter.y + 12f), 5f)
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun formatMegabytes(value: Long): String = if (value >= 1024L) "%.1f GB".format(value / 1024.0) else "$value MB"

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.1f GB".format(value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.1f KB".format(value / 1024.0)
    else -> "$value B"
}

private fun availableLocalBytes(context: android.content.Context): Long = runCatching {
    context.getSystemService(StorageManager::class.java).getAllocatableBytes(StorageManager.UUID_DEFAULT)
}.getOrElse { context.filesDir.usableSpace }

private fun formatDuration(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun shareMedia(context: android.content.Context, items: List<PersistedMediaItem>) {
    if (items.isEmpty()) return
    val summary = items.joinToString("\n") { item ->
        "${item.fileName} — ${item.kind.name.lowercase()}, ${formatBytes(item.sizeBytes)}, ${item.resolution ?: "resolution unavailable"}"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "X-Star media (${items.size})")
        putExtra(Intent.EXTRA_TEXT, summary)
    }
    context.startActivity(Intent.createChooser(intent, "Share X-Star media metadata"))
}
