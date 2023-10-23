package com.kash.servicetimer.service.flow

import android.content.ComponentName
import android.os.IBinder
import com.kash.servicetimer.service.ITimerServiceWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class FlowTimerServiceWrapper(
    override var scope: CoroutineScope? = GlobalScope,
) : ITimerServiceWrapper<FlowTimerService> {
    private var binder: IBinder? = null
    override val service get() = (binder as? FlowTimerService.FlowTimerServiceBinder)?.getService()
    override val isBound: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val clazz = FlowTimerService::class.java

    private var adjustTimeWithSpeedJob: Job? = null

    private fun stopAdjustTimeWithSpeed() = adjustTimeWithSpeedJob?.cancel()

    private fun adjustTimeWithSpeed(delay: Long, offset: Long, isPlus: Boolean) {
        adjustTimeWithSpeedJob = scope?.launch(Dispatchers.IO) {
            while (true) {
                if (isPlus) plusTime(offset) else minusTime(offset)
                delay(delay)
            }
        }
    }

    override fun plusTimeWithSpeed(delay: Long, offset: Long) {
        adjustTimeWithSpeed(delay, offset, true)
    }

    override fun minusTimeWithSpeed(delay: Long, offset: Long) {
        adjustTimeWithSpeed(delay, offset, false)
    }

    override fun stopPlusTimeWithSpeed() {
        stopAdjustTimeWithSpeed()
    }

    override fun stopMinusTimeWithSpeed() {
        stopAdjustTimeWithSpeed()
    }

    override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
        scope?.launch(Dispatchers.IO) {
            binder = service
            isBound.value = true
        }
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        scope?.launch(Dispatchers.IO) {
            binder = null
            isBound.value = false
        }
    }
}