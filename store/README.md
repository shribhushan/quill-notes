# Store listing assets

Text for the Google Play listing, ready to paste into **Play Console → Grow → Store presence → Main store
listing** (and the translated listings for Hindi and Marathi).

```
store/listing/
  en-US/  hi-IN/  mr-IN/
    title.txt              ≤ 30 characters
    short_description.txt  ≤ 80 characters
    full_description.txt   ≤ 4000 characters
    release_notes.txt      ≤ 500 characters   (What's new)
```

All files are within Play's limits (checked when they were written). Keep the claims in them true: the
listing says what the app does in this version, e.g. cloud backup is **Google Drive only** (OneDrive sign-in is
not finished, see `ROADMAP.md`).

## Still needed from you (cannot be generated here)

| Item | Spec | Note |
|---|---|---|
| App icon | 512 × 512 PNG, ≤ 1 MB | Export from the launcher icon artwork |
| Feature graphic | 1024 × 500 PNG/JPG | |
| Phone screenshots | 2–8 images, each side 320–3840 px, **longer side ≤ 2× the shorter side** | The CI screenshots (`ui-screenshots` branch) are rendered at 1080 × 2400, which is slightly too tall for Play (2.22 : 1). Re-shoot with a 16 : 9 profile (for example the Pixel 2 profile) or crop |
| Privacy policy URL | Public HTTPS page | Host `docs/PRIVACY.md` (fill in the developer name and support email first) |
| Data safety form | In Play Console | See `PLAY_STORE_PUBLISHING_GUIDE.md`, part 4.3 |
| Release signing key | Upload key / Play App Signing | See the guide, part 3; credentials go in `keystore.properties`, never in git |

## Translating a new listing

Copy `listing/en-US` to a new folder named after the Play locale, translate the four files, and keep to the
limits above.
