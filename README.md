# notification-brain

A notification listener that dismisses the notifications you would have swiped anyway, keeps
every one of them in an archive you can review and undo, and rewrites its own policy from what
you tell it there. Every new policy is tried against the whole archive before it is adopted.

> Stage 1 of the Evolving App series. The plan is an app that keeps a knowledge base of itself
> and fixes itself when it breaks. That is a ladder, and this is the first rung: an app that
> changes its rules, and carries the test harness it needs to be allowed to.

**Series:** Evolving App (stage 1) · Android × AI · Things Apple Would Never Let Me Do
**Status:** builds; 16 JVM tests pass. On a Galaxy Z Fold 8 (One UI 9.0, Android 17): the
listener binds, every notification is judged and archived, and the one-time-code guard fired on
a real device. On the Android 16 emulator: the full loop was verified end to end (swipe → Learn
tab → "Dismiss ones like this" → policy v2 → the next notification on that channel dismissed by
the new rule, visible in Archive with Reopen). Not yet measured: the false-dismissal rate over
real days of use. See *What I learned*.

## Why I built this

Two things Android hands to any app the user trusts with notification access, and iOS hands to
nobody: read every notification on the device, and remove any of them. That alone is a novelty.
What makes it an experiment is the third thing, which is easy to miss: when a notification leaves
the shade, the listener is told *why*. The user swiped this one. The user tapped that one. The app
took this one back. Those reasons are free labels, and an app that gets free labels can learn
without asking.

The larger question, and the reason this repository exists at all, is whether an app can change
its own behaviour safely. Notification triage is the smallest real problem I could find where
"safely" has teeth: the one fatal failure is swallowing a one-time code.

## The iOS brain

An iOS developer reads "dismiss other apps' notifications" and starts drafting the App Review
rejection in their head. There is no `NotificationListenerService` equivalent. `UNUserNotificationCenter`
lets an app manage its own notifications. Notification Service and Content extensions run on the
app's own pushes. Focus filters let the system tell an app what to hide, not the other way around.
The closest a third party gets is the Shortcuts app, which cannot read the shade either.

So the iOS brain never asks "what would I do with everyone's notifications", and never gets to the
interesting part: the labels.

## What Android exposes

- `NotificationListenerService`: bound by the system once the user grants notification access.
  `onNotificationPosted` for everything, `cancelNotification(key)` and `snoozeNotification` for
  everything clearable.
- `onNotificationRemoved(sbn, ranking, reason)`: `REASON_CANCEL` (user swiped this one),
  `REASON_CANCEL_ALL`, `REASON_CLICK`, `REASON_APP_CANCEL`, `REASON_LISTENER_CANCEL` and more.
- `ApplicationExitInfo` (API 30+): the system's record of this app's own crashes and ANRs, with
  traces, readable after the fact. Nothing acts on it in stage 1; it is collected because stage 3
  will need the history.

## Experiment

```
posted ──► facts ──► HardKeep guard ──► ordered rules ──► default KEEP
                                            │
                                         DISMISS ──► archive row ──► cancelNotification
removed(reason) ──► outcome on the row (USER_SWIPED / USER_OPENED / ...)

Archive / Learn tab: one chip ──► PolicyLearner ──► candidate policy
                                                        │
                       PolicyCourt: replay candidate against every archived row with its label
                                                        │
                                   passes ──► adopt        fails ──► refuse, keep the feedback
```

Four tabs:

- **Home**: notification access, today's counts, the number that matters (dismissed and later
  marked important, all time).
- **Archive**: everything the brain dismissed. *Reopen* fires the notification's own intent if the
  process is still alive, otherwise opens the app. *Teach* offers three chips: Good call, Was
  important, Never touch this app.
- **Learn**: notifications the brain kept and you swiped away one by one. Chips: Dismiss ones like
  this, Always dismiss this app. The optional "why" text is stored for the nightly reviser (stage
  1.5) and ignored by the stage-1 learner.
- **Lab**: the current policy as the app sees it, a *Replay against archive* button, crash and ANR
  history, and the bundled `ARCHITECTURE.md`.

The seed policy has no dismiss rules. The brain keeps everything until taught, and the hard-keep
guard (ongoing, not clearable, group summaries, calls, alarms, dialer and system packages,
anything that looks like a one-time code) is code, not policy. Feedback cannot reach it.

## Architecture

