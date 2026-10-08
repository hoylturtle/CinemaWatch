package com.cinemawatch.update

import org.json.JSONObject
import java.net.URI
import java.util.Locale

const val RELEASES_URL = "https://github.com/hoylturtle/CinemaWatch/releases"
const val RELEASE_API = "https://api.github.com/repos/hoylturtle/CinemaWatch/releases?per_page=20"
private const val DOWNLOAD_PATH = "/hoylturtle/CinemaWatch/releases/download/"

enum class UpdateError { NETWORK, INVALID, SIGNATURE, STORAGE }
class UpdateException(val reason: UpdateError) : Exception(reason.name)
data class UpdateRelease(val versionCode: Long, val versionName: String, val packageName: String,
    val minSdk: Int, val apkUrl: String, val size: Long, val sha256: String, val signerSha256: String, val notes: String)

object UpdateRules {
    fun trustedReleaseUrl(url: String, fileName: String? = null): Boolean = runCatching {
        val uri = URI(url)
        val rest = uri.rawPath.removePrefix(DOWNLOAD_PATH)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.startsWith(DOWNLOAD_PATH) &&
            rest.split('/').size == 2 && rest.split('/').all { it.isNotBlank() && !it.contains("..") && !it.contains('%') } &&
            (fileName == null || rest.substringAfter('/') == fileName)
    }.getOrDefault(false)
    fun parse(json: String): UpdateRelease {
        try {
            val o = JSONObject(json)
            require(o.getInt("schemaVersion") == 1)
            val release = UpdateRelease(o.getLong("versionCode"), o.getString("versionName"), o.getString("packageName"),
                o.getInt("minSdk"), o.getString("apkUrl"), o.getLong("size"), o.getString("sha256").lowercase(Locale.ROOT),
                o.getString("signerSha256").lowercase(Locale.ROOT), o.optString("notes").take(5000))
            require(release.versionCode > 0 && release.minSdk >= 29 && release.versionName.length in 1..60)
            require(release.size in 1..150L * 1024 * 1024)
            require(Regex("[0-9a-f]{64}").matches(release.sha256) && Regex("[0-9a-f]{64}").matches(release.signerSha256))
            require(trustedReleaseUrl(release.apkUrl) && URI(release.apkUrl).path.endsWith(".apk"))
            return release
        } catch (e: Exception) { throw UpdateException(UpdateError.INVALID) }
    }
    fun eligible(release: UpdateRelease, packageName: String, versionCode: Long, sdk: Int, signers: Set<String>): Boolean {
        if (release.packageName != packageName || release.minSdk > sdk || release.versionCode <= versionCode) return false
        if (signers != setOf(release.signerSha256)) throw UpdateException(UpdateError.SIGNATURE)
        return true
    }
    fun validateArchive(release: UpdateRelease, actualPackage: String?, actualVersion: Long?, actualSigners: Set<String>,
        installedPackage: String, installedVersion: Long, installedSigners: Set<String>) {
        if (actualPackage != installedPackage || actualPackage != release.packageName || actualVersion != release.versionCode ||
            release.versionCode <= installedVersion) throw UpdateException(UpdateError.INVALID)
        if (actualSigners.isEmpty() || actualSigners != installedSigners || actualSigners != setOf(release.signerSha256))
            throw UpdateException(UpdateError.SIGNATURE)
    }
}
