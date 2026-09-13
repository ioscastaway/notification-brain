package com.ioscastaway.notificationbrain.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioscastaway.notificationbrain.BuildConfig
import com.ioscastaway.notificationbrain.brain.Digest
import com.ioscastaway.notificationbrain.brain.FeedbackChip
import com.ioscastaway.notificationbrain.brain.Verdict
import com.ioscastaway.notificationbrain.data.CrashRecord
import com.ioscastaway.notificationbrain.data.NotificationRecord
import com.ioscastaway.notificationbrain.platform.NotificationAccess
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainApp(vm: BrainViewModel) {
    var tab by vm::tab
    val snackbar = remember { SnackbarHostState() }
    var feedbackTarget by remember { mutableStateOf<NotificationRecord?>(null) }
    var feedbackFromShade by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(listOf("Notification Brain", "Archive", "Teach", "Rules", "Lab")[tab]) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Inbox, null) }, label = { Text("Archive") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.School, null) }, label = { Text("Teach") })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Rule, null) }, label = { Text("Rules") })
                NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Science, null) }, label = { Text("Lab") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            0 -> HomeScreen(vm, m)
            1 -> ArchiveScreen(vm, m, onFeedback = { feedbackTarget = it; feedbackFromShade = false })
            2 -> TeachScreen(vm, m, onTeach = { record, fromShade -> feedbackTarget = record; feedbackFromShade = fromShade })
            3 -> RulesScreen(vm, m)
            else -> LabScreen(vm, m)
        }
    }

    feedbackTarget?.let { record ->
        FeedbackSheet(record, fromShade = feedbackFromShade, onDismiss = { feedbackTarget = null }) { chip, note ->
            vm.giveFeedback(record, chip, note)
            feedbackTarget = null
        }
    }
}

