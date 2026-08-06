package com.sahaaya.feature.monitoring.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.feature.monitoring.fall.FallCountdownScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * The screen that appears the instant a fall is detected.
 *
 * Its own activity, not a route in the main graph, because it has to appear
 * over the lock screen while the phone is in a pocket and the app is not
 * running. A patient who has just fallen cannot be expected to unlock the phone
 * and navigate to a cancel button.
 */
@AndroidEntryPoint
class FallConfirmationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        setContent {
            SahaayaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FallCountdownScreen(
                        eventId = eventId,
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"

        fun launch(context: Context, eventId: String) {
            val intent = Intent(context, FallConfirmationActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(intent)
        }
    }
}
