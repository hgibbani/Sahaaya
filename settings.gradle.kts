pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sahaaya"

// --- Application ---------------------------------------------------------
include(":app")

// --- Architectural layers ------------------------------------------------
include(":core")       // technical foundation: Outcome, AppError, dispatchers, validation
include(":common")     // shared UI: Material 3 design system, accessibility, components
include(":domain")     // pure business layer: models, repository contracts, use cases
include(":data")       // repository implementations, DTO mapping, DI bindings
include(":firebase")   // Firebase infrastructure: Auth, Firestore, Cloud Messaging
include(":sensor")     // accelerometer, geofencing, activity recognition, location

// --- Features ------------------------------------------------------------
include(":feature:auth")
include(":feature:dashboard")
include(":feature:profile")
include(":feature:pairing")
include(":feature:monitoring")   // fall countdown, SOS, settings, demo mode
include(":feature:medication")  // medicines, reminders, dose history
