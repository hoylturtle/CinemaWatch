package com.cinemawatch.flow

import com.cinemawatch.domain.InspectionPolicy
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Filter before dispatch: no unknown addresses or advertisement payloads enter this queue. */
internal class FlowInbox(targets:List<FlowTarget>,capacity:Int=256) {
    private val approved=targets.associateBy { it.address }
    val queue=Channel<FlowReading>(capacity)
    val heardAt=AtomicLong(0)
    val drops=AtomicInteger(0)
    fun offer(address:String,rssi:Int,at:Long,now:Long,offset:Long) {
        if(now-at !in 0..6000)return
        heardAt.set(at)
        val target=approved[address] ?: return
        if(!InspectionPolicy.validRssi(rssi))return
        if(queue.trySend(FlowReading(target.id,rssi,at+offset)).isFailure)drops.incrementAndGet()
    }
    fun close() { queue.close() }
}
