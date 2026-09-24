package com.sahaaya.core.demo

/**
 * The single switch that chooses which of Sahaaya's two run modes is active.
 *
 * **LIVE MODE ([ENABLED] = `false`) - the two-device demonstration.**
 * Repositories bind to Firebase Auth and Cloud Firestore. Two physically
 * separate devices sign in as the patient and the caregiver, and an event
 * raised on the patient's phone travels over the network and lands on the
 * caregiver's device. This is the mode the project is actually about, and the
 * only mode in which cross-device alerting exists at all.
 *
 * **LOCAL DEMO MODE ([ENABLED] = `true`) - single device, no network.**
 * Repositories bind to an in-memory store. Useful for UI work, for developing
 * without a Firebase project, and for showing the screens on one device. It
 * cannot cross devices: `DemoDataStore` is process-local memory, so a second
 * device gets its own empty copy. Nothing raised on one phone can ever appear
 * on another in this mode.
 *
 * Nothing above the repository contracts knows which side won. The use cases,
 * ViewModels and screens are the same code in both modes, which is the whole
 * point of the layering: the demo exercises the real pipeline, not a mock of it.
 *
 * LIVE MODE requires, in the Firebase console for the project named in
 * `app/google-services.json`: Email/Password sign-in enabled, a Cloud Firestore
 * database created, and `firebase/firestore.rules` deployed. See
 * docs/OFFLINE_DEMO_MODE.md.
 */
object DemoConfig {

    /** `true` = local in-memory repositories. `false` = Firebase / Firestore. */
    const val ENABLED: Boolean = false

    /** Shown in the badge so nobody watching a demo mistakes it for live data. */
    const val BADGE_LABEL: String = "DEMO MODE"

    /**
     * Shown in LIVE mode so it is equally obvious when data *is* real and is
     * crossing the network. Silence in one mode and a badge in the other would
     * leave a viewer guessing which they were looking at.
     */
    const val LIVE_BADGE_LABEL: String = "LIVE"

    /**
     * Seeded accounts for LOCAL DEMO MODE only.
     *
     * In LIVE MODE these do not exist until they are registered against Firebase
     * Auth, because the accounts live in the Firebase project rather than in a
     * seeded map. Register them once from the app and they persist.
     */
    const val PATIENT_EMAIL: String = "asha@demo.in"
    const val CAREGIVER_EMAIL: String = "ravi@demo.in"
    const val SEED_PASSWORD: String = "demo1234"
}
