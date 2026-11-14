# Quill Notes — v2 Roadmap

Status as of **v2.3.0** (November 2026): all three phases are implemented. This document keeps the
assessment of v1.3 and the plan, and records the outcome of every item, what has and has not been verified,
and what is still not done.

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
| 1.2 | Implemented (decision A: passphrase) | Random data key, wrapped with PBKDF2-HMAC-SHA256 from the passphrase, stored as `quill_backup_key.qnote`. Sync screen asks to create or enter the passphrase. **Changing the passphrase is not built.** |
| 1.3 | Implemented | `SyncWorker` (3-hourly, network constraint, exponential backoff) is scheduled only while the auto-sync preference is on. |
| 1.4 | Implemented | `NotePayload` (kotlinx.serialization, format version 3) carries pin, colour, trash state, priority, repeat rule, notebook, archive/template/lock flags, image references, and tombstones for permanent deletes. Every mutation marks a note as changed. |
| 1.5 | Implemented | Settings → Your data: Markdown zip (one file per note, photos included) and an encrypted `.qbackup` file (streaming AES-GCM, passphrase-derived key) that can be restored on any phone; restore adds missing notes and never overwrites a more recent local edit. |
| 1.6 | Implemented | Date **and time** per task, repeat rules, notification permission asked in context, inexact fallback when exact alarms are denied, Done / Snooze actions on the notification, reminders re-armed after reboot, optional daily journal reminder. |
| 1.7 | **Partly done** | 179 JVM unit tests run in CI: sync merge, two-phone simulation (with photos), payload format, key wrapping, backup file, Markdown, checklists, editing rules, search, journal/planner statistics, repeat rules, note links, date helpers. **Not written:** `NoteRepository` and `EditorViewModel` tests (they need a fake Room DAO or instrumented tests). |

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

### Phase 2 progress

| # | Status | Notes |
|---|---|---|
| 2.1 | Implemented | `- [ ]` / `- [x]` lines are tappable boxes in the editor and on note cards (ticking in place); ticked items sink to the bottom. List continuation and backspace rules are pure functions (`MarkdownEditing`, unit-tested). |
| 2.2 | Implemented (decision B: Markdown in the text field) | Live styling through a `VisualTransformation` that replaces characters one-for-one (the offset mapping is the identity, so selection and the keyboard are unaffected); toolbar for bold, italic, heading, lists, indent and link. Stored as plain Markdown, so export, search and sync did not change. Not styled: tables, code blocks, inline images (photos are attachments shown with the note). |
| 2.3 | Implemented | Gallery and camera; EXIF-corrected, resized to ≤ 2048 px, WebP q85, encrypted on disk, LRU-cached. Sync uploads photos before the notes that reference them and fetches them when another phone announces them; photos are shared across notes by UUID. **Not built:** removing cloud photo files that no note references any more. |
| 2.4 | Implemented | Notebooks (a note is in at most one), archive, bulk actions, notebook rename/delete; notebook and archive state sync. |
| 2.5 | Implemented | One search over notes, journal and tasks with type / notebook / tag / archived filters and highlighted matches. It runs in memory because text is encrypted at rest; locked notes match by title only. |
| 2.6 | Implemented, **not exercised on a device** | Quick-capture and today's-tasks widgets (Glance), launcher shortcuts, Quick Settings tile. They compile and survive R8, but nothing has tapped them on a phone or emulator. |

## Phase 3 — Differentiate and polish (v2.3+)

| # | Item | Notes |
|---|---|---|
| 3.1 | Journal depth | Calendar view, monthly mood trends, "on this day", optional daily reminder. |
| 3.2 | Planner depth | Subtasks, priorities, recurring tasks, week view. |
| 3.3 | Voice and scan | Voice notes with on-device transcription; document scan to text. |
| 3.4 | Power features | Note links and backlinks, templates, per-note lock. |
| 3.5 | Reach | Two-pane tablet and foldable layout; strings moved to resources; Hindi and Marathi; TalkBack and font-scale pass. |
| 3.6 | Play Store readiness | Release signing, privacy policy, crash reporting, onboarding, store listing assets. |

### Phase 3 progress

| # | Status | Notes |
|---|---|---|
| 3.1 | Implemented | Calendar with entry markers, monthly mood trend (sparkline and counts), "on this day", optional daily reminder at a time chosen in Settings. |
| 3.2 | Implemented | Subtask progress comes from checklist items in a task; priorities; repeat rules `1D / 1W / 2W / 1M / 1Y` (finishing a task advances the due date from its anchor, so it never drifts, and resets the checklist); week view with per-day quick add. |
| 3.3 | Implemented, **not exercised on a device** | Voice: Android's `RecognizerIntent` with offline preferred. This is the phone's recogniser, not an engine bundled with Quill, so the phone's speech service decides where audio is processed (the privacy policy says so). Scan: ML Kit OCR with bundled Latin and Devanagari models, fully on-device. |
| 3.4 | Implemented | `[[Note title]]` links with backlinks; templates (four built in, plus save-as-template); per-note lock. The lock is an access gate (biometrics or screen lock before the text is shown; locked text is left out of search). The note is still encrypted with the same device key as every other note, not a separate key. |
| 3.5 | Mostly done | All UI text is in resources; Hindi and Marathi translations (473 strings each; placeholders and call-site argument counts checked by script); per-app language list; two-pane layout from 840 dp. **Not done:** a TalkBack pass and large-font-scale checks. Content descriptions exist, but nothing was run with a screen reader. The translations are my own and have not been reviewed by a native speaker, so have them checked before publishing. |
| 3.6 | Mostly done | Onboarding, local opt-in crash report (no note content), release-signing scaffold, privacy policy text, store listing text in three languages, version 2.3.0. **Still yours:** host the privacy policy (fill in developer name and email), icon, feature graphic and screenshots, Play Console forms, the signing key. |

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

## Known issues and what is not done

- **OneDrive does not work.** `OneDriveSyncService.signIn()` is a stub that always returns `false` and the
  Azure `client_id` is a placeholder, so the Sync screen does not list it (`SyncProvider.isAvailable`). To
  finish: wire MSAL into `signIn()`, add the client id to `OneDriveSyncService.kt` and
  `res/raw/msal_config.json`, then set `isAvailable = true`.
- **Google Drive sign-in needs your OAuth client.** An Android OAuth client must be registered for package
  `com.quillnotes` with the SHA-1 of the signing key (the checked-in debug key for debug/sideload builds; the
  upload or release key for Play builds). CI has no Google account, so real Drive sync, and restoring on a
  second phone against a real Drive, have only been exercised against a simulated cloud in unit tests.
- Changing the recovery passphrase is not built; neither is clean-up of cloud photo files that no note uses.
- Files uploaded by v1.3 (`note_<number>.qnote`) are ignored, not imported or deleted.
- Repository and `EditorViewModel` unit tests are missing (roadmap 1.7).
- No TalkBack or font-scale review (3.5); translations need a native-speaker review.
- **APK size.** The sideload APK is about 64 MB: ML Kit's OCR engine is bundled for four CPU ABIs (about 41 MB)
  and the code is about 20 MB because the R8 rules keep the Google API client, MSAL and Tink whole. A Play
  App Bundle delivers one ABI per phone. Options if size matters: an ABI filter for the sideload build,
  narrower keep rules, or ML Kit's Play-Services-delivered models (which need network on first use).
- Compose BOM 2024.01 (Material 3 1.1.2) was deliberately not upgraded in this round.
