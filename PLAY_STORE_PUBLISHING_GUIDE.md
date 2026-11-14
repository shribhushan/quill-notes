# Quill Notes — Google Play Store Publishing Guide

Complete step-by-step instructions for building, signing, and publishing
Quill Notes to the Google Play Store.

---

## Part 1 — Pre-Publishing Setup

### 1.1 Android Studio & SDK
- Install **Android Studio Hedgehog (2023.1)** or newer
- Ensure SDK Platform **API 31, 34, and 35** are installed (SDK Manager → SDK Platforms)
- Install **Build Tools 34.0.0+** (SDK Manager → SDK Tools)

### 1.2 Google Play Developer Account
1. Go to **play.google.com/console** and sign in with a Google account.
2. Pay the **one-time $25 USD** developer registration fee.
3. Complete identity verification (takes 1–3 days for new accounts).
4. Accept the **Google Play Developer Distribution Agreement**.

---

## Part 2 — Third-Party API Setup

### 2.1 Google Drive Integration
1. Go to **console.cloud.google.com** → Create a new project named "QuillNotes".
2. Navigate to **APIs & Services → Library** → search for and enable **"Google Drive API"**.
3. Go to **APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID**.
4. Select **Android** as the application type.
5. Enter **Package name**: `com.quillnotes`
6. Enter your app's **SHA-1 fingerprint** (see Step 2.1a below).
7. Click **Create** — no client secret needed for Android OAuth.

**Step 2.1a — Get your SHA-1 fingerprint:**
```bash
# For debug keystore:
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# For release keystore (replace paths):
keytool -list -v -keystore /path/to/release.keystore -alias your_alias
```
Copy the **SHA1** value (e.g., `AA:BB:CC:...`) and paste it into the Google Cloud Console.

8. Add OAuth consent screen:
   - User Type: **External**
   - App name: Quill Notes
   - Add scope: `https://www.googleapis.com/auth/drive.appdata`
   - Add your email as a test user during development.

### 2.2 Microsoft OneDrive Integration

> **Not offered in 2.3.** `OneDriveSyncService.signIn()` is still a stub that always returns `false`, so the Sync
> screen does not list OneDrive. Skip this part unless you are finishing that integration (wire MSAL into
> `signIn()`, then set `isAvailable = true` for `SyncProvider.ONEDRIVE`).
1. Go to **portal.azure.com** → Sign in with a Microsoft account.
2. Navigate to **Azure Active Directory → App registrations → New registration**.
3. Name: `QuillNotes`, Supported account types: **Accounts in any organizational directory and personal Microsoft accounts**.
4. Redirect URI → Select **Public client/native** → Enter:
   ```
   msauth://com.quillnotes/YOUR_BASE64_SHA1
   ```
   (Generate the base64 SHA-1 with: `keytool -exportcert -alias <alias> -keystore <path> | openssl sha1 -binary | openssl base64`)
5. Go to **API permissions → Add a permission → Microsoft Graph → Delegated → Files.ReadWrite**.
6. Click **Grant admin consent**.
7. Copy the **Application (client) ID** from the Overview page.
8. Paste it into:
   - `app/src/main/java/com/quillnotes/data/sync/OneDriveSyncService.kt` → `CLIENT_ID`
   - `app/src/main/res/raw/msal_config.json` → `client_id`

---

## Part 3 — Building a Signed Release APK / AAB

### 3.1 Create a Release Keystore (one-time, keep this file safe forever)
```bash
keytool -genkey -v \
  -keystore quillnotes-release.keystore \
  -alias quillnotes \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```
Fill in the prompts. **Back up this keystore file and remember the passwords — losing it means you can never update your app on the Play Store.**

### 3.2 Configure signing (already wired up)
`app/build.gradle.kts` reads the release credentials from **`keystore.properties`** in the project root, or from
environment variables. Neither is committed (`.gitignore` excludes `keystore.properties`, `*.jks` and
`*.keystore`). If neither is present the release build is simply unsigned, so CI and local builds never fail on it.

Create `keystore.properties` next to `settings.gradle.kts`:
```properties
storeFile=/absolute/path/to/quillnotes-release.keystore
storePassword=your_store_password
keyAlias=quillnotes
keyPassword=your_key_password
```
or export the same values as `QUILL_KEYSTORE_FILE`, `QUILL_KEYSTORE_PASSWORD`, `QUILL_KEY_ALIAS` and
`QUILL_KEY_PASSWORD` (handy for a CI secret). Do not paste passwords into any tracked file.

> With **Play App Signing** (recommended) the key above is only your *upload* key; Google holds the real app
> signing key. Register the SHA-1 of whichever key signs the build you test with in the Google Cloud OAuth
> client, otherwise Google Drive sign-in fails with a developer error (see part 2.1).

