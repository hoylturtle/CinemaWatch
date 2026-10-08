package com.cinemawatch

import com.cinemawatch.update.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class UpdateRulesTest {
    private val signer = "a".repeat(64)
    private fun release() = UpdateRules.parse("""{"schemaVersion":1,"versionCode":4,"versionName":"0.4.0","packageName":"com.cinemawatch","minSdk":29,"apkUrl":"https://github.com/hoylturtle/CinemaWatch/releases/download/v0.4.0-preview/CinemaWatch-0.4.0.apk","size":500,"sha256":"${"b".repeat(64)}","signerSha256":"$signer","notes":"New version"}""")
    @Test fun onlyRepositoryHttpsAssetsAreAccepted() {
        assertTrue(UpdateRules.trustedReleaseUrl(release().apkUrl))
        listOf("http://github.com/hoylturtle/CinemaWatch/releases/download/v1/app.apk", "https://github.com.evil.test/hoylturtle/CinemaWatch/releases/download/v1/app.apk", "https://github.com/other/CinemaWatch/releases/download/v1/app.apk", "https://github.com/hoylturtle/CinemaWatch/releases/download/v1/../app.apk", "https://github.com/hoylturtle/CinemaWatch/releases/download/v1/%2e%2e", "https://github.com/hoylturtle/CinemaWatch/releases/download/v1/app.apk?redirect=x").forEach { assertFalse(UpdateRules.trustedReleaseUrl(it)) }
    }
    @Test fun updatesRequireSamePackageNewerVersionAndSupportedSdk() {
        val r = release()
        assertTrue(UpdateRules.eligible(r, "com.cinemawatch", 3, 29, setOf(signer)))
        assertFalse(UpdateRules.eligible(r, "com.cinemawatch.preview", 3, 29, setOf(signer)))
        assertFalse(UpdateRules.eligible(r, "com.cinemawatch", 4, 29, setOf(signer)))
        assertFalse(UpdateRules.eligible(r.copy(minSdk=35), "com.cinemawatch", 3, 29, setOf(signer)))
    }
    @Test fun incompatibleSignerCannotBePresentedAsCoverInstall() {
        try { UpdateRules.eligible(release(), "com.cinemawatch", 3, 29, setOf("c".repeat(64))); fail() }
        catch (e: UpdateException) { assertEquals(UpdateError.SIGNATURE, e.reason) }
    }
    @Test fun archiveMustMatchManifestAndInstalledIdentity() {
        val r = release()
        UpdateRules.validateArchive(r, "com.cinemawatch", 4, setOf(signer), "com.cinemawatch", 3, setOf(signer))
        try { UpdateRules.validateArchive(r, "com.fake", 4, setOf(signer), "com.cinemawatch", 3, setOf(signer)); fail() }
        catch (e: UpdateException) { assertEquals(UpdateError.INVALID, e.reason) }
        try { UpdateRules.validateArchive(r, "com.cinemawatch", 3, setOf(signer), "com.cinemawatch", 3, setOf(signer)); fail() }
        catch (e: UpdateException) { assertEquals(UpdateError.INVALID, e.reason) }
        try { UpdateRules.validateArchive(r, "com.cinemawatch", 4, emptySet(), "com.cinemawatch", 3, setOf(signer)); fail() }
        catch (e: UpdateException) { assertEquals(UpdateError.SIGNATURE, e.reason) }
    }
    @Test fun malformedManifestAndOversizedPackagesAreRejected() {
        listOf("{}", """{"schemaVersion":2}""", "not json").forEach { text ->
            try { UpdateRules.parse(text); fail() } catch (e: UpdateException) { assertEquals(UpdateError.INVALID, e.reason) }
        }
    }
}
