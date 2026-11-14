# Quill Notes 🪶

A calm, privacy-first Android notebook: notes, journal and planner in one place, encrypted on the phone and
backed up to your own Google Drive only when you ask. Available in English, हिन्दी and मराठी.

**Version 2.3.0** · Android 12+ (API 31) · Kotlin + Jetpack Compose · no ads, no analytics, no account

## Design

Quill has an editorial look: a warm paper palette, **Instrument Serif** display type over the platform sans,
generous spacing and a floating ink dock.

- **Themes:** Paper (light), Midnight (dark), Aurora (violet), Wallpaper (Material You) or follow the system.
  Each note can carry one of six tones that tint its card and editor. Text size is adjustable.
- **Floating dock** for Notes / Journal / Planner, with a create button that makes the right kind of item for
  the current tab. On tablets and unfolded foldables (≥ 840 dp wide) the list and editor sit side by side.
- Screens are split into stateless `*Content` composables fed by a UI-state object, so they render in tests and
  previews without ViewModels.

## Features

| Area | What it does |
|---|---|
| **Notes** | Staggered grid or list; pinned and recent sections; filter by pinned / tone / tag / notebook; sort; swipe to pin or trash; long-press to multi-select with bulk pin, archive, move or trash (with undo) |
| **Editor** | Markdown in the normal text field with live styling and a toolbar (bold, italic, heading, checklist, bullets, numbered, indent, link); lists continue when you press Enter; undo/redo; autosave; word count and reading time |
| **Checklists** | `- [ ]` items are tappable boxes in the editor *and* on the note card; ticked items sink to the bottom |
| **Photos** | From the gallery or camera; resized (WebP, ≤ 2048 px), encrypted at rest, shown as card covers, included in sync and backups |
| **Links** | `[[Note title]]` links between notes, with a "linked from" list |
| **Organise** | Tags, colours, notebooks, archive, templates (built-in and your own), pinning |
| **Search** | One search over notes, journal and tasks with highlighted matches and type / notebook / tag filters. Locked notes match by title only |
| **Journal** | Moods, writing streak, week strip, timeline or calendar view, monthly mood trend, "on this day", optional daily reminder |
| **Planner** | Due date *and time*, repeat (daily / weekly / fortnightly / monthly / yearly: finishing a task starts the next round), priorities, subtask progress, week view, progress ring |
| **Reminders** | Exact when allowed, gracefully inexact when not; notification buttons for Done and Snooze; re-armed after reboot |
| **Voice and scan** | Dictate with the system recogniser (offline preferred); scan text from a photo with bundled on-device OCR (Latin and Devanagari) |
| **Lock** | Optional app lock and a per-note lock (biometrics or screen lock) |
| **Widgets** | Quick capture and today's tasks (Glance), launcher shortcuts, a Quick Settings tile |
| **Backup and sync** | Two-way sync of encrypted per-note files to your Google Drive app-data folder; restore on a new phone with a recovery passphrase; Markdown zip export; encrypted `.qbackup` file |
| **Trash** | 30-day retention with a days-left countdown |
| **Reach** | English, Hindi and Marathi (per-app language on Android 13+); first-run onboarding; local, opt-in crash reports |

## Security model

```
 note text, title, tags, mood, notebook
        │  AES-256-GCM (Google Tink), key in the Android Keystore
        ▼
   Room database (ciphertext)                photos: encrypted files in app storage
        │
        │  optional cloud sync
        ▼
 per-note file  note_<uuid>.qnote  ── encrypted again with a random *data key*
 photos         att_<uuid>.qatt        │
                                       └─ the data key is wrapped with a key derived from your
                                          recovery passphrase (PBKDF2-HMAC-SHA256) and stored beside
                                          the backup, so a new phone can unwrap it. Google sees only
                                          ciphertext; Quill cannot recover a forgotten passphrase.
```

Dates, due times, colours and flags (pinned, completed, priority) are **not** encrypted: the app needs them
to sort and remind. See [`docs/PRIVACY.md`](docs/PRIVACY.md) for the full policy.

## Tech stack

| Layer | Technology |
|---|---|
| Language / UI | Kotlin 1.9, Jetpack Compose (BOM 2024.01), Material 3, Glance 1.1 for widgets |
| Architecture | MVVM + repository; pure logic (merge, Markdown, search, stats) kept free of Android so it is unit-tested on the JVM |
| DI / storage | Hilt, Room 2.6 (schema v4, explicit migrations, no destructive fallback), DataStore |
| Background | WorkManager (auto-sync), AlarmManager (reminders) |
| Crypto | Google Tink (AES-256-GCM, streaming AEAD for backups), Android Keystore, PBKDF2 |
| Sync | Google Drive API v3 (`drive.appdata`), kotlinx.serialization payloads with a format version |
| On-device ML | ML Kit text recognition (bundled models, Latin + Devanagari) |
| SDK | min 31, target / compile 35 |

