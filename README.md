![BabaG Clear](docs/branding/babag-clear-logo.png)

# BabaG Clear

BabaG Clear is a Kotlin + Jetpack Compose Android app that adds an accessibility-powered clear button over the Android Recents screen.

It is designed for a very specific user experience:

- Show a branded circular `CLEAR RECENTS` control only while Recents is open.
- Swipe away visible recent tasks as fast as the launcher allows.
- Return to Home when the clear pass completes.
- Stay out of the lock screen, notification shade, launcher workspace, and app drawer.

## Why this app exists

Android does not give normal third-party apps direct control over the system Recents UI. Because of that, BabaG Clear uses an `AccessibilityService` plus gesture automation as a workaround.

That means:

- behavior can vary by launcher and device
- the heuristics may need tuning for different phones
- the accessibility logic is the most important part of the codebase

## Branding

The reusable project branding image lives here:

- `docs/branding/babag-clear-logo.png`

The same asset is also used for the launcher icon flow.

## Project structure

```text
app/src/main/java/com/example/autoclear/
├── MainActivity.kt
├── RecentsAccessibilityService.kt
├── SettingsRepository.kt
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

## Core architecture

### `MainActivity.kt`

- Hosts the Compose UI.
- Lets the user enable or disable the overlay behavior.
- Shows whether the accessibility service is currently enabled.

### `RecentsAccessibilityService.kt`

- Watches accessibility events.
- Decides when the overlay should appear.
- Detects whether the current surface still looks like Recents.
- Clears tasks using either `ACTION_DISMISS` or a fallback swipe gesture.
- Hides itself aggressively when the UI is no longer a valid Recents surface.

### `SettingsRepository.kt`

- Stores the simple on/off feature flag shared by the activity and the service.

## How the clear flow works

1. The service sees a Recents-specific accessibility signal.
2. It renders a circular BabaG overlay on the right edge.
3. Tapping the overlay starts a clear pass.
4. The service first tries to click a launcher-provided `Clear all` button if one exists.
5. If that button is missing, it tries to dismiss or swipe away the biggest visible task card.
6. When Recents markers disappear, the clear pass stops and returns Home.

## Setup

### Requirements

- Android Studio
- A physical Android device or emulator
- Accessibility access enabled for BabaG Clear on the target device

### Open the project

1. Open the repo folder in Android Studio.
2. Let Gradle sync.
3. Build with `assembleDebug` or run directly from Android Studio.

### Enable the service

1. Install the app.
2. Open `BabaG Clear`.
3. Tap `Open accessibility settings`.
4. Enable `BabaG Clear Recents Helper`.

## Build commands

From the repo root:

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tuning guide for developers

The main tuning points are in `RecentsAccessibilityService.kt`.

### If the overlay appears in the wrong places

Look at these hint lists:

- `BLOCKED_SURFACE_HINTS`
- `LAUNCHER_SURFACE_HINTS`
- `RECENTS_CLASS_HINTS`
- `RECENTS_ACTION_HINTS`
- `RECENTS_CONTAINER_HINTS`

### If the clear gesture is too slow or too fast

Tune these constants:

- `PASS_DELAY_MS`
- `MAX_CLEAR_PASSES`
- swipe duration inside `dispatchVerticalSwipe(...)`

### If the swipe path is wrong

Tune:

- `swipeNodeUp(...)`

That method controls the start and end coordinates for the fallback upward swipe.

### If the overlay refuses to hide

Inspect:

- `overlayWatchdog`
- `shouldShowOverlay(...)`
- `resetClearState(...)`
- `isLauncherSurface(...)`

### If the app behaves differently on another launcher

Expect to tune:

- task detection in `findLargestTaskNode(...)`
- surface heuristics in `hasExplicitRecentsMarkers(...)`
- launcher package coverage in `LAUNCHER_PACKAGES`

## Known limitations

- This app does not integrate into the real system Recents UI.
- It relies on accessibility events and gesture automation.
- OEM launchers can change class names, view ids, and task layouts at any time.
- Some devices may need device-specific heuristic tuning.

## Publishing notes

Before publishing to GitHub, make sure:

- the latest APK is not committed unless you intentionally want binary releases in-repo
- screenshots are added if you want a stronger project page
- any device-specific debugging logs are removed

## License

Add the license you want before publishing.
