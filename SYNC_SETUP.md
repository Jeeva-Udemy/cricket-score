# Cloud Sync (Mobile1 + Mobile2, TeamA + TeamB) — Setup

This lets two phones score the **same match(es)** together via a **Room**: one phone creates a
room and gets a 6-character **Room Code**; the other phone enters it via **Home > Room > Join
Room**. From then on, every ball either phone enters is mirrored to the other in real time via
Cloud Firestore — and because the code belongs to the *room* rather than to one match, the
same two phones can start match after match in it (req: "3 to 5 matches in the same day with
the same squad") without sharing a new code every time. At most two devices may hold the
room's two slots at once (one per team); either phone can tap **Exit Room** to free its slot
so a replacement device can join.

The code already in this repo (`sync/CloudSync.kt`, `data/RoomStore.kt`, plus the small hooks
in `MatchSetupViewModel`, `ScoringViewModel`, `HomeViewModel`/`HomeScreen`, and
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

This app has no login — the "password" for a room is simply knowing its random 6-character
code, similar to a Google Meet/Zoom link. `firestore.rules` (included in this repo) reflects
that:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /liveMatches/{roomCode} {
      allow read, write: if true;
    }
    match /rooms/{roomCode} {
      allow read, write: if true;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Paste it into **Firestore Database > Rules** in the console and click **Publish**. Every room's
*current* match lives under `liveMatches/{roomCode}` and holds one JSON blob of that match's
score state (same format as the existing Google Drive backup feature); `rooms/{roomCode}`
holds just the room's two device slots and which team each is scoring for — nothing else in
your project is reachable. The "only 2 devices per room" limit is enforced by the app when
joining, not by these rules (matching the rest of this app's no-auth model) — see
`CloudSync.joinRoom`.

Note: because codes aren't ever expired or deleted automatically, `liveMatches` and `rooms`
will accumulate old rooms over time. For a personal/team project this is harmless (each doc is
a few KB), but if you want it tidy, add a scheduled Cloud Function that deletes documents whose
`updatedAt` is older than, say, 30 days — this repo doesn't include one since it needs
Firebase's paid Blaze plan for scheduled functions.

## 5. Build & test

1. Sync Gradle / rebuild the app (Android Studio will pick up the new `google-services.json`
   and the `com.google.gms.google-services` plugin automatically).
2. Install the app on two devices (or one device + one emulator).
3. On **Mobile1**: Home screen > **Room** > **Create Room**. Note the Room Code shown.
4. On **Mobile2**: Home screen > **Room** > enter that code (or scan the QR code) > **Join
   Room**.
5. On **Mobile1**: tap **Start Match** from the Room dialog, set up the match as normal, and
   pick which team **this** phone is scoring the 1st innings for. The other phone picks up the
   match automatically — no separate "join" step needed for it.
6. Score a ball on either phone — it appears on the other within a second or two.
7. Once the match finishes, either phone can tap **Start Next Match** from the Room dialog to
   begin the next match in the same room, still using the same Room Code.

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
  keeps its own local squad list; picking the same saved squad on both phones when setting up
  each match in the room keeps names consistent.
- **A match only gets a share code when it's created inside a Room.** A match started without
  an active room stays purely local/offline (no Firestore write at all) — Rooms are now the
  only way to share a match live between two phones, replacing the old per-match "Join Shared
  Match" flow.
- **Room slots are freed by an explicit Exit, not automatically.** If a phone goes offline or
  the app is closed without tapping **Exit Room**, it still holds its slot — the other user
  needs that phone's owner to exit (or, in a pinch, exit the room and both rejoin) before a
  third device can take the freed spot.
