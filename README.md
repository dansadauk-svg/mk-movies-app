# MK Movies TWA GitHub Build Pack v1.4

This pack builds the MK Movies Android app from GitHub Actions. It uses a Trusted Web Activity so the app opens `https://mkmovies.com.ng` without the normal browser address bar after Digital Asset Links verification.

## Before building

Install these WordPress plugins first:

1. `mk-movies-pwa-plugin-v1.5-offline-refresh-push-ready.zip`
2. `mk-movies-push-notifications-plugin-v1.0.zip`
3. Existing `mk-movies-twa-verification-plugin-v1.0.zip`

Then confirm this URL opens JSON:

```text
https://mkmovies.com.ng/.well-known/assetlinks.json
```

## GitHub setup

1. Create a **private** GitHub repository.
2. Upload all files from this build pack into the repository.
3. Go to **Settings > Secrets and variables > Actions > New repository secret**.
4. Add:

```text
MK_KEYSTORE_PASSWORD
MK_KEY_PASSWORD
```

Use the values in `keystore-info.txt`. Keep that file and the `.jks` file private.

If you do not want to commit the `.jks` file, delete it from the repository and add this extra secret:

```text
MK_KEYSTORE_BASE64
```

Generate it from your computer with:

```bash
base64 -w 0 mk-movies-release-key.jks
```

## Build

1. Open the GitHub repository.
2. Go to **Actions**.
3. Select **Build MK Movies TWA APK/AAB**.
4. Click **Run workflow**.
5. Download the generated APK/AAB from the workflow artifacts.

## Included app features

- Trusted Web Activity wrapper for MK Movies
- App notifications support through notification delegation
- Android back button works as normal browser history
- White splash background
- App stays portrait for normal pages
- Offline pages and pull-to-refresh are handled by the WordPress PWA plugin
- Videos are not cached inside the app

## Important

For notifications, users must tap **Enable movie alerts** once inside the website/app and allow notifications. New movie notifications are sent by WordPress when a movie is first published.
