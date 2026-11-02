# GalleryFeed

**Package:** `uz.ilhomvich.galleryfeed`  
**Platform:** Android 8.0+ (API 26+)  
**Stack:** Kotlin · Jetpack Compose · Room · MediaStore · Media3 ExoPlayer  
**Build:** Gradle 8.14.6 · JDK 21 · Android API 36

GalleryFeed turns the media already on the phone into a local, swipe-based feed. It does not upload media and does not require an account or a backend.

## Implemented in this source

- MediaStore scanning for local photos and videos, newest first.
- Full-screen vertical feed with swipe navigation.
- Inline video playback using Media3 ExoPlayer; images are rendered with Coil.
- Like and bookmark state saved in a Room database.
- Multiple local comments per media item, including delete.
- Android Sharesheet integration using the original content URI.
- Explore grid, filename/folder/MIME search, photo/video filters, saved feed and local profile counters.
- Media metadata: filename, MIME type, date added, file size, dimensions, relative folder and video duration.
- Dark glass-style surfaces, gradient accents and small interaction animations.
- Android 13+ granular media permissions, including Android 14+ selected-media permission.

## Open in Android Studio

1. Extract this project ZIP.
2. Open the `GalleryFeed` folder in Android Studio.
3. Use JDK 21 and allow Gradle to download the pinned Gradle 8.14.6 distribution.
4. Install Android SDK Platform 36 if Android Studio asks for it.
5. Run the `app` configuration on an Android device or emulator.

The included `gradlew` and `gradlew.bat` are small bootstrap scripts because this source archive does not bundle the binary Gradle Wrapper JAR. They download and run the exact Gradle distribution pinned in `gradle/wrapper/gradle-wrapper.properties`.

## GitHub Actions — Release APK

The workflow is `.github/workflows/release.yml`. It runs on **Actions → Android Release APK → Run workflow**, and also when you push a tag beginning with `v`.

### 1. Create your own keystore

Run this on your computer and keep the generated file safe:

```bash
keytool -genkeypair -v \
  -keystore galleryfeed-release.jks \
  -alias galleryfeed \
  -keyalg RSA -keysize 3072 \
  -validity 10000
```

Do not commit the `.jks` file to GitHub. Back it up somewhere private. Losing the keystore can prevent you from updating an app already installed under the same package name.

### 2. Add GitHub repository secrets

Open **Repository → Settings → Secrets and variables → Actions → New repository secret** and create these four secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64 text of your `.jks` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `galleryfeed` (or the alias you selected) |
| `KEY_PASSWORD` | Key password |

Create Base64 text on Linux/macOS:

```bash
base64 -w 0 galleryfeed-release.jks
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("galleryfeed-release.jks"))
```

Copy the resulting one-line text into `KEYSTORE_BASE64`.

### 3. Build and download

- For a manual build: open **Actions → Android Release APK → Run workflow**.
- For an automatically published GitHub Release: push a version tag, for example:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The APK is uploaded as a workflow artifact. A pushed `v*` tag also creates a GitHub Release and attaches the APK. If signing secrets are missing, the workflow deliberately produces an **unsigned** release APK and displays a warning; add all four secrets to get an installable signed release APK.

## Local build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

On Windows:

```bat
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

For a locally signed release, set these environment variables before running `assembleRelease`:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## Important implementation notes

- The app only reads media that Android exposes through MediaStore. On Android 14 and newer, the user may grant access to selected photos/videos only; the feed will then contain only those allowed items.
- Like, bookmark and comment records are stored in the app's private Room database. Uninstalling the app clears that database unless the device restores its app data from backup.
- The current interaction key is the MediaStore content URI. If Android removes and recreates a MediaStore record after a file is moved or replaced, the old interaction record may no longer match the media item.
- The app does not edit, move, rename or delete the user's original photos/videos.
- This is a complete first implementation of the offline app. Cloud accounts, public comments, remote likes and cross-device sync are intentionally not included.

## Project layout

```text
GalleryFeed/
├── app/
│   └── src/main/
│       ├── java/uz/ilhomvich/galleryfeed/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── GalleryDatabase.kt
│       │   │   └── LocalMedia.kt
│       │   └── ui/GalleryFeedApp.kt
│       ├── res/
│       └── AndroidManifest.xml
├── .github/workflows/release.yml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```
