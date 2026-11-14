# Quill Notes — v2 Roadmap

Status as of v1.3.0 (November 2026). This document records where the app
stands against what people expect from a mainstream notes app, and the plan
to close the gap in v2.

## Goal

Make Quill an app someone can trust with their only copy of their notes, and
that covers what most people use a notes app for: rich notes, lists, photos,
reminders, organisation, and safe backup across phones.

## Where v1.3 stands

| Area | Rating | Findings |
|---|---|---|
| Visual design | Strong | Consistent themes, type and components on every screen; verified by emulator screenshots in CI. |
| Core editing | Weak | Plain text only. No checklists, formatting, images, links or voice. |
| Data safety | **Critical gap** | Sync only uploads (`CloudSyncManager.syncAll`); there is no download or restore. The Tink key lives in the Android Keystore and `allowBackup` is off, so cloud copies cannot be decrypted on another phone. |
| Sync behaviour | Broken in places | The auto-sync preference is stored but nothing reads it. Deleted notes are never removed from the cloud. `isPinned` and `color` are missing from the upload payload. The payload is hand-built JSON. |
| Reminders | Unreliable | `POST_NOTIFICATIONS` is declared but never requested, so reminders are silent on Android 13+. Due dates have no time of day and cannot repeat. |
| Organisation | Basic | Tags and colours only. No notebooks, no archive. Search covers Notes, not Journal or Planner. |
| Platform fit | Missing | No widgets, quick-capture shortcut or tile, tablet layout, export or import. |
| Accessibility and reach | Weak | UI text is hardcoded in Kotlin (no `stringResource` use), so it cannot be translated. TalkBack and large-font behaviour are untested. |
| Engineering | Thin | No unit tests (only `ScreenshotTest`). No crash reporting. Release signing and privacy policy are not set up. |

## Phase 1 — Trustworthy (v2.0)

Nothing in later phases ships before this. New content types on top of a
backup that cannot be restored only make eventual data loss worse.

| # | Item | Notes |
|---|---|---|
| 1.1 | Restore and two-way sync | Download and merge on sign-in and on each sync. Last edit wins; when both sides changed, keep both copies. Needs a stable per-note UUID instead of the local row id. |
| 1.2 | Recoverable encryption key | Wrap the data key with a key derived from a user recovery passphrase and store the wrapped key with the backup. See open decision A. |
| 1.3 | Working auto-sync | WorkManager job honouring the existing preference, with network constraints and backoff. |
| 1.4 | Complete sync payload | Include pin, colour and deletion (tombstones); replace hand-built JSON with kotlinx.serialization and a format version field. |
| 1.5 | Export and import | Encrypted backup file, plus Markdown / plain-text export per note and in bulk. |
| 1.6 | Reliable reminders | Ask for notification permission in context; add time of day and repeat rules; handle the exact-alarm permission being denied. |
| 1.7 | Test coverage | Unit tests for repository, encryption, sync merge, editor save and undo, date helpers. Run in CI. |

**Done when:** a user can install on a second phone, sign in, enter their
passphrase and see every note, journal entry and task, including after
deleting and editing on both phones.

### Phase 1 progress

| # | Status | Notes |
|---|---|---|
| 1.1 | Implemented | `SyncEngine` downloads and merges every cloud note, then uploads. Each note has a stable UUID (DB v3 migration). Last edit wins; if both sides changed, the older edit is kept as a "(conflicted copy)" note. |
| 1.2 | Implemented (decision A: passphrase) | Random data key, wrapped with PBKDF2-HMAC-SHA256 from the passphrase, stored as `quill_backup_key.qnote`. Sync screen asks to create or enter the passphrase. Changing the passphrase is not built yet. |
| 1.3 | Implemented | `SyncWorker` (3-hourly, network constraint, exponential backoff) is scheduled only while the auto-sync preference is on. |
| 1.4 | Implemented | `NotePayload` (kotlinx.serialization, format version 2) carries pin, colour, trash state and tombstones for permanent deletes. Pin, complete, trash and restore now mark a note as changed (they did not before). |
| 1.5 | Not started | Encrypted backup file and Markdown / plain-text export. |
| 1.6 | Partly done | Notification permission is requested when a task gets a due date; reminders fall back to inexact alarms when exact alarms are denied. Time of day and repeat rules are not built. |
| 1.7 | Partly done | JVM unit tests for merge rules, payload format, key wrapping and a two-phone sync simulation; run in CI. Not covered yet: repository, editor save/undo, date helpers. |

Behaviour to know about: files uploaded by v1.3 (`note_<number>.qnote`) are ignored, not
imported or deleted. Every note is re-uploaded under its new name on the first v2 sync.

## Phase 2 — Feature parity (v2.1–v2.2)

| # | Item | Notes |
|---|---|---|
| 2.1 | Checklists in notes | Tap to tick from the card and the editor; checked items sink to the bottom. |
| 2.2 | Text formatting | Headings, bold, italic, bullets, numbered lists, links. Stored as Markdown. See open decision B. |
| 2.3 | Image attachments | Camera and gallery; files encrypted at rest and included in sync. |
| 2.4 | Notebooks and Archive | Folders alongside tags; archive hides without deleting. |
| 2.5 | Global search | One search across notes, journal and tasks with highlighted matches and filters. |
| 2.6 | Widgets and quick capture | Quick-note and today's-tasks widgets (Glance), app shortcut, Quick Settings tile. |

## Phase 3 — Differentiate and polish (v2.3+)

| # | Item | Notes |
|---|---|---|
| 3.1 | Journal depth | Calendar view, monthly mood trends, "on this day", optional daily reminder. |
| 3.2 | Planner depth | Subtasks, priorities, recurring tasks, week view. |
| 3.3 | Voice and scan | Voice notes with on-device transcription; document scan to text. |
| 3.4 | Power features | Note links and backlinks, templates, per-note lock. |
| 3.5 | Reach | Two-pane tablet and foldable layout; strings moved to resources; Hindi and Marathi; TalkBack and font-scale pass. |
| 3.6 | Play Store readiness | Release signing, privacy policy, crash reporting, onboarding, store listing assets. |

## Open decisions

**A. How backups stay recoverable (blocks 1.2)**

| Option | For | Against |
|---|---|---|
| Recovery passphrase (recommended) | Keeps the promise that the cloud provider cannot read notes. | A forgotten passphrase means the backup cannot be opened. |
| Key stored with the Google / Microsoft account | Nothing for the user to remember. | Weaker privacy: account access is enough to read notes. |

**B. Rich text model (blocks 2.2)**

| Option | For | Against |
|---|---|---|
| Markdown in the existing text field (recommended) | Small change; exports cleanly; encryption and sync unchanged. | Limited layout; images sit outside the text. |
| Block-based editor | Richer content, drag to reorder, embeds. | Large rewrite of editor, storage and sync. |

## Known issues carried from v1.3

- Google Drive sign-in needs an Android OAuth client registered for package
  `com.quillnotes` with the debug key's SHA-1; the release key will need its own.
- OneDrive needs a real `client_id` in `OneDriveSyncService.kt` and
  `res/raw/msal_config.json`.
- Compose BOM 2024.01 pins Material 3 1.1.2; upgrading unlocks stable swipe,
  pull-to-refresh and item animation APIs and should precede Phase 2 UI work.
