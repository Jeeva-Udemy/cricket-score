# Cloud Sync (Mobile1 + Mobile2, TeamA + TeamB) — Setup

This lets two phones score the **same match** together: whoever starts the match gets a
6-character **Match Code**; the other phone enters it via **Home > Join Shared Match**. From
then on, every ball either phone enters is mirrored to the other in real time via Cloud
Firestore.

The code already in this repo (`sync/CloudSync.kt`, plus the small hooks in
`MatchSetupViewModel`, `ScoringViewModel`, `HomeViewModel`/`HomeScreen`, and
`CricketRepository`) is complete and will build as-is — but it talks to a **placeholder**
Firebase project (`app/google-services.json`), so syncing will silently fail (the app just
behaves as fully local/offline) until you point it at a real Firebase project. That's a
one-time, ~10 minute console setup:

## 1. Create a Firebase project

1. Go to https://console.firebase.google.com and create a new project (any name).
2. Inside it, click **Add app > Android**.
3. Package name: `com.example.cricketscorer` (must match exactly).
4. App nickname: anything (e.g. "Cricket Scorer").
5. Debug signing certificate SHA-1: not required for Firestore itself (only needed if you
   later add Firebase Auth or Dynamic Links). You can skip it.
6. Download the generated **`google-services.json`**.

## 2. Replace the placeholder file

Replace `app/google-services.json` in this repo with the one you just downloaded. (The
placeholder that ships in this repo is only there so the project still compiles before you've
done this step — it has no real credentials in it.)

## 3. Enable Firestore

1. In the Firebase console, go to **Build > Firestore Database > Create database**.
2. Start in **test mode** for now (open rules with an expiry) — or paste the rules from
   `firestore.rules` in this repo right away (recommended, see below).
3. Pick any region close to your users.

## 4. Security rules

This app has no login — the "password" for a match is simply knowing its random 6-character
code, similar to a Google Meet/Zoom link. `firestore.rules` (included in this repo) reflects
that:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /liveMatches/{shareCode} {
      allow read, write: if true;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Paste it into **Firestore Database > Rules** in the console and click **Publish**. Every
match document lives under `liveMatches/{shareCode}` and holds one JSON blob of that match's
score state (same format as the existing Google Drive backup feature) — nothing else in your
project is reachable.

Note: because codes aren't ever expired or deleted automatically, `liveMatches` will
accumulate old finished matches over time. For a personal/team project this is harmless (each
doc is a few KB), but if you want it tidy, add a scheduled Cloud Function that deletes
documents whose `updatedAt` is older than, say, 30 days — this repo doesn't include one since
it needs Firebase's paid Blaze plan for scheduled functions.

## 5. Build & test

1. Sync Gradle / rebuild the app (Android Studio will pick up the new `google-services.json`
   and the `com.google.gms.google-services` plugin automatically).
2. Install the app on two devices (or one device + one emulator).
3. On **Mobile1**: Start a new match as normal. Once scoring opens, the top bar shows
   **"Match Code: XXXXXX"**.
4. On **Mobile2**: Home screen > **Join Shared Match** > type in that code > Join. It opens
   straight into the same live match.
5. Score a ball on either phone — it appears on the other within a second or two.

## How it works / limitations

- **Last write wins, not a merge.** Each device pushes its *entire* current match state
  (match + innings + every ball) as one JSON document whenever anything changes locally, and
  applies whatever the other device last pushed. This reuses the exact same "replace rows by
  id" logic already used by the existing Google Drive Backup & Resync feature
  (`CricketRepository.restoreFromBackup` / the new `applyMatchSnapshot`), rather than a
  field-by-field merge — simple and robust for one-ball-at-a-time scoring, but if both phones
  score within the same instant, one ball can get overwritten. In practice, agree on who is
  entering the *current* ball (e.g. hand-off scoring duties between overs/innings) rather than
  both tapping simultaneously.
- **Requires internet on both phones.** If offline, each phone keeps scoring locally (nothing
  crashes), and the next time it's back online its next change is pushed/pulled as normal —
  but changes made while offline on *both* phones at once can conflict per the point above.
- **Squads/players are not synced** — only the match, its innings, and its balls. Each device
  keeps its own local squad list.
- Every match gets a share code and an (empty, harmless) push attempt when created, even if
  you never intend to share it — this just costs one small Firestore write. If you'd rather
  opt in per-match instead, that would be a good place to add a "Share this match" toggle to
  `MatchSetupScreen`/`MatchSetupViewModel` in a follow-up.
