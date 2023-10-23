package com.kash.servicetimer.service.flow

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import com.kash.servicetimer.MainActivity
import com.kash.servicetimer.R
import com.kash.servicetimer.service.TimerService
import com.kash.servicetimer.service.TimerState
import com.kash.servicetimer.service.minute
import com.kash.servicetimer.service.toMinute
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

const val CHANNEL_ID = "TimerService"
const val KEY_REMAIN = "key_remain"
const val KEY_TOTAL = "key_total"

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("ApplySharedPref")
class FlowTimerService : TimerService() {

    private val binder = FlowTimerServiceBinder()
    private lateinit var _timerState: MutableStateFlow<TimerState>
    private val notificationId = 0x123
    private val serviceRunningId = 0x456
    private val preference: SharedPreferences by lazy {
        getSharedPreferences(
            "com.kash.servicetimer.pref_timer_service",
            Context.MODE_PRIVATE
        )
    }
    override val timerState: StateFlow<TimerState> get() = _timerState

    private val lastState: TimerState get() = _timerState.value

    private var currentJob: Job? = null

    inner class FlowTimerServiceBinder : Binder() {
        fun getService(): FlowTimerService = this@FlowTimerService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i("FlowTimerService", "TimerService onBind")
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i("FlowTimerService", "TimerService onRebind")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("FlowTimerService", "TimerService onUnbind")
        return true
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("FlowTimerService", "TimerService onCreate")
        val total = preference.getLong(KEY_TOTAL, -1L)
        _timerState = if (total < 0) {
            MutableStateFlow(TimerState.TimerFinished(total = 1.minute()))
        } else {
            val remain = preference.getLong(KEY_REMAIN, total)
            MutableStateFlow(TimerState.TimerPaused(remain = remain, total = total))
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("FlowTimerService", "TimerService onStartCommand")

        try {
            val notification = createNotificationWithContent("Timer is running")
            ServiceCompat.startForeground(
                this@FlowTimerService,
                serviceRunningId, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
                // (e.g. started from bg)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("FlowTimerService", "TimerService onDestroy")
    }

    private fun tickerFlow(total: Long, tick: Long = 100L, initialDelay: Duration = Duration.ZERO) =
        flow {
            delay(initialDelay)
            var l = total
            while (true) {
                emit(Unit)
                delay(tick)
                l -= tick
                if (l <= 0) {
                    break
                }
            }
        }

    @Suppress("LocalVariableName")
    override fun startTimer(tick: Long, total: Long) {
        currentJob = GlobalScope.launch(Dispatchers.IO) {
            removeCurrentTime()
            var remain =
                if (lastState is TimerState.TimerFinished) lastState.total else lastState.remain
            val _total = lastState.total
            tickerFlow(total = _total, tick = tick)
                .onEach {
                    remain -= tick
                    if (remain <= 0) remain = 0L
                    _timerState.value =
                        TimerState.TimerTick(remain = remain, total = _total)
                    updateNotification(remain)
                    if (remain == 0L) {
                        _timerState.value =
                            TimerState.TimerFinished(total = lastState.total)
                    }
                }
                .flowOn(Dispatchers.Main)
                .onCompletion {
                    it?.printStackTrace()
                    ServiceCompat.stopForeground(
                        this@FlowTimerService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
                .collect()
        }
    }

    override fun stopTimer() {
        currentJob?.cancel()
        GlobalScope.launch(Dispatchers.IO) {
            removeCurrentTime()
            _timerState.value = TimerState.TimerFinished(total = lastState.total)
            withContext(Dispatchers.Main) {
                removeNotification()
            }
        }
    }


    override fun pauseTimer() {
        currentJob?.cancel()
        GlobalScope.launch(Dispatchers.IO) {
            saveCurrentTime()
            _timerState.value =
                TimerState.TimerPaused(remain = lastState.remain, total = lastState.total)
        }
    }

    override fun plusTime(time: Long) {
        if (lastState.total + time <= 60.minute()) {
            currentJob?.cancel()
            GlobalScope.launch(Dispatchers.IO) {
                when (val last = lastState) {
                    is TimerState.TimerFinished -> {
                        _timerState.value = last + time
                    }

                    is TimerState.TimerTick -> {
                        _timerState.value = last + time
                        startTimer(total = lastState.total)
                    }

                    is TimerState.TimerPaused -> {
                        _timerState.value = last + time
                        pauseTimer()
                    }
                }
            }
        }
    }

    override fun minusTime(time: Long) {
        if (lastState.total - time >= 1.minute()) {
            plusTime(time = (-1) * time)
        }
    }

    private fun saveCurrentTime() {
        preference.edit()
            .putLong(KEY_REMAIN, lastState.remain)
            .putLong(KEY_TOTAL, lastState.total)
            .commit()
    }

    private fun removeCurrentTime() {
        preference.edit()
            .remove(KEY_REMAIN)
            .remove(KEY_TOTAL)
            .commit()
    }

    private fun createNotificationWithContent(contentText: CharSequence): Notification {
        // Create an Intent for the activity you want to start.
        val resultIntent = Intent(this, MainActivity::class.java)
        // Create the TaskStackBuilder.
        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            // Add the intent, which inflates the back stack.
            addNextIntentWithParentStack(resultIntent)
            // Get the PendingIntent containing the entire back stack.
            getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_timer_24)
            .setContentTitle("Timer")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(resultPendingIntent)
            .build()
    }



    private fun removeNotification() =
        NotificationManagerCompat.from(this).cancel(notificationId)

    private fun updateNotification(remain: Long) {
        val notification = createNotificationWithContent(
            contentText = when (val text = remain.toMinute()) {
                "00:00" -> "Time's up"
                else -> text
            }
        )

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define.
            if (ActivityCompat.checkSelfPermission(
                    this@FlowTimerService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, notification)
            }
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system.
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}