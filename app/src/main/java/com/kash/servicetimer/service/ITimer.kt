package com.kash.servicetimer.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface ITimer {
    fun startTimer(tick: Long = 100L, total: Long = 1.minute())

    fun stopTimer()

    fun pauseTimer()

    fun plusTime(time: Long)

    fun minusTime(time: Long)
}

interface TimerDetail {
    val remain: Long
    val total: Long
}

sealed class TimerState : TimerDetail {
    data class TimerTick(override val remain: Long, override val total: Long) : TimerState() {
        operator fun plus(time: Long): TimerTick = TimerTick(remain + time, total + time)
        operator fun minus(time: Long): TimerTick = TimerTick(remain - time, total - time)
    }

    data class TimerPaused(override val remain: Long, override val total: Long) : TimerState() {
        operator fun plus(time: Long): TimerPaused = TimerPaused(remain + time, total + time)
        operator fun minus(time: Long): TimerPaused = TimerPaused(remain - time, total - time)
    }

    data class TimerFinished(override val remain: Long = 0, override val total: Long) :
        TimerState() {
        operator fun plus(time: Long): TimerFinished = TimerFinished(remain, total + time)
        operator fun minus(time: Long): TimerFinished = TimerFinished(remain, total - time)
    }
}

internal fun Int.minute() = this * 60 * 1000L

internal fun Long.toMinute() = toDate(format = "mm:ss")

internal fun Long.toDate(format: String = "yyyy-MM-dd HH:mm.ss.SSS"): String {
    val date = Date(this)
    val df = SimpleDateFormat(format, Locale.US)
    return df.format(date)
}