# Game Turbo M12 (Kotlin, no dependencies, minSdk 29)
Open the folder in Android Studio (Hedgehog+), let it sync, run on the phone (enable USB debugging) or Build > Build APK.

## First-run setup (in-app "SETUP" list walks you through it)
1. Display over other apps – required for the sidebar.
2. Accessibility > Game Turbo M12 – detects the foreground game, enables screenshot / recents / quick-settings buttons. It does not read screen content.
3. Optional: Do Not Disturb access, Modify system settings (brightness), ignore battery optimization.
4. Optional real FPS: `adb shell pm grant com.gameturbo.m12 android.permission.DUMP`
5. Samsung "Device care / Game Launcher" or battery "Sleeping apps" may kill the service: set Game Turbo to Unrestricted battery.

## Usage
Turn on the sidebar switch, open a game: a thin green handle appears on the edge. Swipe inward (or tap) to open the glass panel; swipe the header toward the edge, tap outside, or press X to close. Drag the handle up/down to reposition.

## What is real, and what is limited
- RAM/storage/battery/temp: Android APIs. CPU temp and clock: read from /sys only when One UI allows it, else "restricted".
- Ping: real TCP handshake to 1.1.1.1:443.
- FPS: real, from SurfaceFlinger present timestamps of the foreground game, only after the one-time ADB DUMP grant. Otherwise no number is shown.
- Boost: killBackgroundProcesses() on cached non-system apps; RAM gain is measured, may be 0, and Android may restart apps.
- Screenshot: accessibility global action. Screen recording: opens Quick Settings so you can tap Samsung's Screen recorder (a non-system app cannot start it silently).
- Touch sensitivity: Samsung's setting is not writable by apps; the button opens Display settings.
- Touch lock: full-screen overlay that swallows game touches; hold the chip to unlock. Nav gestures/power button still work.
- Some games or anti-cheat may hide or block overlays; Android 12+ glass blur appears only on devices with window blur enabled.
- Not built/tested in this environment: please report compile errors.