## Project structure

```
app/src/main/java/com/quillnotes/
├── data/
│   ├── encryption/   NoteCipher, NoteEncryptionManager
│   ├── local/        Room database, DAOs, entities, migrations
│   ├── repository/   NoteRepository, attachments, preferences
│   ├── sync/         SyncEngine (merge), payloads, key wrapping, Google Drive service, WorkManager job
│   ├── export/       Markdown + encrypted backup writers/readers
│   └── media/        image import, bitmap cache, text scanner
├── domain/           search, journal/planner statistics, repeat rules, note links
├── text/             Markdown parsing, checklist toggling, editing rules, previews
├── notifications/    alarms, reminder and boot receivers, journal reminder
├── widget/           Glance widgets and the Quick Settings tile
└── ui/
    ├── components/   design system, dock, note cards, dialogs
    ├── navigation/   routes
    ├── screens/      home, editor, search, journal, planner, settings, sync, trash, onboarding
    ├── theme/        colours, type, shapes
    └── util/         date formatting (TimeText), UiText, greeting
app/src/main/res/     values/, values-hi/, values-mr/ (473 strings each)
docs/PRIVACY.md       privacy policy        store/listing/   Play listing text (en, hi, mr)
```

## Build, test, release

```bash
./gradlew testDebugUnitTest      # JVM unit tests (sync merge, payloads, key wrap, Markdown, search, stats…)
./gradlew assembleDebug          # debug APK (unminified, large)
./gradlew assembleSideload       # debug-signed but minified APK: the one to install by hand
./gradlew bundleRelease          # AAB for the Play Store (see below for signing)
```

**Signing.** Release credentials are read from `keystore.properties` (git-ignored) or `QUILL_KEYSTORE_FILE`,
`QUILL_KEYSTORE_PASSWORD`, `QUILL_KEY_ALIAS`, `QUILL_KEY_PASSWORD`. Without them the release build is simply
unsigned. See [`PLAY_STORE_PUBLISHING_GUIDE.md`](PLAY_STORE_PUBLISHING_GUIDE.md).

**Google Drive.** Sync needs an Android OAuth client in Google Cloud Console for package `com.quillnotes` and
the SHA-1 of the key that signs your build (the checked-in `app/ci-debug.keystore` gives debug and sideload
builds a stable SHA-1). Guide, part 2.1. OneDrive is not offered: its sign-in is not implemented (`ROADMAP.md`).

### Continuous integration

`.github/workflows/build-apk.yml` runs on every push:

| Job | Does |
|---|---|
| `build` | unit tests → `assembleDebug assembleSideload` → publishes the minified APK and a `BUILD_INFO.txt` (commit, SHA-256, size) to the **`apk-builds`** branch; the unminified debug APK is a workflow artifact (it exceeds GitHub's 100 MB file limit) |
| `screenshots` | on an API 34 emulator: installs the **sideload** APK and runs `.github/scripts/smoke.py` (first-run onboarding → create a note with a list → add a task → tabs → search → settings → switch to Hindi and Marathi, checking logcat for crashes), then runs `ScreenshotTest` (every screen, several themes and languages). PNGs, UI dumps and logcat go to the **`ui-screenshots`** branch |

Locally, `ScreenshotTest` can be run with:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.quillnotes.ScreenshotTest
adb exec-out run-as com.quillnotes tar -c -C files screenshots > screenshots.tar
```

## Languages

Strings live in `res/values*/strings.xml`. Text that is built in code goes through `stringResource`, `UiText`
(ViewModels) or `TimeText` (relative dates), never string literals. To add a language: copy
`values/strings.xml`, translate it keeping every `%1$s` / `%1$d` placeholder, add the locale to
`res/xml/locales_config.xml`, and add a listing under `store/listing/`.

## Known limitations

See [`ROADMAP.md`](ROADMAP.md) for the complete list. In short: OneDrive is not offered; Google Drive sync
has not been exercised against a real Google account in CI; the recovery passphrase cannot be changed yet;
and widgets, notifications, camera/photo picker, voice input, OCR and biometrics have only been
compile- and launch-tested, not driven on a device.

## Publishing

See **[`PLAY_STORE_PUBLISHING_GUIDE.md`](PLAY_STORE_PUBLISHING_GUIDE.md)** and
[`store/README.md`](store/README.md).