@Composable
private fun HomeScreen(vm: BrainViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val seen by vm.seenToday.collectAsStateWithLifecycle()
    val dismissed by vm.dismissedToday.collectAsStateWithLifecycle()
    val wrong by vm.falseDismissals.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notification access", style = MaterialTheme.typography.titleMedium)
                Text(if (vm.accessGranted) "Granted. The listener is bound and judging." else "Not granted. Nothing is being read.")
                if (!vm.accessGranted) Button(onClick = { context.startActivity(NotificationAccess.settingsIntent(context)) }) { Text("Open settings") }
                if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { askPost.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow the daily summary notification")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Text("Seen: $seen")
                Text("Dismissed: $dismissed")
                Text("Dismissed and later marked important (all time): $wrong")
                Text("Policy v${policy.version}, ${policy.rules.size} rule(s)")
            }
        }
        Text(
            "Everything stays on this phone. No network permission. The archive keeps 60 days.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Build ${BuildConfig.VERSION_NAME} @ ${BuildConfig.GIT_SHA}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TeachScreen(vm: BrainViewModel, modifier: Modifier, onTeach: (NotificationRecord, Boolean) -> Unit) {
    val shade = vm.shade
    val swiped by vm.candidates.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("In your shade now", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = vm::refreshShade) { Text("Refresh") }
            }
            Text(
                "Go through what is up right now and tell the brain, one by one, whether it should have dismissed it. " +
                    "This is the fastest way to teach it while it knows nothing.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            shade == null -> item { Text("The listener is not connected. Grant notification access on Home.", style = MaterialTheme.typography.bodyMedium) }
            shade.isEmpty() -> item { Text("The shade is empty.", style = MaterialTheme.typography.bodyMedium) }
            else -> items(shade, key = { "shade-" + it.id }) { r -> RecordCard(r, onFeedback = { onTeach(r, true) }, onReopen = null) }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("You swiped these away", style = MaterialTheme.typography.titleMedium)
            Text("Kept by the brain, then dismissed by you one at a time. Should it have dismissed them?", style = MaterialTheme.typography.bodySmall)
        }
        if (swiped.isEmpty()) item { Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium) }
        else items(swiped, key = { "swiped-" + it.id }) { r -> RecordCard(r, onFeedback = { onTeach(r, false) }, onReopen = null) }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(vm: BrainViewModel, modifier: Modifier, onFeedback: (NotificationRecord) -> Unit) {
    val dismissed by vm.dismissed.collectAsStateWithLifecycle()
    val unreviewed by vm.unreviewed.collectAsStateWithLifecycle()
    val recordCount by vm.recordCount.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val byId = remember(dismissed) { dismissed.associateBy { it.id } }
    val groups = remember(dismissed) {
        Digest.group(dismissed.map { Digest.Item(it.id, it.appLabel, it.packageName, it.title, it.text, it.postedAt, it.reviewedAt != null) }, System.currentTimeMillis())
    }

    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Digest") }
                SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("All") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (unreviewed == 0) "Everything reviewed." else "$unreviewed to review", style = MaterialTheme.typography.bodyMedium)
                if (unreviewed > 0) TextButton(onClick = vm::markAllReviewed) { Text("Mark all reviewed") }
            }
        }
        if (dismissed.isEmpty()) item { Text("Nothing dismissed yet. The seed policy keeps everything; teach it from the Teach tab.") }
        else if (mode == 0) {
            groups.forEach { day ->
                item(key = "day-${day.dayStart}") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("${day.label} · ${day.total}" + if (day.unreviewed > 0) " · ${day.unreviewed} new" else "", style = MaterialTheme.typography.titleMedium)
                    }
                }
                day.apps.forEach { app -> item(key = "app-${day.dayStart}-${app.packageName}") { AppGroupCard(app, byId, vm, onFeedback, onConfirm = { confirm = it }) } }
            }
        } else {
            items(dismissed, key = { it.id }) { r ->
                RecordCard(r, onFeedback = { onFeedback(r) }, onReopen = { vm.reopen(r) }, onDelete = { vm.deleteRecords(listOf(r.id)) })
            }
        }
        item { StorageCard(recordCount, vm, onConfirm = { confirm = it }) }
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete?") },
            text = { Text("$text\n\nRules are not affected. Anything you taught on these notifications is kept as a lesson for the court; only the notification content goes.") },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppGroupCard(
    app: Digest.AppGroup,
    byId: Map<Long, NotificationRecord>,
    vm: BrainViewModel,
    onFeedback: (NotificationRecord) -> Unit,
    onConfirm: (Pair<String, () -> Unit>) -> Unit,
) {
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(app.unreviewed > 0 && app.items.size <= 3) }
    val ids = app.items.map { it.id }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${app.appLabel} · ${app.items.size}" + if (app.unreviewed > 0) " · ${app.unreviewed} new" else "", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
            }
            if (!expanded) {
                app.items.take(2).forEach { Text("· ${it.title ?: it.text ?: ""}", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                if (app.items.size > 2) Text("· and ${app.items.size - 2} more", style = MaterialTheme.typography.bodySmall)
            } else {
                app.items.forEach { item ->
                    val r = byId[item.id] ?: return@forEach
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text((if (item.reviewed) "" else "● ") + (r.title ?: "") + " · " + timeOf(r.postedAt), style = MaterialTheme.typography.labelMedium)
                        r.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { vm.reopen(r) }) { Text("Reopen") }
                            TextButton(onClick = { onFeedback(r) }) { Text(if (r.feedbackChip == null) "Teach" else "Change") }
                            TextButton(onClick = { vm.deleteRecords(listOf(r.id)) }) { Text("Delete") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.unreviewed > 0) TextButton(onClick = { vm.markReviewed(ids) }) { Text("Mark reviewed") }
                TextButton(onClick = { onConfirm("Delete all ${app.items.size} from ${app.appLabel} in this group?" to { vm.deleteRecords(ids) }) }) { Text("Delete group") }
            }
        }
    }
}

