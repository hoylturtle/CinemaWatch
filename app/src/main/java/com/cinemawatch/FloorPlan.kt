package com.cinemawatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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

data class PlanPin(val target: String, val x: Float, val y: Float, val width: Float = 0f, val height: Float = 0f)
/** Coordinates refer to the imported image, never to inferred RF location. */
class FloorPlanStore(private val context: Context, cinemaId: String) {
    private val dir = File(context.filesDir, "plans/$cinemaId").apply { mkdirs() }
    val image = File(dir, "image.png")
    private val pinsFile = File(dir, "pins.json")
    fun pins(): List<PlanPin> = if (!pinsFile.exists()) emptyList() else JSONArray(pinsFile.readText()).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { PlanPin(it.getString("target"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.optDouble("width", 0.0).toFloat(), it.optDouble("height", 0.0).toFloat()) } }
    }
    fun save(pins: List<PlanPin>) {
        require(pins.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f && it.width.isFinite() && it.height.isFinite() && it.width in 0f..1f && it.height in 0f..1f })
        val array = JSONArray(); pins.forEach { array.put(JSONObject().put("target", it.target).put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height)) }
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

@Composable internal fun FloorPlanDialog(cinema: Cinema, zones: List<Zone>, assets: List<CinemaAsset>, onDismiss: () -> Unit, onAddZone: suspend (String) -> Zone) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val store = remember(cinema.id) { FloorPlanStore(context, cinema.id) }
    var bitmap by remember(cinema.id) { mutableStateOf<Bitmap?>(null) }
    var pins by remember(cinema.id) { mutableStateOf<List<PlanPin>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }; var sketching by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("1") }; var corner by remember { mutableStateOf<Offset?>(null) }; var drawArea by remember { mutableStateOf(false) }
    var newZone by remember { mutableStateOf("") }
    val labels = zones.associate { "zone:${it.id}" to it.name } + assets.associate { "asset:${it.id}" to it.name }
    LaunchedEffect(store) {
        runCatching { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(store.image.path) to store.pins() } }.onSuccess { bitmap = it.first; pins = it.second }.onFailure { error = true }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = false
            runCatching { withContext(Dispatchers.IO) { store.import(uri, page.toIntOrNull() ?: 1); BitmapFactory.decodeFile(store.image.path) } }.onSuccess { bitmap = it; pins = emptyList() }.onFailure { error = true }
            busy = false
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${cinema.name} · ${stringResource(R.string.floor_plan)}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.plan_note))
                Button(onClick = { if (bitmap == null) picker.launch(arrayOf("*/*")) else replacing = true }, enabled = !busy) { Text(stringResource(R.string.plan_import)) }
                OutlinedTextField(page, { page = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.plan_pdf_page)) }, enabled = !busy)
                OutlinedButton(onClick = { sketching = true }, enabled = !busy) { Text(stringResource(R.string.plan_sketch)) }
                OutlinedTextField(newZone, { newZone = it.take(80) }, label = { Text(stringResource(R.string.zone_name)) })
                TextButton(onClick = { scope.launch {
                    busy = true
                    runCatching { onAddZone(newZone) }.onSuccess { selected = "zone:${it.id}"; newZone = ""; drawArea = true; corner = null }.onFailure { error = true }
                    busy = false
                } }, enabled = !busy && newZone.isNotBlank()) { Text(stringResource(R.string.add_zone)) }
                Text(stringResource(R.string.plan_select))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { (id, label) -> FilterChip(selected == id, { selected = if (selected == id) null else id; corner = null }, label = { Text(label) }, enabled = !busy) }
                }
                if (selected?.startsWith("zone:") == true) FilterChip(drawArea, { drawArea = !drawArea; corner = null }, label = { Text(stringResource(R.string.plan_draw_area)) })
                if (corner != null) Text(stringResource(R.string.plan_second_corner))
                bitmap?.let { image ->
                    Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height).testTag("floor-plan-image")) {
                        Image(image.asImageBitmap(), stringResource(R.string.floor_plan), Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize().pointerInput(selected, pins, busy, drawArea, corner) {
                            detectTapGestures { point ->
                                val target = selected
                                if (target != null && !busy) {
                                    val position = Offset((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))
                                    if (drawArea && target.startsWith("zone:") && corner == null) corner = position else {
                                        val first = if (drawArea && target.startsWith("zone:")) corner else null
                                        val pin = if (first != null) PlanPin(target, (first.x + position.x) / 2, (first.y + position.y) / 2, kotlin.math.abs(position.x - first.x), kotlin.math.abs(position.y - first.y)) else PlanPin(target, position.x, position.y)
                                        val next = pins.filterNot { it.target == target } + pin
                                        runCatching { store.save(next) }.onSuccess { pins = next; corner = null }.onFailure { error = true }
                                    }
                                }
                            }
                        }) {
                            pins.filter { it.target in labels }.forEach { pin ->
                                val at = Offset(pin.x * size.width, pin.y * size.height)
                                if (pin.width > 0 && pin.height > 0) {
                                    val origin = Offset((pin.x - pin.width / 2) * size.width, (pin.y - pin.height / 2) * size.height)
                                    val dimensions = androidx.compose.ui.geometry.Size(pin.width * size.width, pin.height * size.height)
                                    drawRect(Color(0x3059DBC6), origin, dimensions)
                                    drawRect(Color(0xFF159B86), origin, dimensions, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                }
                                drawCircle(if (pin.target.startsWith("asset:")) Color(0xFFFFCF80) else Color(0xFF59DBC6), 7.dp.toPx(), at)
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 12.dp.toPx(); setShadowLayer(3f, 0f, 0f, android.graphics.Color.WHITE) }
                                drawContext.canvas.nativeCanvas.drawText(labels.getValue(pin.target).take(24), at.x.coerceAtMost((size.width - 100.dp.toPx()).coerceAtLeast(0f)), (at.y + 20.dp.toPx()).coerceAtMost(size.height - 2.dp.toPx()), paint)
                            }
                        }
                    }
                }
                pins.filter { it.target in labels }.forEach { pin ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labels.getValue(pin.target), Modifier.weight(1f))
                        TextButton(onClick = { runCatching { store.save(pins.filterNot { it.target == pin.target }) }.onSuccess { pins = pins.filterNot { it.target == pin.target } }.onFailure { error = true } }, enabled = !busy) { Text(stringResource(R.string.plan_remove_pin)) }
                    }
                }
                if (error) Text(stringResource(R.string.plan_import_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) }
            }
        }
    }
    if (sketching) AlertDialog(onDismissRequest = { sketching = false }, text = { Text(stringResource(R.string.plan_replace_note)) }, confirmButton = { TextButton(onClick = {
        sketching = false; scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.sketch(zones); BitmapFactory.decodeFile(store.image.path) to store.pins() } }.onSuccess { bitmap = it.first; pins = it.second }.onFailure { error = true }
            busy = false
        }
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { sketching = false }) { Text(stringResource(R.string.cancel)) } })
    if (replacing) AlertDialog(onDismissRequest = { replacing = false }, text = { Text(stringResource(R.string.plan_replace_note)) }, confirmButton = { TextButton(onClick = { replacing = false; picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { replacing = false }) { Text(stringResource(R.string.cancel)) } })
}
