package com.ioscastaway.notificationbrain.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ioscastaway.notificationbrain.App
import com.ioscastaway.notificationbrain.brain.Adoption
import com.ioscastaway.notificationbrain.brain.FeedbackChip
import com.ioscastaway.notificationbrain.brain.ReplayReport
import com.ioscastaway.notificationbrain.data.NotificationRecord
import com.ioscastaway.notificationbrain.platform.BrainNotificationListener
import com.ioscastaway.notificationbrain.platform.ContentIntentCache
import com.ioscastaway.notificationbrain.platform.NotificationAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph get() = App.graph
    private val repo get() = graph.repository

    var accessGranted by mutableStateOf(NotificationAccess.isGranted(app))
        private set
    /** Selected bottom tab. Lives here so a new intent (summary notification tap) can change it. */
    var tab by mutableIntStateOf(MainActivity.TAB_HOME)
    var message by mutableStateOf<String?>(null)
    var replayReport by mutableStateOf<ReplayReport?>(null)
        private set
    val architectureNotes: String by lazy {
        runCatching { app.assets.open("ARCHITECTURE.md").bufferedReader().use { it.readText() } }
            .getOrDefault("(docs/ARCHITECTURE.md was not bundled)")
    }

    private val eagerly = SharingStarted.WhileSubscribed(5_000)
    private val today = BrainNotificationListener.startOfToday()
    val dismissed = repo.dismissed().stateIn(viewModelScope, eagerly, emptyList())
    val candidates = repo.swipedCandidates().stateIn(viewModelScope, eagerly, emptyList())
    val seenToday = repo.seenSince(today).stateIn(viewModelScope, eagerly, 0)
    val dismissedToday = repo.dismissedSince(today).stateIn(viewModelScope, eagerly, 0)
    val falseDismissals = repo.falseDismissals().stateIn(viewModelScope, eagerly, 0)
    val policy = graph.policyStore.policy
    val crashes = repo.crashes().stateIn(viewModelScope, eagerly, emptyList())

    fun refreshAccess() {
        accessGranted = NotificationAccess.isGranted(getApplication())
    }

    fun giveFeedback(record: NotificationRecord, chip: FeedbackChip, note: String?) {
        viewModelScope.launch {
            message = when (val result = repo.giveFeedback(record.id, chip, note)) {
                is Adoption.Adopted -> "Policy v${result.policy.version} adopted. ${result.report.summary()}"
                is Adoption.Rejected -> "Refused: that rule would have dismissed ${result.report.falseDismissals.size} " +
                    "notification(s) you marked important. Feedback saved, policy unchanged."
                Adoption.Unchanged -> "Noted. No rule change."
            }
        }
    }

    fun reopen(record: NotificationRecord) {
        message = when (graph.contentIntentCache.reopen(record.key, record.packageName)) {
            ContentIntentCache.Reopened.Content -> "Opened the notification's own target."
            ContentIntentCache.Reopened.AppOnly -> "The notification's intent is gone (process restarted); opened the app instead."
            ContentIntentCache.Reopened.Nothing -> "That app has no launcher activity."
        }
    }

    fun runReplay() {
        viewModelScope.launch { replayReport = repo.replayCurrent() }
    }

    fun resetPolicy() {
        viewModelScope.launch { repo.resetPolicy(); message = "Policy reset to seed."; replayReport = null }
    }
}
