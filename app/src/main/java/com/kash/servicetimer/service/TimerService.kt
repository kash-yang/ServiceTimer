package com.kash.servicetimer.service

import android.app.Service
import kotlinx.coroutines.flow.StateFlow

abstract class TimerService : ITimer, Service() {
    abstract val timerState: StateFlow<TimerState>
}