`brain/` is pure Kotlin with no Android imports: facts, policy, engine, learner, replay, court. It
runs in JVM tests and inside the app's replay. `platform/` is the only place that touches
`StatusBarNotification`, files, or the notification manager. `data/` is Room. The full package map
and invariants are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), which the build bundles as an
asset so the running app can show it on the Lab tab next to its own git revision. That file is the
first entry of the knowledge base the later stages read.

## What I learned

- **The removal reason is the whole experiment.** Without `reason` in `onNotificationRemoved` this
  would be a rules engine with a settings screen. With it, the archive labels itself: a targeted
  swipe within ten minutes is noise, a tap is important, "clear all" is nothing.
- **Order of operations matters more than the classifier.** Archive row first, contentIntent
  cached second, cancel third. A crash between steps loses a cancel, never a record.
- **An app cannot run its own tests, so it needs a courtroom.** `PolicyCourt` replays every
  candidate against the labeled history and refuses anything that would have dismissed a
  notification the user marked important. On the JVM this is four tests. On the device it is the
  reason "Dismiss ones like this" can be refused, which the snackbar explains.
- **Shell notifications are enough to verify the loop.** `cmd notification post` from adb posts
  as `com.android.shell`, which the listener treats like any other app. The whole
  swipe → teach → dismiss cycle was driven that way on the emulator.
- **The listener only sees a notification after it is posted.** A heads-up can flash before the
  cancel lands. Doing better needs `NotificationAssistantService`, which is a system role a
  sideloaded app can only take through `adb shell cmd notification allow_assistant`. That is a
  chapter for later, not a footnote.

## iOS comparison

| | Android | iOS |
|---|---|---|
| Read other apps' notifications | `NotificationListenerService`, user-granted | No public API |
| Dismiss other apps' notifications | `cancelNotification(key)` | No public API |
| Learn why a notification went away | `onNotificationRemoved(..., reason)` | No equivalent surface |
| An app rewriting its own rules from feedback | Possible | Possible; this stage is not Android-specific |
| Read the app's own crash history after the fact | `ApplicationExitInfo` | `MetricKit` diagnostics, delivered on the system's schedule |

The stage-1 mechanism (rules that change) is portable. The data it feeds on is not: the difference
is API-level. Apple does not expose the notification shade to third parties at all, so the labels
this app learns from do not exist on iOS.

## Limitations

- **Security and privacy.** Notification access means this app reads the content of every
  notification, including messages and codes. Everything stays on the device: the manifest has no
  `INTERNET` permission, the archive lives in app-private storage and is pruned at 60 days. The
  hard-keep guard never dismisses calls, alarms, ongoing notifications, or one-time codes, and it
  is not reachable from feedback. Dismissing removes a notification from the shade only; nothing
  in the source app is deleted or marked read.
- **Undo is partial.** A `PendingIntent` cannot be persisted. *Reopen* works fully while the
  listener process is alive and degrades to launching the app after a restart.
- **Heads-up flash.** See *What I learned*.
- **Sideloading on Android 13+.** Notification access is a restricted setting for sideloaded apps.
  Install with the installer recorded as Play (`adb install -i com.android.vending`) or clear the
  restriction in App info, and on an emulator just run
  `adb shell cmd notification allow_listener com.ioscastaway.notificationbrain/.platform.BrainNotificationListener`.
- **Samsung.** One UI has its own notification settings on top of Android's. Whether the listener
  stays bound across days of battery optimisation is not yet measured.
- **The false-dismissal rate is the result, and it is not in yet.** This README will get a table
  after a few weeks of real use.

## Setup

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -i com.android.vending -r app/build/outputs/apk/debug/app-debug.apk
```

Then Settings → Notifications → Device & app notifications → Notification Brain, or the *Open
settings* button on the Home tab.

## Verdict

Useful already in the narrow sense: the loop works and the guard held on a real device. Whether
it is useful in the wide sense depends on one number, false dismissals per week, which needs weeks.
As an experiment in an app changing itself, the interesting result is structural: the replay
harness is what makes the rewrite safe, and it will outlive the rules it currently protects.

## Next

- **Stage 1.5**: a nightly `PolicyReviser` that reads the chips, the "why" notes, and per-app
  statistics (never notification bodies), writes a candidate policy, and goes through the same
  court.
- **Stage 2**: the classifier behind `NotificationClassifier` becomes a loaded module.
- **Stage 3**: `CrashRecord` + `ARCHITECTURE.md` + `BuildConfig.GIT_SHA` become the input of an
  app that opens a pull request on itself.

---

**Reason I don't regret switching to Android** (number to be assigned in the profile index):
The phone's notifications are a data source, not just a distraction.
