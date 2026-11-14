# Quill Notes — Privacy Policy

_Last updated: 14 November 2026 · Applies to Quill Notes 2.3 and later._

> **Before publishing:** replace the two bracketed placeholders below (developer name and support email),
> then host this page at a public URL and enter that URL in the Play Console. The text describes what the
> app does today; if you change behaviour, change this page in the same release.

**Developer:** [Developer name]  ·  **Contact:** [support email]

## The short version

* Your notes live **on your phone**, encrypted. There is no Quill account and no Quill server.
* Nothing leaves your phone unless **you** turn on cloud backup or export a file.
* No ads, no analytics, no tracking, no third-party advertising or attribution SDKs.

## What the app stores, and where

| Data | Where it is kept | Protection |
|---|---|---|
| Notes, journal entries and tasks | A database on your phone | Titles, text, tags, moods and notebook names are encrypted with **AES-256-GCM**; the key is held in the **Android Keystore** (hardware-backed where the phone supports it) and never leaves the device |
| Dates, due times, repeat rules, colour, priority, pinned/completed/archived flags | The same database | **Not encrypted**: the app needs them to sort, remind you and show your calendar. They contain no note text |
| Photos you attach | Files in the app's private storage | Encrypted at rest with the same Keystore-protected key; resized before storing |
| Preferences (theme, text size, sort order, reminder time) | App-private preferences | Not sensitive; not shared |
| A crash report, only after the app has crashed | App-private storage, until you share or dismiss it | See "Crash reports" below |

Uninstalling the app deletes all of this from your phone.

## Cloud backup (optional)

If you choose **Cloud sync**, Quill uploads encrypted copies of your notes and photos to the **hidden app-data
folder of your own Google Drive** (the `drive.appdata` permission: Quill cannot see or touch any other file in
your Drive).

* Files are encrypted **on your phone** before upload, with a data key that is itself protected by a
  **recovery passphrase that only you know** (derived with PBKDF2-HMAC-SHA256).
* Google can see that encrypted files exist, their approximate sizes and modification times. It cannot read
  their content. **Quill cannot recover a forgotten passphrase** — without it a backup cannot be opened.
* Signing in uses Google's standard sign-in. Quill receives an access token to read and write its own app-data
  folder; it does not receive your Google password.
* "Disconnect" in the app stops syncing. To remove the backup itself, delete the app's data from your Google
  account's Drive settings.

Microsoft OneDrive is **not** offered in this version.

## Files you export

**Export as Markdown** and **Create encrypted backup** write a file to a location you pick with the system
file picker. A Markdown export is readable by anyone who gets the file; the `.qbackup` file is encrypted with a
passphrase you choose. Quill does not send these files anywhere.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | Cloud backup only, and only after you connect an account |
| Notifications | Task and journal reminders |
| Exact alarms | So a reminder fires at its chosen time; if you decline, reminders still work but may be slightly late |
| Receive boot completed | Re-schedules your reminders after the phone restarts |
| Biometric | The optional app lock and per-note lock; handled entirely by Android |
| Wake lock, foreground service | Standard permissions added by Android's WorkManager library so a background sync can finish; Quill does not run a persistent service |

Quill does **not** request access to your contacts, location, microphone, camera, photos library or files.
Photos are added through the system photo picker or camera app, which hand Quill only the single picture you chose.

## Voice input and text scanning

* **Voice dictation** uses the speech recognition service built into your phone (on most phones, Google's).
  Quill asks it to work offline where possible, but whether audio is processed on the phone or on the provider's
  servers is decided by that service and your phone's settings, under that provider's privacy policy. Quill only
  receives the resulting text and never receives or stores the audio.
* **Scan text from a photo** runs entirely on your phone using models bundled with the app. The image is not
  uploaded.

## Crash reports

Quill has no automatic crash reporting. If the app crashes, the next time you open it you are asked whether to
share a short technical report. It contains the app version, Android version, device model and the technical
stack trace. It **contains no note text, titles, tags or photos**, and is only sent if you choose where to send
it (for example, your email app). You can dismiss it and it is deleted.

## Analytics, advertising and children

There are no ads and no analytics. Quill does not build profiles, sell data, or share data with third parties.
The app is a general-purpose notes tool and is not directed at children under 13.

## Your choices

* Export everything as Markdown at any time (Settings → Your data).
* Delete everything by uninstalling, or by deleting the backup from your own Google account.
* Turn the app lock, individual note locks, reminders and cloud backup on or off in the app.

## Changes

If this policy changes, the new version is published at the same address with a new "last updated" date, and
significant changes are mentioned in the release notes.
