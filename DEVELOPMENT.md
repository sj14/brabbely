# Development

How to build, test and release Brabbely. For what the app does, see the [README](README.md).

Built with Kotlin, Jetpack Compose (Material 3) and Media3. It has no Google Play Services
dependency and is meant to be distributed through F-Droid.

## Environment

The development environment is a Nix flake. With direnv it loads automatically when you enter
the directory (run `direnv allow` once); without direnv use `nix develop`.

```sh
./gradlew assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected phone or a running emulator
./gradlew installPreview         # optimized like a release, signed with the debug key
```

The debug build is much slower than a release, mostly because of Jetpack Compose. To judge
how smooth the app is, use the preview build. It installs as `io.github.sj14.brabbely.preview`
next to the debug build.

To test on a phone, enable USB debugging in the developer options of the phone and connect it.
`adb devices` should list it.

To use the emulator, create a virtual device once and start it:

```sh
avdmanager create avd --name pixel --package "system-images;android-36;default;x86_64" --device pixel_8
emulator -avd pixel
```

## Updating dependencies

```sh
./gradlew versionCatalogUpdate                # newest stable versions into gradle/libs.versions.toml
./gradlew versionCatalogUpdate --interactive  # only list them, apply with --apply
```

A version marked `# @pin` in `gradle/libs.versions.toml` is kept; the comment says why. The JDK
and the Android SDK come from the Nix flake, which follows the nixpkgs revision of the system
flake, so a new Android platform needs a newer nixpkgs there first.

## Tests

```sh
./gradlew testDebugUnitTest      # unit tests on the computer
```

Every change of the database schema needs a new version in `AppDatabase` and a migration. The
schemas of all versions are kept in `app/schemas`; Room checks auto migrations against them when
building. To test migrations, add `androidx.room:room-testing` with `androidx.test:runner` and
`androidx.test.ext:junit` as `androidTestImplementation`, set `testInstrumentationRunner` to
`androidx.test.runner.AndroidJUnitRunner` and use `MigrationTestHelper`. Two things to know:

- room-testing needs a newer kotlinx-serialization than the app pulls in, and the test APK has to
  use the versions of the app. Without a constraint that raises it for the app
  (`constraints { implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:…") }`), the
  tests fail with an `AbstractMethodError` in `FieldBundle$$serializer`.
- Instrumented tests install a debug test APK. With a phone connected too, run them with
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.

Android's own backup (to the Google account, Seedvault on GrapheneOS, or the transfer to a new
device) gets the app's backup from `SystemBackupAgent`. On the emulator it can be tried with
Android's local transport:

```sh
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow io.github.sj14.brabbely.preview
# uninstall and install again: the backup is restored at the next start of the app
```

## Releases

Pushing a version tag builds a signed APK on GitHub Actions and attaches it to the GitHub
release of that tag (`.github/workflows/release.yml`).

One-time setup of the signing key:

1. Create the key inside the dev shell, and keep the file and passwords somewhere safe. Updates
   only install over an existing app when they are signed with the same key.
   ```sh
   keytool -genkeypair -v -keystore brabbely-release.jks -alias brabbely \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these repository secrets under Settings → Secrets and variables → Actions:
   - `KEYSTORE_BASE64`: the output of `base64 -w0 brabbely-release.jks`
   - `KEYSTORE_PASSWORD`: the keystore password
   - `KEY_ALIAS`: `brabbely`
   - `KEY_PASSWORD`: the key password (the same as the keystore password, unless you chose another one)

To publish a release, raise `versionCode` and `versionName` in `app/build.gradle.kts`, write
what changed to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and the German
version to `de-DE/changelogs/<versionCode>.txt` and commit. Check that the release is ready,
then tag and push:

```sh
./scripts/check-release.sh v0.1.0
git tag v0.1.0
git push origin v0.1.0
```

The version stays written in `app/build.gradle.kts` instead of coming from the tag, because
F-Droid reads it from there to find new releases. The release workflow runs the same check and
stops when the tag does not match `versionName`, `versionCode` is not higher than at the
previous tag or a changelog is missing. It also runs the unit tests and stops when the build
wrote a Room schema to `app/schemas` that is not committed.

F-Droid builds the app from source itself and signs it with its own key, so it does not use
the APKs from GitHub. It takes the app's title, descriptions, icon, screenshots and changelogs
from `fastlane/metadata/android/`; screenshots go to `en-US/images/phoneScreenshots/` and are
shown for German too.
