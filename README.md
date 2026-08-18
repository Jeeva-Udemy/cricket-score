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
