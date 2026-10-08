package com.cinemawatch

import com.cinemawatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class SignalGroupingTest {
    @Test fun appleCompanyDoesNotProveIphone() {
        val g = SignalGrouping.guess("BLE", "", null, "Apple Inc.", null)
        assertEquals(SignalGroup.APPLE, g.group); assertEquals(GuessEvidence.COMPANY_ID, g.evidence)
    }
    @Test fun explicitNameIsOnlyLowConfidence() {
        val g = SignalGrouping.guess("WIFI", "Alice’s iPhone", null, null, null)
        assertEquals(SignalGroup.IPHONE, g.group); assertEquals("LOW", g.confidence)
    }
    @Test fun wifiRolesIncludeHotspotsAndUnknownBleStaysUnknown() {
        val router = SignalGrouping.guess("WIFI", "Cinema", "TP-Link", null, null)
        assertEquals(SignalGroup.TP_LINK, router.group)
        assertTrue(SignalGrouping.memberships("WIFI", router, emptySet()).containsAll(setOf(SignalGroup.TP_LINK, SignalGroup.ACCESS_POINT)))
        assertEquals(SignalGroup.UNKNOWN, SignalGrouping.guess("BLE", "", null, null, null).group)
    }
    @Test fun companyCluesIdentifyEcosystemNotPhoneModel() {
        assertEquals(SignalGroup.XIAOMI, SignalGrouping.guess("BLE", "", null, "Xiaomi Communications", null).group)
        assertEquals(SignalGroup.HUAWEI, SignalGrouping.guess("WIFI", "", "Huawei Technologies", null, null).group)
        assertEquals(SignalGroup.COMPUTER, SignalGrouping.guess("BLE", "MacBook Pro", null, "Apple", null).group)
    }
    @Test fun everyUpstreamClassIsMappedAndMultiMatchesArePreserved() {
        app.fieldwatch.domain.SignatureClass.entries.forEach { assertTrue(SignalGrouping.fromSignature(it) in SignalGrouping.deviceTypes) }
        val g = SignalGrouping.guess("WIFI", "", "Huawei Technologies", null, null)
        val memberships = SignalGrouping.memberships("WIFI", g, setOf(app.fieldwatch.domain.SignatureClass.CAMERA, app.fieldwatch.domain.SignatureClass.MESH))
        assertTrue(memberships.containsAll(setOf(SignalGroup.CAMERA, SignalGroup.MESH, SignalGroup.ACCESS_POINT, SignalGroup.HUAWEI)))
        assertFalse(SignalGroup.UNKNOWN in memberships)
        assertTrue(SignalGroup.UNKNOWN in SignalGrouping.memberships("BLE", g, emptySet()))
    }
    @Test fun domesticEcosystemsUseEvidenceAndDoNotInferDeviceType() {
        val brands = mapOf("Honor Device" to SignalGroup.HONOR, "OPPO" to SignalGroup.OPPO, "vivo" to SignalGroup.VIVO,
            "realme" to SignalGroup.REALME, "OnePlus" to SignalGroup.ONEPLUS, "DJI" to SignalGroup.DJI,
            "Hikvision" to SignalGroup.HIKVISION, "Dahua" to SignalGroup.DAHUA, "TP-Link" to SignalGroup.TP_LINK, "ZTE" to SignalGroup.ZTE)
        brands.forEach { (brand, category) ->
            val g = SignalGrouping.guess("BLE", "", brand, "Unrelated company", null)
            assertEquals(category, g.group); assertEquals(GuessEvidence.OUI, g.evidence)
            val fromName = SignalGrouping.guess("BLE", brand, null, null, null)
            assertEquals(category, fromName.group); assertEquals("LOW", fromName.confidence)
            assertTrue(SignalGroup.UNKNOWN in SignalGrouping.memberships("BLE", g, emptySet()))
        }
        assertEquals(SignalGroup.UNKNOWN, SignalGrouping.guess("BLE", "notoppo randomzte", null, null, null).group)
    }
    @Test fun groupingClearsIdentifiersAndExcludesAuthorizedRadios() {
        val w = PrivacyWindow()
        w.accept("WIFI", "a", -60, true, false); w.classify("WIFI", "a", "ACCESS_POINT")
        w.accept("BLE", "b", -60, true, false); w.classify("BLE", "b", "APPLE")
        w.accept("BLE", "b", -55, true, false)
        assertEquals(1, w.groupCounts()["BLE" to "APPLE"])
        w.exclude("BLE", "b"); assertEquals(0, w.counts().second)
        assertFalse(w.groupCounts().keys.any { it.first == "BLE" })
        w.clear(); assertTrue(w.groupCounts().isEmpty()); assertEquals(0 to 0, w.counts())
    }
    @Test fun duplicateWifiCallbacksCannotMakeHealthyCoverage() {
        val tracker = WifiBatchTracker()
        assertTrue(tracker.accept(listOf(100, 120)))
        assertFalse(tracker.accept(listOf(100, 120))); assertFalse(tracker.accept(listOf(110)))
        assertTrue(tracker.accept(listOf(140)))
    }
    @Test fun overrunAndBurstOnlyBleAreNotHealthy() {
        assertFalse(SamplingQuality.complete(434, 120, 0)); assertFalse(SamplingQuality.complete(110, 120, 0))
        assertFalse(SamplingQuality.complete(120, 120, 1)); assertTrue(SamplingQuality.complete(122, 120, 0))
        assertFalse(SamplingQuality.bleHealthy(true, 122, 0, 2, 120, false))
        assertFalse(SamplingQuality.bleHealthy(true, 122, 0, 90, 120, false))
        assertTrue(SamplingQuality.bleHealthy(true, 122, 0, 119, 120, false))
    }
}
