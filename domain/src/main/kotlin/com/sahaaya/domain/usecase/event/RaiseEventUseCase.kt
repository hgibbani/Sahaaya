package com.sahaaya.domain.usecase.event

import com.sahaaya.core.result.AppError
import com.sahaaya.core.result.Outcome
import com.sahaaya.domain.model.EventType
import com.sahaaya.domain.model.GeoPoint
import com.sahaaya.domain.model.HealthEvent
import com.sahaaya.domain.repository.AuthRepository
import com.sahaaya.domain.repository.EventRepository
import com.sahaaya.domain.repository.LocationRepository
import com.sahaaya.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The single entry point for every alert in Sahaaya.
 *
 * The fall detector, the geofence receiver, the inactivity worker, the SOS
 * button and the missed-dose worker all call this. That is the point: one place
 * attaches the location, one place writes the document, one place decides what
 * the summary says. A second path would eventually diverge, and the divergence
 * would show up as an alert that behaves differently from the others in the
 * exact situation nobody is watching.
 *
 * Location is attached best-effort. An event with no coordinates is still worth
 * far more than no event, so a location failure never fails the alert.
 */
class RaiseEventUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
) {

    data class Params(
        val type: EventType,
        val summary: String,
        val details: Map<String, String> = emptyMap(),
        /** Skip the location lookup for events where it adds nothing. */
        val attachLocation: Boolean = true,
        /** Supplied when the caller already knows it, e.g. a geofence exit point. */
        val knownLocation: GeoPoint? = null,
        val occurredAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend operator fun invoke(params: Params): Outcome<HealthEvent> {
        val patientId = authRepository.currentUserId()
            ?: return Outcome.Failure(AppError.NotAuthenticated())

        val patientName = runCatching {
            profileRepository.observeUser(patientId).first()?.displayName
        }.getOrNull().orEmpty()

        val location = params.knownLocation ?: if (params.attachLocation) {
            when (val result = locationRepository.currentLocation()) {
                is Outcome.Success -> result.data
                is Outcome.Failure -> null
            }
        } else {
            null
        }

        return eventRepository.raiseEvent(
            HealthEvent(
                id = "",
                patientId = patientId,
                patientName = patientName,
                type = params.type,
                occurredAtEpochMillis = params.occurredAtEpochMillis,
                location = location,
                summary = params.summary,
                details = params.details,
            ),
        )
    }
}
