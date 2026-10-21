# REAL

One repository for every REAL app.

| Folder | What it is |
|---|---|
| `android/` | Android app (Kotlin, Jetpack Compose). Open this folder in Android Studio. |
| `ios/` | iOS app (SwiftUI). The Xcode project is generated from `ios/project.yml` with XcodeGen. |
| `.github/workflows/` | CI. `ios.yml` builds the iOS app when `ios/` changes on `main`. |

## Working on it

- New work goes on a short branch (for example `feature/pano-dock`) and is merged into `main` when done.
- Releases are marked with tags (`android-<version>`, `ios-<version>`); each app keeps its own version.

## Secrets

The repository is public. Firebase configs, signing keys, keystore properties and database dumps are never
committed; they are listed in `.gitignore`. iOS CI receives its Firebase config and App Check token from GitHub
secrets.
