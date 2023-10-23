package com.kash.servicetimer.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

interface ITimerServiceWrapper< T : TimerService> : ITimer, ServiceConnection {
    var scope: CoroutineScope?
    val service: T?
    val isBound: MutableStateFlow<Boolean>
    val clazz: Class<T>

    fun plusTimeWithSpeed(delay: Long, offset: Long)

    fun minusTimeWithSpeed(delay: Long, offset: Long)

    fun stopPlusTimeWithSpeed()

    fun stopMinusTimeWithSpeed()

    override fun startTimer(tick: Long, total: Long) {
        service?.startTimer()
    }

    override fun stopTimer() {
        service?.stopTimer()
    }

    override fun pauseTimer() {
        service?.pauseTimer()
    }

    override fun plusTime(time: Long) {
        service?.plusTime(time)
    }

    override fun minusTime(time: Long) {
        service?.minusTime(time)
    }

    fun startService(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(
                Intent(context, clazz)
            )
        } else {
            context.startService(Intent(context, clazz))
        }
    }

    fun bindService(context: Context) {
        Intent(context, clazz).also { intent ->
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        context.unbindService(this)
    }
}