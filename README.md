# WoW Mobile Auto-Fishing Automation

An Android application designed to automate fishing in mobile clients and remote streaming sessions for World of Warcraft (such as WoW Forever).

---

## Key Features

- **Non-Root Accessibility Automation**: Dispatches touch events via Android's native `AccessibilityService` (`dispatchGesture`), interacting with the game without requiring device root.
- **Audio Peak Detection**: Real-time microphone listening via `AudioRecord` calculates RMS amplitude and triggers when the splash sound of a biting fish exceeds the calibrated sensitivity threshold.
- **Dual Visual Target Bindings**:
  - **Marker 1 (Cast)**: Draggable on-screen target positioned over the Fishing spell or action button.
  - **Marker 2 (Reel)**: Draggable on-screen target positioned over the Bobber or Interact action button.
- **Humanized Randomization**: Configurable randomized delay between reeling in the catch and recasting the line to simulate natural player timing.
- **Floating Overlay Dashboard**: Control playback, monitor audio volume levels in real-time, and tweak sensitivity and delays without leaving the game.
- **Cast Timeout Recovery**: Recasts automatically if no bite occurs within a specified time limit (e.g., 22 seconds).

---

## Architecture Overview

```
com.notabot.wowfishing/
├── MainActivity.kt                      # Permission checks and initial configuration
├── audio/
│   └── AudioSplashDetector.kt          # Continuous PCM audio stream processor and RMS threshold trigger
├── model/
│   └── BotConfig.kt                    # Configuration parameters and persistent SharedPreferences
└── service/
    ├── FishingAccessibilityService.kt  # Dispatches touch gestures to screen coordinates
    └── FishingOverlayService.kt        # Foreground service managing floating overlay, markers, and state machine
```

### Automation Flow

1. **Cast**: The bot taps the Cast target coordinates (Marker 1).
2. **Audio Arming**: Audio input is muted for 2.5 seconds (grace period) to avoid false triggers caused by the cast swing or initial water splash.
3. **Detection**: The bot enters `WAITING_FOR_SPLASH` state and continuously measures RMS audio levels.
4. **Trigger**: When audio level exceeds the user threshold (fish bite splash):
   - The bot taps the Reel target coordinates (Marker 2).
   - Calculates a random delay between `minRecastDelaySec` and `maxRecastDelaySec`.
5. **Recast**: After the delay, the line is recast (Marker 1) and the cycle repeats.
6. **Safety Timeout**: If no bite is heard before the timeout expires (e.g., 22s), the line is recast automatically.

---

## Permissions Required

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**: Required for the floating control pill and draggable coordinate markers.
2. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**: Required to simulate touches on external applications.
3. **Record Audio (`RECORD_AUDIO`)**: Required to measure sound amplitude and detect the bobber splash.
4. **Foreground Service (`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MICROPHONE`)**: Ensures Android does not kill the process while the game is running in the foreground.

---

## How to Build the APK

### Prerequisites
- JDK 17 or higher
- Android SDK (API 34) or [Android Studio Hedgehog / Iguana / Jellyfish or newer](https://developer.android.com/studio)

### Option A: Using Android Studio (Recommended)
1. Open Android Studio.
2. Select **Open** and select this directory (`notABot`).
3. Let Gradle sync dependencies.
4. Navigate to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. The generated APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Command Line (Gradle)
Run the Gradle wrapper:

```bash
# On Linux / macOS:
./gradlew assembleDebug

# On Windows:
.\gradlew.bat assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Usage Guide

1. Install and launch the APK on your Android device.
2. Grant the four required permissions displayed on the setup screen:
   - Overlay permission
   - Accessibility service (toggle on in System Settings > Accessibility > WoW Fishing Automation Service)
   - Microphone permission
   - Notification permission
3. Adjust the default **Sound Threshold** (typically 35–50) and **Recast Delay**.
4. Tap **Launch Floating Overlay**.
5. Switch to your World of Warcraft mobile app or stream:
   - Drag **Marker 1 (CAST)** directly over your Fishing action bar button.
   - Drag **Marker 2 (REEL)** directly over your Bobber / Interact button.
6. Make sure your phone's media volume is audible so the microphone can detect the splash sound.
7. Tap the **Play** button on the floating bar to start automation.
