package com.cinemawatch

import com.cinemawatch.flow.*
import org.junit.Assert.*
import org.junit.Test

class FlowTrackerTest {
    private val target=FlowTarget("tag","Authorized tag","AA:BB:CC:DD:EE:01")
    private fun tracker()=FlowTracker(listOf(target),setOf("A","B"))
    private fun feed(t:FlowTracker,id:String,zone:String,rssi:Int,now:Long,seq:Long=1,offset:Int=0,count:Int=3,healthy:Boolean=true) {
        t.ingest(FlowNode(id,id,zone,offset,now,healthy,seq),(0 until count).map { FlowReading("tag",rssi,now-it*20) },now)
    }
    @Test fun requiresTwoAreasAndThreeFreshReadingsBeforeTwoStableDecisions() {
        val t=tracker();feed(t,"a","A",-45,10000);t.evaluate(10000);assertNull(t.snapshot().presence.single().zone)
        feed(t,"b","B",-75,10000);assertNull(t.evaluate(10000).presence.single().zone)
        assertEquals("A",t.evaluate(12000).presence.single().zone)
        assertEquals(0,t.snapshot().transitions.size)
    }
    @Test fun strongerCandidateNeedsHysteresisThenProducesOneTransition() {
        val t=tracker();feed(t,"a","A",-45,10000);feed(t,"b","B",-75,10000);t.evaluate(10000);t.evaluate(12000)
        feed(t,"a","A",-75,13000,2,count=7);feed(t,"b","B",-45,13000,2,count=7)
        assertEquals("A",t.evaluate(13000).presence.single().zone)
        assertEquals("B",t.evaluate(15000).presence.single().zone)
        assertEquals(listOf(FlowTransition("Authorized tag","A","B",15000)),t.snapshot().transitions)
    }
    @Test fun ambiguousEvidenceIsUnknownAndDoesNotJoinAcrossLostEvidence() {
        val t=tracker();feed(t,"a","A",-45,10000);feed(t,"b","B",-75,10000);t.evaluate(10000);t.evaluate(12000)
        assertNull(t.evaluate(20000).presence.single().zone)
        feed(t,"a","A",-75,21000,2);feed(t,"b","B",-45,21000,2);t.evaluate(21000);t.evaluate(23000)
        assertEquals("B",t.snapshot().presence.single().zone);assertTrue(t.snapshot().transitions.isEmpty())
    }
    @Test fun rejectsWeakMarginAndTwoNodesInSameArea() {
        val t=tracker();feed(t,"a","A",-50,10000);feed(t,"b","B",-55,10000);t.evaluate(10000)
        assertNull(t.evaluate(12000).presence.single().zone)
        val same=tracker();feed(same,"a","A",-45,10000);feed(same,"b","A",-60,10000);same.evaluate(10000)
        assertNull(same.evaluate(12000).presence.single().zone)
    }
    @Test fun ignoresUnknownInvalidStaleAndUnhealthyPackets() {
        val t=tracker();val node=FlowNode("a","a","A",0,10000,true,1)
        t.ingest(node,listOf(FlowReading("stranger",-45,10000),FlowReading("tag",127,10000),FlowReading("tag",-45,3000)),10000)
        feed(t,"b","B",-75,10000,healthy=false)
        assertTrue(t.evaluate(10000).signals.isEmpty())
    }
    @Test fun offsetIsExplicitAndRetransmissionDoesNotInflateSamples() {
        val t=tracker();feed(t,"a","A",-58,10000,offset=10);feed(t,"b","B",-55,10000)
        feed(t,"a","A",-58,10000,seq=2,offset=10)
        t.evaluate(10000);val s=t.evaluate(12000)
        assertEquals("A",s.presence.single().zone);assertEquals(3,s.signals.first { it.node=="a" }.samples)
    }
    @Test fun rejectsReplayAndRecordsIndependentGroundTruth() {
        val t=tracker();feed(t,"a","A",-45,10000)
        assertThrows(IllegalArgumentException::class.java) { feed(t,"a","A",-45,10000) }
        feed(t,"b","B",-75,10000);t.evaluate(10000);t.markTruth("tag","A",10001)
        assertNull(t.snapshot().truths.single().observed)
        t.evaluate(12000);t.markTruth("tag","B",12001)
        assertEquals("B",t.snapshot().truths.last().expected);assertEquals("A",t.snapshot().truths.last().observed)
    }
    @Test fun snapshotAndTruthDoNotAccelerateConfirmationAndClockRollbackClearsEvidence() {
        val t=tracker();feed(t,"a","A",-45,10000);feed(t,"b","B",-75,10000);t.evaluate(10000)
        repeat(20) { assertNull(t.snapshot().presence.single().zone) };t.markTruth("tag","A",10000)
        assertNull(t.snapshot().presence.single().zone);t.evaluate(12000)
        assertNull(t.evaluate(9000).presence.single().zone)
    }
}