### 3.3 Build the Release AAB (recommended for Play Store)
```bash
# From the project root:
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

### 3.4 Build a Release APK (for sideloading / testing)
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## Part 4 — Play Console Submission

### 4.1 Create the App
1. Open **play.google.com/console** → **All apps → Create app**.
2. App name: `Quill Notes`
3. Default language: `English (United States)`
4. App or game: **App**
5. Free or paid: **Free** (or Paid)
6. Accept the declarations and click **Create app**.

### 4.2 Store Listing
Navigate to **Store presence → Main store listing** and fill in:

| Field | Value |
|---|---|
| App name | Quill Notes |
| Short description (≤80 chars) | `store/listing/en-US/short_description.txt` |
| Full description (≤4000 chars) | `store/listing/en-US/full_description.txt` |
| App icon | 512×512 PNG, ≤1024 KB |
| Feature graphic | 1024×500 PNG or JPG |
| Screenshots | At least 2 phone screenshots (1080×1920 recommended; the longer side must be ≤ 2× the shorter) |
| Category | Productivity |
| Tags | notes, journal, planner, encrypted, to-do |
| Email | your-support@email.com |
| Privacy policy URL | Required — host [`docs/PRIVACY.md`](docs/PRIVACY.md) (fill in the two placeholders first) |

**Listing text:** ready-made, length-checked text in English, Hindi and Marathi is in
[`store/listing/`](store/listing) (title, short and full description, release notes). Paste it from there rather
than from an older copy, and see [`store/README.md`](store/README.md) for the graphics you still need to supply.

### 4.3 App Content & Compliance

**Privacy Policy:**
You must host a privacy policy at a public URL. It must cover:
- What data is collected (nothing stored on your servers)
- How encryption works
- Cloud sync (user-controlled, optional)
- Contact information

**Data Safety form** (Play Console → Policy → App content → Data safety):

| Question | Answer |
|---|---|
| Does your app collect or share user data? | No: notes never leave the phone unless the user turns on backup, and backups are encrypted on the phone with a passphrase only the user knows, so neither you nor Google can read them (Play treats data that is end-to-end encrypted this way as not collected). **Confirm the wording against the current form before submitting.** |
| Does your app use encryption? | Yes |
| Is encryption solely for DRM? | No |
| Does your app collect location? | No |

**Content Rating:**
- Go to **Policy → App content → Content rating**.
- Fill out the IARC questionnaire — Quill Notes qualifies for **Everyone**.

**Target audience:**
- Policy → App content → Target audience and content
- Select: **18 and older** (simplest; adjust if targeting younger audiences with appropriate safeguards)

**Ads declaration:**
- Select: **No, my app does not contain ads**

### 4.4 Upload the AAB
1. Navigate to **Release → Production → Create new release**.
2. Click **Upload** and select `app-release.aab`.
3. Enter **Release name** (e.g., `2.3.0 (4)`, matching `versionName` and `versionCode` in `app/build.gradle.kts`)
   and paste the **Release notes** from `store/listing/<locale>/release_notes.txt`.
4. Click **Next → Save**.

### 4.5 Pricing & Distribution
1. Go to **Monetisation → Pricing & distribution**.
2. Set availability to **Available** for all countries or select specific ones.
3. Confirm the app complies with **US export laws** (AES-256 is classified as "mass-market" encryption — select "Yes, my app uses encryption" and "Yes, it is publicly available").

### 4.6 Submit for Review
1. Review all sections — each should show a green ✓.
2. Click **Start rollout to Production** (or start with an **Internal/Closed testing track** first — highly recommended).
3. Google typically reviews within **1–7 business days** for new apps.

---

## Part 5 — Recommended Testing Workflow

### 5.1 Internal Testing (fastest review — minutes)
1. Release → Internal testing → Create new release
2. Upload the `.aab`, add tester emails
3. Install via the opt-in link — test all flows

### 5.2 Closed Testing (Alpha/Beta)
- Broader group, 1–2 day review
- Useful for catching device-specific bugs

### 5.3 Production Rollout
- Start at **10–20% rollout** with **Halt rollout** button ready
- Monitor **Android Vitals** for crashes and ANRs
- Expand to 100% over several days

---

## Part 6 — Post-Release Checklist

- [ ] Watch crashes and ANRs in **Android Vitals** (Play Console). The app deliberately has no analytics or crash-reporting SDK; its own crash report is local and shared only if the user chooses to
- [ ] Configure **Play App Signing** (Google manages your signing key after upload)
- [ ] Set up **in-app review** prompts using `com.google.android.play:review`
- [ ] Monitor **Android Vitals** weekly — target <1% crash rate
- [ ] Respond to user reviews within 48 hours
- [ ] Plan regular updates (bug fixes, features) — dormant apps get de-ranked
- [ ] Renew your privacy policy URL annually or when data practices change

---

## Part 7 — Play Store Policy Compliance Notes

Quill Notes is designed to comply with all current Play Store policies:

| Policy | Compliance |
|---|---|
| Target API level (API 35) | ✅ Required from Aug 2024 |
| No cleartext HTTP traffic | ✅ network_security_config.xml enforces HTTPS |
| Data Safety form completed | ✅ No user data collected server-side |
| Permissions justified | ✅ INTERNET (backup), BIOMETRIC (lock), POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM (task reminders at the time the user picks; falls back to inexact alarms if declined), RECEIVE_BOOT_COMPLETED (re-arm reminders). Complete the Play Console permission declaration for exact alarms |
| No deceptive behaviour | ✅ No ads, no hidden subscriptions |
| Privacy policy present | ⚠️ `docs/PRIVACY.md` is written; fill in the developer name and support email, host it, and add the URL |
| allowBackup=false | ✅ Protects encrypted key material |
| Encryption export compliance | ⚠️ Declare AES-256 use in the Export compliance questionnaire |

---

*Last updated: November 2026 — cross-check with developer.android.com and play.google.com/console/about/guides for any policy changes.*
