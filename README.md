# FaceMatch SDK

On-device face verification for Android, built for attendance and identity-check use cases. Liveness detection, anti-spoofing, and 1:1 face matching run **entirely on the device** — no network calls, no server-side matching.

The repo contains two modules:

| Module | Description |
| --- | --- |
| **`facematch`** | The reusable face-verification SDK (Android library, published via JitPack). |
| **`app`** | A demo app that launches the SDK and displays the outcome. |

## Features

- **1:1 face matching** — (`mobilefacenet.tflite`) extracts a 192-dimensional embedding; the live face is compared against a reference embedding via cosine similarity.
- **Liveness detection** — blink detection using ML Kit face landmarks, with a time-based hold window so a single confirmed blink stays valid for a few seconds.
- **Anti-spoofing** — MiniFASNetV2 (`2.7_80x80_MiniFASNetV2.tflite`) rejects printed photos, screen replays, and mask attacks.
- **Camera + UI** — CameraX preview with a Jetpack Compose overlay, guideline framing, Lottie success/scan animations, and configurable retry behavior.
- **Simple integration** — exposed as an `ActivityResultContract`; you get back a typed `Matched` / `Rejected` / `Cancelled` outcome.

## Requirements

- Android **minSdk 24**, target/compile SDK 36
- Kotlin 2.0, Jetpack Compose
- Java 17
- A device/emulator with a front camera and the `CAMERA` permission granted

## Installation

The SDK is distributed through [JitPack](https://jitpack.io).

**1. Add the JitPack repository** (in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Add the dependency** (in your app module's `build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.github.cdcountrydelight:facematch:<version>")
}
```

The `.tflite` models ship inside the library's assets, so no extra setup is required.

## Usage

The SDK is launched via an `ActivityResultContract`. You provide a **reference embedding** (base64) — the enrolled face you want to verify against — and receive a `FaceMatchOutcome`.

```kotlin
val launcher = rememberLauncherForActivityResult(FaceMatchContract()) { outcome ->
    when (outcome) {
        is FaceMatchOutcome.Matched   -> { /* outcome.croppedFacePath */ }
        is FaceMatchOutcome.Rejected  -> { /* outcome.croppedFacePath (nullable) */ }
        FaceMatchOutcome.Cancelled    -> { /* user backed out */ }
    }
}

// Ensure CAMERA permission is granted first, then:
launcher.launch(
    FaceMatchInput(
        referenceEmbeddingBase64 = referenceEmbedding,
        config = FaceMatchConfig(maxRetries = 1),
    )
)
```

> **Note:** the SDK does not request the `CAMERA` permission for you — request it before launching.

### Outcomes

```kotlin
sealed interface FaceMatchOutcome {
    data class Matched(val croppedFacePath: String) : FaceMatchOutcome   // verified; cropped face saved to disk
    data class Rejected(val croppedFacePath: String?) : FaceMatchOutcome // a face was seen but did not pass
    data object Cancelled : FaceMatchOutcome                             // dismissed / no result
}
```

## Configuration

Tune matching, frame, and spoof behavior via `FaceMatchConfig` (defaults shown):

```kotlin
FaceMatchConfig(
    matchThreshold = 72f,            // cosine-similarity % required to accept a match
    triggerFrames = 8,               // consecutive matching frames before accepting
    rejectFrames = 20,               // consecutive non-matching frames before rejecting
    skipFrames = 2,                  // frames skipped between analyses (throughput)
    spoofRejectThreshold = 25,       // anti-spoof sensitivity
    spoofEnabled = true,             // toggle anti-spoofing
    maxRetries = Int.MAX_VALUE,      // retries allowed before giving up
)
```

The demo (`app/MainActivity.kt`) ships with a dummy reference embedding, so it will exercise the camera/liveness/spoof flow but will **not** match a real face — replace `REAL_EMBEDDING` with a genuine enrolled embedding to see a `Matched` outcome.
