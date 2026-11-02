# Quill Notes 🪶

A calm, privacy-first Android notebook — notes, journal and planner in one
place, encrypted with AES-256 and backed up to the cloud only when you ask.

## Design

Quill 1.3 has an editorial look: a warm paper palette, **Instrument Serif**
display type over the platform sans, generous spacing and a floating ink dock.

- **Themes:** Paper (light), Midnight (dark), Aurora (violet), Wallpaper
  (Material You) or follow the system. Each note can carry one of six tones
  (yellow, green, blue, pink, purple, orange) that tint its card and editor.
- **Floating dock** for Notes / Journal / Planner, with a create button that
  makes the right kind of item for the current tab.
- Screens are split into stateless `*Content` composables fed by a UI-state
  object, so they render in tests and previews without ViewModels.

## Features

| Feature | Details |
|---|---|
| **Notes** | Staggered grid or list, pinned + recent sections, filter by pinned / tone / tag, sort menu, swipe to pin or trash |
| **Multi-select** | Long-press to select; floating bar to bulk pin or trash, with undo |
| **Search** | Searches title, body and tags of your (decrypted) notes as you type |
| **Editor** | Tone-tinted canvas, serif title, inline tag chips, word count + reading time, undo/redo, pin, share, move to trash, autosave |
| **Journal** | Weekly mood strip, writing streak, day-grouped timeline, daily prompt |
| **Planner** | Progress ring, quick add (optionally due today), Overdue / Today / Upcoming / Someday / Completed groups, swipe to complete, reminders |
| **Trash** | 30-day retention with days-left countdown, restore or delete forever |
| **Encryption** | AES-256-GCM via Google Tink, hardware-backed key in Android Keystore |
| **Cloud Sync** | Google Drive & Microsoft OneDrive (encrypted before upload) |
| **Biometric Lock** | Optional fingerprint / face unlock, re-locks after a minute in the background |

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
    ├── components/     QuillDesign (header, chips, search…), QuillDock, NoteCards,
    │                   EmptyState, MoodSelector, ProgressRing, swipe actions, BiometricGate
    ├── navigation/     Screen sealed class
    ├── screens/
    │   ├── home/       HomeScreen + HomeViewModel
    │   ├── editor/     NoteEditorScreen + EditorViewModel
    │   ├── journal/    JournalScreen + JournalViewModel
    │   ├── planner/    PlannerScreen + PlannerViewModel
    │   ├── settings/   SettingsScreen + SettingsViewModel
    │   ├── sync/       SyncScreen + SyncViewModel
    │   └── trash/      TrashScreen + TrashViewModel
    ├── theme/          Color.kt, Type.kt, Shape.kt, Theme.kt
    └── util/           Date, tag and word-count formatters
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

### 5. UI screenshots
`app/src/androidTest/.../ScreenshotTest.kt` renders every screen with fixture
data (`SampleData.kt`) in several themes. CI runs it on an API 34 emulator on
every push and publishes the PNGs to the `ui-screenshots` branch. Locally:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.quillnotes.ScreenshotTest
adb exec-out run-as com.quillnotes tar -c -C files screenshots > screenshots.tar
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
