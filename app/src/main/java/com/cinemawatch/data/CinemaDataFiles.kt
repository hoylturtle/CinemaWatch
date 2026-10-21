package com.cinemawatch.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/** Owns only CinemaWatch cinema data. Language preferences and unrelated files are retained. */
class CinemaDataFiles(private val context: Context) {
    private fun remove(file: File) {
        check(!file.exists() || file.deleteRecursively()) { "Could not remove cinema data" }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        listOf("plans", "phone-flow", "maintenance-photos").forEach { remove(File(context.filesDir, it)) }
        listOf("maintenance-capture", "maintenance-photo.tmp", "reports", "updates").forEach { remove(File(context.cacheDir, it)) }
    }

    suspend fun removeAsset(assetId: String, photoNames: Set<String>) = withContext(Dispatchers.IO) {
        photoNames.forEach { name ->
            require(Regex("[a-zA-Z0-9-]+[.]jpg").matches(name))
            remove(File(context.filesDir, "maintenance-photos/$name"))
        }
        // A deleted asset must not remain as a hidden marker in an existing floor plan.
        File(context.filesDir, "plans").listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            val pins = File(dir, "pins.json")
            if (pins.exists()) {
                val original = JSONArray(pins.readText())
                val retained = JSONArray()
                for (i in 0 until original.length()) {
                    val pin = original.getJSONObject(i)
                    if (pin.optString("target") != "asset:$assetId") retained.put(pin)
                }
                if (retained.length() != original.length()) {
                    val temp = File(dir, "pins.tmp")
                    temp.writeText(retained.toString())
                    check(temp.renameTo(pins)) { "Could not remove asset marker" }
                }
            }
        }
        // Export/capture caches can contain the deleted asset's notes or original photo.
        listOf("reports", "maintenance-capture", "maintenance-photo.tmp").forEach { remove(File(context.cacheDir, it)) }
    }
}
