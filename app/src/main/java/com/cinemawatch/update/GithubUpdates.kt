package com.cinemawatch.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

interface UpdateSource {
    suspend fun latest(): UpdateRelease?
    suspend fun download(release: UpdateRelease, progress: (Int) -> Unit): File
}
internal fun certificateHashes(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
    .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

class GithubUpdates(private val context: Context) : UpdateSource {
    private fun installed() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private fun connection(url: String): HttpURLConnection {
        var uri = URI(url)
        repeat(6) {
            if (uri.scheme != "https" || (uri.host !in setOf("github.com", "api.github.com") &&
                    !uri.host.orEmpty().endsWith(".githubusercontent.com")) || uri.userInfo != null || uri.port != -1)
                throw UpdateException(UpdateError.INVALID)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "CinemaWatch updater")
            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next == null) throw UpdateException(UpdateError.INVALID)
                uri = uri.resolve(next)
            } else {
                if (status != 200) { connection.disconnect(); throw IOException("Update server unavailable") }
                return connection
            }
        }
        throw UpdateException(UpdateError.INVALID)
    }
    private suspend fun text(url: String): String {
        val connection = connection(url)
        try {
            return connection.inputStream.use { stream ->
                val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer); if (count < 0) break
                    if (bytes.size() + count > 1024 * 1024) throw UpdateException(UpdateError.INVALID)
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
    override suspend fun latest(): UpdateRelease? = withContext(Dispatchers.IO) {
        val list = try { JSONArray(text(RELEASE_API)) } catch (e: IOException) { throw e }
            catch (e: Exception) { throw UpdateException(UpdateError.INVALID) }
        val own = installed(); val candidates = mutableListOf<UpdateRelease>()
        var inspected = 0
        var signatureMismatch = false
        for (i in 0 until list.length()) {
            val release = list.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.optString("name") != "update.json" || inspected >= 5) continue
                val url = asset.optString("browser_download_url")
                if (!UpdateRules.trustedReleaseUrl(url, "update.json")) throw UpdateException(UpdateError.INVALID)
                inspected++
                val update = UpdateRules.parse(text(url))
                try {
                    if (UpdateRules.eligible(update, context.packageName, own.longVersionCode, Build.VERSION.SDK_INT, certificateHashes(own))) candidates += update
                } catch (e: UpdateException) { signatureMismatch = true }
            }
        }
        candidates.maxByOrNull { it.versionCode } ?: if (signatureMismatch) throw UpdateException(UpdateError.SIGNATURE) else null
    }
    override suspend fun download(release: UpdateRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val own = installed()
        if (!UpdateRules.eligible(release, context.packageName, own.longVersionCode, Build.VERSION.SDK_INT, certificateHashes(own)))
            throw UpdateException(UpdateError.INVALID)
        if (context.cacheDir.usableSpace < release.size + 2 * 1024 * 1024) throw UpdateException(UpdateError.STORAGE)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(dir, "update.apk.part"); val output = File(dir, "update.apk")
        output.delete(); partial.delete()
        val connection = connection(release.apkUrl)
        try {
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != release.size) throw UpdateException(UpdateError.INVALID)
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L; var lastPercent = -1
            partial.outputStream().use { out -> connection.inputStream.use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    received += count
                    if (received > release.size) throw UpdateException(UpdateError.INVALID)
                    out.write(buffer, 0, count); digest.update(buffer, 0, count)
                    val percent = (received * 100 / release.size).toInt()
                    if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                }
            } }
            if (received != release.size || digest.digest().hex() != release.sha256) throw UpdateException(UpdateError.INVALID)
            validateDownloaded(context, partial, release)
            if (!partial.renameTo(output)) throw UpdateException(UpdateError.STORAGE)
            output
        } finally { connection.disconnect(); partial.delete() }
    }
    companion object {
        suspend fun validateDownloaded(context: Context, file: File, release: UpdateRelease) = withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() != release.size) throw UpdateException(UpdateError.INVALID)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(65536)
                while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
            }
            if (digest.digest().hex() != release.sha256) throw UpdateException(UpdateError.INVALID)
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: throw UpdateException(UpdateError.INVALID)
            val own = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            UpdateRules.validateArchive(release, archive.packageName, archive.longVersionCode, certificateHashes(archive),
                context.packageName, own.longVersionCode, certificateHashes(own))
        }
    }
}
