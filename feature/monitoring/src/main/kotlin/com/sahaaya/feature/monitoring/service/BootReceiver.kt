package com.sahaaya.feature.monitoring.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.MonitoringController
import com.sahaaya.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts monitoring after the phone reboots.
 *
 * Without this, a patient whose phone restarted overnight - an update, a flat
 * battery - wakes up unmonitored and nobody knows. The failure is silent, which
 * is what makes it dangerous: the app looks fine, the caregiver assumes cover,
 * and there is none.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var monitoringController: MonitoringController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val patientId = authRepository.currentUserId() ?: return@launch
                when (val result = settingsRepository.getSettings(patientId)) {
                    is Outcome.Success -> monitoringController.applySettings(result.data)
                    is Outcome.Failure -> Log.w(
                        TAG,
                        "Could not restore monitoring after boot: ${result.error.message}",
                    )
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to restart monitoring after boot", throwable)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SahaayaBoot"
    }
}
