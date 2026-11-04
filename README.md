# KidTube Android app

KidTube is an offline-first video library for Android.

## Features
- Import one or multiple videos from the device using Android's document picker.
- Copy imported videos into KidTube's private app storage so playback does not depend on the original file location.
- Save the library between launches.
- Display videos in a two-column thumbnail grid.
- Tap a card to play with Android playback controls.
- Expand the player to fullscreen landscape; use the fullscreen icon or Android Back to return.
- Optional four-digit parent PIN for parent settings.
- Long-press a video card to remove that video.

## Build
Open this folder in Android Studio. Use JDK 17 and Gradle 8.9. Let Android Studio sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Application ID: `com.kidtube.offline`

## Notes
- Imported videos are stored under the app's private `files/videos` directory. Android removes these files if the app is uninstalled or its data is cleared.
- The parent PIN currently protects the parent-settings screen only. It is not an encrypted lock on the Android app itself.
- This first rebuild is intentionally dependency-light and uses Android's built-in `VideoView` and `MediaController` for broad compatibility.
