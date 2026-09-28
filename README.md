# Quill Notes 🪶

A minimalist, privacy-first Android note-taking app with AES-256 encryption,
cloud sync, and three beautiful themes.

## Features

| Feature | Details |
|---|---|
| **Quick Notes** | Distraction-free editor, pin & search, color labels, list/grid view |
| **Multi-Select** | Long-press to select notes, bulk pin/unpin & delete |
| **Sorting** | Newest, oldest, or title A–Z / Z–A |
| **Journal** | Daily entries with mood tracking, grouped by date |
| **Planner** | Tasks with due dates and one-tap completion |
| **Encryption** | AES-256-GCM via Google Tink, hardware-backed key in Android Keystore |
| **Cloud Sync** | Google Drive & Microsoft OneDrive (encrypted before upload) |
| **Themes** | Light · Dark · Colorful · Dynamic (Material You) · System default |
| **Biometric Lock** | Optional fingerprint / face authentication |

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository pattern |
| DI | Hilt |
| Database | Room + AES-256-GCM encryption |
| Navigation | Navigation Compose |
| Preferences | DataStore |
| Cloud (Google) | Google Drive API v3 + GoogleAccountCredential |
| Cloud (Microsoft) | Microsoft Graph API + MSAL |
| Encryption | Google Tink (`AES256_GCM`) |
| Minimum SDK | API 31 (Android 12) |
| Target SDK | API 35 (Android 15) |

## Project Structure

```
app/src/main/java/com/quillnotes/
├── data/
│   ├── encryption/     NoteEncryptionManager (AES-256-GCM)
│   ├── local/          Room database, DAO, entities, converters
│   ├── repository/     NoteRepository, PreferencesRepository
│   └── sync/           CloudSyncManager, SyncProvider, Google/OneDrive services
├── di/                 Hilt modules (AppModule, SyncModule)
└── ui/
    ├── components/     NoteCard, EmptyState, MoodSelector, DatePickerButton, BottomBar
    ├── navigation/     Screen sealed class
    ├── screens/
    │   ├── home/       HomeScreen + HomeViewModel
    │   ├── editor/     NoteEditorScreen + EditorViewModel
    │   ├── journal/    JournalScreen + JournalViewModel
    │   ├── planner/    PlannerScreen + PlannerViewModel
    │   ├── settings/   SettingsScreen + SettingsViewModel
    │   └── sync/       SyncScreen + SyncViewModel
    └── theme/          Color.kt, Type.kt, Theme.kt
```

## Setup

### 1. Open in Android Studio
```bash
git clone <repo>
# File → Open → select the QuillNotes/ folder
```

### 2. Configure Google Drive
- Enable Drive API in Google Cloud Console
- Create an OAuth 2.0 Android credential with your SHA-1 + package name
- See `PLAY_STORE_PUBLISHING_GUIDE.md` → Part 2.1 for full steps

### 3. Configure OneDrive
- Register app in Azure Portal
- Paste your `client_id` into `OneDriveSyncService.kt` and `res/raw/msal_config.json`
- See `PLAY_STORE_PUBLISHING_GUIDE.md` → Part 2.2 for full steps

### 4. Build
```bash
./gradlew assembleDebug          # Debug APK
./gradlew bundleRelease          # Signed AAB for Play Store
```

## Security Model

```
┌─────────────────────────────────────────────────┐
│  User writes note                               │
│         ↓                                       │
│  AES-256-GCM encrypt (Google Tink)              │
│         ↓                                       │
│  Store ciphertext in Room database              │
│         ↓  (optional cloud sync)                │
│  Re-encrypt for export  ──→  Google Drive       │
│                         ──→  OneDrive           │
│                                                 │
│  Key: Android Keystore (hardware-backed)        │
│  Never leaves the device in plaintext           │
└─────────────────────────────────────────────────┘
```

## Publishing

See **`PLAY_STORE_PUBLISHING_GUIDE.md`** for the complete step-by-step
guide to signing, uploading, and launching on the Play Store.
