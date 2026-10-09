package com.cinemawatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cinemawatch.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PlanPoint(val x: Float, val y: Float)
data class PlanPin(val target: String, val x: Float, val y: Float, val width: Float = 0f, val height: Float = 0f, val vertices: List<PlanPoint> = emptyList())
/** Coordinates refer to the imported image, never to inferred RF location. */
class FloorPlanStore(private val context: Context, cinemaId: String) {
    private val dir = File(context.filesDir, "plans/$cinemaId").apply { mkdirs() }
    val image = File(dir, "image.png")
    private val pinsFile = File(dir, "pins.json")
    fun pins(): List<PlanPin> = if (!pinsFile.exists()) emptyList() else JSONArray(pinsFile.readText()).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { PlanPin(it.getString("target"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.optDouble("width", 0.0).toFloat(), it.optDouble("height", 0.0).toFloat(), it.optJSONArray("vertices")?.let { points -> (0 until points.length()).map { index -> points.getJSONObject(index).let { point -> PlanPoint(point.getDouble("x").toFloat(), point.getDouble("y").toFloat()) } } }.orEmpty()) } }
    }
    fun save(pins: List<PlanPin>) {
        require(pins.size <= 2000)
        require(pins.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f && it.width.isFinite() && it.height.isFinite() && it.width in 0f..1f && it.height in 0f..1f && it.vertices.size <= 256 && it.vertices.all { point -> point.x.isFinite() && point.y.isFinite() && point.x in 0f..1f && point.y in 0f..1f } })
        val array = JSONArray(); pins.forEach { pin ->
            val vertices = JSONArray(); pin.vertices.forEach { vertices.put(JSONObject().put("x", it.x).put("y", it.y)) }
            array.put(JSONObject().put("target", pin.target).put("x", pin.x).put("y", pin.y).put("width", pin.width).put("height", pin.height).put("vertices", vertices))
        }
        val temp = File(dir, "pins.tmp"); temp.writeText(array.toString()); check(temp.renameTo(pinsFile))
    }
    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply { var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2; inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, options) ?: error("Invalid image")
    }
    private fun writeImage(bitmap: Bitmap) {
        try {
            val temp = File(dir, "image.tmp")
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(temp.renameTo(image)); save(emptyList())
        } finally { bitmap.recycle() }
    }
    fun import(uri: Uri, pdfPage: Int = 1) {
        require(pdfPage in 1..999)
        val source = File(dir, "source.tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                require(input != null)
                source.outputStream().use { out ->
                    var count = 0L; val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 32 * 1024 * 1024); out.write(buffer, 0, n) }
                }
            }
            val header = source.inputStream().use { it.readNBytesCompat(4) }
            val bitmap = if (header.contentEquals("%PDF".toByteArray())) {
                android.os.ParcelFileDescriptor.open(source, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                        require(pdfPage <= renderer.pageCount)
                        renderer.openPage(pdfPage - 1).use { page ->
                            val scale = 2048f / maxOf(page.width, page.height)
                            Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { image ->
                                image.eraseColor(android.graphics.Color.WHITE)
                                page.render(image, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            } else if (header.take(2) == listOf(80.toByte(), 75.toByte())) {
                // Modern CDR containers sometimes include a bitmap thumbnail; no vector-parser claim.
                java.util.zip.ZipFile(source).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && ("thumbnail" in it.name.lowercase() || "preview" in it.name.lowercase()) && it.name.lowercase().endsWith(".png") } ?: error("CDR preview unavailable")
                    require(entry.size in 1..(8 * 1024 * 1024))
                    val preview = File(dir, "preview.tmp")
                    try {
                        zip.getInputStream(entry).use { input -> preview.outputStream().use { out -> var count = 0; val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= 8 * 1024 * 1024); out.write(buffer, 0, n) } } }
                        decode(preview)
                    } finally { preview.delete() }
                }
            } else decode(source)
            writeImage(bitmap)
        } finally { source.delete() }
    }
    fun sketch(zones: List<Zone>) {
        writeImage(Bitmap.createBitmap(1400, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) })
        val columns = kotlin.math.ceil(kotlin.math.sqrt(zones.size.coerceAtLeast(1).toDouble())).toInt()
        val rows = (zones.size + columns - 1) / columns
        val width = .9f / columns; val height = .9f / rows.coerceAtLeast(1)
        save(zones.mapIndexed { index, zone -> PlanPin("zone:${zone.id}", .05f + (index % columns + .5f) * width, .05f + (index / columns + .5f) * height, width * .85f, height * .85f) })
    }
    private fun java.io.InputStream.readNBytesCompat(count: Int) = ByteArray(count).also { bytes -> var offset = 0; while (offset < count) { val n = read(bytes, offset, count - offset); if (n < 0) break; offset += n } }

}

