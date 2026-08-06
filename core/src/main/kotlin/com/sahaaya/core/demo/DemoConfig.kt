package com.sahaaya.core.demo

/**
 * The single switch that decides whether Sahaaya runs against Firebase or
 * against the in-memory demo backend.
 *
 * This exists because the Firebase project the app is registered to is not
 * currently billable, so every Firestore read and write fails. Rather than
 * removing the Firebase layer - which is the real implementation and stays in
 * the source tree untouched - the Hilt bindings in `:data`, `:sensor` and
 * `:feature:monitoring` consult this flag and pick the demo implementation
 * instead.
 *
 * Nothing above the repository contracts knows which side won. The use cases,
 * ViewModels and screens are the same code in both modes, which is the whole
 * point of the layering: the demo exercises the real pipeline, not a mock of it.
 *
 * Flip [ENABLED] to `false` and the app is back on Firebase with no other
 * change anywhere.
 */
object DemoConfig {

    /** `true` = local demo repositories. `false` = Firebase / Firestore. */
    const val ENABLED: Boolean = true

    /** Shown in the badge so nobody watching a demo mistakes it for live data. */
    const val BADGE_LABEL: String = "DEMO MODE"

    /**
     * Seeded accounts, so a demo can be given without registering first.
     * Registration still works and creates further accounts alongside these.
     */
    const val PATIENT_EMAIL: String = "asha@demo.in"
    const val CAREGIVER_EMAIL: String = "ravi@demo.in"
    const val SEED_PASSWORD: String = "demo1234"
}
