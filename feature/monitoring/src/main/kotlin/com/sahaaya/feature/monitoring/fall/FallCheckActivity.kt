package com.sahaaya.feature.monitoring.fall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.theme.SahaayaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * "Possible fall detected - Are you okay?"
 *
 * Its own activity so it can appear over the lock screen: a patient on the floor
 * cannot unlock a phone and find a button. It holds no state of its own - the
 * countdown and the outcome belong to [FallAlertCoordinator] - so closing or
 * killing this screen cannot stop a no-response alert from being sent.
 */
@AndroidEntryPoint
class FallCheckActivity : ComponentActivity() {

    @Inject lateinit var coordinator: FallAlertCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SahaayaTheme {
                val prompt by coordinator.prompt.collectAsStateWithLifecycle()
                val current = prompt
                if (current == null) {
                    // Answered here, from the notification, or timed out.
                    LaunchedEffect(Unit) { finish() }
                } else {
                    FallCheckContent(
                        deadline = current.deadlineEpochMillis,
                        simulated = current.candidate.simulated,
                        onOkay = coordinator::onImOkay,
                        onHelp = coordinator::onNeedHelp,
                    )
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, FallCheckActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun FallCheckContent(
    deadline: Long,
    simulated: Boolean,
    onOkay: () -> Unit,
    onHelp: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val secondsLeft = ((deadline - now + 999) / 1000).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8FC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (simulated) {
            Text(
                text = "TEST - simulated fall",
                color = Color(0xFF7C4DDB),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = "Possible fall detected",
            color = Color(0xFF1B2A41),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Are you okay?",
            color = Color(0xFF1B2A41),
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "If you do not answer in $secondsLeft seconds,\nyour caregiver will be told.",
            color = Color(0xFF5B6B82),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onOkay,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22A55B)),
        ) {
            Text("I'M OKAY", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onHelp,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D)),
        ) {
            Text("NEED HELP", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The "I'm okay" / "Need help" buttons on the prompt notification. */
@AndroidEntryPoint
class FallResponseReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: FallAlertCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OKAY -> coordinator.onImOkay()
            ACTION_HELP -> coordinator.onNeedHelp()
        }
    }

    companion object {
        const val ACTION_OKAY = "com.sahaaya.fall.OKAY"
        const val ACTION_HELP = "com.sahaaya.fall.HELP"
    }
}
