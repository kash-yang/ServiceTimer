package com.kash.servicetimer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kash.servicetimer.service.flow.FlowTimerServiceWrapper
import com.kash.servicetimer.service.TimerDetail
import com.kash.servicetimer.service.TimerState
import com.kash.servicetimer.service.toMinute
import com.kash.servicetimer.ui.theme.ServiceTimerTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        MainViewModelFactory(
            FlowTimerServiceWrapper()
        )
    }

    override fun onStart() {
        super.onStart()
        viewModel.bindService(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ServiceTimerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            RequirePostNotificationPermission(navigateToSettingsScreen = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts(
                                            "package",
                                            this@MainActivity.packageName,
                                            null
                                        )
                                    )
                                )
                            }) {
                                TimerScreen(
                                    viewModel = viewModel,
                                    context = LocalContext.current
                                )
                            }
                        } else {
                            TimerScreen(
                                viewModel = viewModel,
                                context = LocalContext.current
                            )
                        }
                        FloatingActionButton(
                            modifier = Modifier
                                .align(TopEnd)
                                .padding(end = 16.dp, top = 16.dp),
                            onClick = { this@MainActivity.finish() },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "close",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    context: Context,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp)
                .wrapContentSize()
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .padding(16.dp)
        ) {

            val isBound = viewModel.mainViewState.collectAsState().value.isServerBound
            val timerUiState = viewModel.mainViewState.collectAsState().value.timerState

            val brushBackground = SolidColor(value = MaterialTheme.colorScheme.surfaceVariant)
            val brushForeground = SolidColor(value = MaterialTheme.colorScheme.secondary)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                drawArc(
                    brush = brushBackground,
                    startAngle = 0f,
                    sweepAngle = -360f,
                    useCenter = false,
                    style = Stroke(width = 72f, cap = StrokeCap.Round)
                )
            }

            when (isBound) {
                true -> {
                    val percentage = when (timerUiState) {
                        is TimerState.TimerFinished -> {
                            -360f
                        }

                        is TimerDetail -> {
                            (1 - timerUiState.remain.toFloat() / timerUiState.total.toFloat()) * 360f
                        }

                        else -> {
                            -360f
                        }
                    }
                    val time = when (timerUiState) {
                        is TimerState.TimerFinished -> timerUiState.total
                        is TimerDetail -> timerUiState.remain
                        else -> null
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        drawArc(
                            brush = brushForeground,
                            startAngle = -90f - percentage,
                            sweepAngle = -(360 - percentage),
                            useCenter = false,
                            style = Stroke(width = 72f, cap = StrokeCap.Round)
                        )
                    }

                    time?.let { _time ->
                        Text(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(16.dp)
                                .align(Alignment.Center),
                            text = _time.toMinute(),
                            fontSize = 90.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                false -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(64.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        trackColor = MaterialTheme.colorScheme.secondary,
                    )
                }

            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            var plusPressed by remember {
                mutableStateOf(false)
            }

            var stopPressed by remember {
                mutableStateOf(false)
            }

            var minusPressed by remember {
                mutableStateOf(false)
            }

            var playPressed by remember {
                mutableStateOf(false)
            }

            var pausePressed by remember {
                mutableStateOf(false)
            }

            val timerUiState =
                viewModel.mainViewState.collectAsState().value.timerState

            ControlImage(
                isPressed = minusPressed,
                resId = R.drawable.baseline_remove_24,
                contentDescription = "minus",
                onTap = {
                    viewModel.minusTime()
                },
                onPressed = {
                    minusPressed = true
                },
                onPressReleased = {
                    minusPressed = false
                    viewModel.stopMinusTimeWithSpeed()
                },
                onLongPress = {
                    minusPressed = true
                    viewModel.minusTimeWithSpeed()
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            when (timerUiState) {
                is TimerState.TimerTick -> {
                    ControlImage(
                        isPressed = pausePressed,
                        resId = R.drawable.baseline_pause_24,
                        contentDescription = "pause",
                        onTap = {
                            viewModel.pauseTimer()
                        },
                        onPressed = {
                            pausePressed = true
                        },
                        onPressReleased = {
                            pausePressed = false
                        }
                    )
                }

                else -> {
                    ControlImage(
                        isPressed = playPressed,
                        resId = R.drawable.baseline_play_arrow_24,
                        contentDescription = "play",
                        onTap = {
                            viewModel.startService(context)
                            viewModel.startTimer()
                        },
                        onPressed = {
                            playPressed = true
                        },
                        onPressReleased = {
                            playPressed = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            ControlImage(
                isPressed = stopPressed,
                resId = R.drawable.baseline_stop_24,
                contentDescription = "stop",
                onTap = {
                    viewModel.stopTimer()
                },
                onPressed = {
                    stopPressed = true
                },
                onPressReleased = {
                    stopPressed = false
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            ControlImage(
                isPressed = plusPressed,
                resId = R.drawable.baseline_add_24,
                contentDescription = "plus",
                onTap = {
                    viewModel.plusTime()
                },
                onPressed = {
                    plusPressed = true
                },
                onPressReleased = {
                    plusPressed = false
                    viewModel.stopPlusTimeWithSpeed()
                },
                onLongPress = {
                    plusPressed = true
                    viewModel.plusTimeWithSpeed()
                }
            )
        }
    }
}

@Composable
fun ControlImage(
    modifier: Modifier = Modifier,
    isPressed: Boolean,
    @DrawableRes resId: Int,
    contentDescription: String = "",
    onPressed: (() -> Unit)? = null,
    onPressReleased: ((isCanceled: Boolean) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.tint(Color.White),
        modifier = modifier
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary, CircleShape
            )
            .size(56.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = onTap,
                    onPress = {
                        onPressed?.invoke()
                        //start
                        val released = try {
                            tryAwaitRelease()
                        } catch (_: Exception) {
                            false
                        }
                        onPressReleased?.invoke(released.not())
                    },
                    onLongPress = onLongPress
                )
            }
    )
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@ExperimentalPermissionsApi
@Composable
fun RequirePostNotificationPermission(
    navigateToSettingsScreen: () -> Unit,
    content: @Composable() () -> Unit,
) {
    // Track if the user doesn't want to see the rationale any more.
    var doNotShowRationale by rememberSaveable { mutableStateOf(false) }
    // Permission state
    val permissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )
    when {
        permissionState.status.isGranted -> {
            content()
        }
        // If the user denied the permission but a rationale should be shown, or the user sees
        // the permission for the first time, explain why the feature is needed by the app and allow
        // the user to be presented with the permission again or to not see the rationale any more.
        permissionState.status.shouldShowRationale -> {
            if (doNotShowRationale) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Center
                ) {
                    Text(
                        text = "Feature not available"
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = "Need to post notification to notify the timer status. Please grant the permission."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                onClick = { permissionState.launchPermissionRequest() }
                            ) {
                                Text(
                                    fontSize = 12.sp,
                                    text = "Request permission"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                onClick = { doNotShowRationale = true }) {
                                Text(
                                    fontSize = 12.sp,
                                    text = "Don't show rationale again"
                                )
                            }
                        }
                    }
                }
            }
        }
        // If the criteria above hasn't been met, the user denied the permission. Let's present
        // the user with a FAQ in case they want to know more and send them to the Settings screen
        // to enable it the future there if they want to.
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp), contentAlignment = Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Post notification permission denied. " +
                                "Need to post notification to notify timer status. " +
                                "Please grant access on the Settings screen."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = CenterStart
                    ) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            onClick = navigateToSettingsScreen
                        ) {
                            Text(
                                fontSize = 14.sp,
                                text = "Open Settings"
                            )
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ServiceTimerTheme {
        Greeting("Android")
    }
}

