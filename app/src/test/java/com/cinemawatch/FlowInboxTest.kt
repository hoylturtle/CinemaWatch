package com.cinemawatch

import com.cinemawatch.flow.*
import org.junit.Assert.*
import org.junit.Test

class FlowInboxTest {
    private val target=FlowTarget("session-tag","Authorized beacon","AA:BB:CC:DD:EE:01")
    @Test fun filtersDenseUnknownTrafficBeforeQueueingAndKeepsOriginalTimestamp() {
        val inbox=FlowInbox(listOf(target),2)
        repeat(10000) { inbox.offer("UNKNOWN-$it",-45,9500,10000,100) }
        assertTrue(inbox.queue.tryReceive().isFailure);assertEquals(0,inbox.drops.get())
        inbox.offer(target.address,-55,9500,10000,100)
        assertEquals(FlowReading("session-tag",-55,9600),inbox.queue.tryReceive().getOrNull())
        inbox.close()
    }
    @Test fun reportsOverflowAndRejectsOldOrInvalidSamples() {
        val inbox=FlowInbox(listOf(target),2)
        inbox.offer(target.address,127,10000,10000,0);inbox.offer(target.address,-55,3000,10000,0)
        assertTrue(inbox.queue.tryReceive().isFailure)
        repeat(10) { inbox.offer(target.address,-55,10000+it,10000+it,0) }
        assertEquals(8,inbox.drops.get());assertNotNull(inbox.queue.tryReceive().getOrNull());assertNotNull(inbox.queue.tryReceive().getOrNull())
        assertTrue(inbox.queue.tryReceive().isFailure);inbox.close()
    }
}