@Composable
private fun StorageCard(recordCount: Int, vm: BrainViewModel, onConfirm: (Pair<String, () -> Unit>) -> Unit) {
    val bytes = remember(recordCount) { vm.databaseBytes() }
    val lessons by vm.lessonCount.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Text("$recordCount records (kept and dismissed) · $lessons lessons kept from deleted ones · ${"%.1f".format(bytes / 1024.0 / 1024.0)} MB on disk. The app prunes at 60 days; anything sooner is yours. Deleting never touches rules.", style = MaterialTheme.typography.bodySmall)
            FlowRowButtons(
                "Delete reviewed" to { onConfirm("Delete every dismissal you have already reviewed?" to vm::deleteReviewed) },
                "Older than 30 days" to { onConfirm("Delete every record older than 30 days?" to { vm.deleteOlderThan(30) }) },
                "Older than 7 days" to { onConfirm("Delete every record older than 7 days?" to { vm.deleteOlderThan(7) }) },
                "Delete everything" to { onConfirm("Delete the whole archive? The court will have no history until new notifications arrive." to vm::deleteAll) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(vararg buttons: Pair<String, () -> Unit>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (label, action) -> OutlinedButton(onClick = action) { Text(label) } }
    }
}

@Composable
private fun RecordCard(r: NotificationRecord, onFeedback: () -> Unit, onReopen: (() -> Unit)?, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${r.appLabel} · ${timeOf(r.postedAt)}", style = MaterialTheme.typography.labelMedium)
            r.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            r.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4) }
            Text("${r.verdict} via ${r.ruleId}: ${r.because}", style = MaterialTheme.typography.bodySmall)
            r.feedbackChip?.let { Text("You said: ${it.title}${r.feedbackNote?.let { n -> " ($n)" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onReopen != null) TextButton(onClick = onReopen) { Text("Reopen") }
                TextButton(onClick = onFeedback) { Text(if (r.feedbackChip == null) "Teach" else "Change") }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun RecordList(
    records: List<NotificationRecord>,
    modifier: Modifier,
    empty: String,
    onFeedback: (NotificationRecord) -> Unit,
    onReopen: ((NotificationRecord) -> Unit)?,
) {
    if (records.isEmpty()) {
        Column(modifier.padding(24.dp)) { Text(empty, style = MaterialTheme.typography.bodyMedium) }
        return
    }
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(records, key = { it.id }) { r ->
            RecordCard(r, onFeedback = { onFeedback(r) }, onReopen = onReopen?.let { f -> { f(r) } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FeedbackSheet(record: NotificationRecord, fromShade: Boolean, onDismiss: () -> Unit, onSubmit: (FeedbackChip, String?) -> Unit) {
    var chip by remember { mutableStateOf<FeedbackChip?>(null) }
    var note by remember { mutableStateOf("") }
    val chips = FeedbackChip.entries.filter { it.appliesTo == record.verdict }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(record.title ?: record.appLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    record.verdict == Verdict.DISMISS -> "The brain dismissed this. Was that right?"
                    fromShade -> "This is in your shade now. Should the brain dismiss ones like it, or keep them?"
                    else -> "The brain kept this and you swiped it. Should it have dismissed it?"
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                chips.forEach { c ->
                    AssistChip(onClick = { chip = c }, label = { Text(c.title) },
                        colors = if (chip == c) androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ) else androidx.compose.material3.AssistChipDefaults.assistChipColors())
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Why? (optional; kept with the rule)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { chip?.let { onSubmit(it, note) } }, enabled = chip != null) { Text("Teach the brain") }
        }
    }
}

@Composable
private fun RulesScreen(vm: BrainViewModel, modifier: Modifier) {
    val policy by vm.policy.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val state = vm.compileState
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Write a rule in your own words", style = MaterialTheme.typography.titleMedium)
        Text(
            "Examples: \"Dismiss shopping promotions but keep delivery updates.\"  \"Never touch anything from my bank.\"  " +
                "The sentence and the list of app names go to the model; notification contents never do.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            label = { Text("Rule") }, enabled = state !is CompileState.Compiling)
        if (!vm.canCompile) {
            Text("This build has no ANTHROPIC_API_KEY in local.properties, so typed rules cannot be compiled. Chips on Archive and Learn still work.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.compileInstruction(text) }, enabled = vm.canCompile && text.isNotBlank() && state !is CompileState.Compiling) {
                Text("Compile")
            }
            if (state is CompileState.Compiling) CircularProgressIndicator(Modifier.padding(8.dp))
        }
        when (state) {
            is CompileState.Failed -> Text("Could not compile: ${state.error}", color = MaterialTheme.colorScheme.error)
            is CompileState.Preview -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preview", style = MaterialTheme.typography.titleSmall)
                    Text(state.compiled.explanation, style = MaterialTheme.typography.bodySmall)
                    if (state.compiled.rules.isEmpty()) Text("No rules came out of that sentence.")
                    state.compiled.rules.forEach { r ->
                        Text("${r.action}  ${r.match().describe()}", style = MaterialTheme.typography.bodyMedium)
                        if (r.because.isNotBlank()) Text("  ${r.because}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.compiled.unresolved.isNotEmpty()) {
                        Text("Could not map: ${state.compiled.unresolved.joinToString()}", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.adoptInstruction(); text = "" }, enabled = state.compiled.rules.isNotEmpty()) { Text("Adopt") }
                        OutlinedButton(onClick = vm::discardPreview) { Text("Discard") }
                    }
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(8.dp))
        Text("Your rules (${policy.instructions.size})", style = MaterialTheme.typography.titleMedium)
        if (policy.instructions.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
        policy.instructions.asReversed().forEach { ins ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\u201c${ins.text}\u201d", style = MaterialTheme.typography.bodyMedium)
                    Text("${timeOf(ins.createdAt)} · ${ins.explanation}", style = MaterialTheme.typography.bodySmall)
                    policy.rules.filter { it.instructionId == ins.id }.forEach { r ->
                        Text("  ${r.action}  ${r.match.describe()}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.removeInstruction(ins.id) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun LabScreen(vm: BrainViewModel, modifier: Modifier) {
    val policy by vm.policy.collectAsStateWithLifecycle()
    val crashes by vm.crashes.collectAsStateWithLifecycle()
    var showNotes by remember { mutableStateOf(false) }
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Policy v${policy.version}", style = MaterialTheme.typography.titleMedium)
        Text(if (policy.revisedAt == 0L) "Seed" else "Revised ${timeOf(policy.revisedAt)}", style = MaterialTheme.typography.bodySmall)
        Text(policy.notes, style = MaterialTheme.typography.bodySmall)
        if (policy.rules.isEmpty()) Text("No rules. First match wins once there are some; anything unmatched is kept.")
        policy.rules.forEach { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${r.action}  ${r.match.describe()}", style = MaterialTheme.typography.titleSmall)
                    Text("${r.origin.name.lowercase()} · ${r.because}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::runReplay) { Text("Replay against archive") }
            OutlinedButton(onClick = vm::resetPolicy) { Text("Reset to seed") }
        }
        vm.replayReport?.let { Text(it.summary()) }

        Spacer(Modifier.height(8.dp))
        Text("Crashes and ANRs (${crashes.size})", style = MaterialTheme.typography.titleMedium)
        Text("Collected for Evolving App stage 3. Nothing acts on them yet.", style = MaterialTheme.typography.bodySmall)
        crashes.forEach { CrashCard(it) }

        Spacer(Modifier.height(8.dp))
        Text("Knowledge base", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Hide ARCHITECTURE.md" else "Show ARCHITECTURE.md (bundled at build)") }
        if (showNotes) Text(vm.architectureNotes, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CrashCard(c: CrashRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${c.reason} · ${timeOf(c.timestamp)} · ${c.source}", style = MaterialTheme.typography.titleSmall)
            Text("${c.versionName} @ ${c.gitSha}", style = MaterialTheme.typography.bodySmall)
            Text(c.description, style = MaterialTheme.typography.bodySmall)
            if (c.trace.isNotBlank()) Text(c.trace.lineSequence().take(6).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun timeOf(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

