package com.kash.servicetimer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kash.servicetimer.service.ITimerServiceWrapper
import com.kash.servicetimer.service.TimerState
import com.kash.servicetimer.service.minute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MainViewState(
    val isServerBound: Boolean = false,
    val timerState: TimerState? = null,
)

class MainViewModel(
    private val serviceWrapper: ITimerServiceWrapper<*>,
) : ViewModel() {

    private val timerState get() = serviceWrapper.service?.timerState
    private val isBound get() = serviceWrapper.isBound

    val mainViewState: StateFlow<MainViewState>
        get() {
            return when (timerState) {
                null -> {
                    isBound.map { MainViewState(isServerBound = it) }
                }

                else -> {
                    isBound.combine(timerState!!) { isBound, state ->
                        MainViewState(isServerBound = isBound, timerState = state)
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = MainViewState()
            )
        }

    init {
        serviceWrapper.scope = viewModelScope
    }

    fun startTimer() = serviceWrapper.startTimer()

    fun stopTimer() = serviceWrapper.stopTimer()

    fun pauseTimer() = serviceWrapper.pauseTimer()

    fun plusTime() = serviceWrapper.plusTime(1.minute())

    fun minusTime() = serviceWrapper.minusTime(1.minute())

    fun plusTimeWithSpeed(delay: Long = 100L) =
        serviceWrapper.plusTimeWithSpeed(delay, 1.minute())

    fun minusTimeWithSpeed(delay: Long = 100L) =
        serviceWrapper.minusTimeWithSpeed(delay, 1.minute())

    fun stopPlusTimeWithSpeed() = serviceWrapper.stopPlusTimeWithSpeed()

    fun stopMinusTimeWithSpeed() = serviceWrapper.stopMinusTimeWithSpeed()

    fun startService(context: Context) = serviceWrapper.startService(context)
    fun bindService(context: Context) = serviceWrapper.bindService(context)

    fun unbindService(context: Context) = serviceWrapper.unbindService(context)

}