package com.sahaaya.feature.monitoring.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.domain.model.PatientLocation
import com.sahaaya.domain.model.TrackingState
import com.sahaaya.domain.usecase.monitoring.ObserveMyTrackingStateUseCase
import com.sahaaya.domain.usecase.profile.ObservePatientLocationUseCase
import com.sahaaya.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class PatientTrackingUiState(
    val tracking: TrackingState = TrackingState.INACTIVE,
    val location: PatientLocation? = null,
)

/**
 * What the patient is told about being tracked.
 *
 * Read-only by design. There is no method on this ViewModel that changes
 * tracking state, because there is no patient-facing way to stop it - and the
 * Firestore rule would refuse the write even if somebody added one here.
 */
@HiltViewModel
class PatientTrackingViewModel @Inject constructor(
    authRepository: AuthRepository,
    observeMyTrackingState: ObserveMyTrackingStateUseCase,
    observePatientLocation: ObservePatientLocationUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PatientTrackingUiState> = combine(
        observeMyTrackingState(),
        flow { emit(authRepository.currentUserId()) }
            .flatMapLatest { uid ->
                if (uid == null) flow { emit(null) } else observePatientLocation(uid)
            },
    ) { tracking, location ->
        PatientTrackingUiState(tracking = tracking, location = location)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PatientTrackingUiState(),
    )
}

/**
 * "Tracking Active" - the patient's own view.
 *
 * Deliberately has no stop control. That is the point of the feature and it is
 * stated plainly on the screen rather than left for the patient to discover by
 * looking for a button that is not there: being watched without being told, or
 * being told in a way that implies you could opt out, would both be worse than
 * saying it directly.
 */
@Composable
fun PatientTrackingStatusScreen(
    onNavigateBack: () -> Unit,
    viewModel: PatientTrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active = state.tracking.active

    SahaayaScreen(
        title = if (active) "Tracking Active" else "Tracking Inactive",
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(if (active) PatientPalette.GoBg else PatientPalette.OffBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(if (active) PatientPalette.Go else PatientPalette.Off),
                )
            }

            Text(
                text = if (active) "Tracking Active" else "Tracking Inactive",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = PatientPalette.Ink,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (active) {
                    "Your caregiver is monitoring your location for your safety."
                } else {
                    "Your caregiver is not monitoring your location right now."
                },
                fontSize = 19.sp,
                color = PatientPalette.Muted,
                textAlign = TextAlign.Center,
            )

            if (active) {
                InfoCard(
                    background = PatientPalette.InfoBg,
                    text = "Tracking was started by your caregiver. You cannot stop " +
                        "tracking from this device.",
                )

                state.location?.let { location ->
                    InfoCard(
                        background = PatientPalette.Strip,
                        text = "Last location update: ${relativeTime(location)}",
                    )
                }

                InfoCard(
                    background = PatientPalette.GoBg,
                    text = "Only your caregiver can stop tracking.",
                    bold = true,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(
    background: Color,
    text: String,
    bold: Boolean = false,
) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = PatientPalette.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(16.dp),
    )
}

private object PatientPalette {
    val Ink = Color(0xFF1B2A41)
    val Muted = Color(0xFF5B6B82)
    val Go = Color(0xFF22A55B)
    val GoBg = Color(0xFFDDF6E7)
    val Off = Color(0xFF94A3B8)
    val OffBg = Color(0xFFECEFF4)
    val Strip = Color(0xFFEEF3FB)
    val InfoBg = Color(0xFFE8F1FE)
}
