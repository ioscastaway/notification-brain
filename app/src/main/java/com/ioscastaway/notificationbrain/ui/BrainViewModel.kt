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
import com.ioscastaway.notificationbrain.brain.CompiledRules
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
    val canCompile: Boolean get() = repo.canCompile
    /** Rows for what is in the shade right now; null while the listener is not bound. */
    var shade by mutableStateOf<List<NotificationRecord>?>(null)
        private set
    var compileState by mutableStateOf<CompileState>(CompileState.Idle)
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

    fun refreshShade() {
        viewModelScope.launch {
            shade = BrainNotificationListener.instance?.let { runCatching { it.shade() }.getOrNull() }
        }
    }

    /** After any adoption: judge the shade again so the change is visible immediately. */
    private suspend fun applyToShade(): String {
        val n = BrainNotificationListener.instance?.let { runCatching { it.applyPolicyToShade() }.getOrDefault(0) } ?: 0
        refreshShade()
        return if (n > 0) " Dismissed $n from the shade." else ""
    }

    fun giveFeedback(record: NotificationRecord, chip: FeedbackChip, note: String?) {
        viewModelScope.launch {
            val result = repo.giveFeedback(record.id, chip, note)
            message = when (result) {
                is Adoption.Rejected -> describe(result).replace("Policy unchanged.", "Feedback saved, policy unchanged.")
                Adoption.Unchanged -> "Noted. No rule change."
                else -> describe(result) + applyToShade()
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

    fun compileInstruction(text: String) {
        if (text.isBlank()) return
        compileState = CompileState.Compiling
        viewModelScope.launch {
            compileState = runCatching { repo.compileInstruction(text) }
                .fold({ CompileState.Preview(text, it) }, { CompileState.Failed(it.message ?: it.toString()) })
        }
    }

    fun adoptInstruction() {
        val preview = compileState as? CompileState.Preview ?: return
        viewModelScope.launch {
            val result = repo.adoptInstruction(preview.text, preview.compiled)
            message = describe(result) + if (result is Adoption.Adopted) applyToShade() else ""
            compileState = CompileState.Idle
        }
    }

    fun discardPreview() {
        compileState = CompileState.Idle
    }

    fun removeInstruction(id: String) {
        viewModelScope.launch {
            val result = repo.removeInstruction(id)
            message = describe(result) + if (result is Adoption.Adopted) applyToShade() else ""
        }
    }

    private fun describe(result: Adoption): String = when (result) {
        is Adoption.Adopted -> "Policy v${result.policy.version} adopted. ${result.report.summary()}"
        is Adoption.Rejected -> "Refused: that would have dismissed ${result.report.falseDismissals.size} " +
            "notification(s) you marked important. Policy unchanged."
        Adoption.Unchanged -> "No rule change."
    }

    fun runReplay() {
        viewModelScope.launch { replayReport = repo.replayCurrent() }
    }

    fun resetPolicy() {
        viewModelScope.launch { repo.resetPolicy(); message = "Policy reset to seed."; replayReport = null }
    }
}

sealed class CompileState {
    data object Idle : CompileState()
    data object Compiling : CompileState()
    data class Preview(val text: String, val compiled: CompiledRules) : CompileState()
    data class Failed(val error: String) : CompileState()
}
