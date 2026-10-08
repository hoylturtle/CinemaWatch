package com.cinemawatch.update

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val release: UpdateRelease? = null,
    val percent: Int = 0, val file: File? = null, val error: UpdateError? = null)
class UpdateController(private val source: () -> UpdateSource) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutable.value = UpdateState(UpdatePhase.CHECKING)
            try {
                val release = source().latest()
                mutable.value = UpdateState(if (release == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE, release)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = UpdateState(UpdatePhase.ERROR, error = (e as? UpdateException)?.reason ?: UpdateError.NETWORK) }
        }
    }
    fun download() {
        if (job?.isActive == true) return
        val release = mutable.value.release ?: return
        job = scope.launch {
            mutable.value = UpdateState(UpdatePhase.DOWNLOADING, release)
            try {
                val file = source().download(release) { percent -> mutable.value = UpdateState(UpdatePhase.DOWNLOADING, release, percent) }
                mutable.value = UpdateState(UpdatePhase.READY, release, 100, file)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = UpdateState(UpdatePhase.ERROR, release, error = (e as? UpdateException)?.reason ?: UpdateError.NETWORK) }
        }
    }
    fun reject(error: UpdateError) { mutable.value = mutable.value.copy(phase = UpdatePhase.ERROR, file = null, error = error) }
}
