# Lauscher

A podcast app for Android with audio and video playback, a queue, downloads and a sleep timer
that restarts when you shake your phone.

Built with Kotlin, Jetpack Compose (Material 3) and Media3. It has no Google Play Services
dependency and is meant to be distributed through F-Droid.

## Features

- Search podcasts (iTunes Search API) or add any RSS feed by URL
- Audio and video episodes, video in full screen and picture-in-picture
- Background playback with notification, lock screen and Bluetooth controls
- Queue, playback speed, skip silence, chapters (Podlove Simple Chapters and Podcasting 2.0)
- Downloads, automatic download of new episodes (optionally only on Wi-Fi), deleting played episodes
- Sleep timer: fades out, can stop at the end of an episode, restarts when the phone is shaken
- OPML import and export
- English and German, Material You colors on Android 12+

## Development

The development environment is a Nix flake. With direnv it loads automatically when you enter
the directory (run `direnv allow` once); without direnv use `nix develop`.

```sh
./gradlew assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected phone or a running emulator
```

To test on a phone, enable USB debugging in the developer options of the phone and connect it.
`adb devices` should list it.

To use the emulator, create a virtual device once and start it:

```sh
avdmanager create avd --name pixel --package "system-images;android-36;default;x86_64" --device pixel_8
emulator -avd pixel
```

## Releases

Pushing a version tag builds a signed APK on GitHub Actions and attaches it to the GitHub
release of that tag (`.github/workflows/release.yml`).

One-time setup of the signing key:

1. Create the key inside the dev shell, and keep the file and passwords somewhere safe. Updates
   only install over an existing app when they are signed with the same key.
   ```sh
   keytool -genkeypair -v -keystore lauscher-release.jks -alias lauscher \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these repository secrets under Settings → Secrets and variables → Actions:
   - `KEYSTORE_BASE64`: the output of `base64 -w0 lauscher-release.jks`
   - `KEYSTORE_PASSWORD`: the keystore password
   - `KEY_ALIAS`: `lauscher`
   - `KEY_PASSWORD`: the key password (the same as the keystore password, unless you chose another one)

To publish a release, raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit,
then tag and push:

```sh
git tag v0.1.0
git push origin v0.1.0
```

F-Droid builds the app from source itself and signs it with its own key, so it does not use
the APKs from GitHub.
