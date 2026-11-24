plugins {
    alias(libs.plugins.android.application) apply false
    // Pins the Kotlin version used by the Kotlin support built into AGP.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
