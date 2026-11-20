# PhotoLock

PhotoLock is a native Android app that displays a selected photo in a fullscreen overlay and blocks touch input. To unlock the controls, tap the screen five times in succession.

The app is built with Kotlin and Android SDK APIs, with no external Android UI framework.

## Features

- Selects a photo from the device image picker
- Stores the selected photo locally as `photo.jpg`
- Displays the photo in a fullscreen system overlay
- Locks out touch input while the overlay is active
- Unlocks after five taps within 2.5 seconds
- Supports zoom, pan, and rotation after unlocking
- Uses Material 3-inspired colors, typography, shapes, and spring animations
- Supports dynamic system colors on Android 12+
- Includes a foreground service notification
- Builds and signs release APKs through GitHub Actions

## Requirements

- Android 8.0+ (API 28 or newer)
- Android Studio with JDK 17
- Android device or emulator

## Permissions

PhotoLock requests:

- **Display over other apps** — required for the fullscreen overlay
- **Foreground services** — keeps the overlay active
- **Notifications** — shows the active-lock notification on Android 13+

The user must manually grant the overlay permission when prompted.

## Running locally

Open the repository in Android Studio and run the `app` module.

To build from the command line, install JDK 17 and Android SDK 34, then run:

```bash
gradle assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A Gradle wrapper is not currently included. Gradle 8.9 is used by the GitHub Actions workflow.

## Usage

1. Launch **PhotoLock**.
2. Tap the photo card or the **Фото** button.
3. Select an image.
4. Tap **Заблокировать**.
5. Grant the overlay permission if prompted.
6. The app will display the photo fullscreen and block touches.
7. Tap the screen five times to reveal the controls.

While unlocked, the photo supports:

- Pinch-to-zoom
- One-finger movement
- Two-finger rotation
- Changing the photo
- Closing the overlay
- Returning the overlay to locked mode

## Building a signed release

Release signing uses environment variables. Signing values are never stored in the source code.

Required environment variables:

```text
KEYSTORE_FILE
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Build the release APK:

```bash
gradle assembleRelease --no-daemon
```

A valid `KEYSTORE_FILE` must be available at the configured path.

## GitHub Actions release build

`.github/workflows/build.yml` builds `app/build/outputs/apk/release/*.apk` when changes are pushed to `main` or `master`, or when the workflow is run manually.

Required GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Create `KEYSTORE_BASE64` from a release keystore:

```bash
base64 -w 0 release.jks
```

Pushing a `v*` tag publishes the signed APK to a GitHub Release.

## Project structure

```text
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/wafuv/photolock/
      MainActivity.kt       Main UI and photo selection
      OverlayService.kt     Fullscreen overlay and touch locking
      M3.kt                 Material 3 colors, typography, and controls
    res/
      drawable/             Image and lock icons
      values/               Strings and theme resources
      values-night/         Night theme resources
.github/workflows/
  build.yml               Signed APK build and release workflow
build.gradle.kts
settings.gradle.kts
gradle.properties
```

## Technical details

- Namespace and application ID: `com.wafuv.photolock`
- Kotlin: 1.9.24
- Android Gradle Plugin: 8.5.2
- Compile and target SDK: 34
- Minimum SDK: 28
- JVM target: 17
- AndroidX is disabled in `gradle.properties`
- The app uses platform `Activity`, `Service`, `WindowManager`, and native Android widgets
- Selected images are resized to a maximum dimension of 2560 pixels before JPEG compression
- The overlay uses `TYPE_APPLICATION_OVERLAY`
- The service is foreground type `specialUse`
- There is currently no Gradle wrapper or test suite

## Security note

Keep release keystores, passwords, aliases, and signing secrets outside the repository. The provided `.gitignore` excludes common keystore and Gradle local files.

## License

No license is currently specified.
