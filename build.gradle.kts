plugins {
    alias(libs.plugins.android.application) apply false
    // Pins the Kotlin version used by the Kotlin support built into AGP.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // `./gradlew versionCatalogUpdate` updates all versions in gradle/libs.versions.toml,
    // like `go get -u ./...`; with `--interactive` it only lists them first.
    alias(libs.plugins.versions)
    alias(libs.plugins.version.catalog.update)
}

/** Alpha, beta and release candidates, which the update leaves out. */
fun isUnstable(version: String): Boolean =
    Regex("""(?i)[.-](alpha|beta|rc|cr|m|eap|preview|dev|snapshot)[.\d-]*$""").containsMatchIn(version)

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf { isUnstable(candidate.version) && !isUnstable(currentVersion) }
}

versionCatalogUpdate {
    // Keeps the order of gradle/libs.versions.toml, which groups related entries.
    sortByKey = false
}
