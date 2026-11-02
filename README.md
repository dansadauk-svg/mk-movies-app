# MK Movies Android GitHub Build Pack v2.10.1

This pack builds the MK Movies Android app from GitHub Actions. It uses a Trusted Web Activity so the app opens `https://mkmovies.com.ng` without the normal browser address bar after Digital Asset Links verification.

## Before building

Install these WordPress plugins first:

1. `mk-movies-pwa-plugin-v1.5-offline-refresh-push-ready.zip`
2. `ksd-movie-push-notifications-v3.9.2-firebase-android-fix.zip`
3. Existing `mk-movies-twa-verification-plugin-v1.0.zip`

Then confirm this URL opens JSON:

```text
https://mkmovies.com.ng/.well-known/assetlinks.json
```

## GitHub setup

1. Create a GitHub repository. A private repository is recommended.
2. Upload all files from this build pack into the repository.
3. Go to **Settings > Secrets and variables > Actions > New repository secret**.
4. If you are using the same repository, keep the three existing repository secrets. Add them only when setting up a new repository:

```text
KB_KEYSTORE_PASSWORD
KB_KEY_ALIAS
KB_KEY_PASSWORD
KB_KEYSTORE_BASE64
```

The workflow uses the existing `KB_KEY_ALIAS` secret. Never upload the `.jks` file or its password file into the GitHub repository. Generate `KB_KEYSTORE_BASE64` from the original KB Movies upload keystore with Windows PowerShell only when creating a new repository:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kb-movies-release-key.jks")) | Set-Clipboard
```

Paste the copied value into the `KB_KEYSTORE_BASE64` GitHub secret. The workflow verifies that its SHA-1 is `59:0D:C2:C2:11:7A:04:5B:8A:03:44:24:2D:A4:C4:32:43:3F:A3:05` before compiling.

## Build

1. Open the GitHub repository.
2. Go to **Actions**.
3. Select **Build MK Movies v2.10.1 Named Resumable Downloads APK/AAB**.
4. Click **Run workflow**.
5. Download the generated APK/AAB from the workflow artifacts.

The output files are named `MK-Movies-v2.10.1-release.apk` and `MK-Movies-v2.10.1-play-console.aab`. Upload only the Play Console AAB to Google Play.

## Included app features

- Full-screen MK Movies WebView app
- Native Firebase Cloud Messaging alerts
- Native movie downloads with a visible Android progress notification
- Movie files use the movie title instead of a generic filename
- Downloads run in a native foreground service with Pause, Resume and Cancel controls
- Android back button works as normal browser history
- White splash background
- App stays portrait for normal pages
- Offline pages and pull-to-refresh are handled by the WordPress PWA plugin
- Videos are not cached inside the app

## Important

Users must allow Android notification permission when prompted. The app registers its token with WordPress and also subscribes to the Firebase topic `mk_movies_all`.

The supported WordPress sender is KSD Movie Push Notifications v3.9.2. It sends to all active saved Android tokens in batches of 1,000 with Firebase priority set to `HIGH` and channel `mk_movies_alerts_v3`.


## v1.7 dependency fix
This pack uses androidbrowserhelper 2.5.0 to avoid AndroidX browser/core versions that require compileSdk 36.


## v2.6.2 Force Download Fix
Movie download button clicks now start Android DownloadManager instead of opening the movie in a video player. Target API remains 36.

## v2.7.0 Download Notifications, Filenames and Broadcast Push

- Shows native Android movie-download progress immediately after a user taps Download.
- Preserves the movie title in the saved filename and completion notification.
- Passes login cookies, the WebView user agent and page referrer to DownloadManager.
- Uses Firebase topic `mk_movies_all` and a new high-priority Android alert channel.
- Targets and compiles against Android 16 / API 36 for current Google Play submission requirements.
- Uses minimum Android 7.0 / API 24, required by the current Firebase Messaging SDK.
- Updates Android Gradle Plugin to 9.4.1, Gradle to 9.6.0, Google Services to 4.5.0 and Firebase BoM to 35.1.0.
- Uses alerting notification channels with the default sound and vibration; repeated download progress updates alert only once.
- Bumps the app to versionName 2.7.0 and versionCode 17.

## v2.8.0 GitHub and Play Signing Update

- Bumps the app to versionName 2.8.0 and versionCode 18.
- Produces clearly named APK and Play Console AAB artifacts.
- Requires the signing keystore through GitHub Actions secrets instead of committing it to the repository.
- Verifies the upload-key SHA-1 before building and verifies the completed AAB certificate afterward.
- Preserves the v2.7.0 native download notifications, movie-title filenames, high-priority FCM topic alerts, API 36 target, and alerting notification channels.

## v2.9.0 API 36 Release

- Keeps both `compileSdk` and `targetSdk` on Android API 36.
- Bumps the Play Store release to versionName 2.9.0 and versionCode 19.
- Installs Android platform 36 and build-tools 36.0.0 in GitHub Actions.
- Adds a build-time validation step that stops the workflow if the SDK or version values are incorrect.
- Produces `MK-Movies-v2.9.0-release.apk` and `MK-Movies-v2.9.0-play-console.aab`.

## v2.9.1 Firebase Android Fix

- Uses the fresh audible `mk_movies_alerts_v3` Android notification channel.
- Posts the notification immediately and loads the optional poster afterward, preventing a slow image request from losing the alert.
- Explicitly enables Firebase Messaging auto-initialization.
- Refreshes the Firebase topic and WordPress token registration whenever the app resumes.
- Sends the real Android notification-permission state to WordPress and re-registers when that state changes.
- Supports the older `OPEN_MOVIE` click action while the updated sender uses normal launcher routing.

## v2.10.1 Named Resumable Download Fix

- Resolves the movie title directly from the current movie page, Open Graph title, movie card and download-button metadata.
- Rejects timestamp, random-ID and storage-object filenames before saving the movie.
- Uses the server Content-Disposition filename only when it is a meaningful name.
- Shows Pause and Cancel while downloading, then Resume and Cancel while paused.
- Continues a paused download with HTTP Range requests instead of restarting from zero when the storage server supports ranges.
- Cancelling removes the incomplete file.
- Saves completed movies in `Downloads/MK Movies`.
- Uses a fresh `mk_movie_downloads_v4` notification channel so the new progress controls are not mixed with an older download notification.
- Targets Android 16 / API 36 and releases as versionName 2.10.1, versionCode 22.
