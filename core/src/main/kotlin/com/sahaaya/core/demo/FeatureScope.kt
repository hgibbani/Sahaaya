package com.sahaaya.core.demo

/**
 * Which monitoring features are active in this build.
 *
 * This exists so that a feature can be *switched off* without being *deleted*.
 * The fall-detection pipeline - `FallDetectionEngine`, `FallSensorMonitor`,
 * `MonitoringService`, `MonitoringControllerImpl` and their tests - is complete
 * and stays in the source tree untouched. It is simply not started while the
 * flag below is `false`.
 *
 * The reason is demonstration stability rather than any defect. Continuous
 * accelerometer monitoring can raise a fall alert from a phone being set down
 * on a table, and an unexpected alarm in the middle of a presentation is worse
 * than the feature is valuable at that moment. Inactivity monitoring is off for
 * the same reason: it fires after a quiet period, which is exactly what a phone
 * sitting on a desk during a demo looks like.
 *
 * Turning either back on is a one-line change here. Nothing else needs editing:
 * the use cases, the service, the settings screen and the caregiver timeline
 * all already handle them.
 *
 * Note that this does **not** disable the Developer Mode simulations. Those are
 * deliberate button presses, not background detection, and they still run the
 * real pipeline end to end.
 */
object FeatureScope {

    /**
     * `false` for the mentor demonstration: the accelerometer is never
     * registered and the foreground monitoring service is never started.
     */
    const val FALL_DETECTION_ACTIVE: Boolean = true

    /** `false` for the same reason - a still phone is not an emergency here. */
    const val INACTIVITY_DETECTION_ACTIVE: Boolean = false

    /**
     * Safe-zone geofencing stays available, because it is opt-in: it does
     * nothing at all until the patient sets a safe zone on the settings screen.
     */
    const val GEOFENCE_ACTIVE: Boolean = true

    /**
     * How often the patient's phone takes a fix while watching a safe zone.
     *
     * **30 s is a demonstration value.** [PRODUCTION_LOCATION_INTERVAL_MILLIS]
     * is what a real deployment should use: a crossing that takes four minutes
     * to report is fine when the alternative is a phone that dies by lunchtime,
     * and a wandering patient is not usually 200 m away within sixty seconds.
     *
     * Shortening the interval is safe precisely because it is *not* the thing
     * preventing alert spam. Two independent filters do that, and both are
     * untouched by this value:
     *
     * - the accuracy gate, which discards fixes worse than 50 m, and
     * - the two-fix confirmation plus 25 m hysteresis band in
     *   [com.sahaaya.domain.usecase.monitoring.SafeZoneEvaluator].
     *
     * A faster interval only means those filters reach their verdict sooner. It
     * still takes two consecutive confirmed fixes to change state, and still
     * exactly one alert per inside-to-outside crossing.
     *
     * The real cost is battery: at 30 s the radio wakes four times as often.
     * That is acceptable for a demonstration and not for daily wear.
     */
    const val LOCATION_INTERVAL_MILLIS: Long = 30_000L

    /** What [LOCATION_INTERVAL_MILLIS] should be set to outside a demo. */
    const val PRODUCTION_LOCATION_INTERVAL_MILLIS: Long = 2 * 60 * 1000L
}
