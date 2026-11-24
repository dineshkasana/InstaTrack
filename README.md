# InstaTrack (Android starter project)

## What it does
- Add public Instagram usernames.
- Attempts to read follower/following/post counts from publicly served page metadata.
- Stores successful snapshots locally (up to 500 per profile).
- Runs a WorkManager check every 4 hours when network is available.
- Notifies when a previously available count changes.

## Important limitations
Instagram does not provide an unrestricted official API for arbitrary public profiles. Public profile HTML is not a stable API: Instagram may omit counts, require a login, rate-limit requests, or change its markup. When a count is unavailable or a request is blocked, this app records nothing and preserves the last successful snapshot. It does not bypass login, CAPTCHA, privacy settings, or anti-bot controls. Following counts may be unavailable.
Android WorkManager periodic jobs are inexact. A 4-hour interval means "not more frequently than about every 4 hours"; battery optimization and Doze can delay execution.

## Build on Windows (beginner steps)
1. Install Android Studio from https://developer.android.com/studio
2. Extract the InstaTrack.zip file.
3. In Android Studio choose **Open**, then select the extracted `InstaTrack` folder (the folder containing `settings.gradle.kts`).
4. Allow Gradle sync to finish. If asked, install Android SDK Platform 35 and accept the license.
5. Connect your Android phone with USB debugging enabled, or create an emulator.
6. Press the green **Run** triangle. Android Studio will build and install the app.
7. On first launch, allow notifications. Tap **Add Instagram username**, enter the public username without @, then tap **Check now**.

## Export / history
This starter UI shows the latest successful sample and the number of saved samples. The records are stored locally in app preferences. CSV export and detailed charts are not included in this first starter build.

## Privacy
No Instagram password is requested or stored. Data stays on the device. Removing a profile also removes its local history.
