package com.cinemawatch

import com.cinemawatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class InspectionPolicyTest {
    @Test fun brokenRadioNeverTurnsIntoMissing() {
        val r = InspectionPolicy.evaluate(emptyList(), listOf(-50,-52,-48), 1, false)
        assertEquals(AssetStatus.UNAVAILABLE, r.status)
        assertEquals(1, r.misses)
    }
    @Test fun repeatedHealthyMissRequired() {
        assertEquals(AssetStatus.REVIEW, InspectionPolicy.evaluate(emptyList(), emptyList(),0,true).status)
        assertEquals(AssetStatus.MISSING, InspectionPolicy.evaluate(emptyList(), emptyList(),1,true).status)
    }
    @Test fun weakIsRelativeToRepeatedBaseline() {
        assertEquals(AssetStatus.LEARNING, InspectionPolicy.evaluate(listOf(-90), listOf(-50,-51),0,true).status)
        val result = InspectionPolicy.evaluate(listOf(-70,-68,-69),listOf(-51,-50,-49),0,true)
        assertEquals(AssetStatus.WEAK,result.status)
        assertEquals(-50,result.baseline)
    }
    @Test fun positiveAnd127NeverAccepted() {
        val w=PrivacyWindow()
        listOf(127,0,15,-121).forEach { assertFalse(w.accept("BLE","AA",it,true,false)) }
        assertEquals(0 to 0,w.counts());assertEquals(4,w.discarded)
    }
    @Test fun dedupWithinWindowExcludesRegisteredAndCaches() {
        val w=PrivacyWindow()
        repeat(20) { w.accept("BLE","rotating-address-1",-70,true,false) }
        w.accept("WIFI","asset-radio",-70,true,true)
        w.accept("WIFI","cached-ap",-70,false,false)
        assertEquals(0 to 1,w.counts())
        w.clear();assertEquals(0 to 0,w.counts())
    }
    @Test fun capacityLimitIsReported() {
        val w=PrivacyWindow(1)
        w.accept("BLE","a",-50,true,false)
        assertFalse(w.accept("BLE","b",-50,true,false))
        assertEquals(1,w.dropped)
    }
}
