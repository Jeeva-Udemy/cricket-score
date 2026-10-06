# Cricket Scorer (Jetpack Compose + MVVM + Room)

## Building the APK with no local Android Studio (GitHub Actions)
This repo includes `.github/workflows/build-apk.yml`, which builds a debug
APK on GitHub's servers every time you push to `main` — you never need
Android Studio or the SDK installed locally.

1. Push this project to your GitHub repo (see commands below).
2. Go to your repo → **Actions** tab. The "Build APK" workflow starts
   automatically (or click **Run workflow** to trigger it manually).
3. When it finishes (green check), click into the run → scroll to
   **Artifacts** → download `cricket-scorer-debug-apk`. That's a zip
   containing `app-debug.apk` — copy it to your phone and install it
   (enable "install from unknown sources" if prompted).

### Pushing this code to your repo
From inside the extracted `CricketScorer/` folder:
```bash
cd CricketScorer
git init
git branch -M main
git remote add origin https://github.com/Jeeva-Udemy/cricket-score.git
git add .
git commit -m "Initial commit: Cricket Scorer Android app"
git push -u origin main
```
Notes:
- If `git remote add origin` fails with "remote already exists" (e.g. you
  already ran `git init` before), use
  `git remote set-url origin https://github.com/Jeeva-Udemy/cricket-score.git`
  instead.
- GitHub no longer accepts your account password over HTTPS. When `git push`
  prompts for a password, use a **Personal Access Token** instead (GitHub →
  Settings → Developer settings → Personal access tokens → generate one with
  `repo` scope, and paste it in place of the password), or push over SSH if
  you have an SSH key set up.
- Every subsequent push to `main` will re-trigger the build automatically.

## One-time setup: Google Sign-In for Backup & Resync
The "Backup & Resync" feature signs the user into Google and reads/writes a single
file in their Drive "App Data" folder. Google matches sign-in attempts against an
**OAuth 2.0 Client ID** registered in Google Cloud Console for this exact
`applicationId` + signing-certificate fingerprint — if that registration doesn't
exist (or doesn't match), sign-in fails with `DEVELOPER_ERROR`, which is what you
were seeing.

This repo now signs every debug build (locally and in GitHub Actions) with a fixed,
committed keystore at `app/debug.keystore`, so the fingerprint below is permanent —
it will never change between builds or machines. You only need to register it once:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/), create a
   project (or pick an existing one).
2. **APIs & Services → Library** → search "Google Drive API" → **Enable**.
3. **APIs & Services → OAuth consent screen** → configure it (External is fine for
   testing; add your own Google account under "Test users" if the app stays in
   "Testing" mode).
4. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.example.cricketscorer`
   - SHA-1 certificate fingerprint: `7F:12:D4:09:6A:EC:67:B6:9D:3D:24:9F:A2:90:3C:ED:D6:06:39:59`
5. Save. No download or code change is needed afterwards — Android matches sign-in
   attempts to this client ID purely by package name + SHA-1 at runtime.

Reinstall the APK on your device after registering (an existing install can cache
the old failed sign-in state), then try Backup & Resync again.

⚠️ If you ever regenerate `app/debug.keystore` (e.g. `keytool -genkeypair ...`
again), the SHA-1 above changes and you must update the OAuth client to match.
Otherwise, leave the committed keystore file alone.

## How to open (optional — only if you later get Android Studio)
1. Open the `CricketScorer/` folder in Android Studio (Koala or newer).
2. Let Gradle sync (uses KSP for Room's annotation processing — no `kapt` needed).
3. Run on a device/emulator with API 24+.

## Project layout
```
app/src/main/java/com/example/cricketscorer/
├── model/Enums.kt                 TossDecision, ExtraType, WicketType
├── data/
│   ├── MatchEntity.kt             Room entity — 1 row per match
│   ├── InningsEntity.kt           Room entity — 1 row per innings (running score)
│   ├── BallEventEntity.kt         Room entity — 1 row per ball (audit log / undo)
│   ├── Converters.kt              Room TypeConverters for enums
│   ├── CricketDao.kt              All queries (Flow-based reads, suspend writes)
│   ├── CricketDatabase.kt         RoomDatabase singleton
│   └── CricketRepository.kt       Single source of truth used by ViewModels
├── viewmodel/
│   ├── MatchSetupViewModel.kt     Form state + validation, creates Match + Innings #1
│   ├── ScoringViewModel.kt        The scoring engine (see below)
│   └── ViewModelFactory.kt        Manual DI (no Hilt, to keep the sample self-contained)
├── ui/
│   ├── MatchSetupScreen.kt        Team names, overs, toss winner + decision
│   └── ScoringScreen.kt           Live scoreboard + run/extra/wicket controls
├── CricketApplication.kt          Provides the Database/Repository singletons
└── MainActivity.kt                NavHost: "setup" -> "scoring/{matchId}/{inningsId}"
```

## Scoring engine rules (`ScoringViewModel.applyDelivery`)
- **Legal vs illegal delivery**: WIDE and NO_BALL do **not** consume one of the 6
  balls in the over. BYE and LEG_BYE **do** consume a ball (they're legal
  deliveries, the batsman just didn't hit it).
- **Over completion**: once 6 legal balls have been bowled, `completedOvers`
  increments, `ballsThisOver` resets to 0, and the strike automatically rotates
  (the batsmen change ends between overs).
- **Strike rotation mid-over**: rotates whenever the *run count taken by the
  batsmen* is odd (1 or 3). This applies to normal runs and to any runs run on
  a wide/no-ball/bye/leg-bye.
- **Wickets**: increments the wicket count and brings in "the next batsman"
  (tracked as an incrementing number, since no player roster is captured — see
  "Simplifications" below) at the striker's end. For a run-out you can record
  how many runs were completed before the dismissal.
- **Innings switch**: triggered automatically when overs are used up, the side
  is all out (10 wickets), or (in the 2nd innings) the target is reached.
  On switch, the batting/bowling teams swap and `target = firstInningsRuns + 1`.
- **Match completion**: computed after the 2nd innings ends — win by wickets,
  win by runs, or a tie — and persisted on the `MatchEntity`.
- **Undo**: deletes the last `BallEventEntity` and reverses its exact effect on
  the innings totals/over count, including correctly stepping back over an
  over boundary.

## Simplifications (called out explicitly, since a full production scorer is a
much larger project)
- No player roster/name input — batsmen are tracked as "Batsman 1, 2, 3…" and
  there's no individual batsman/bowler stats (runs faced, economy, etc.). This
  was out of scope per the requirements (only team names were requested), but
  the `strikerBatsmanNumber` field on `BallEventEntity` gives you a hook to
  build per-batsman stats later.
- No bowler tracking/rotation — the requirements didn't call for a bowling
  team roster either.
- Wicket + wide/no-ball combinations (e.g., run-out off a wide) aren't modeled
  as a single compound event — the UI treats "extra" and "wicket" as separate
  actions, matching how most simple scoring apps work.
- Follow-on, DLS/rain rules, and super overs are not implemented.

## Recent changes
- **Dashboard** — new Home tile next to Share Data. All-time numbers across every match, drawn
  as bar graphs: wins/losses per team, head-to-head, most runs and most wickets by team, 4s and 6s
  per team, per-team records (most 4s, most 6s, highest innings score, highest overall score), and
  Top-10 lists (overall points, run scorers, wicket takers). Super Overs only decide a tied
  match's winner; they are not counted in runs, wickets or boundaries. Respects Merge players.
- **Match History by date** — the page now lists only the dates played (newest first, with the
  match count); tap a date to expand/collapse that day's matches. The calendar filter still works
  and opens the chosen day automatically.
- **Live Match tab** — Home "Tournaments" is now **Live Match**. It lists every match being
  scored right now on any phone with the app (teams, score, overs, target, batters, bowler,
  this-over balls), updating in real time. Matches appear while scored inside a Room (that is
  what syncs to the cloud); they disappear when completed or after 12 h without updates.
  Data lives in Firestore `live_scores/{roomCode}` (summary only) — **redeploy `firestore.rules`**.
- **Run-out fix** — the Wicket dialog now asks *which batsman* is run out (by name, nothing
  pre-selected), the runs completed, and which end the new batsman comes in at, with a
  "Next ball: X on strike" preview. The scorecard now marks the batsman who was actually
  dismissed (it used to mark the striker when the non-striker was run out), and run-outs are
  no longer credited to the bowler.
- **Rankings / Player Stats** — players are identified by name + team, so two players with
  the same name in different teams stay separate. Names differing only in case/spaces are
  treated as the same player.
- **Merge players** — Player Stats / Rankings → merge icon (or long-press a player), tick the
  duplicate entries (suggested duplicates are shown), choose the correct name → Merge. Stats
  are combined without rewriting scorecards; undo from the history icon. (DB v8: `player_merges`.)
- **Match Dashboard** — dashboard icon on the scoring screen, or "Match summary / share" in
  Match History: the whole match as one image, shareable straight to WhatsApp.
- **Share Data** — Home → Share Data sends squads and/or matches as a file (e.g. on WhatsApp).
  The teammate taps the file and opens it with Wickt, or uses Import from File; data is added
  to theirs (no deletion, duplicates skipped). Match History selection mode can also share just
  the selected matches.

## Team Sharing (Share Data page)
Home → **Share Data**.
- **First launch** asks for name, mobile number and Gmail (can be skipped, and edited later
  under Share Data → Edit profile).
- **Admin login**: username `root`, password `root` (stored as SHA-256 in
  `data/UserProfileStore.kt` — change both hashes before sharing the app widely).
- **Roles by mobile number**
  - Admin — all options: add/remove members, change roles, upload data.
  - Manager — add members (as Manager or User) and upload data.
  - User — read only; receives the team's data.
- **Adding people**: *Add* → *Pick from Contacts* (no contacts permission needed) or type the
  number. When that person installs the app and enters the same number, the team's matches
  and squads download automatically (on app open, or *Sync now*).
- Admin/Manager phones upload their matches (only the ones that changed) and squads. Imports
  merge into local data; nothing is deleted on anyone's phone.
- Firestore collections: `team_members`, `team_matches`, `team_squads` — deploy the updated
  `firestore.rules`.

**Team Sharing security:** there is no SMS/OTP verification, so a phone is trusted to be the
number typed into it, and the root/root login ships inside the app. Fine for a friendly local
team; for anything more, switch to Firebase Phone Auth + rules that check roles.

## Automatic app updates (Firebase App Distribution)
The email entered in the app is saved to Firestore `tester_requests`. On every push to `main`,
the GitHub workflow builds the APK, adds all new emails as testers (group `team`), uploads
the build to App Distribution (Firebase emails every tester a one-tap install link / notifies
the Firebase App Tester app), and writes `app_release/latest` so the app shows a
"New version available → Update" card on Home.

One-time setup (in the Firebase project owned by your account):
1. Firebase console → **App Distribution** → Get started (for the Android app).
2. Project settings → **General**: copy the Android **App ID** (`1:…:android:…`).
3. Project settings → **Service accounts** → Generate new private key (JSON). In Google Cloud
   IAM give that service account the roles **Firebase App Distribution Admin** and
   **Cloud Datastore User**.
4. GitHub repo → Settings → Secrets and variables → Actions → add
   `FIREBASE_APP_ID` (step 2) and `FIREBASE_SERVICE_ACCOUNT` (the whole JSON from step 3).
5. Deploy `firestore.rules`.

Each tester accepts the first invitation email once (signing in with that Google account);
after that every new build reaches them automatically. Android never allows silent installs,
so the final "Install" tap is always the tester's.

## Backup & Resync = entire app
Choosing **Everything** (the default) backs up and restores the whole app: matches, innings,
balls, squads, players, player merges (Rankings / Player Stats), plus the Rooms list and the
current room, which team this phone scores for in shared matches, remembered player names,
profile (name / mobile / email) and the phone's sync id (see `backup/AppSettingsBackup.kt`).
Not included on purpose: the root-admin login. Automatic backups now also run when squads or
player merges change, and never upload from an empty phone over an existing backup.
