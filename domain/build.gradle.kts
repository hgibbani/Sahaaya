plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :domain is pure Kotlin. It knows nothing about Android, Firebase or Compose.
// Everything the app "means" lives here: models, repository contracts, use cases.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core"))

    // javax.inject only - constructor annotations, no Android or Dagger runtime.
    // Hilt in the Android modules understands @Inject on these constructors.
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
