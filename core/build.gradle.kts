plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core is a pure Kotlin/JVM module on purpose. Nothing here may touch the
// Android framework, which keeps it unit-testable on the JVM and reusable by
// any future non-Android surface (Cloud Functions, admin tooling, KMP).
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
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
