# notification-brain — architecture notes

These notes are bundled into the APK as an asset (`assets/ARCHITECTURE.md`) so the running app
carries a description of itself. They are the seed of the knowledge base that later stages of the
Evolving App series read when the app diagnoses its own failures. Keep them accurate; the app
shows them verbatim on the Lab tab. The build stamps `BuildConfig.GIT_SHA` so a reader (human or
model) can pair these notes with the exact source revision.

## Package map

```
com.ioscastaway.notificationbrain
├── App.kt                  Application: builds Graph, installs CrashCollector, imports pending crashes
├── Graph.kt                Manual DI. Every object below is created here, once.
├── brain/                  Pure Kotlin. No Android imports. Runs on the JVM in tests and in replay.
│   ├── NotificationFacts   Data about one notification + Decision/Verdict + NotificationClassifier seam
│   ├── Policy              The mutable part: ordered Rules with Match, serialized to files/policy.json
│   ├── PolicyEngine        HardKeep guard → first matching rule → default KEEP
│   ├── Feedback            FeedbackChip, Outcome (from REASON_*), Label, Labeler
│   ├── PolicyLearner       (Policy, facts, Feedback) → candidate Policy. Pure.
│   └── Replay              Replay.run, ReplayReport, PolicyCourt (adopt / reject / unchanged)
├── data/                   Room. NotificationRecord (archive row), CrashRecord, BrainDao, BrainRepository
├── platform/               Everything that touches Android APIs
│   ├── BrainNotificationListener   NotificationListenerService: posted → classify → archive → cancel
│   ├── FactsExtractor              StatusBarNotification → NotificationFacts (the only reader of sbn)
│   ├── FilePolicyStore             files/policy.json + StateFlow<Policy>
│   ├── ContentIntentCache          in-memory key → PendingIntent for "Reopen"
│   ├── SummaryNotifier             the one notification we post ("N tidied today")
│   ├── NotificationAccess          is the listener enabled / open the settings row
│   └── CrashCollector              uncaught handler → file; ApplicationExitInfo → CrashRecord
└── ui/                     Compose. MainActivity, BrainViewModel, BrainApp (Home/Archive/Learn/Lab)
```

## Data flow

```
system posts notification
  → BrainNotificationListener.onNotificationPosted
  → FactsExtractor.from(sbn)                      NotificationFacts
  → Graph.classifier.classify(facts)              Decision (PolicyEngine over current Policy)
  → BrainRepository.recordPosted                  NotificationRecord row (verdict, ruleId, because)
  → if DISMISS: ContentIntentCache.put, cancelNotification(key), SummaryNotifier.update

system removes notification (any reason)
  → onNotificationRemoved(sbn, ranking, reason)
  → Outcome from REASON_*                         USER_SWIPED / USER_OPENED / APP_REMOVED / ...
  → BrainRepository.recordRemoved                 fills outcome + removedAt on the PENDING row

user taps a chip on the Archive or Learn tab
  → BrainRepository.giveFeedback
  → PolicyLearner.apply(current, facts, feedback)  candidate Policy
  → PolicyCourt.judge(current, candidate, history) Replay over every archived row with its Label
  → Adopted: FilePolicyStore.save     Rejected: feedback stored, policy unchanged
```

## Invariants

- `brain/` never imports `android.*`. If it needs something from the platform, it takes a function
  or a value, not a Context.
- `HardKeep` is code. No policy, feedback, or future model revision may dismiss an ongoing
  notification, a call, an alarm, a group summary, a system/dialer package, or a one-time code.
- The archive row is written before the cancel is sent. A crash between the two loses a cancel,
  never a record.
- Every archived row can be replayed: `NotificationRecord.facts()` rebuilds the exact input the
  engine saw, and `NotificationRecord.label` derives the ground truth (explicit chip first,
  observed outcome second).
- A candidate policy is adopted only if `ReplayReport.passes` (zero false dismissals). Missed
  noise is reported, never blocking.
- Nothing leaves the device. The manifest has no `INTERNET` permission in stage 1.

## Known limits (see README → Limitations for the user-facing version)

- The listener sees a notification only after it is posted; a heads-up can flash before the cancel.
- `PendingIntent`s are not persistable, so "Reopen" degrades to launching the app after a restart.
- `ApplicationExitInfo` traces are only available for ANRs and some crash types.
- The archive is pruned at 60 days; replay only sees what is left.

## Where the next stages plug in

- Stage 1.5 (nightly reviser): implement `RuleOrigin.REVISER` producers as a `PolicyReviser` that
  reads chips, notes, and per-app statistics (never notification bodies), writes a candidate
  `Policy`, and submits it to `PolicyCourt`. Same court, same adoption rule.
- Stage 2 (logic): replace `Graph.classifier` with a loaded module implementing
  `NotificationClassifier`. The facts type and the replay harness stay.
- Stage 3 (code): `CrashRecord` rows + this file + `BuildConfig.GIT_SHA` are the diagnosis input.
