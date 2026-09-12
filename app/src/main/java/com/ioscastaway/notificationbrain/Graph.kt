package com.ioscastaway.notificationbrain

import android.content.Context
import com.ioscastaway.notificationbrain.brain.NotificationClassifier
import com.ioscastaway.notificationbrain.brain.PolicyCourt
import com.ioscastaway.notificationbrain.brain.PolicyEngine
import com.ioscastaway.notificationbrain.brain.PolicyLearner
import com.ioscastaway.notificationbrain.data.BrainDatabase
import com.ioscastaway.notificationbrain.data.BrainRepository
import com.ioscastaway.notificationbrain.platform.ContentIntentCache
import com.ioscastaway.notificationbrain.platform.CrashCollector
import com.ioscastaway.notificationbrain.platform.FilePolicyStore
import com.ioscastaway.notificationbrain.platform.PolicyStore
import com.ioscastaway.notificationbrain.platform.SummaryNotifier

/** Manual constructor injection. Small enough that a DI framework would be more code than this. */
class Graph(context: Context) {
    private val app = context.applicationContext
    val database: BrainDatabase = BrainDatabase.open(app)
    val policyStore: PolicyStore = FilePolicyStore(app)
    val classifier: NotificationClassifier = PolicyEngine({ policyStore.current() }, app.packageName)
    val repository = BrainRepository(
        dao = database.dao(),
        policyStore = policyStore,
        learner = PolicyLearner(),
        court = PolicyCourt(app.packageName),
        ownPackage = app.packageName,
    )
    val contentIntentCache = ContentIntentCache(app)
    val summaryNotifier = SummaryNotifier(app)
    val crashCollector = CrashCollector(app)
}
