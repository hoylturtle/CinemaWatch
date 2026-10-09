package com.cinemawatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.cinemawatch.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal fun issueLabel(state: String) = when (state) {
    "IN_PROGRESS" -> R.string.issue_progress; "WAITING_RECHECK" -> R.string.issue_waiting
    "CLOSED", "RECHECK_PASSED" -> R.string.issue_closed
    "DETECTED" -> R.string.issue_detected; "RECHECK_ABNORMAL" -> R.string.issue_failed
    "NOTE" -> R.string.issue_note; else -> R.string.issue_open
}
internal object MaintenancePhotos {
    fun file(c: Context, name: String) = File(c.filesDir, "maintenance-photos/$name")
    fun import(c: Context, uri: Uri): String {
        val temp = File(c.cacheDir, "maintenance-photo.tmp")
        try {
            c.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input); temp.outputStream().use { out ->
                    val buffer = ByteArray(8192); var count = 0
                    while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 16 * 1024 * 1024); out.write(buffer, 0, n) }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val options = BitmapFactory.Options().apply { var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2; inSampleSize = sample }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(temp.path, options))
            val name = "${UUID.randomUUID()}.jpg"; val dest = file(c, name); dest.parentFile?.mkdirs()
            try { dest.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) } } finally { bitmap.recycle() }
            return name
        } finally { temp.delete() }
    }
}

@Composable internal fun MaintenanceDialog(asset: CinemaAsset, zoneName: String, repository: CinemaRepository,
    results: List<AssetResult>, sessions: List<Inspection>, baseline: AssetBaseline?, issues: List<MaintenanceIssue>,
    events: List<IssueEvent>, inspectionBusy: Boolean, onDismiss: () -> Unit, onInspect: () -> Unit) {
    val c = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("") }; var photo by remember { mutableStateOf("") }
    val last = results.firstOrNull { it.assetId == asset.id }; val source = sessions.find { it.id == last?.sessionId }
    val eligible = last?.status in setOf("LEARNING", "NORMAL") && last?.medianRssi != null && last.spread < 18 && source != null && !source.demo && !source.imported && source.mode == "INSPECTION" && !source.overran()
    val active = issues.firstOrNull { it.assetId == asset.id && it.state != "CLOSED" }
    fun importPhoto(uri: Uri) { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { MaintenancePhotos.import(c, uri) } }.onSuccess { photo = it }.onFailure { error = true }; busy = false } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importPhoto) }
    val capture = remember { File(c.cacheDir, "maintenance-capture/photo.jpg").apply { parentFile?.mkdirs() } }
    val captureUri = remember { FileProvider.getUriForFile(c, "${c.packageName}.files", capture) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if (it) importPhoto(captureUri) }
    fun action(code: String) { active?.let { issue -> scope.launch { busy = true; runCatching { repository.updateIssue(issue.id, code, note, photo) }.onSuccess { note = ""; photo = ""; error = false }.onFailure { error = true }; busy = false } } }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.95f).systemBarsPadding()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(asset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
                LazyColumn(Modifier.weight(1f).testTag("maintenance-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(zoneName); if (asset.location.isNotBlank()) Text(asset.location)
                        last?.let { StatusChip(it.status) }
                        Text(stringResource(R.string.baseline_confirmed, baseline?.rssi?.let { "$it dBm" } ?: stringResource(R.string.no_baseline)))
                        Text(stringResource(R.string.workflow_help), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { confirm = true }, enabled = eligible && !busy && !inspectionBusy, modifier = Modifier.testTag("confirm-baseline")) { Text(stringResource(R.string.establish_baseline)) }
                        if (!eligible) Text(stringResource(R.string.baseline_needs_inspection), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onInspect, enabled = !inspectionBusy && !busy, modifier = Modifier.testTag("maintenance-reinspect")) { Text(stringResource(R.string.reinspect_area)) }
                        if (error) Text(stringResource(R.string.error_save), color = MaterialTheme.colorScheme.error)
                    }
                    active?.let { issue ->
                        item { Panel {
                            Text(stringResource(R.string.issue_title)); StatusChip(issue.reason)
                            Text(stringResource(issueLabel(issue.state)))
                            OutlinedTextField(note, { note = it.take(1000) }, label = { Text(stringResource(R.string.processing_note)) }, modifier = Modifier.fillMaxWidth().testTag("issue-note"), enabled = !busy)
                            Row { TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !busy) { Text(stringResource(R.string.attach_photo)) }; TextButton(onClick = { runCatching { camera.launch(captureUri) }.onFailure { error = true } }, enabled = !busy) { Text(stringResource(R.string.take_photo)) } }
                            if (photo.isNotBlank()) PhotoEvidence(photo)
                            Row { TextButton(onClick = { action("NOTE") }, enabled = !busy && (note.isNotBlank() || photo.isNotBlank())) { Text(stringResource(R.string.save_note)) }; TextButton(onClick = { action("IN_PROGRESS") }, enabled = !busy && (note.isNotBlank() || photo.isNotBlank())) { Text(stringResource(R.string.issue_progress)) } }
                            Button(onClick = { action("WAITING_RECHECK") }, enabled = !busy && !inspectionBusy && (note.isNotBlank() || photo.isNotBlank()), modifier = Modifier.testTag("issue-ready-recheck")) { Text(stringResource(R.string.mark_repaired)) }
                            Text(stringResource(R.string.recheck_rule), style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                    item { Text(stringResource(R.string.processing_history), style = MaterialTheme.typography.titleMedium) }
                    val ownIssues = issues.filter { it.assetId == asset.id }.map { it.id }.toSet()
                    val ownEvents = events.filter { it.issueId in ownIssues }
                    if (ownEvents.isEmpty()) item { Text(stringResource(R.string.no_processing_history)) }
                    items(ownEvents, key = { it.id }) { event -> Panel {
                        Text(stringResource(issueLabel(event.action)))
                        Text(java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", c.resources.configuration.locales[0]).format(java.util.Date(event.at)))
                        if (event.note.isNotBlank()) Text(event.note)
                        if (event.photo.isNotBlank()) PhotoEvidence(event.photo)
                    } }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.establish_baseline)) }, text = { Text(stringResource(R.string.confirm_normal_note)) },
        confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { repository.confirmBaseline(asset.id, requireNotNull(last).sessionId) }.onSuccess { confirm = false; error = false }.onFailure { error = true }; busy = false } }, enabled = !busy) { Text(stringResource(R.string.confirm_device_normal)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun PhotoEvidence(name: String) {
    val c = LocalContext.current
    val bitmap = remember(name) { BitmapFactory.decodeFile(MaintenancePhotos.file(c, name).path) }
    bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.attached_photo), Modifier.fillMaxWidth().heightIn(max = 220.dp)) }
}