private enum class PlanTool { SELECT, PAN, WALL, ROOM, DOOR, ASSET }

@Composable internal fun FloorPlanDialog(cinema: Cinema, zones: List<Zone>, assets: List<CinemaAsset>, onDismiss: () -> Unit, onAddZone: suspend (String) -> Zone) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val activity = remember(context) {
        generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
    }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }
    val store = remember(cinema.id) { FloorPlanStore(context, cinema.id) }
    var bitmap by remember(cinema.id) { mutableStateOf<Bitmap?>(null) }
    var pins by remember(cinema.id) { mutableStateOf<List<PlanPin>>(emptyList()) }
    var undo by remember { mutableStateOf<List<List<PlanPin>>>(emptyList()) }; var redo by remember { mutableStateOf<List<List<PlanPin>>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }; var tool by remember { mutableStateOf(PlanTool.SELECT) }
    var draft by remember { mutableStateOf<List<PlanPoint>>(emptyList()) }; var room by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }; var importOpen by remember { mutableStateOf(false) }; var blankOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }; var snap by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    var page by remember { mutableStateOf("1") }; var name by remember { mutableStateOf("") }
    val labels = zones.associate { "zone:${it.id}" to it.name } + assets.associate { "asset:${it.id}" to it.name }
    fun commit(next: List<PlanPin>, previous: List<PlanPin> = pins) {
        runCatching { store.save(next) }.onSuccess { undo = (undo + listOf(previous)).takeLast(30); redo = emptyList(); pins = next; error = false }.onFailure { pins = previous; error = true }
    }
    fun addRoom(id: String) { commit(pins.filterNot { it.target == id } + PlanGeometry.shape(id, draft)); selected = id; draft = emptyList(); room = false; tool = PlanTool.SELECT }
    fun finish() {
        if (tool == PlanTool.ROOM && draft.size >= 3 && PlanGeometry.area(draft) > .0004f) { name = ""; room = true }
        if (tool == PlanTool.WALL && draft.size >= 2) { commit(pins + PlanGeometry.shape("wall:${java.util.UUID.randomUUID()}", draft)); draft = emptyList() }
    }
    LaunchedEffect(store) {
        runCatching { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(store.image.path) to store.pins() } }.onSuccess { bitmap = it.first; pins = it.second }.onFailure { error = true }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.import(uri, page.toIntOrNull() ?: 1); BitmapFactory.decodeFile(store.image.path) } }.onSuccess { bitmap = it; pins = emptyList(); undo = emptyList(); redo = emptyList(); draft = emptyList(); selected = null; zoom = 1f; pan = Offset.Zero; error = false }.onFailure { error = true }
            busy = false
        }
    }
    Dialog(onDismissRequest = { if (!busy) { draft = emptyList(); onDismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(cinema.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { importOpen = true }, enabled = !busy) { Text(stringResource(R.string.plan_import_short)) }
                    TextButton(onClick = { blankOpen = true }, enabled = !busy) { Text(stringResource(R.string.plan_blank)) }
                    TextButton(onClick = onDismiss, enabled = !busy && draft.isEmpty()) { Text(stringResource(R.string.close)) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (draft.isNotEmpty()) draft = draft.dropLast(1) else if (undo.isNotEmpty()) {
                            val prior = undo.last(); runCatching { store.save(prior) }.onSuccess { redo = redo + listOf(pins); pins = prior; undo = undo.dropLast(1); selected = null }.onFailure { error = true }
                        }
                    }, enabled = !busy && (draft.isNotEmpty() || undo.isNotEmpty()), modifier = Modifier.testTag("plan-undo")) { Text(stringResource(R.string.plan_undo)) }
                    TextButton(onClick = { val next = redo.last(); runCatching { store.save(next) }.onSuccess { undo = undo + listOf(pins); pins = next; redo = redo.dropLast(1) }.onFailure { error = true } }, enabled = !busy && redo.isNotEmpty() && draft.isEmpty(), modifier = Modifier.testTag("plan-redo")) { Text(stringResource(R.string.plan_redo)) }
                    FilterChip(snap, { snap = !snap }, label = { Text(stringResource(R.string.plan_snap)) })
                    TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(.75f) }) { Text("−") }
                    Text("${(zoom * 100).toInt()}%", Modifier.testTag("plan-zoom"))
                    TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(5f) }) { Text("+") }
                    TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.testTag("plan-fit")) { Text(stringResource(R.string.plan_fit)) }
                }
                val hint = when (tool) { PlanTool.WALL -> R.string.plan_wall_hint; PlanTool.ROOM -> R.string.plan_room_hint; PlanTool.DOOR -> R.string.plan_door_hint; PlanTool.ASSET -> R.string.plan_asset_hint; PlanTool.PAN -> R.string.plan_pan_hint; else -> R.string.plan_select_hint }
                Text(stringResource(hint), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                if (error) Text(stringResource(R.string.plan_import_error), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: 1.4f
                fun metrics(size: androidx.compose.ui.geometry.Size): Pair<Float, Float> { val w = minOf(size.width * .94f, size.height * .94f * ratio); return w to w / ratio }
                fun local(point: Offset, size: androidx.compose.ui.geometry.Size): PlanPoint {
                    val (w, h) = metrics(size)
                    return PlanPoint(((point.x - size.width / 2 - pan.x) / zoom / w + .5f).coerceIn(0f, 1f), ((point.y - size.height / 2 - pan.y) / zoom / h + .5f).coerceIn(0f, 1f))
                }
                fun hit(p: PlanPoint): PlanPin? = pins.asReversed().firstOrNull { pin ->
                    if (pin.target.startsWith("zone:")) PlanGeometry.contains(PlanGeometry.outline(pin), p) else if (pin.target.startsWith("wall:") || pin.target.startsWith("door:")) PlanGeometry.near(PlanGeometry.outline(pin), p, .025f / zoom) else kotlin.math.hypot((pin.x - p.x).toDouble(), (pin.y - p.y).toDouble()) < .035 / zoom
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.width(136.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(PlanTool.SELECT to R.string.plan_tool_select, PlanTool.PAN to R.string.plan_tool_pan, PlanTool.WALL to R.string.plan_tool_wall, PlanTool.ROOM to R.string.plan_tool_room, PlanTool.DOOR to R.string.plan_tool_door, PlanTool.ASSET to R.string.plan_tool_asset).forEach { (value, title) ->
                        FilterChip(tool == value, { if (value == PlanTool.ASSET) pickerOpen = true else { tool = value; draft = emptyList(); selected = null } }, label = { Text(stringResource(title)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("plan-tool-${value.name}"))
                    }
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().testTag("floor-plan-image")
                    .pointerInput(tool, busy, ratio) {
                        if (busy) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val before = pins
                            var moved = false
                            var transformed = false
                            var distance = Offset.Zero
                            var target: String? = null
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2 || transformed) {
                                    if (!transformed) { pins = before; transformed = true }
                                    if (pressed >= 2) {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val next = (zoom * event.calculateZoom()).coerceIn(.5f, 8f)
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        pan = centroid - center - (centroid - center - pan) * (next / zoom) + event.calculatePan()
                                        zoom = next
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null && change.pressed) {
                                        val amount = change.positionChange()
                                        distance += amount
                                        if (!moved && distance.getDistance() > viewConfiguration.touchSlop) {
                                            moved = true
                                            if (tool == PlanTool.SELECT) {
                                                target = hit(local(start, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())))?.target
                                                selected = target
                                            }
                                        }
                                        if (moved) {
                                            if (tool == PlanTool.PAN) pan += amount
                                            if (tool == PlanTool.SELECT && target != null) {
                                                val (w, h) = metrics(androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                                                pins = before.map { if (it.target == target) PlanGeometry.move(it, distance.x / zoom / w, distance.y / zoom / h) else it }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            if (!transformed && moved && pins != before) commit(pins, before)
                            if (!transformed && !moved) {
                                val p = local(start, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                                when (tool) {
                                    PlanTool.SELECT -> selected = hit(p)?.target
                                    PlanTool.ASSET -> selected?.takeIf { it.startsWith("asset:") }?.let { id -> commit(pins.filterNot { it.target == id } + PlanPin(id, p.x, p.y)) }
                                    PlanTool.ROOM, PlanTool.WALL, PlanTool.DOOR -> {
                                        if (tool == PlanTool.ROOM && draft.size >= 3 && kotlin.math.hypot((p.x - draft.first().x).toDouble(), (p.y - draft.first().y).toDouble()) < .035 / zoom) finish()
                                        else if (draft.size < 256) {
                                            val point = PlanGeometry.snap(p, draft.lastOrNull(), snap)
                                            if (draft.lastOrNull() != point) draft = draft + point
                                            if (tool == PlanTool.DOOR && draft.size == 2) { commit(pins + PlanGeometry.shape("door:${java.util.UUID.randomUUID()}", draft)); draft = emptyList() }
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }) {
                    val (w, h) = metrics(size)
                    fun screen(p: PlanPoint) = Offset(size.width / 2 + pan.x + (p.x - .5f) * w * zoom, size.height / 2 + pan.y + (p.y - .5f) * h * zoom)
                    val origin = screen(PlanPoint(0f, 0f)); val dimensions = androidx.compose.ui.geometry.Size(w * zoom, h * zoom)
                    drawRect(Color(0xFFF5F6F3), origin, dimensions)
                    bitmap?.let { drawImage(it.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(origin.x.toInt(), origin.y.toInt()), dstSize = androidx.compose.ui.unit.IntSize(dimensions.width.toInt().coerceAtLeast(1), dimensions.height.toInt().coerceAtLeast(1)), alpha = .6f) }
                    for (i in 0..50) { val v = i / 50f; drawLine(Color(0x22687878), screen(PlanPoint(v, 0f)), screen(PlanPoint(v, 1f)), 1f); drawLine(Color(0x22687878), screen(PlanPoint(0f, v)), screen(PlanPoint(1f, v)), 1f) }
                    pins.forEach { pin ->
                        val points = PlanGeometry.outline(pin); val isRoom = pin.target.startsWith("zone:")
                        val color = if (pin.target == selected) Color(0xFF1AAFA0) else Color(0xFF33454D)
                        if (points.size >= 2) {
                            val path = androidx.compose.ui.graphics.Path().apply { val first = screen(points.first()); moveTo(first.x, first.y); points.drop(1).forEach { val point = screen(it); lineTo(point.x, point.y) }; if (isRoom) close() }
                            if (isRoom) drawPath(path, Color(0x2059DBC6))
                            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(if (pin.target.startsWith("door:")) 2.dp.toPx() else 5.dp.toPx()))
                            if (pin.target.startsWith("door:")) { val a = screen(points.first()); val b = screen(points.last()); val length = (b - a).getDistance(); drawArc(Color(0xFF1AAFA0), (kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()) * 180 / kotlin.math.PI).toFloat(), 90f, false, Offset(a.x - length, a.y - length), androidx.compose.ui.geometry.Size(length * 2, length * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                        } else drawCircle(if (pin.target.startsWith("asset:")) Color(0xFFFFB545) else color, 7.dp.toPx(), screen(PlanPoint(pin.x, pin.y)))
                        labels[pin.target]?.let { label -> val point = screen(PlanPoint(pin.x, pin.y)); val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.BLACK; textSize = 12.dp.toPx(); setShadowLayer(2f, 0f, 0f, android.graphics.Color.WHITE) }; drawContext.canvas.nativeCanvas.drawText(label.take(24), point.x, point.y - 10.dp.toPx(), paint) }
                    }
                    draft.forEachIndexed { index, point -> val at = screen(point); drawCircle(Color(0xFF1AAFA0), 5.dp.toPx(), at); if (index > 0) drawLine(Color(0xFF1AAFA0), screen(draft[index - 1]), at, 4.dp.toPx()) }
                }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (draft.isNotEmpty()) {
                        TextButton(onClick = { draft = emptyList() }) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { finish() }, enabled = !busy && if (tool == PlanTool.ROOM) draft.size >= 3 && PlanGeometry.area(draft) > .0004f else draft.size >= 2, modifier = Modifier.testTag("plan-finish")) { Text(stringResource(R.string.plan_finish)) }
                    } else if (selected != null) {
                        Text(labels[selected] ?: stringResource(R.string.plan_wall_object), Modifier.weight(1f).padding(8.dp))
                        TextButton(onClick = { commit(pins.filterNot { it.target == selected }); selected = null }) { Text(stringResource(R.string.plan_remove_pin)) }
                    } else Text(stringResource(R.string.plan_autosave), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (room) AlertDialog(onDismissRequest = { room = false }, title = { Text(stringResource(R.string.plan_name_room)) }, text = {
        Column { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.zone_name)) }, modifier = Modifier.testTag("plan-room-name")); androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 220.dp)) { items(zones.size) { index -> val zone = zones[index]; TextButton(onClick = { addRoom("zone:${zone.id}") }) { Text(zone.name) } } } }
    }, confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { onAddZone(name) }.onSuccess { addRoom("zone:${it.id}") }.onFailure { error = true }; busy = false } }, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.add_zone)) } }, dismissButton = { TextButton(onClick = { room = false }) { Text(stringResource(R.string.cancel)) } })
    if (pickerOpen) AlertDialog(onDismissRequest = { pickerOpen = false }, title = { Text(stringResource(R.string.plan_tool_asset)) }, text = {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) { items(assets.size) { index -> val asset = assets[index]; TextButton(onClick = { selected = "asset:${asset.id}"; tool = PlanTool.ASSET; draft = emptyList(); pickerOpen = false }) { Column { Text(asset.name); if (asset.location.isNotBlank()) Text(asset.location, style = MaterialTheme.typography.bodySmall) } } } }
    }, confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.close)) } })
    if (importOpen) AlertDialog(onDismissRequest = { importOpen = false }, text = { Column { Text(stringResource(R.string.plan_replace_note)); OutlinedTextField(page, { page = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.plan_pdf_page)) }) } }, confirmButton = { TextButton(onClick = { importOpen = false; picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.plan_import_short)) } }, dismissButton = { TextButton(onClick = { importOpen = false }) { Text(stringResource(R.string.cancel)) } })
    if (blankOpen) AlertDialog(onDismissRequest = { blankOpen = false }, text = { Text(stringResource(R.string.plan_replace_note)) }, confirmButton = { TextButton(onClick = { blankOpen = false; scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { store.sketch(emptyList()); BitmapFactory.decodeFile(store.image.path) } }.onSuccess { bitmap = it; pins = emptyList(); undo = emptyList(); redo = emptyList(); selected = null; draft = emptyList() }.onFailure { error = true }; busy = false } }) { Text(stringResource(R.string.plan_blank)) } }, dismissButton = { TextButton(onClick = { blankOpen = false }) { Text(stringResource(R.string.cancel)) } })
}
